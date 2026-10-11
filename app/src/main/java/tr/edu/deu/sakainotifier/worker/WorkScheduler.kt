package tr.edu.deu.sakainotifier.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val UNIQUE_WORK_NAME = "DEU_SAKAI_PERIODIC_MONITOR"

    /**
     * Schedules periodic background polling using WorkManager.
     * Enforces minimum 20 minutes (1200 seconds) to prevent server overload.
     */
    fun schedule(context: Context, intervalMinutes: Int, forceUpdate: Boolean = false) {
        val validatedInterval = if (intervalMinutes < SecurePreferencesManager.MIN_POLL_INTERVAL_MINUTES) {
            SecurePreferencesManager.MIN_POLL_INTERVAL_MINUTES
        } else {
            intervalMinutes
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<SakaiNotificationWorker>(
            validatedInterval.toLong(),
            TimeUnit.MINUTES,
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            periodicWorkRequest
        )
    }

    /**
     * Cancels scheduled periodic monitoring.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
