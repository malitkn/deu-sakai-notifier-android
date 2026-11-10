package tr.edu.deu.sakainotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tr.edu.deu.sakainotifier.data.api.DebisClient
import tr.edu.deu.sakainotifier.data.api.DeuWebmailClient
import tr.edu.deu.sakainotifier.data.api.SakaiApiClient
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.storage.DebisGradeStateManager
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper

class SakaiNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val securePrefs = SecurePreferencesManager(context)
    private val stateManager = NotificationStateManager(context)
    private val gradeStateManager = DebisGradeStateManager(context)
    private val apiClient = SakaiApiClient()
    private val webmailClient = DeuWebmailClient()
    private val debisClient = DebisClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val checkSakai = securePrefs.isMonitoringActive
        val checkMail = securePrefs.isMailMonitoringActive
        val checkGrades = gradeStateManager.isGradeMonitoringActive()

        if ((!checkSakai && !checkMail && !checkGrades) || !securePrefs.hasCredentials()) {
            return@withContext Result.success()
        }

        val accounts = securePrefs.getAccounts()
        if (accounts.isEmpty()) {
            return@withContext Result.success()
        }

        // Quiet hours protection: skip heavy polling & notifications between 00:00 - 05:00,
        // but still send lightweight DEBİS Keep-Alive heartbeat if a session is active so PHPSESSID doesn't expire overnight!
        if (securePrefs.isInQuietHours()) {
            if (checkGrades) {
                for (account in accounts) {
                    val debisCookie = gradeStateManager.getSessionCookie(account.username)
                    if (!debisCookie.isNullOrBlank()) {
                        try {
                            debisClient.sendKeepAlive(debisCookie)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            return@withContext Result.success()
        }

        var anyNetworkFailure = false

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
                        val taggedNotifications = allNotifications.map { it.copy(accountUsername = account.username) }

                        // Filter out previously seen notifications and handle Silent Bootstrap for this account
                        val newNotifications = stateManager.filterNewNotifications(taggedNotifications)

                        if (newNotifications.isNotEmpty()) {
                            for (notif in newNotifications) {
                                NotificationHelper.showNotification(context, notif, account.username)
                            }
                            stateManager.markProcessed(newNotifications)
                        }
                    } else {
                        val ex = fetchResult.exceptionOrNull()
                        if (ex is java.io.IOException) {
                            anyNetworkFailure = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (e is java.io.IOException) {
                        anyNetworkFailure = true
                    }
                }
            }

            // 2. Poll Student Webmail (Only if emailAddress is explicitly configured)
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
                    } else {
                        // Mail server outage should not break Sakai notifications.
                        // It will simply re-attempt on the next regular 20-min cycle.
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Poll DEBİS Grades & Send Keep-Alive (if session cookie exists)
            val debisCookie = gradeStateManager.getSessionCookie(account.username)
            if (gradeStateManager.isGradeMonitoringActive() && !debisCookie.isNullOrBlank()) {
                try {
                    // Send lightweight keep-alive heartbeat to reset PHP session idle timer
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
                                // Silent bootstrap: save initial snapshot without firing alerts
                                gradeStateManager.saveGradeSnapshot(account.username, freshGrades)
                                gradeStateManager.setFirstGradeBootCompleted(account.username)
                            } else {
                                // Diff against local snapshot and notify for new/changed grades
                                val differences = gradeStateManager.detectDifferences(account.username, freshGrades)
                                for (diff in differences) {
                                    NotificationHelper.showGradeNotification(context, diff, account.username)
                                }
                            }
                        }
                    } else {
                        val ex = coursesResult.exceptionOrNull()
                        if (ex is IllegalStateException) {
                            // Session expired: clear invalid cookie and notify student once to refresh CAPTCHA
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

        if (anyNetworkFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
