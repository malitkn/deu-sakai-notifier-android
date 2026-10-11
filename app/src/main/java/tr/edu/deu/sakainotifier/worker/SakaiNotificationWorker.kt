package tr.edu.deu.sakainotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tr.edu.deu.sakainotifier.data.api.DeuWebmailClient
import tr.edu.deu.sakainotifier.data.api.SakaiApiClient
import tr.edu.deu.sakainotifier.data.storage.NotificationStateManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import tr.edu.deu.sakainotifier.notification.NotificationHelper

class SakaiNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val securePrefs = SecurePreferencesManager(context)
    private val stateManager = NotificationStateManager(context)
    private val apiClient = SakaiApiClient()
    private val webmailClient = DeuWebmailClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val checkSakai = securePrefs.isMonitoringActive
        val checkMail = securePrefs.isMailMonitoringActive

        if ((!checkSakai && !checkMail) || !securePrefs.hasCredentials()) {
            return@withContext Result.success()
        }

        val accounts = securePrefs.getAccounts()
        if (accounts.isEmpty()) {
            return@withContext Result.success()
        }

        var anyNetworkFailure = false

        for (account in accounts) {
            stateManager.checkAccountSwitch(account.username)

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
        }

        securePrefs.lastCheckTimestamp = System.currentTimeMillis()

        if (anyNetworkFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
