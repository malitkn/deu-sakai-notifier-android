package tr.edu.deu.sakainotifier.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.SakaiAccount
import tr.edu.deu.sakainotifier.data.parser.DebisParser
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/**
 * Handles automatic HTTP authentication and AJAX grade retrieval for DEÜ OBS (`https://obs.deu.edu.tr`).
 */
class DebisClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://obs.deu.edu.tr"
        private const val GRADE_PAGE_PATH = "/modules/ogrenci/notgoruntuleme/index.php"
        private const val GRADE_AJAX_PATH = "/modules/ogrenci/notgoruntuleme/index.ajax.php"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    /**
     * Decodes raw HTTP response bytes using strict UTF-8 first, falling back to ISO-8859-9
     * (used by OBS HTML endpoints) if malformed UTF-8 sequences are encountered.
     */
    private fun decodeResponseBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("ISO-8859-9"))
        }
    }

    private fun extractLatestSessionCookie(response: Response): String? {
        val responsesOldestFirst = mutableListOf<Response>()
        var current: Response? = response
        while (current != null) {
            responsesOldestFirst.add(current)
            current = current.priorResponse
        }
        // OBS login.php emits pre-login PHPSESSID first, then regenerated authenticated PHPSESSID last.
        return responsesOldestFirst
            .asReversed()
            .flatMap { it.headers("Set-Cookie") }
            .lastOrNull { it.startsWith("PHPSESSID=", ignoreCase = true) }
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Authenticates automatically against `POST https://obs.deu.edu.tr/login.php` without CAPTCHA.
     * Requires the student's mail username (e.g. `ad.soyad@ogr.deu.edu.tr`), NOT a numeric student ID.
     */
    suspend fun login(usernameOrEmail: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = usernameOrEmail.substringBefore("@").trim()
            val domainPart = usernameOrEmail.substringAfter("@", "").trim()
            val mailDomain = if (domainPart.equals("deu.edu.tr", ignoreCase = true)) {
                "@deu.edu.tr"
            } else {
                "@ogr.deu.edu.tr"
            }

            if (cleanUser.isEmpty() || cleanUser.all { it.isDigit() }) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "OBS girişi için öğrenci numarası yerine öğrenci e-posta adresi (ad.soyad@ogr.deu.edu.tr) gereklidir."
                    )
                )
            }

            val formBody = FormBody.Builder()
                .add("mail", cleanUser)
                .add("pass", password)
                .add("mail_domain", mailDomain)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/login.php")
                .post(formBody)
                .header("Referer", "$baseUrl/login.php")
                .header("Origin", baseUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val activeCookie = extractLatestSessionCookie(response)
                val location = response.header("Location").orEmpty()
                val responseBody = decodeResponseBytes(response.body?.bytes() ?: ByteArray(0))
                val finalUrl = response.request.url.toString()

                val isRedirectToApp = response.isRedirect &&
                    location.isNotBlank() &&
                    !location.contains("login.php", ignoreCase = true)

                val stayedOnLogin = !isRedirectToApp && (
                    finalUrl.endsWith("login.php", ignoreCase = true) ||
                        responseBody.contains("id='local-login-form'", ignoreCase = true) ||
                        responseBody.contains("id=\"local-login-form\"", ignoreCase = true) ||
                        responseBody.contains("name='mail'", ignoreCase = true) ||
                        responseBody.contains("name=\"mail\"", ignoreCase = true)
                    )

                if (stayedOnLogin) {
                    return@withContext Result.failure(
                        IllegalArgumentException("OBS girişi başarısız: E-posta kullanıcı adı veya şifre hatalı.")
                    )
                }

                if (activeCookie.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException("OBS oturum çerezi (PHPSESSID) alınamadı.")
                    )
                }

                Result.success(activeCookie)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Loads the grade viewing page to verify session permissions (required by OBS before AJAX calls)
     * and returns its HTML if authenticated, or null if the session is expired/invalid.
     */
    private suspend fun loadGradeIndexPage(cookie: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$GRADE_PAGE_PATH")
                .get()
                .header("Cookie", cookie)
                .header("Referer", "$baseUrl/index.php")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("login.php", ignoreCase = true)) return@withContext null

                val html = decodeResponseBytes(response.body?.bytes() ?: ByteArray(0))
                if (html.contains("local-login-form", ignoreCase = true) ||
                    html.contains("name='mail'", ignoreCase = true) ||
                    html.contains("name=\"mail\"", ignoreCase = true)
                ) {
                    return@withContext null
                }

                if (html.contains("name='donem'", ignoreCase = true) ||
                    html.contains("name=\"donem\"", ignoreCase = true) ||
                    html.contains("Not Görüntüleme", ignoreCase = true)
                ) {
                    html
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches all courses and their grade tables for the latest active semester on OBS.
     * Reuses `cachedCookie` if still valid, otherwise automatically logs in using `account` credentials.
     * Returns `(activeSessionCookie, courseGrades)` on success.
     */
    suspend fun fetchAllCurrentTermGrades(
        account: SakaiAccount,
        cachedCookie: String? = null
    ): Result<Pair<String, List<CourseGrade>>> = withContext(Dispatchers.IO) {
        try {
            val loginIdentity = account.emailAddress?.takeIf { it.isNotBlank() } ?: account.username

            var activeCookie = cachedCookie?.trim()?.takeIf { it.isNotBlank() }
            var indexHtml = activeCookie?.let { loadGradeIndexPage(it) }

            if (indexHtml == null) {
                val loginResult = login(loginIdentity, account.password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(
                        loginResult.exceptionOrNull() ?: IOException("OBS girişi başarısız.")
                    )
                }
                activeCookie = loginResult.getOrThrow()
                indexHtml = loadGradeIndexPage(activeCookie)
                    ?: return@withContext Result.failure(
                        IOException("OBS Not Görüntüleme sayfası yüklenemedi.")
                    )
            }

            val cookieHeader = activeCookie!!
            val activeTermId = DebisParser.parseActiveTermId(indexHtml)
            val gradePageUrl = "$baseUrl$GRADE_PAGE_PATH"
            val ajaxUrl = "$baseUrl$GRADE_AJAX_PATH"

            // 1. Fetch courses JSON for the active semester
            val coursesForm = FormBody.Builder()
                .add("islem", "dersgetir")
                .add("donem", activeTermId)
                .build()

            val coursesRequest = Request.Builder()
                .url(ajaxUrl)
                .post(coursesForm)
                .header("Cookie", cookieHeader)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", gradePageUrl)
                .header("Origin", baseUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val coursesJson = client.newCall(coursesRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("OBS ders listesi alınamadı: HTTP ${response.code}")
                    )
                }
                decodeResponseBytes(response.body?.bytes() ?: ByteArray(0))
            }

            val courseOptions = DebisParser.parseCourseOptionsJson(coursesJson, activeTermId)
            val freshGrades = mutableListOf<CourseGrade>()

            // 2. Fetch grade details table for each course
            for (option in courseOptions) {
                val gradeForm = FormBody.Builder()
                    .add("islem", "notgetir")
                    .add("donem", activeTermId)
                    .add("ders", option.rawValue)
                    .build()

                val gradeRequest = Request.Builder()
                    .url(ajaxUrl)
                    .post(gradeForm)
                    .header("Cookie", cookieHeader)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", gradePageUrl)
                    .header("Origin", baseUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                try {
                    client.newCall(gradeRequest).execute().use { gradeResp ->
                        if (gradeResp.isSuccessful) {
                            val gradeHtml = decodeResponseBytes(gradeResp.body?.bytes() ?: ByteArray(0))
                            freshGrades.add(DebisParser.parseGradeTable(gradeHtml, option))
                        }
                    }
                } catch (_: Exception) {
                    // Continue fetching remaining courses if one course call fails transiently
                }
            }

            Result.success(cookieHeader to freshGrades)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
