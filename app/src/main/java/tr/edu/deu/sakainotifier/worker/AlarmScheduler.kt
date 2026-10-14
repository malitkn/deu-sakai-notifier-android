package tr.edu.deu.sakainotifier.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import tr.edu.deu.sakainotifier.data.storage.SecurePreferencesManager

object AlarmScheduler {

    const val ACTION_CHECK_NOTIFICATIONS = "tr.edu.deu.sakainotifier.ACTION_CHECK_NOTIFICATIONS"
    private const val REQUEST_CODE_ALARM = 42001

    /**
     * Schedules the next exact hardware alarm.
     * Enforces the architectural minimum interval of 20 minutes (1200 seconds).
     * Uses setExactAndAllowWhileIdle to pierce Doze Mode on Android 12, 13, 14,
     * and Xiaomi MIUI / HyperOS.
     */
    fun scheduleNext(context: Context, intervalMinutes: Int) {
        val validatedInterval = if (intervalMinutes < SecurePreferencesManager.MIN_POLL_INTERVAL_MINUTES) {
            SecurePreferencesManager.MIN_POLL_INTERVAL_MINUTES
        } else {
            intervalMinutes
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + (validatedInterval * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CHECK_NOTIFICATIONS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    AlarmManagerCompat.setExactAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact idle alarm if exact alarm permission was revoked by user
                    AlarmManagerCompat.setAndAllowWhileIdle(
                        alarmManager,
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // In case of permission check race condition on Android 12+
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Cancels any pending scheduled alarm.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CHECK_NOTIFICATIONS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Checks whether an alarm is actively scheduled in the system.
     */
    fun isScheduled(context: Context): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CHECK_NOTIFICATIONS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        return pendingIntent != null
    }
}
