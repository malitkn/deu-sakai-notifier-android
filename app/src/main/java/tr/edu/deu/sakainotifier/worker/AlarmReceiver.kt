package tr.edu.deu.sakainotifier.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tr.edu.deu.sakainotifier.data.api.DebisClient
import tr.edu.deu.sakainotifier.data.api.DeuWebmailClient
import tr.edu.deu.sakainotifier.data.api.SakaiApiClient
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.storage.DebisGradeStateManager
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper

/**
 * BroadcastReceiver triggered by AlarmManager exact RTC alarms.
 * Pierces Doze Mode on Android 12, 13, 14 and Xiaomi MIUI / HyperOS.
 * Uses a safe 25-second partial WakeLock and goAsync() to execute
 * the lightweight 2-3 second HTTPS check before rescheduling the next alarm.
 */
class AlarmReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val securePrefs = SecurePreferencesManager(context)
        val gradeStateManager = DebisGradeStateManager(context)
        val checkSakai = securePrefs.isMonitoringActive
        val checkMail = securePrefs.isMailMonitoringActive
        val checkGrades = gradeStateManager.isGradeMonitoringActive()

        // If user stopped tracking or no accounts exist, do not poll and do not reschedule
        if ((!checkSakai && !checkMail && !checkGrades) || !securePrefs.hasCredentials()) {
            AlarmScheduler.cancel(context)
            return
        }

        val pendingResult = goAsync()

        // Acquire a temporary WakeLock with 25s safety ceiling to guarantee CPU remains
        // awake until network requests and notification dispatch finish.
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DEUSakaiNotifier:AlarmPollWakeLock"
        )?.apply {
            acquire(25 * 1000L)
        }

        receiverScope.launch {
            try {
                if (securePrefs.isInQuietHours()) {
                    // During night quiet hours (00:00 - 05:00), skip heavy polling & alerts,
                    // but send lightweight DEBİS Keep-Alive if session is active so PHPSESSID survives until morning
                    if (checkGrades) {
                        val debisClient = DebisClient()
                        for (account in securePrefs.getAccounts()) {
                            val cookie = gradeStateManager.getSessionCookie(account.username)
                            if (!cookie.isNullOrBlank()) {
                                try {
                                    debisClient.sendKeepAlive(cookie)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                } else {
                    performPoll(context, securePrefs, gradeStateManager, checkSakai, checkMail, checkGrades)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Chain the next exact alarm so monitoring continues uninterrupted
                if (securePrefs.isMonitoringActive ||
                    securePrefs.isMailMonitoringActive ||
                    gradeStateManager.isGradeMonitoringActive()
                ) {
                    AlarmScheduler.scheduleNext(context, securePrefs.pollIntervalMinutes)
                }

                // Release WakeLock safely and signal broadcast completion
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    private suspend fun performPoll(
        context: Context,
        securePrefs: SecurePreferencesManager,
        gradeStateManager: DebisGradeStateManager,
        checkSakai: Boolean,
        checkMail: Boolean,
        checkGrades: Boolean
    ) {
        val accounts = securePrefs.getAccounts()
        if (accounts.isEmpty()) return

        val stateManager = NotificationStateManager(context)
        val apiClient = SakaiApiClient()
        val webmailClient = DeuWebmailClient()
        val debisClient = DebisClient()

        for ((index, account) in accounts.withIndex()) {
            if (index > 0) {
                // Anti-throttle cooldown to prevent Roundcube brute-force lockout & WAF rate limits
                delay(1500)
            }
            stateManager.checkAccountSwitch(account.username)
            apiClient.invalidateSession()
            webmailClient.invalidateSession()

            // 1. Poll Sakai Announcements
            if (checkSakai) {
                try {
                    val fetchResult = apiClient.fetchNotifications(account.username, account.password)
                    if (fetchResult.isSuccess) {
                        val allNotifications = fetchResult.getOrDefault(emptyList())
                        val tagged = allNotifications.map { it.copy(accountUsername = account.username) }
                        val newNotifications = stateManager.filterNewNotifications(tagged)

                        if (newNotifications.isNotEmpty()) {
                            for (notif in newNotifications) {
                                NotificationHelper.showNotification(context, notif, account.username)
                            }
                            stateManager.markProcessed(newNotifications)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Poll Student Webmail (Only if emailAddress is configured)
            if (checkMail && !account.emailAddress.isNullOrBlank()) {
                try {
                    val mailResult = webmailClient.fetchUnreadEmails(
                        email = account.emailAddress,
                        pass = account.password,
                        studentUsername = account.username
                    )

                    if (mailResult.isSuccess) {
                        val unreadMails = mailResult.getOrDefault(emptyList())
                        val newMails = stateManager.filterNewEmailNotifications(unreadMails)

                        if (newMails.isNotEmpty()) {
                            NotificationHelper.showEmailNotifications(context, newMails, account.username)
                            stateManager.markProcessed(newMails)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Poll DEBİS Grades & Send Keep-Alive (if session cookie exists)
            val debisCookie = gradeStateManager.getSessionCookie(account.username)
            if (checkGrades && !debisCookie.isNullOrBlank()) {
                try {
                    debisClient.sendKeepAlive(debisCookie)

                    val coursesResult = debisClient.fetchCourseOptions(debisCookie)
                    if (coursesResult.isSuccess) {
                        val courseOptions = coursesResult.getOrDefault(emptyList())
                        val freshGrades = mutableListOf<CourseGrade>()

                        for (option in courseOptions) {
                            val detailResult = debisClient.fetchCourseGradeDetails(
                                sessionCookie = debisCookie,
                                courseOption = option,
                                studentNo = account.username.substringBefore("@")
                            )
                            if (detailResult.isSuccess) {
                                freshGrades.add(detailResult.getOrThrow())
                            }
                        }

                        if (freshGrades.isNotEmpty()) {
                            if (gradeStateManager.isFirstGradeBoot(account.username)) {
                                gradeStateManager.saveGradeSnapshot(account.username, freshGrades)
                                gradeStateManager.setFirstGradeBootCompleted(account.username)
                            } else {
                                val differences = gradeStateManager.detectDifferences(account.username, freshGrades)
                                for (diff in differences) {
                                    NotificationHelper.showGradeNotification(context, diff, account.username)
                                }
                            }
                        }
                    } else {
                        val ex = coursesResult.exceptionOrNull()
                        if (ex is IllegalStateException) {
                            val shouldNotify = !gradeStateManager.wasSessionExpiredNotified(account.username)
                            gradeStateManager.saveSessionCookie(account.username, null)
                            if (shouldNotify) {
                                gradeStateManager.markSessionExpiredNotified(account.username)
                                NotificationHelper.showDebisSessionExpiredNotification(context, account.username)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        securePrefs.lastCheckTimestamp = System.currentTimeMillis()
    }
}
