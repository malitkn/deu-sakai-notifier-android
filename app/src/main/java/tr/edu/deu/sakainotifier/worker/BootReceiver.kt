package tr.edu.deu.sakainotifier.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val securePrefs = SecurePreferencesManager(context)
            val gradeStateManager = tr.edu.deu.sakainotifier.data.storage.DebisGradeStateManager(context)
            val anyServiceActive = securePrefs.isMonitoringActive ||
                securePrefs.isMailMonitoringActive ||
                gradeStateManager.isGradeMonitoringActive()
            if (anyServiceActive && securePrefs.hasCredentials()) {
                AlarmScheduler.scheduleNext(context, securePrefs.pollIntervalMinutes)
                WorkScheduler.schedule(context, securePrefs.pollIntervalMinutes)
            }
        }
    }
}
