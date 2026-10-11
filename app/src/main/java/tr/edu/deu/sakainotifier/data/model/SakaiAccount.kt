package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject

/**
 * Represents a DEU Sakai student account credential.
 */
data class SakaiAccount(
    val username: String, // Normalized username e.g. "2021123456@ogr.deu.edu.tr" or "2021123456"
    val password: String
) {
    val shortUsername: String
        get() = username.substringBefore("@").trim()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("username", username)
        obj.put("password", password)
        return obj
    }

    companion object {
        fun fromJson(json: JSONObject): SakaiAccount {
            return SakaiAccount(
                username = json.optString("username", ""),
                password = json.optString("password", "")
            )
        }
    }
}
