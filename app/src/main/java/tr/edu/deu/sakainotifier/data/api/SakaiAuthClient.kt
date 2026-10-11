package tr.edu.deu.sakainotifier.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class SakaiSession(
    val sakai2SessionId: String,
    val sakaiAffinity: String? = null
) {
    fun toCookieHeader(): String {
        val parts = mutableListOf<String>()
        if (!sakaiAffinity.isNullOrBlank()) {
            parts.add("SAKAI_AFFINITY=$sakaiAffinity")
        }
        parts.add("SAKAI2SESSIONID=$sakai2SessionId")
        return parts.joinToString("; ")
    }
}

sealed class AuthResult {
    data class Success(val session: SakaiSession) : AuthResult()
    data class InvalidCredentials(val message: String = "Kullanıcı adı yada şifreniz hatalı.") : AuthResult()
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult()
}

object SakaiUsernameUtils {
    /**
     * Extracts the short username (e.g. "2024123456") from either student number or full email.
     */
    fun shortUsername(input: String): String {
        return input.substringBefore("@").trim()
    }

    /**
     * Automatically appends @ogr.deu.edu.tr if the student enters only their student ID number.
     * Preserves existing domain if user explicitly typed @ogr.deu.edu.tr or @deu.edu.tr.
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        return if (!trimmed.contains("@")) {
            "$trimmed@ogr.deu.edu.tr"
        } else {
            trimmed
        }
    }
}

class SakaiAuthClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false) // Must not follow 302 redirect so authenticated session cookie is not discarded for guest session
        .followSslRedirects(false)
        .build()
) {
    companion object {
        const val LOGIN_URL = "https://online.deu.edu.tr/portal/xlogin"
        const val BASE_URL = "https://online.deu.edu.tr"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }

    suspend fun authenticate(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val normalizedUser = SakaiUsernameUtils.normalize(username)
        val formBody = FormBody.Builder()
            .add("eid", normalizedUser)
            .add("pw", password)
            .add("submit", "Giriş")
            .build()

        val request = Request.Builder()
            .url(LOGIN_URL)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", LOGIN_URL)
            .header("Origin", BASE_URL)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()

                if (bodyText.contains("Kullanıcı adı yada şifreniz hatalı")) {
                    return@withContext AuthResult.InvalidCredentials()
                }

                var sakai2Session: String? = null
                var sakaiAffinity: String? = null

                // Extract cookies from Set-Cookie headers of this response and any prior response
                val allResponses = listOfNotNull(response, response.priorResponse)
                for (resp in allResponses) {
                    val cookieHeaders = resp.headers("Set-Cookie")
                    for (header in cookieHeaders) {
                        val cookiePart = header.substringBefore(";").trim()
                        if (cookiePart.startsWith("SAKAI2SESSIONID=")) {
                            sakai2Session = cookiePart.substringAfter("SAKAI2SESSIONID=")
                        } else if (cookiePart.startsWith("SAKAI_AFFINITY=")) {
                            sakaiAffinity = cookiePart.substringAfter("SAKAI_AFFINITY=")
                        }
                    }
                }

                if (response.code == 200 && (bodyText.contains("xlogin") || bodyText.contains("Şifrenizi mi unuttunuz"))) {
                    return@withContext AuthResult.InvalidCredentials("Giriş başarısız: Kullanıcı adı ya da şifreniz hatalı.")
                }

                if (sakai2Session.isNullOrBlank()) {
                    return@withContext AuthResult.Error(
                        "Giriş başarılı olmadı (SAKAI2SESSIONID çerezi alınamadı, durum: ${response.code})."
                    )
                }

                return@withContext AuthResult.Success(
                    SakaiSession(
                        sakai2SessionId = sakai2Session,
                        sakaiAffinity = sakaiAffinity
                    )
                )
            }
        } catch (e: IOException) {
            return@withContext AuthResult.Error("Ağ bağlantı hatası: ${e.localizedMessage ?: e.message}", e)
        } catch (e: Exception) {
            return@withContext AuthResult.Error("Beklenmeyen bir hata oluştu: ${e.localizedMessage ?: e.message}", e)
        }
    }
}
