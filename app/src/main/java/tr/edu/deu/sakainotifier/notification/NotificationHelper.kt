package tr.edu.deu.sakainotifier.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tr.edu.deu.sakainotifier.MainActivity
import tr.edu.deu.sakainotifier.data.model.SakaiNotification

object NotificationHelper {

    const val CHANNEL_ID = "deu_sakai_announcements"
    private const val CHANNEL_NAME = "DEU Sakai Duyuruları"
    private const val CHANNEL_DESCRIPTION = "Sakai LMS üzerindeki yeni duyuru ve ödev bildirimleri"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        notification: SakaiNotification,
        accountUsername: String? = notification.accountUsername
    ) {
        createNotificationChannel(context)

        // PendingIntent to open browser URL if available, else open MainActivity
        val intent = if (!notification.url.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(notification.url))
        } else {
            Intent(context, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val accountPrefix = (accountUsername ?: notification.accountUsername)
            ?.substringBefore("@")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val uniqueId = "${accountPrefix.orEmpty()}_${notification.sakaiId}".hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            uniqueId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format for Email: "✉️ <studentNo>: [<Sender>] <Subject>"
        // Format for Sakai: "<studentNo>: [<Course>] <Title>"
        val userPrefix = when {
            notification.isEmail && !accountPrefix.isNullOrBlank() -> "✉️ $accountPrefix: "
            notification.isEmail -> "✉️ "
            !accountPrefix.isNullOrBlank() -> "$accountPrefix: "
            else -> ""
        }

        val displayTitle = if (!notification.siteTitle.isNullOrBlank()) {
            "${userPrefix}[${notification.siteTitle}] ${notification.title}"
        } else {
            "${userPrefix}${notification.title}"
        }

        val displayBody = notification.body ?: if (notification.isEmail) {
            "Yeni bir öğrenci e-postası alındı."
        } else {
            "Yeni bir DEU Sakai duyurusu yayınlandı."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notification.sakaiId.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Android 13 POST_NOTIFICATIONS permission not granted yet
            e.printStackTrace()
        }
    }
}
