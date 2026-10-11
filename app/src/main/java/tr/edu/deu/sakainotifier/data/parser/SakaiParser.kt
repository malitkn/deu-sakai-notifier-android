package tr.edu.deu.sakainotifier.data.parser

import org.json.JSONArray
import org.json.JSONObject
import tr.edu.deu.sakainotifier.data.model.SakaiNotification

object SakaiParser {
    /**
     * Parses raw response from Sakai /api/users/me/notifications.
     * Supports:
     * - Empty array: `[]`
     * - Array of objects: `[{...}, {...}]`
     * - Wrapped objects: `{"notifications": [...]}` or `{"data": [...]}`
     */
    fun parseNotifications(rawResponse: String): List<SakaiNotification> {
        val trimmed = rawResponse.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "null") {
            return emptyList()
        }

        val notifications = mutableListOf<SakaiNotification>()

        try {
            if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i)
                    if (obj != null) {
                        notifications.add(SakaiNotification.fromJsonObject(obj))
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val jsonObject = JSONObject(trimmed)
                val candidateKeys = listOf("notifications", "data", "items", "results")
                var foundArray: JSONArray? = null

                for (key in candidateKeys) {
                    if (jsonObject.has(key) && jsonObject.optJSONArray(key) != null) {
                        foundArray = jsonObject.optJSONArray(key)
                        break
                    }
                }

                if (foundArray != null) {
                    for (i in 0 until foundArray.length()) {
                        val obj = foundArray.optJSONObject(i)
                        if (obj != null) {
                            notifications.add(SakaiNotification.fromJsonObject(obj))
                        }
                    }
                } else {
                    // Single notification object wrapped
                    notifications.add(SakaiNotification.fromJsonObject(jsonObject))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return notifications
    }
}
