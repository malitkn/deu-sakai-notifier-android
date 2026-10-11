package tr.edu.deu.sakainotifier.data.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import tr.edu.deu.sakainotifier.data.model.SakaiNotification

class NotificationStateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "deu_sakai_notification_state"
        private const val KEY_CURRENT_USER = "key_current_username"
        private const val KEY_SEEN_IDS = "key_seen_notification_ids"
        private const val KEY_SEEN_HASHES = "key_seen_content_hashes"
        private const val KEY_IS_FIRST_BOOT = "key_is_first_boot"
        const val KEY_RECENT_NOTIFICATIONS = "key_recent_notifications_json"
    }

    val isFirstBoot: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_BOOT, true)

    /**
     * Checks if the active student account has changed.
     * If changed, resets seen notification history so the new account undergoes
     * a clean Silent Bootstrap without alert spam or cross-account contamination.
     */
    fun checkAccountSwitch(newUsername: String): Boolean {
        val currentUsername = prefs.getString(KEY_CURRENT_USER, "") ?: ""
        val normalized = newUsername.trim().lowercase()
        if (normalized.isNotBlank() && currentUsername.isNotBlank() && currentUsername != normalized) {
            prefs.edit()
                .putString(KEY_CURRENT_USER, normalized)
                .remove(KEY_SEEN_IDS)
                .remove(KEY_SEEN_HASHES)
                .putBoolean(KEY_IS_FIRST_BOOT, true)
                .remove(KEY_RECENT_NOTIFICATIONS)
                .apply()
            return true
        } else if (currentUsername.isBlank() && normalized.isNotBlank()) {
            prefs.edit().putString(KEY_CURRENT_USER, normalized).apply()
        }
        return false
    }

    fun resetState() {
        prefs.edit().clear().apply()
    }

    private fun getSeenIds(): MutableSet<String> {
        return prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun getSeenHashes(): MutableSet<String> {
        return prefs.getStringSet(KEY_SEEN_HASHES, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    /**
     * Performs silent bootstrap on initial installation:
     * saves existing notification IDs so they are not broadcast as alerts.
     */
    fun performSilentBootstrap(notifications: List<SakaiNotification>) {
        val seenIds = getSeenIds()
        val seenHashes = getSeenHashes()

        for (notif in notifications) {
            seenIds.add(notif.sakaiId)
            seenHashes.add(notif.contentHash)
        }

        prefs.edit()
            .putStringSet(KEY_SEEN_IDS, seenIds)
            .putStringSet(KEY_SEEN_HASHES, seenHashes)
            .putBoolean(KEY_IS_FIRST_BOOT, false)
            .apply()

        saveRecentNotifications(notifications.take(20))
    }

    /**
     * Filters incoming notifications to only return items that have never been seen.
     */
    fun filterNewNotifications(notifications: List<SakaiNotification>): List<SakaiNotification> {
        if (isFirstBoot) {
            val seenIds = getSeenIds()
            val seenHashes = getSeenHashes()

            for (notif in notifications) {
                seenIds.add(notif.sakaiId)
                seenHashes.add(notif.contentHash)
            }

            // Mark state as initialized
            prefs.edit()
                .putStringSet(KEY_SEEN_IDS, seenIds)
                .putStringSet(KEY_SEEN_HASHES, seenHashes)
                .putBoolean(KEY_IS_FIRST_BOOT, false)
                .apply()

            // Initial installation / account switch: notify user about active announcements (up to 5)
            // markProcessed will handle saving with strict deduplication
            return notifications.take(5)
        }

        val seenIds = getSeenIds()
        val seenHashes = getSeenHashes()

        return notifications.filter { notif ->
            !seenIds.contains(notif.sakaiId) && !seenHashes.contains(notif.contentHash)
        }
    }

    /**
     * Marks notifications as processed.
     */
    fun markProcessed(notifications: List<SakaiNotification>) {
        if (notifications.isEmpty()) return

        val seenIds = getSeenIds()
        val seenHashes = getSeenHashes()

        for (notif in notifications) {
            seenIds.add(notif.sakaiId)
            seenHashes.add(notif.contentHash)
        }

        prefs.edit()
            .putStringSet(KEY_SEEN_IDS, seenIds)
            .putStringSet(KEY_SEEN_HASHES, seenHashes)
            .apply()

        // Prepend new notifications to recent cache with strict deduplication
        val currentRecent = getRecentNotifications()
        val combined = (notifications + currentRecent).distinctBy {
            if (it.sakaiId.isNotBlank()) it.sakaiId else it.contentHash
        }
        saveRecentNotifications(combined.take(30))
    }

    private fun saveRecentNotifications(items: List<SakaiNotification>) {
        val distinctItems = items.distinctBy {
            if (it.sakaiId.isNotBlank()) it.sakaiId else it.contentHash
        }
        val array = JSONArray()
        for (item in distinctItems) {
            val obj = JSONObject()
            obj.put("id", item.sakaiId)
            obj.put("hash", item.contentHash)
            obj.put("title", item.title)
            obj.put("body", item.body)
            obj.put("siteTitle", item.siteTitle)
            obj.put("url", item.url)
            obj.put("publishedAt", item.publishedAt)
            array.put(obj)
        }
        prefs.edit().putString(KEY_RECENT_NOTIFICATIONS, array.toString()).apply()
    }

    fun getRecentNotifications(): List<SakaiNotification> {
        val raw = prefs.getString(KEY_RECENT_NOTIFICATIONS, "[]") ?: "[]"
        val list = mutableListOf<SakaiNotification>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    SakaiNotification(
                        sakaiId = obj.optString("id"),
                        contentHash = obj.optString("hash"),
                        title = obj.optString("title", "Bildirim"),
                        body = obj.optString("body").takeIf { it.isNotBlank() },
                        siteTitle = obj.optString("siteTitle").takeIf { it.isNotBlank() },
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() },
                        rawJson = obj.toString()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.distinctBy {
            if (it.sakaiId.isNotBlank()) it.sakaiId else it.contentHash
        }
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
