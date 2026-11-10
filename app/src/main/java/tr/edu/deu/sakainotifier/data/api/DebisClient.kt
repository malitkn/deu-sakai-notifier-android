package tr.edu.deu.sakainotifier.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.DebisCaptchaSession
import tr.edu.deu.sakainotifier.data.model.DebisCourseOption
import tr.edu.deu.sakainotifier.data.parser.DebisParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Handles HTTP operations for DEÜ DEBİS (Student Information System).
 * Manages cookie session continuity for CAPTCHA retrieval, user-authenticated login,
 * keep-alive heartbeat and grade scraping.
 */
class DebisClient(
    private val baseUrl: String = "https://debis.deu.edu.tr",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    private val CAPTCHA_SRC_REGEX = Pattern.compile(
        """<img[^>]+src=["']([^"']*(?:captcha|guvenlik|security)[^"']*)["']""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Initializes a fresh session, extracts PHPSESSID, and downloads CAPTCHA image bytes.
     * The raw image is passed directly to the native Compose UI for human visual resolution.
     */
    suspend fun fetchCaptcha(): Result<DebisCaptchaSession> = withContext(Dispatchers.IO) {
        try {
            val loginPageRequest = Request.Builder()
                .url("$baseUrl/debis.php")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val loginResponse: Response = client.newCall(loginPageRequest).execute()
            val responseHtml = loginResponse.body?.string() ?: ""

            // Extract session cookie (PHPSESSID)
            val setCookies = loginResponse.headers("Set-Cookie")
            val phpsessid = setCookies.firstOrNull { it.startsWith("PHPSESSID=") }
                ?.substringBefore(";")
                ?: "PHPSESSID=unknown_${System.currentTimeMillis()}"

            // Determine captcha image URL
            val matcher = CAPTCHA_SRC_REGEX.matcher(responseHtml)
            val relativeCaptchaPath = if (matcher.find()) {
                matcher.group(1) ?: "captcha.php"
            } else {
                "captcha.php"
            }

            val fullCaptchaUrl = if (relativeCaptchaPath.startsWith("http")) {
                relativeCaptchaPath
            } else if (relativeCaptchaPath.startsWith("/")) {
                "$baseUrl$relativeCaptchaPath"
            } else {
                "$baseUrl/$relativeCaptchaPath"
            }

            // Fetch captcha image bytes using the SAME session cookie
            val captchaRequest = Request.Builder()
                .url(fullCaptchaUrl)
                .header("Cookie", phpsessid)
                .header("Referer", "$baseUrl/debis.php")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val captchaResponse = client.newCall(captchaRequest).execute()
            if (!captchaResponse.isSuccessful) {
                return@withContext Result.failure(IOException("CAPTCHA indirilemedi: HTTP ${captchaResponse.code}"))
            }

            val imageBytes = captchaResponse.body?.bytes()
                ?: return@withContext Result.failure(IOException("CAPTCHA görseli boş döndü"))

            Result.success(
                DebisCaptchaSession(
                    sessionId = phpsessid,
                    captchaImageBytes = imageBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submits login credentials with human-entered CAPTCHA code.
     */
    suspend fun login(
        username: String,
        password: String,
        captchaCode: String,
        sessionCookie: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("guvenlik_kod", captchaCode)
                .add("login", "Giriş")
                .build()

            val request = Request.Builder()
                .url("$baseUrl/debis.php")
                .post(formBody)
                .header("Cookie", sessionCookie)
                .header("Referer", "$baseUrl/debis.php")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            // Check if login failed (e.g. invalid captcha or credentials)
            if (responseBody.contains("Güvenlik kodu hatalı", ignoreCase = true) ||
                responseBody.contains("kod yanlış", ignoreCase = true) ||
                responseBody.contains("güvenlik kodunu", ignoreCase = true)
            ) {
                return@withContext Result.failure(IllegalArgumentException("Güvenlik kodu hatalı girildi. Lütfen tekrar deneyin."))
            }

            if (responseBody.contains("Kullanıcı adı veya şifre hatalı", ignoreCase = true) ||
                responseBody.contains("hatalı giriş", ignoreCase = true) ||
                responseBody.contains("şifreniz hatalı", ignoreCase = true)
            ) {
                return@withContext Result.failure(IllegalArgumentException("Öğrenci numarası veya şifre hatalı."))
            }

            // Guard against silent login failure where DEBİS re-renders the login form
            val stillOnLoginForm = responseBody.contains("name=\"guvenlik_kod\"", ignoreCase = true) ||
                responseBody.contains("name='guvenlik_kod'", ignoreCase = true) ||
                (responseBody.contains("name=\"password\"", ignoreCase = true) &&
                    !responseBody.contains("Ogrenci", ignoreCase = true))

            if (stillOnLoginForm) {
                return@withContext Result.failure(
                    IllegalArgumentException("Giriş doğrulanamadı. Öğrenci numaranızı, şifrenizi ve güvenlik kodunu kontrol edip tekrar deneyin.")
                )
            }

            // Capture rotated PHPSESSID if server issued a new Set-Cookie during redirect chain
            val activeCookie = extractLatestSessionCookie(response, sessionCookie)

            // Return active authenticated cookie
            Result.success(activeCookie)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractLatestSessionCookie(response: Response, fallbackCookie: String): String {
        val allCookies = mutableListOf<String>()
        var current: Response? = response
        while (current != null) {
            allCookies.addAll(current.headers("Set-Cookie"))
            current = current.priorResponse
        }
        val latestPhpSessId = allCookies
            .firstOrNull { it.startsWith("PHPSESSID=", ignoreCase = true) }
            ?.substringBefore(";")
            ?.trim()
        return if (!latestPhpSessId.isNullOrBlank()) latestPhpSessId else fallbackCookie
    }

    /**
     * Sends a lightweight keep-alive request to reset the PHP idle timeout timer.
     */
    suspend fun sendKeepAlive(sessionCookie: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pingRequest = Request.Builder()
                .url("$baseUrl/Ogrenci/index.php")
                .header("Cookie", sessionCookie)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val response = client.newCall(pingRequest).execute()
            response.isSuccessful && !response.request.url.toString().contains("login")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads the course listing page and extracts all available `<option>` items.
     */
    suspend fun fetchCourseOptions(
        sessionCookie: String,
        gradeUrl: String = "$baseUrl/Ogrenci/OgrNot/index.php"
    ): Result<List<DebisCourseOption>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(gradeUrl)
                .header("Cookie", sessionCookie)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Check if session has expired
            if (html.contains("Oturumunuz sonlanmış", ignoreCase = true) ||
                response.request.url.toString().contains("login") ||
                response.request.url.toString().contains("debis.php")
            ) {
                return@withContext Result.failure(IllegalStateException("DEBİS oturum süresi doldu. Lütfen tekrar giriş yapın."))
            }

            val courses = DebisParser.parseCourseSelectOptions(html)
            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Posts the course form to query the exact grade table for a course.
     * Uses: ders=B_BA+_1_9634_3_46_&ogretim_donemi_id=324&relative=..%2F..%2F..%2F&ogrenci_no=2025282042&liste=
     */
    suspend fun fetchCourseGradeDetails(
        sessionCookie: String,
        courseOption: DebisCourseOption,
        studentNo: String,
        termId: String = courseOption.termId.ifBlank { "324" },
        gradeQueryUrl: String = "$baseUrl/Ogrenci/OgrNot/index.php"
    ): Result<CourseGrade> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("ders", courseOption.rawValue)
                .add("ogretim_donemi_id", termId)
                .add("relative", "../../../")
                .add("ogrenci_no", studentNo)
                .add("liste", "")
                .build()

            val request = Request.Builder()
                .url(gradeQueryUrl)
                .post(formBody)
                .header("Cookie", sessionCookie)
                .header("Referer", gradeQueryUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val grade = DebisParser.parseGradeTable(html, courseOption)
            Result.success(grade)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
