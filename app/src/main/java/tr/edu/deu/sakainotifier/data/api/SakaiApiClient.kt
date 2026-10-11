package tr.edu.deu.sakainotifier.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import tr.edu.deu.sakainotifier.data.parser.SakaiParser
import java.io.IOException

class SakaiApiClient(
    private val authClient: SakaiAuthClient = SakaiAuthClient(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        const val NOTIFICATIONS_URL = "https://online.deu.edu.tr/api/users/me/notifications"
        const val PORTAL_URL = "https://online.deu.edu.tr/portal"
    }

    private var currentSession: SakaiSession? = null
    private var sessionUsername: String? = null

    fun invalidateSession() {
        currentSession = null
        sessionUsername = null
    }

    suspend fun fetchNotifications(
        username: String,
        password: String
    ): Result<List<SakaiNotification>> = withContext(Dispatchers.IO) {
        val normalizedUser = SakaiUsernameUtils.normalize(username)
        if (sessionUsername != normalizedUser) {
            currentSession = null
            sessionUsername = normalizedUser
        }

        // Ensure valid session exists
        if (currentSession == null) {
            when (val authRes = authClient.authenticate(normalizedUser, password)) {
                is AuthResult.Success -> currentSession = authRes.session
                is AuthResult.InvalidCredentials -> return@withContext Result.failure(Exception(authRes.message))
                is AuthResult.Error -> return@withContext Result.failure(Exception(authRes.message, authRes.cause))
            }
        }

        val session = currentSession ?: return@withContext Result.failure(Exception("Oturum başlatılamadı."))

        try {
            val request = Request.Builder()
                .url(NOTIFICATIONS_URL)
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", PORTAL_URL)
                .header("Cookie", session.toCookieHeader())
                .header("User-Agent", SakaiAuthClient.USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val rawBody = response.body?.string().orEmpty()

                // Check if session has expired or redirected to login
                val isExpired = response.code == 401 ||
                        response.code == 403 ||
                        response.code in 300..399 ||
                        response.request.url.encodedPath.contains("xlogin") ||
                        (contentType.contains("text/html") && (rawBody.contains("xlogin") || rawBody.contains("Giriş")))

                if (isExpired) {
                    // Re-authenticate once and retry
                    currentSession = null
                    when (val reAuth = authClient.authenticate(normalizedUser, password)) {
                        is AuthResult.Success -> {
                            currentSession = reAuth.session
                            val retryRequest = request.newBuilder()
                                .header("Cookie", reAuth.session.toCookieHeader())
                                .build()

                            httpClient.newCall(retryRequest).execute().use { retryResp ->
                                val retryRawBody = retryResp.body?.string().orEmpty()
                                if (!retryResp.isSuccessful) {
                                    return@withContext Result.failure(Exception("Bildirimler alınamadı (HTTP ${retryResp.code}). Detay: ${retryRawBody.take(120)}"))
                                }
                                return@withContext Result.success(SakaiParser.parseNotifications(retryRawBody))
                            }
                        }
                        is AuthResult.InvalidCredentials -> return@withContext Result.failure(Exception(reAuth.message))
                        is AuthResult.Error -> return@withContext Result.failure(Exception(reAuth.message, reAuth.cause))
                    }
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Bildirimler alınamadı (HTTP ${response.code}). Detay: ${rawBody.take(120)}"))
                }

                return@withContext Result.success(SakaiParser.parseNotifications(rawBody))
            }
        } catch (e: IOException) {
            return@withContext Result.failure(Exception("Ağ bağlantı hatası: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
