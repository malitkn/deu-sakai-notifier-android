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

        val username = securePrefs.username
        val password = securePrefs.password

        val fetchResult = apiClient.fetchNotifications(username, password)
        if (fetchResult.isFailure) {
            // Transient network failure: retry later with exponential backoff
            return@withContext Result.retry()
        }

        val allNotifications = fetchResult.getOrDefault(emptyList())

        // Filter out previously seen notifications and handle Silent Bootstrap
        val newNotifications = stateManager.filterNewNotifications(allNotifications)

        if (newNotifications.isNotEmpty()) {
            for (notif in newNotifications) {
                NotificationHelper.showNotification(context, notif)
            }
            stateManager.markProcessed(newNotifications)
        }

        securePrefs.lastCheckTimestamp = System.currentTimeMillis()
        Result.success()
    }
}
