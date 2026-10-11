package tr.edu.deu.sakainotifier.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val hasUpdate: Boolean
)

object UpdateChecker {
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/malitkn/deu-sakai-notifier-android/releases/latest"

    private val httpClient = OkHttpClient.Builder().build()

    suspend fun checkForUpdates(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "DEU-Sakai-Notifier-Android")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val rawBody = response.body?.string().orEmpty()
                if (rawBody.isBlank()) return@withContext null

                val json = JSONObject(rawBody)
                val tagName = json.optString("tag_name", "").trim()
                val body = json.optString("body", "").trim()
                val htmlUrl = json.optString("html_url", "")

                var downloadUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tagName, currentVersion)
                return@withContext AppUpdateInfo(
                    latestVersion = tagName,
                    releaseNotes = body,
                    downloadUrl = downloadUrl,
                    hasUpdate = hasUpdate
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isNewerVersion(remoteTag: String, currentTag: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").trim()
        val cleanCurrent = currentTag.removePrefix("v").trim()
        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) return false
        if (cleanRemote == cleanCurrent) return false

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
