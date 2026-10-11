package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val rawJson: String,
    val accountUsername: String? = null
) {
    companion object {
        /**
         * Resolves a SakaiNotification from arbitrary JSON structures.
         * Guarantees deterministic SHA-256 hash and fallback ID resolution.
         */
        fun fromJsonObject(json: JSONObject, accountUsername: String? = null): SakaiNotification {
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

            val rawDate = when {
                json.has("formattedEventDate") -> json.opt("formattedEventDate")
                json.has("formattedDate") -> json.opt("formattedDate")
                json.has("displayDate") -> json.opt("displayDate")
                json.has("eventDate") -> json.opt("eventDate")
                json.has("createdAt") -> json.opt("createdAt")
                json.has("publishedDate") -> json.opt("publishedDate")
                json.has("date") -> json.opt("date")
                json.has("dateTime") -> json.opt("dateTime")
                else -> null
            }

            val publishedAt = formatPublishedDate(rawDate)

            return SakaiNotification(
                sakaiId = sakaiId,
                contentHash = contentHash,
                title = title.trim(),
                body = body?.trim(),
                siteTitle = siteTitle?.trim(),
                url = url?.trim(),
                publishedAt = publishedAt?.trim(),
                rawJson = rawString,
                accountUsername = accountUsername
            )
        }

        /**
         * Normalizes epoch timestamps (float or integer, milliseconds or seconds)
         * or ISO-8601 strings into a human-readable date string (e.g. "11.10.2026 17:07").
         */
        fun formatPublishedDate(rawVal: Any?): String? {
            if (rawVal == null || rawVal == JSONObject.NULL) return null

            val rawStr = rawVal.toString().trim()
            if (rawStr.isEmpty() || rawStr.equals("null", ignoreCase = true)) return null

            // Check if numeric (epoch timestamp)
            val numericTs = rawStr.toDoubleOrNull()
            if (numericTs != null && numericTs > 1e7) {
                val millis = if (numericTs > 1e11) {
                    numericTs.toLong()
                } else {
                    (numericTs * 1000).toLong()
                }
                return try {
                    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
                    sdf.timeZone = TimeZone.getTimeZone("GMT+3")
                    sdf.format(Date(millis))
                } catch (_: Exception) {
                    rawStr
                }
            }

            // Check ISO-8601 / standard SQL timestamp patterns
            if (rawStr.contains("T") || (rawStr.length >= 19 && rawStr.getOrNull(4) == '-' && rawStr.getOrNull(7) == '-')) {
                val patterns = arrayOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss"
                )
                for (pattern in patterns) {
                    try {
                        val parser = SimpleDateFormat(pattern, Locale.ROOT)
                        val parsedDate = parser.parse(rawStr)
                        if (parsedDate != null) {
                            val outFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
                            outFormat.timeZone = TimeZone.getTimeZone("GMT+3")
                            return outFormat.format(parsedDate)
                        }
                    } catch (_: Exception) {
                        // try next pattern
                    }
                }
            }

            return rawStr
        }

        fun parsePublishedMillis(rawVal: Any?): Long {
            if (rawVal == null || rawVal == JSONObject.NULL) return 0L
            val rawStr = rawVal.toString().trim()
            if (rawStr.isEmpty() || rawStr.equals("null", ignoreCase = true)) return 0L

            val numericTs = rawStr.toDoubleOrNull()
            if (numericTs != null && numericTs > 1e7) {
                return if (numericTs > 1e11) numericTs.toLong() else (numericTs * 1000).toLong()
            }

            if (rawStr.contains("T") || (rawStr.length >= 19 && rawStr.getOrNull(4) == '-' && rawStr.getOrNull(7) == '-')) {
                val patterns = arrayOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd HH:mm:ss"
                )
                for (pattern in patterns) {
                    try {
                        val parser = SimpleDateFormat(pattern, Locale.ROOT)
                        val parsedDate = parser.parse(rawStr)
                        if (parsedDate != null) {
                            return parsedDate.time
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            return 0L
        }

        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
