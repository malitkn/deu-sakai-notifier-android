package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val accountUsername: String? = null,
    val isEmail: Boolean = false
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

            val isEmail = json.optBoolean("isEmail", false)

            val rawBodyCandidate = when {
                json.has("body") -> json.optString("body")
                json.has("message") -> json.optString("message")
                json.has("content") -> json.optString("content")
                json.has("text") -> json.optString("text")
                json.has("description") -> json.optString("description")
                json.has("announcementBody") -> json.optString("announcementBody")
                json.has("html") -> json.optString("html")
                else -> null
            }?.takeIf { it.isNotBlank() }

            val body = rawBodyCandidate?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

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
            }?.takeIf { it.isNotBlank() } ?: if (isEmail) "https://ogrmail.deu.edu.tr/" else "https://online.deu.edu.tr/portal"

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
                accountUsername = accountUsername,
                isEmail = isEmail
            )
        }

        /**
         * Normalizes epoch timestamps (float or integer, milliseconds or seconds)
         * or ISO-8601 strings into a human-readable date string (e.g. "11.10.2026 17:07").
         */
        /**
         * Robustly parses arbitrary date representations into epoch milliseconds:
         * 1. Epoch timestamps (numeric seconds or milliseconds, string or number)
         * 2. Relative times: "HH:mm" (today), "Bugün HH:mm", "Dün HH:mm", "Today HH:mm", "Yesterday HH:mm"
         * 3. Turkish month strings: "dd MMM yyyy HH:mm", "dd MMM HH:mm", "dd MMMM yyyy HH:mm" (with current year fallback)
         * 4. Short day/month: "dd.MM HH:mm", "dd/MM HH:mm"
         * 5. Standard formats: "dd.MM.yyyy HH:mm[:ss]", "dd/MM/yyyy HH:mm[:ss]", "dd-MM-yyyy HH:mm[:ss]", "dd.MM.yyyy", etc.
         * 6. ISO-8601 & SQL timestamps
         * 7. RFC 2822 email timestamps
         */
        fun parseDateToMillis(rawVal: Any?): Long? {
            if (rawVal == null || rawVal == JSONObject.NULL) return null

            val rawStr = rawVal.toString().trim()
            if (rawStr.isEmpty() || rawStr.equals("null", ignoreCase = true)) return null

            val turkeyZone = TimeZone.getTimeZone("GMT+3")

            // 1. Check if numeric (epoch timestamp in seconds or milliseconds)
            val numericTs = rawStr.toDoubleOrNull()
            if (numericTs != null && numericTs > 1e7) {
                return if (numericTs > 1e11) {
                    numericTs.toLong()
                } else {
                    (numericTs * 1000).toLong()
                }
            }

            // 2. Relative dates: "Bugün 14:30", "Bugün, 14:30", "Today 14:30"
            val todayMatch = Regex("""^(?:bugün|today)[,\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?${'$'}""", RegexOption.IGNORE_CASE).find(rawStr)
            if (todayMatch != null) {
                val cal = Calendar.getInstance(turkeyZone)
                val hour = todayMatch.groupValues[1].toInt()
                val min = todayMatch.groupValues[2].toInt()
                val sec = todayMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, sec)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            // 3. Relative dates: "Dün 14:30", "Dün, 14:30", "Yesterday 14:30"
            val yesterdayMatch = Regex("""^(?:dün|yesterday)[,\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?${'$'}""", RegexOption.IGNORE_CASE).find(rawStr)
            if (yesterdayMatch != null) {
                val cal = Calendar.getInstance(turkeyZone)
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val hour = yesterdayMatch.groupValues[1].toInt()
                val min = yesterdayMatch.groupValues[2].toInt()
                val sec = yesterdayMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, sec)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            // 4. Time only: "14:30", "09:15:00" (Roundcube default for emails received today)
            val timeOnlyMatch = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?${'$'}""").find(rawStr)
            if (timeOnlyMatch != null) {
                val cal = Calendar.getInstance(turkeyZone)
                val hour = timeOnlyMatch.groupValues[1].toInt()
                val min = timeOnlyMatch.groupValues[2].toInt()
                val sec = timeOnlyMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, sec)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            // 5. Turkish month names with or without year (e.g. "18 Eki 14:30", "18 Eki 2026 14:30", "18 Ekim 2026")
            val monthTextMatch = Regex("""^(\d{1,2})\s+([a-zA-ZçğıöşüÇĞİÖŞÜ]+)(?:\s+(\d{4}))?(?:[,\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?${'$'}""").find(rawStr)
            if (monthTextMatch != null) {
                val day = monthTextMatch.groupValues[1].toInt()
                val monthWord = monthTextMatch.groupValues[2].lowercase(Locale("tr", "TR"))
                val turkishMonths = mapOf(
                    "oca" to 0, "ocak" to 0, "jan" to 0, "january" to 0,
                    "şub" to 1, "şubat" to 1, "sub" to 1, "subat" to 1, "feb" to 1, "february" to 1,
                    "mar" to 2, "mart" to 2, "march" to 2,
                    "nis" to 3, "nisan" to 3, "apr" to 3, "april" to 3,
                    "may" to 4, "mayıs" to 4, "mayis" to 4,
                    "haz" to 5, "haziran" to 5, "jun" to 5, "june" to 5,
                    "tem" to 6, "temmuz" to 6, "jul" to 6, "july" to 6,
                    "ağu" to 7, "ağustos" to 7, "agu" to 7, "agustos" to 7, "aug" to 7, "august" to 7,
                    "eyl" to 8, "eylül" to 8, "eylul" to 8, "sep" to 8, "september" to 8,
                    "eki" to 9, "ekim" to 9, "oct" to 9, "october" to 9,
                    "kas" to 10, "kasım" to 10, "kasim" to 10, "nov" to 10, "november" to 10,
                    "ara" to 11, "aralık" to 11, "aralik" to 11, "dec" to 11, "december" to 11
                )
                val monthIdx = turkishMonths[monthWord]
                if (monthIdx != null) {
                    val cal = Calendar.getInstance(turkeyZone)
                    val year = monthTextMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                    val hour = monthTextMatch.groupValues.getOrNull(4)?.toIntOrNull() ?: 0
                    val min = monthTextMatch.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
                    val sec = monthTextMatch.groupValues.getOrNull(6)?.toIntOrNull() ?: 0
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, monthIdx)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, sec)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }

            // 6. Day.Month without year: "18.10 14:30" or "18/10 14:30"
            val shortDayMonthMatch = Regex("""^(\d{1,2})[./](\d{1,2})(?:[,\s]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?${'$'}""").find(rawStr)
            if (shortDayMonthMatch != null) {
                val day = shortDayMonthMatch.groupValues[1].toInt()
                val month = shortDayMonthMatch.groupValues[2].toInt() - 1
                if (month in 0..11 && day in 1..31) {
                    val cal = Calendar.getInstance(turkeyZone)
                    val hour = shortDayMonthMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                    val min = shortDayMonthMatch.groupValues.getOrNull(4)?.toIntOrNull() ?: 0
                    val sec = shortDayMonthMatch.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
                    cal.set(Calendar.MONTH, month)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, sec)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            }

            // 7. Comprehensive array of standard date and time formats
            val standardPatterns = arrayOf(
                // Turkish standard dot
                "dd.MM.yyyy HH:mm:ss",
                "dd.MM.yyyy HH:mm",
                "dd.MM.yyyy",
                // Slash notation
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy",
                // Dash notation
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd-MM-yyyy",
                // ISO-8601 / SQL
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
                // RFC 2822
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z",
                "dd MMM yyyy HH:mm:ss Z",
                "dd MMM yyyy HH:mm:ss"
            )

            for (pattern in standardPatterns) {
                try {
                    val parser = SimpleDateFormat(pattern, Locale.ROOT)
                    parser.timeZone = turkeyZone
                    val parsed = parser.parse(rawStr)
                    if (parsed != null) {
                        return parsed.time
                    }
                } catch (_: Exception) {
                }
            }

            return null
        }

        /**
         * Normalizes epoch timestamps, relative time strings or ISO-8601 strings
         * into a human-readable date string (e.g. "18.10.2026 17:07").
         */
        fun formatPublishedDate(rawVal: Any?): String? {
            val millis = parseDateToMillis(rawVal)
            if (millis != null) {
                return try {
                    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
                    sdf.timeZone = TimeZone.getTimeZone("GMT+3")
                    sdf.format(Date(millis))
                } catch (_: Exception) {
                    rawVal?.toString()?.trim()
                }
            }
            return rawVal?.toString()?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        }

        /**
         * Parses arbitrary date string or epoch into numeric milliseconds for sorting.
         * Guarantees that valid Turkish dates, relative dates, and epoch timestamps
         * are accurately converted rather than defaulting to 0L.
         */
        fun parsePublishedMillis(rawVal: Any?): Long {
            return parseDateToMillis(rawVal) ?: 0L
        }

        fun stripHtml(html: String): String {
            if (!html.contains("<") && !html.contains("&")) return html.trim()
            return html
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }

        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
