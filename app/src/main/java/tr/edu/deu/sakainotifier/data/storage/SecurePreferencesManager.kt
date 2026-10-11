package tr.edu.deu.sakainotifier.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePreferencesManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for older emulators without hardware KeyStore
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_FILE_NAME = "deu_sakai_secure_prefs"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_PASSWORD = "key_password"
        private const val KEY_POLL_INTERVAL = "key_poll_interval_minutes"
        private const val KEY_MONITORING_ACTIVE = "key_monitoring_active"
        private const val KEY_LAST_CHECK_TIME = "key_last_check_timestamp"

        // ENFORCED ARCHITECTURAL MINIMUM: 20 Minutes
        const val MIN_POLL_INTERVAL_MINUTES = 20
        const val DEFAULT_POLL_INTERVAL_MINUTES = 20
    }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var pollIntervalMinutes: Int
        get() {
            val saved = prefs.getInt(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL_MINUTES)
            // Enforce minimum 20 minutes under all circumstances
            return if (saved < MIN_POLL_INTERVAL_MINUTES) MIN_POLL_INTERVAL_MINUTES else saved
        }
        set(value) {
            val validated = if (value < MIN_POLL_INTERVAL_MINUTES) MIN_POLL_INTERVAL_MINUTES else value
            prefs.edit().putInt(KEY_POLL_INTERVAL, validated).apply()
        }

    var isMonitoringActive: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ACTIVE, value).apply()

    var lastCheckTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK_TIME, value).apply()

    fun hasCredentials(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }
}
