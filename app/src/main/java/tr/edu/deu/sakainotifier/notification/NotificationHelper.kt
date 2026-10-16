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
    const val CHANNEL_ID_SAKAI = "deu_sakai_announcements"
    private const val CHANNEL_NAME_SAKAI = "DEU Sakai Duyuruları"
    private const val CHANNEL_DESC_SAKAI = "Sakai LMS üzerindeki yeni duyuru ve ödev bildirimleri"

    const val CHANNEL_ID_MAIL = "deu_student_webmail"
    private const val CHANNEL_NAME_MAIL = "DEU Öğrenci E-postası"
    private const val CHANNEL_DESC_MAIL = "DEU öğrenci e-posta kutusuna gelen yeni iletiler için bildirimler"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val sakaiChannel = NotificationChannel(
                CHANNEL_ID_SAKAI,
                CHANNEL_NAME_SAKAI,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_SAKAI
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(sakaiChannel)

            val mailChannel = NotificationChannel(
                CHANNEL_ID_MAIL,
                CHANNEL_NAME_MAIL,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_MAIL
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(mailChannel)
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

        val channelId = if (notification.isEmail) CHANNEL_ID_MAIL else CHANNEL_ID_SAKAI
        val groupKey = if (notification.isEmail) {
            "group_deu_mail_${accountPrefix.orEmpty()}"
        } else {
            "group_deu_sakai_${accountPrefix.orEmpty()}"
        }

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

        val displayBody = notification.body?.takeIf { it.isNotBlank() } ?: if (notification.isEmail) {
            "Yeni bir öğrenci e-postası alındı."
        } else {
            "Yeni bir DEU Sakai duyurusu yayınlandı."
        }

        // Android SystemUI BigTextStyle safe maximum boundary (~4500 chars) to prevent TransactionTooLargeException while displaying the absolute maximum possible announcement content in notification shade
        val MAX_SYSTEM_NOTIFICATION_CHARS = 4500
        val notificationBigText = if (displayBody.length > MAX_SYSTEM_NOTIFICATION_CHARS) {
            displayBody.take(MAX_SYSTEM_NOTIFICATION_CHARS) + "\n\n...(Tamamını görmek için dokunun)"
        } else {
            displayBody
        }

        val summaryCategory = if (notification.isEmail) {
            "DEU Öğrenci E-postası"
        } else {
            notification.siteTitle?.ifBlank { "DEU Sakai" } ?: "DEU Sakai"
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(notificationBigText)
            .setBigContentTitle(displayTitle)
            .setSummaryText(summaryCategory)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (notification.isEmail) android.R.drawable.ic_dialog_email else android.R.drawable.ic_popup_reminder)
            .setContentTitle(displayTitle)
            .setContentText(displayBody.take(140))
            .setStyle(bigTextStyle)
            .setGroup(groupKey)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(uniqueId, builder.build())
        } catch (e: SecurityException) {
            // Android 13 POST_NOTIFICATIONS permission not granted yet
            e.printStackTrace()
        }
    }

    /**
     * Intelligently dispatches email notifications.
     * If 1 to 3 emails: sends individual clean notification cards grouped together.
     * If more than 3 emails (burst / bulk mail): sends a single consolidated InboxStyle summary
     * card to avoid spamming the user's phone with dozens of repeated alerts.
     */
    fun showEmailNotifications(
        context: Context,
        emails: List<SakaiNotification>,
        accountUsername: String? = null
    ) {
        if (emails.isEmpty()) return

        createNotificationChannel(context)

        val accountPrefix = (accountUsername ?: emails.firstOrNull()?.accountUsername)
            ?.substringBefore("@")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()

        if (emails.size <= 3) {
            for (email in emails) {
                showNotification(context, email, accountUsername)
            }
        } else {
            // Consolidated summary notification for burst emails
            val summaryTitle = if (accountPrefix.isNotBlank()) {
                "✉️ $accountPrefix: ${emails.size} Yeni E-posta"
            } else {
                "✉️ ${emails.size} Yeni E-posta"
            }

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(summaryTitle)

            for (email in emails.take(5)) {
                val sender = email.siteTitle?.ifBlank { "Öğrenci Maili" } ?: "Öğrenci Maili"
                inboxStyle.addLine("[$sender] ${email.title}")
            }

            if (emails.size > 5) {
                inboxStyle.setSummaryText("+${emails.size - 5} diğer e-posta")
            } else {
                inboxStyle.setSummaryText("${emails.size} yeni ileti")
            }

            val webmailUrl = "https://ogrmail.deu.edu.tr/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webmailUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                "summary_${accountPrefix}".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val firstSender = emails.first().siteTitle?.ifBlank { "Öğrenci Maili" } ?: "Öğrenci Maili"
            val summaryBody = "${emails.size} yeni e-posta alındı. İlk: [$firstSender] ${emails.first().title}"

            val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID_MAIL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(summaryTitle)
                .setContentText(summaryBody)
                .setStyle(inboxStyle)
                .setGroup("group_deu_mail_$accountPrefix")
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            try {
                val notificationManager = NotificationManagerCompat.from(context)
                val summaryId = "mail_batch_summary_$accountPrefix".hashCode()
                notificationManager.notify(summaryId, summaryBuilder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}
