package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Resilient data model representing a DEU Sakai notification.
 */
data class SakaiNotification(
    val sakaiId: String,
    val contentHash: String,
    val title: String,
    val body: String?,
    val siteTitle: String?,
    val url: String?,
    val publishedAt: String?,
    val rawJson: String
) {
    companion object {
        /**
         * Resolves a SakaiNotification from arbitrary JSON structures.
         * Guarantees deterministic SHA-256 hash and fallback ID resolution.
         */
        fun fromJsonObject(json: JSONObject): SakaiNotification {
            val rawString = json.toString()
            val contentHash = sha256(rawString)

            val sakaiId = when {
                json.has("id") -> json.optString("id")
                json.has("notificationId") -> json.optString("notificationId")
                json.has("notifId") -> json.optString("notifId")
                json.has("uuid") -> json.optString("uuid")
                json.has("event") -> json.optString("event")
                else -> contentHash.take(16)
            }

            val title = when {
                json.has("title") -> json.optString("title")
                json.has("subject") -> json.optString("subject")
                json.has("name") -> json.optString("name")
                json.has("eventName") -> json.optString("eventName")
                else -> "Yeni Sakai Bildirimi"
            }

            val body = when {
                json.has("body") -> json.optString("body")
                json.has("text") -> json.optString("text")
                json.has("content") -> json.optString("content")
                json.has("message") -> json.optString("message")
                json.has("description") -> json.optString("description")
                else -> null
            }?.takeIf { it.isNotBlank() }

            val siteTitle = when {
                json.has("siteTitle") -> json.optString("siteTitle")
                json.has("contextTitle") -> json.optString("contextTitle")
                json.has("siteName") -> json.optString("siteName")
                json.has("courseName") -> json.optString("courseName")
                else -> null
            }?.takeIf { it.isNotBlank() }

            val url = when {
                json.has("url") -> json.optString("url")
                json.has("link") -> json.optString("link")
                json.has("portalUrl") -> json.optString("portalUrl")
                else -> null
            }?.takeIf { it.isNotBlank() }

            val publishedAt = when {
                json.has("eventDate") -> json.optString("eventDate")
                json.has("createdAt") -> json.optString("createdAt")
                json.has("publishedDate") -> json.optString("publishedDate")
                json.has("date") -> json.optString("date")
                else -> null
            }?.takeIf { it.isNotBlank() }

            return SakaiNotification(
                sakaiId = sakaiId,
                contentHash = contentHash,
                title = title.trim(),
                body = body?.trim(),
                siteTitle = siteTitle?.trim(),
                url = url?.trim(),
                publishedAt = publishedAt?.trim(),
                rawJson = rawString
            )
        }

        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
