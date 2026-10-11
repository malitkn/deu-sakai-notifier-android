package tr.edu.deu.sakainotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!securePrefs.isMonitoringActive || !securePrefs.hasCredentials()) {
            return@withContext Result.success()
        }

        val accounts = securePrefs.getAccounts()
        if (accounts.isEmpty()) {
            return@withContext Result.success()
        }

        var anyNetworkFailure = false

        for (account in accounts) {
            try {
                stateManager.checkAccountSwitch(account.username)

                val fetchResult = apiClient.fetchNotifications(account.username, account.password)
                if (fetchResult.isFailure) {
                    val ex = fetchResult.exceptionOrNull()
                    if (ex is java.io.IOException) {
                        anyNetworkFailure = true
                    }
                    continue
                }

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
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is java.io.IOException) {
                    anyNetworkFailure = true
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
