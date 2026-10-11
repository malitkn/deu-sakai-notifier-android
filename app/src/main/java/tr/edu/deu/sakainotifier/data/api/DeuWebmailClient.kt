package tr.edu.deu.sakainotifier.data.api

import android.net.Uri
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class WebmailSession(
    val sessId: String,
    val sessAuth: String?,
    val requestToken: String
) {
    fun toCookieHeader(): String {
        val cookies = mutableListOf("roundcube_sessid=$sessId")
        if (!sessAuth.isNullOrBlank()) {
            cookies.add("roundcube_sessauth=$sessAuth")
        }
        return cookies.joinToString("; ")
    }
}

sealed class WebmailAuthResult {
    data class Success(val session: WebmailSession) : WebmailAuthResult()
    data class InvalidCredentials(val message: String = "Öğrenci e-posta adresi ya da şifreniz hatalı.") : WebmailAuthResult()
    data class Error(val message: String, val cause: Throwable? = null) : WebmailAuthResult()
}

sealed class WebmailTestResult {
    object Success : WebmailTestResult()
    data class InvalidCredentials(val message: String = "Öğrenci e-posta adresi ya da şifreniz hatalı.") : WebmailTestResult()
    data class ServerUnavailable(val message: String = "DEU e-posta sunucusuna şu an ulaşılamıyor.", val cause: Throwable? = null) : WebmailTestResult()
}

class DeuWebmailClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val BASE_URL = "https://ogrmail.deu.edu.tr"
        const val LOGIN_URL = "https://ogrmail.deu.edu.tr/?_task=login"
        const val INBOX_URL = "https://ogrmail.deu.edu.tr/?_task=mail&_mbox=INBOX"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

        fun normalizeEmail(email: String): String {
            val trimmed = email.trim()
            if (trimmed.isBlank()) return ""
            return if (!trimmed.contains("@")) {
                "$trimmed@ogr.deu.edu.tr"
            } else {
                trimmed
            }
        }

        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun unescapeHtml(text: String): String {
            if (text.isBlank()) return text
            return try {
                Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } catch (_: Exception) {
                text.replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&#39;", "'")
                    .trim()
            }
        }
    }

    private var cachedSession: WebmailSession? = null
    private var cachedEmail: String? = null

    fun invalidateSession() {
        cachedSession = null
        cachedEmail = null
    }

    suspend fun authenticate(email: String, pass: String): WebmailAuthResult = withContext(Dispatchers.IO) {
        val normalizedEmail = normalizeEmail(email)
        if (normalizedEmail.isBlank() || pass.isBlank()) {
            return@withContext WebmailAuthResult.InvalidCredentials("E-posta adresi veya şifre boş olamaz.")
        }

        try {
            // Step 1: GET login page to obtain initial roundcube_sessid cookie and CSRF _token
            val getRequest = Request.Builder()
                .url(LOGIN_URL)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            var initialSessId: String? = null
            var loginToken: String? = null

            client.newCall(getRequest).execute().use { getResp ->
                val cookieHeaders = getResp.headers("Set-Cookie")
                for (header in cookieHeaders) {
                    val part = header.substringBefore(";").trim()
                    if (part.startsWith("roundcube_sessid=")) {
                        initialSessId = part.substringAfter("roundcube_sessid=")
                    }
                }

                val bodyHtml = getResp.body?.string().orEmpty()
                val tokenPattern = Pattern.compile("name=[\"']_token[\"']\\s+value=[\"']([^\"']+)[\"']")
                val matcher = tokenPattern.matcher(bodyHtml)
                if (matcher.find()) {
                    loginToken = matcher.group(1)
                } else {
                    val reversedPattern = Pattern.compile("value=[\"']([^\"']+)[\"']\\s+name=[\"']_token[\"']")
                    val reversedMatcher = reversedPattern.matcher(bodyHtml)
                    if (reversedMatcher.find()) {
                        loginToken = reversedMatcher.group(1)
                    }
                }
            }

            if (initialSessId.isNullOrBlank() || loginToken.isNullOrBlank()) {
                return@withContext WebmailAuthResult.Error("Webmail oturum belirteci veya çerez alınamadı.")
            }

            // Step 2: POST credentials to authenticate
            val formBody = FormBody.Builder()
                .add("_token", loginToken!!)
                .add("_task", "login")
                .add("_action", "login")
                .add("_timezone", "Europe/Istanbul")
                .add("_url", "")
                .add("_user", normalizedEmail)
                .add("_pass", pass)
                .build()

            val postRequest = Request.Builder()
                .url(LOGIN_URL)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", BASE_URL)
                .header("Referer", LOGIN_URL)
                .header("Cookie", "roundcube_sessid=$initialSessId")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(postRequest).execute().use { postResp ->
                if (postResp.code == 401) {
                    return@withContext WebmailAuthResult.InvalidCredentials("Öğrenci e-posta adresi ya da şifreniz hatalı.")
                }

                var currentSessId = initialSessId!!
                var sessAuth: String? = null

                val postCookieHeaders = postResp.headers("Set-Cookie")
                for (header in postCookieHeaders) {
                    val part = header.substringBefore(";").trim()
                    if (part.startsWith("roundcube_sessid=")) {
                        currentSessId = part.substringAfter("roundcube_sessid=")
                    } else if (part.startsWith("roundcube_sessauth=")) {
                        sessAuth = part.substringAfter("roundcube_sessauth=")
                    }
                }

                val location = postResp.header("Location").orEmpty()
                val bodyText = postResp.body?.string().orEmpty()

                // Check for failed login indications
                if (location.contains("_task=login") ||
                    bodyText.contains("Giriş başarısız") ||
                    bodyText.contains("Login failed") ||
                    bodyText.contains("rcmloginuser")
                ) {
                    return@withContext WebmailAuthResult.InvalidCredentials("Öğrenci e-posta adresi ya da şifreniz hatalı.")
                }

                // Check for successful 302 redirect to mail task
                var requestToken = loginToken!!
                if (location.contains("_token=")) {
                    try {
                        val parsedUri = Uri.parse(if (location.startsWith("http")) location else "$BASE_URL$location")
                        val tokenInQuery = parsedUri.getQueryParameter("_token")
                        if (!tokenInQuery.isNullOrBlank()) {
                            requestToken = tokenInQuery
                        }
                    } catch (_: Exception) {
                    }
                }

                val isSuccess = postResp.code == 302 && (location.contains("_task=mail") || sessAuth != null) ||
                        (postResp.code == 200 && sessAuth != null)

                if (!isSuccess) {
                    return@withContext WebmailAuthResult.InvalidCredentials("Öğrenci e-posta girişi başarısız oldu (HTTP ${postResp.code}).")
                }

                val session = WebmailSession(
                    sessId = currentSessId,
                    sessAuth = sessAuth,
                    requestToken = requestToken
                )

                cachedSession = session
                cachedEmail = normalizedEmail
                return@withContext WebmailAuthResult.Success(session)
            }
        } catch (e: IOException) {
            return@withContext WebmailAuthResult.Error("Webmail bağlantı hatası: ${e.localizedMessage ?: e.message}", e)
        } catch (e: Exception) {
            return@withContext WebmailAuthResult.Error("Webmail doğrulanırken beklenmeyen hata: ${e.localizedMessage ?: e.message}", e)
        }
    }

    suspend fun testLogin(email: String, pass: String): WebmailTestResult {
        return when (val res = authenticate(email, pass)) {
            is WebmailAuthResult.Success -> WebmailTestResult.Success
            is WebmailAuthResult.InvalidCredentials -> WebmailTestResult.InvalidCredentials(res.message)
            is WebmailAuthResult.Error -> WebmailTestResult.ServerUnavailable(res.message, res.cause)
        }
    }

    suspend fun fetchUnreadEmails(
        email: String,
        pass: String,
        studentUsername: String
    ): Result<List<SakaiNotification>> = withContext(Dispatchers.IO) {
        val normalizedEmail = normalizeEmail(email)
        if (cachedEmail != normalizedEmail) {
            cachedSession = null
            cachedEmail = normalizedEmail
        }

        if (cachedSession == null) {
            when (val authRes = authenticate(normalizedEmail, pass)) {
                is WebmailAuthResult.Success -> cachedSession = authRes.session
                is WebmailAuthResult.InvalidCredentials -> return@withContext Result.failure(Exception(authRes.message))
                is WebmailAuthResult.Error -> return@withContext Result.failure(Exception(authRes.message, authRes.cause))
            }
        }

        var session = cachedSession ?: return@withContext Result.failure(Exception("Webmail oturumu başlatılamadı."))

        try {
            val unreadList = performSearchRequest(session, normalizedEmail, studentUsername)
            if (unreadList != null) {
                return@withContext Result.success(unreadList)
            }

            // Session might have expired; retry once
            cachedSession = null
            when (val reAuth = authenticate(normalizedEmail, pass)) {
                is WebmailAuthResult.Success -> {
                    session = reAuth.session
                    val retryList = performSearchRequest(session, normalizedEmail, studentUsername)
                    if (retryList != null) {
                        return@withContext Result.success(retryList)
                    }
                    return@withContext Result.failure(Exception("Yeniden bağlanma sonrasında e-postalar alınamadı."))
                }
                is WebmailAuthResult.InvalidCredentials -> return@withContext Result.failure(Exception(reAuth.message))
                is WebmailAuthResult.Error -> return@withContext Result.failure(Exception(reAuth.message, reAuth.cause))
            }
        } catch (e: IOException) {
            return@withContext Result.failure(Exception("E-posta kontrolünde ağ hatası: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun performSearchRequest(
        session: WebmailSession,
        email: String,
        studentUsername: String
    ): List<SakaiNotification>? {
        val timestamp = System.currentTimeMillis()
        val searchUrl = "$BASE_URL/?_task=mail&_action=search&_filter=UNSEEN&_scope=base&_mbox=INBOX&_remote=1&_unlock=loading$timestamp&_=$timestamp"

        val request = Request.Builder()
            .url(searchUrl)
            .get()
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Roundcube-Request", session.requestToken)
            .header("Referer", INBOX_URL)
            .header("Cookie", session.toCookieHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code in 300..399) {
                return null
            }
            val rawBody = response.body?.string().orEmpty()
            if (rawBody.contains("rcmloginuser") || rawBody.contains("Giriş")) {
                return null
            }
            if (!response.isSuccessful) {
                return null
            }

            return parseSearchResponse(rawBody, email, studentUsername)
        }
    }

    private fun parseSearchResponse(
        rawJson: String,
        email: String,
        studentUsername: String
    ): List<SakaiNotification> {
        val resultList = mutableListOf<SakaiNotification>()
        try {
            val json = JSONObject(rawJson)
            val exec = json.optString("exec", "")
            if (exec.isBlank()) return emptyList()

            // Matches: this.add_message_row( <id>, <metadata_json_object> )
            val rowPattern = Pattern.compile("add_message_row\\s*\\(\\s*(\\d+)\\s*,\\s*(\\{.+?\\})", Pattern.DOTALL)
            val matcher = rowPattern.matcher(exec)

            while (matcher.find()) {
                val msgId = matcher.group(1) ?: continue
                val metaStr = matcher.group(2) ?: continue

                try {
                    val metaObj = JSONObject(metaStr)
                    val rawSubject = metaObj.optString("subject", "(Konusuz E-posta)")
                    val subject = unescapeHtml(rawSubject)

                    val fromtoHtml = metaObj.optString("fromto", "")
                    val sender = parseSender(fromtoHtml)

                    val date = metaObj.optString("date", "")
                    val normalizedDate = SakaiNotification.formatPublishedDate(date) ?: date

                    val shortAccount = studentUsername.substringBefore("@").trim()
                    val notifId = "mail_${shortAccount}_$msgId"
                    val contentHash = sha256("$notifId:$sender:$subject:$date")

                    resultList.add(
                        SakaiNotification(
                            sakaiId = notifId,
                            contentHash = contentHash,
                            title = subject.ifBlank { "Öğrenci E-postası" },
                            body = "Kimden: $sender",
                            siteTitle = sender.ifBlank { "Öğrenci Maili" },
                            url = BASE_URL,
                            publishedAt = normalizedDate.ifBlank { null },
                            rawJson = metaStr,
                            accountUsername = studentUsername,
                            isEmail = true
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultList
    }

    private fun parseSender(fromtoHtml: String): String {
        if (fromtoHtml.isBlank()) return "Bilinmeyen Gönderen"

        // 1. Try class="rcmContactAddress">Name</span>
        val contactPattern = Pattern.compile("class=[\"']rcmContactAddress[\"'][^>]*>([^<]+)<")
        val contactMatcher = contactPattern.matcher(fromtoHtml)
        if (contactMatcher.find()) {
            return unescapeHtml(contactMatcher.group(1).orEmpty())
        }

        // 2. Try title="email@domain.com"
        val titlePattern = Pattern.compile("title=[\"']([^\"']+)[\"']")
        val titleMatcher = titlePattern.matcher(fromtoHtml)
        if (titleMatcher.find()) {
            return unescapeHtml(titleMatcher.group(1).orEmpty())
        }

        // 3. Fallback: strip all HTML tags
        val stripped = fromtoHtml.replace(Regex("<[^>]+>"), "").trim()
        return unescapeHtml(stripped.ifBlank { "Öğrenci Maili" })
    }
}
