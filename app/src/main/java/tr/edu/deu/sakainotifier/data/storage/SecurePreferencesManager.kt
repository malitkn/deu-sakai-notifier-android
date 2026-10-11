package tr.edu.deu.sakainotifier.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import tr.edu.deu.sakainotifier.data.model.SakaiAccount

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
        private const val KEY_ACCOUNTS_JSON = "key_accounts_json"
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

    fun getAccounts(): List<SakaiAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null)
        val list = mutableListOf<SakaiAccount>()
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val acc = SakaiAccount.fromJson(obj)
                    if (acc.username.isNotBlank() && acc.password.isNotBlank()) {
                        list.add(acc)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Fallback to legacy single username/password if accounts list is empty
        if (list.isEmpty() && username.isNotBlank() && password.isNotBlank()) {
            list.add(SakaiAccount(username, password))
        }
        return list
    }

    fun saveAccount(account: SakaiAccount) {
        val current = getAccounts().toMutableList()
        val index = current.indexOfFirst { it.username.equals(account.username, ignoreCase = true) }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        saveAccounts(current)
        // Keep active credentials in legacy fields for backward compatibility
        username = account.username
        password = account.password
    }

    fun removeAccount(usernameToRemove: String) {
        val current = getAccounts().toMutableList()
        current.removeAll { it.username.equals(usernameToRemove.trim(), ignoreCase = true) }
        saveAccounts(current)
        if (current.isNotEmpty()) {
            username = current.first().username
            password = current.first().password
        } else {
            clearCredentials()
        }
    }

    private fun saveAccounts(accounts: List<SakaiAccount>) {
        val array = JSONArray()
        for (acc in accounts) {
            array.put(acc.toJson())
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply()
    }

    fun hasCredentials(): Boolean {
        return getAccounts().isNotEmpty()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_ACCOUNTS_JSON)
            .apply()
    }
}
