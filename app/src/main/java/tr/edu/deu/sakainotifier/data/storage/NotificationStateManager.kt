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
        private const val KEY_IS_FIRST_MAIL_BOOT = "key_is_first_mail_boot"
        const val KEY_RECENT_NOTIFICATIONS = "key_recent_notifications_json"
    }

    var currentUser: String = prefs.getString(KEY_CURRENT_USER, "") ?: ""
        private set

    init {
        if (currentUser.isNotBlank()) {
            migrateLegacyKeysIfNeeded(currentUser)
        }
    }

    private fun getAccountKey(baseKey: String): String {
        return if (currentUser.isNotBlank()) {
            "${baseKey}_${currentUser}"
        } else {
            baseKey
        }
    }

    private fun migrateLegacyKeysIfNeeded(user: String) {
        val userFirstBootKey = "${KEY_IS_FIRST_BOOT}_$user"
        if (!prefs.contains(userFirstBootKey) && prefs.contains(KEY_IS_FIRST_BOOT)) {
            val legacyFirstBoot = prefs.getBoolean(KEY_IS_FIRST_BOOT, true)
            val legacyMailFirstBoot = prefs.getBoolean(KEY_IS_FIRST_MAIL_BOOT, true)
            val legacyIds = prefs.getStringSet(KEY_SEEN_IDS, emptySet())
            val legacyHashes = prefs.getStringSet(KEY_SEEN_HASHES, emptySet())
            val legacyRecent = prefs.getString(KEY_RECENT_NOTIFICATIONS, "[]")

            prefs.edit()
                .putBoolean(userFirstBootKey, legacyFirstBoot)
                .putBoolean("${KEY_IS_FIRST_MAIL_BOOT}_$user", legacyMailFirstBoot)
                .putStringSet("${KEY_SEEN_IDS}_$user", legacyIds)
                .putStringSet("${KEY_SEEN_HASHES}_$user", legacyHashes)
                .putString("${KEY_RECENT_NOTIFICATIONS}_$user", legacyRecent)
                .apply()
        }
    }

    val isFirstBoot: Boolean
        get() = prefs.getBoolean(getAccountKey(KEY_IS_FIRST_BOOT), true)

    val isFirstMailBoot: Boolean
        get() = prefs.getBoolean(getAccountKey(KEY_IS_FIRST_MAIL_BOOT), true)

    /**
     * Checks if the active student account has changed.
     * Switches the active namespace to the new student account without destroying
     * the previous account's seen history or cache.
     */
    fun checkAccountSwitch(newUsername: String): Boolean {
        val normalized = newUsername.trim().lowercase()
        if (normalized.isNotBlank() && currentUser != normalized) {
            currentUser = normalized
            migrateLegacyKeysIfNeeded(normalized)
            prefs.edit().putString(KEY_CURRENT_USER, normalized).apply()
            return true
        } else if (currentUser.isBlank() && normalized.isNotBlank()) {
            currentUser = normalized
            migrateLegacyKeysIfNeeded(normalized)
            prefs.edit().putString(KEY_CURRENT_USER, normalized).apply()
        }
        return false
    }

    fun resetState() {
        if (currentUser.isNotBlank()) {
            prefs.edit()
                .remove(getAccountKey(KEY_SEEN_IDS))
                .remove(getAccountKey(KEY_SEEN_HASHES))
                .remove(getAccountKey(KEY_IS_FIRST_BOOT))
                .remove(getAccountKey(KEY_IS_FIRST_MAIL_BOOT))
                .remove(getAccountKey(KEY_RECENT_NOTIFICATIONS))
                .apply()
        } else {
            prefs.edit().clear().apply()
        }
    }

    private fun getSeenIds(): MutableSet<String> {
        return prefs.getStringSet(getAccountKey(KEY_SEEN_IDS), emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun getSeenHashes(): MutableSet<String> {
        return prefs.getStringSet(getAccountKey(KEY_SEEN_HASHES), emptySet())?.toMutableSet() ?: mutableSetOf()
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
            .putStringSet(getAccountKey(KEY_SEEN_IDS), seenIds)
            .putStringSet(getAccountKey(KEY_SEEN_HASHES), seenHashes)
            .putBoolean(getAccountKey(KEY_IS_FIRST_BOOT), false)
            .apply()

        val currentRecent = getRecentNotifications()
        val combined = (notifications.take(20) + currentRecent).distinctBy {
            if (it.sakaiId.isNotBlank()) it.sakaiId else it.contentHash
        }
        saveRecentNotifications(combined.take(30))
    }

    /**
     * Filters incoming notifications to only return items that have never been seen.
     * On first boot of an account, alerts up to 5 active items and marks all as seen.
     */
    fun filterNewNotifications(notifications: List<SakaiNotification>): List<SakaiNotification> {
        if (isFirstBoot) {
            val seenIds = getSeenIds()
            val seenHashes = getSeenHashes()

            for (notif in notifications) {
                seenIds.add(notif.sakaiId)
                seenHashes.add(notif.contentHash)
            }

            // Mark state as initialized for this account
            prefs.edit()
                .putStringSet(getAccountKey(KEY_SEEN_IDS), seenIds)
                .putStringSet(getAccountKey(KEY_SEEN_HASHES), seenHashes)
                .putBoolean(getAccountKey(KEY_IS_FIRST_BOOT), false)
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
     * Filters incoming student webmail notifications.
     * On first check for an account, merges emails into recent cache non-destructively.
     * If the inbox has a small number of emails (<= 2, e.g. newly sent test email), alerts immediately.
     * If there is an existing large backlog (> 2), silently baselines to avoid alert spam.
     */
    fun filterNewEmailNotifications(emails: List<SakaiNotification>): List<SakaiNotification> {
        val seenIds = getSeenIds()
        val seenHashes = getSeenHashes()

        if (isFirstMailBoot) {
            for (notif in emails) {
                seenIds.add(notif.sakaiId)
                seenHashes.add(notif.contentHash)
            }

            prefs.edit()
                .putStringSet(getAccountKey(KEY_SEEN_IDS), seenIds)
                .putStringSet(getAccountKey(KEY_SEEN_HASHES), seenHashes)
                .putBoolean(getAccountKey(KEY_IS_FIRST_MAIL_BOOT), false)
                .apply()

            // Non-destructively merge with existing recent notifications
            val currentRecent = getRecentNotifications()
            val combined = (emails.take(20) + currentRecent).distinctBy {
                if (it.sakaiId.isNotBlank()) it.sakaiId else it.contentHash
            }
            saveRecentNotifications(combined.take(30))

            // If the user has a small number of unread emails (<= 2, e.g. newly sent test email or immediate arrival),
            // notify them immediately so test emails or immediate arrivals are not lost.
            // If the user has a large backlog (> 2), silently baseline them to prevent notification flood.
            return if (emails.size <= 2) {
                emails
            } else {
                emptyList()
            }
        }

        return emails.filter { notif ->
            !seenIds.contains(notif.sakaiId) && !seenHashes.contains(notif.contentHash)
        }
    }

    /**
     * Marks notifications as processed for current account.
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
            .putStringSet(getAccountKey(KEY_SEEN_IDS), seenIds)
            .putStringSet(getAccountKey(KEY_SEEN_HASHES), seenHashes)
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
            obj.put("accountUsername", item.accountUsername?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: currentUser)
            obj.put("isEmail", item.isEmail)
            array.put(obj)
        }
        prefs.edit().putString(getAccountKey(KEY_RECENT_NOTIFICATIONS), array.toString()).apply()
    }

    fun getRecentNotifications(): List<SakaiNotification> {
        val raw = prefs.getString(getAccountKey(KEY_RECENT_NOTIFICATIONS), "[]") ?: "[]"
        val list = mutableListOf<SakaiNotification>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val accUser = obj.optString("accountUsername").trim().lowercase().takeIf { it.isNotBlank() } ?: currentUser
                list.add(
                    SakaiNotification(
                        sakaiId = obj.optString("id"),
                        contentHash = obj.optString("hash"),
                        title = obj.optString("title", "Bildirim"),
                        body = obj.optString("body").takeIf { it.isNotBlank() },
                        siteTitle = obj.optString("siteTitle").takeIf { it.isNotBlank() },
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() }?.let { SakaiNotification.formatPublishedDate(it) ?: it },
                        rawJson = obj.toString(),
                        accountUsername = accUser,
                        isEmail = obj.optBoolean("isEmail", false)
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

    /**
     * Aggregates recent notifications across all registered accounts.
     * Deduplicates per (accountUsername + sakaiId/contentHash) and sorts by publishedAt descending.
     */
    fun getAllRecentNotifications(accounts: List<String>): List<SakaiNotification> {
        val all = mutableListOf<SakaiNotification>()
        val seenSignatures = mutableSetOf<String>()

        val primaryUser = accounts.firstOrNull()?.trim()?.lowercase().orEmpty()

        for (acc in accounts) {
            val norm = acc.trim().lowercase()
            val shortUser = norm.substringBefore("@")

            // Check both full normalized username and short student ID keys
            val candidateKeys = listOf(
                "${KEY_RECENT_NOTIFICATIONS}_$norm",
                "${KEY_RECENT_NOTIFICATIONS}_$shortUser"
            ).distinct()

            for (key in candidateKeys) {
                val raw = prefs.getString(key, null)
                if (!raw.isNullOrBlank()) {
                    try {
                        val array = JSONArray(raw)
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val notifUser = obj.optString("accountUsername").trim().lowercase().takeIf { it.isNotBlank() } ?: norm
                            val id = obj.optString("id")
                            val hash = obj.optString("hash")
                            val primaryId = if (id.isNotBlank()) id else hash
                            val signature = "${notifUser.substringBefore("@")}_$primaryId"
                            if (seenSignatures.add(signature)) {
                                all.add(
                                    SakaiNotification(
                                        sakaiId = id,
                                        contentHash = hash,
                                        title = obj.optString("title", "Bildirim"),
                                        body = obj.optString("body").takeIf { it.isNotBlank() },
                                        siteTitle = obj.optString("siteTitle").takeIf { it.isNotBlank() },
                                        url = obj.optString("url").takeIf { it.isNotBlank() },
                                        publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() },
                                        rawJson = obj.toString(),
                                        accountUsername = notifUser,
                                        isEmail = obj.optBoolean("isEmail", false)
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Also check legacy un-suffixed key if any notifications remain there
        val legacyRaw = prefs.getString(KEY_RECENT_NOTIFICATIONS, null)
        if (!legacyRaw.isNullOrBlank()) {
            try {
                val array = JSONArray(legacyRaw)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val notifUser = obj.optString("accountUsername").trim().lowercase().takeIf { it.isNotBlank() } ?: primaryUser
                    val id = obj.optString("id")
                    val hash = obj.optString("hash")
                    val primaryId = if (id.isNotBlank()) id else hash
                    val signature = "${notifUser.substringBefore("@")}_$primaryId"
                    if (seenSignatures.add(signature)) {
                        all.add(
                            SakaiNotification(
                                sakaiId = id,
                                contentHash = hash,
                                title = obj.optString("title", "Bildirim"),
                                body = obj.optString("body").takeIf { it.isNotBlank() },
                                siteTitle = obj.optString("siteTitle").takeIf { it.isNotBlank() },
                                url = obj.optString("url").takeIf { it.isNotBlank() },
                                publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() },
                                rawJson = obj.toString(),
                                accountUsername = notifUser,
                                isEmail = obj.optBoolean("isEmail", false)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (all.isEmpty()) {
            return getRecentNotifications().map {
                if (it.accountUsername.isNullOrBlank()) it.copy(accountUsername = primaryUser) else it
            }
        }

        return all.sortedByDescending { notif ->
            SakaiNotification.parsePublishedMillis(notif.publishedAt)
        }
    }

    /**
     * Clears all cached recent notifications across all registered accounts.
     * Does NOT erase seen IDs or hashes (preventing spam alerts for already seen announcements).
     */
    fun clearAllRecentNotifications(accounts: List<String>) {
        val editor = prefs.edit()
        editor.remove(KEY_RECENT_NOTIFICATIONS)
        editor.remove(getAccountKey(KEY_RECENT_NOTIFICATIONS))
        for (acc in accounts) {
            val norm = acc.trim().lowercase()
            editor.remove("${KEY_RECENT_NOTIFICATIONS}_$norm")
            editor.remove("${KEY_RECENT_NOTIFICATIONS}_${norm.substringBefore("@")}")
        }
        editor.apply()
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
