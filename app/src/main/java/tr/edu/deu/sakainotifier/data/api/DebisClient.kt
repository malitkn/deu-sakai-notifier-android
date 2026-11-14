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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Handles automatic HTTP authentication and AJAX grade retrieval for DEÜ OBS (`https://obs.deu.edu.tr`).
 */
class DebisClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = buildDefaultObsHttpClient()
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://obs.deu.edu.tr"
        private const val GRADE_PAGE_PATH = "/modules/ogrenci/notgoruntuleme/index.php"
        private const val GRADE_AJAX_PATH = "/modules/ogrenci/notgoruntuleme/index.ajax.php"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        // GlobalSign GCC R46 OV TLS CA 2025 (Intermediate 1 for *.deu.edu.tr issued 2026-11-10)
        // Included because obs.deu.edu.tr omits this intermediate in its TLS handshake chain.
        private const val GLOBALSIGN_GCC_R46_OV_TLS_CA_2025_PEM = """-----BEGIN CERTIFICATE-----
MIIFfDCCA2SgAwIBAgIRAIRDWJCDb2c5QYLLnJpdyZ8wDQYJKoZIhvcNAQELBQAw
RjELMAkGA1UEBhMCQkUxGTAXBgNVBAoTEEdsb2JhbFNpZ24gbnYtc2ExHDAaBgNV
BAMTE0dsb2JhbFNpZ24gUm9vdCBSNDYwHhcNMjUwOTE3MDI1NTU2WhcNMjkwNjIz
MDAwMDAwWjBUMQswCQYDVQQGEwJCRTEZMBcGA1UEChMQR2xvYmFsU2lnbiBudi1z
YTEqMCgGA1UEAxMhR2xvYmFsU2lnbiBHQ0MgUjQ2IE9WIFRMUyBDQSAyMDI1MIIB
IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1JyrGiv+210Lw4LTp9qxx9WC
o6w8HnxcTKr5XwR6WwtKidGXriLqGXtBINGTi4HUZ1Vl3FUIvscLwNcq2DRLwjWs
cYFNClVnuSw4CtwAcfa7Iltz+0FmFeh/KOWv5BfgCxAo9FaeXRG725b2eedo/7fb
0zBc6M/XcfQREVteZ6GovnLE96+T8RzRImvX38Y8vZoulp/XWv3p09C1pgp/53+1
itDl7xbrM4sglGNkeJ5LBN2dOR1sqWCMZ/V4a4cPQwopBtZis1vVh7/k4S6Ysgk0
CTi5vei0RSEIhxoFk48BHSXzTA4FJxqjfauYCZ4M5tmZ/R5VgXOZ4Ck/PifnXQID
AQABo4IBVTCCAVEwDgYDVR0PAQH/BAQDAgGGMBMGA1UdJQQMMAoGCCsGAQUFBwMB
MBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFGl0Pq/DWwGVSe4UQVqT+rEw
mNqiMB8GA1UdIwQYMBaAFANcq3OBh6jMsKbVlOI2lkn/BZksMHsGCCsGAQUFBwEB
BG8wbTAuBggrBgEFBQcwAYYiaHR0cDovL29jc3AuZ2xvYmFsc2lnbi5jb20vcm9v
dHI0NjA7BggrBgEFBQcwAoYvaHR0cDovL3NlY3VyZS5nbG9iYWxzaWduLmNvbS9j
YWNlcnQvcm9vdHI0Ni5jcnQwNgYDVR0fBC8wLTAroCmgJ4YlaHR0cDovL2NybC5n
bG9iYWxzaWduLmNvbS9yb290cjQ2LmNybDAhBgNVHSAEGjAYMAgGBmeBDAECAjAM
BgorBgEEAaAyCgECMA0GCSqGSIb3DQEBCwUAA4ICAQBEUTiKxe5jEintARUvLBm9
qWZtGiOSV9E+3bntbFFBDBAroqwB6Cj53Zp/W08HwgxaPXdkVaRNYHB/eAatEtSm
1ldtoorfPc+mVlzbwCwfbpIs2uqW5rF78ne37qy2o+iVnJptq9AzPnlC03+zhhB9
JwmjUXVtPuqQZ96tFl0fAT77xGSLzCO8yfEDrxCqdWz2wneShSbCCsC15JB07OgO
StE+MsVBkwe5+PNzAlAr8NZ6f8mzeY/FzaBzlhYw5+c1yyzXJqp+gjRXWrLpD3Ho
hGOvIXIvCBnyVrYI/HPe6DR5w7oteui9Rt0xfUUudaTkt0iz7fc23eGboZ+bpvgT
gbd/kYK6JOrxawMyfBYxrR5zDHIJX0Mws99DNgACKBUfFadKAfwFw0+0airY5WAI
Xs8yhCb5XGwyzVpcB30BrQbWtqdI0PoE9usNvNbH3YFGfuS8oRmAJEgUUQnwOoGK
jMWtHacw0n8QESdRM274LJvLd9nwawYU4svJpf06FtKPqGH3nXefL741NO9KzDAG
PM11YScyJVfYdBDXFM86HU1fBGTKlkLcG/qMJxOqppY4wydRI3koSH6A78nO2QaJ
yqjTOQyCNHaSlmGjdiOvhJ8y1PiazHnuvWBx6z+7JJF2ukqqfjlSARwyfkfnRUIY
la7ZYEqcc56eoPAiElhvrg==
-----END CERTIFICATE-----"""

        // GlobalSign Root R46 (Intermediate 2 cross-signed by GlobalSign Root CA - R3)
        private const val GLOBALSIGN_ROOT_R46_PEM = """-----BEGIN CERTIFICATE-----
MIIFRDCCBCygAwIBAgIQdai62Hj4rzBfct0cEUDfOjANBgkqhkiG9w0BAQwFADBM
MSAwHgYDVQQLExdHbG9iYWxTaWduIFJvb3QgQ0EgLSBSMzETMBEGA1UEChMKR2xv
YmFsU2lnbjETMBEGA1UEAxMKR2xvYmFsU2lnbjAeFw0xOTExMjAwMDAwMDBaFw0y
OTAzMTgwMDAwMDBaMEYxCzAJBgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWdu
IG52LXNhMRwwGgYDVQQDExNHbG9iYWxTaWduIFJvb3QgUjQ2MIICIjANBgkqhkiG
9w0BAQEFAAOCAg8AMIICCgKCAgEArKx0MuizZeW67UMmHaaJDUW6KYiypB1j3dPB
LAlXiTmhVelnNHcMbuRVHVIl0hNrXuEdqbd9iTJfDZ6fLHpjYEAfprC2eI+ZVJYI
WK7kBrxiBQIWv6+oIwO2lA+8bmzCy9Wmuwzp9sEC+yHeZt0Xq3RC7/B0LyX06mtV
W5Dbnd9ehwpA+60Za/v3ymCI3trBj9au1X/UPIPu1xZMg0UzayfQhtAcLWvzq33x
han1KNKt7/OESxyH/BOjOnKiWhEr1idxJ+2BLW1mgZKHtBtYesw/CvpGT014XPgr
SOMEhMtd9rRqs2X8Qp5RJiMgyz0U+YHtZRYATxpkl2YIz4x74yvAnfkU8hvxVmoW
vyyFhc14OJrrQmoCNBiDF06UVvi2grXzlt09875/IHc+exkjayzUcnNDV33g+Ndp
Txc2BPnAkGA3Rd7mDNh0ja6com10XUK+BvXZZG4CEKyJsEw7B01AfiTFipiCeY6k
p4IgjSP6J3HJ38ZBdKBN9pEW3EaMXyljMVlxDNhvwrYyffvmXVOmfhX8u3V8Xez4
9hcc7MdrGcvze/ArB6XZbHlUdmydHKZuDul5DKgjaqPfGzAxn7FUe/5qy2aq3GXQ
op5Kmgcha4GP28RZ+t4iwASc46pbNpPoPb16oZ0LdrELx539z5ioBsL4KqOhg6C3
JXKlAuMCAwEAAaOCASYwggEiMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8EBTAD
AQH/MB0GA1UdDgQWBBQDXKtzgYeozLCm1ZTiNpZJ/wWZLDAfBgNVHSMEGDAWgBSP
8Et/qC5FJK5NUPpjmove4t0bvDA+BggrBgEFBQcBAQQyMDAwLgYIKwYBBQUHMAGG
Imh0dHA6Ly9vY3NwMi5nbG9iYWxzaWduLmNvbS9yb290cjMwNgYDVR0fBC8wLTAr
oCmgJ4YlaHR0cDovL2NybC5nbG9iYWxzaWduLmNvbS9yb290LXIzLmNybDBHBgNV
HSAEQDA+MDwGBFUdIAAwNDAyBggrBgEFBQcCARYmaHR0cHM6Ly93d3cuZ2xvYmFs
c2lnbi5jb20vcmVwb3NpdG9yeS8wDQYJKoZIhvcNAQEMBQADggEBAGBD9cJLVP2v
gPSlomstHU38guKTe4ELEFOnM9OnuzK2HlzmgVSPdf4Go7r8FZt4+d8lVs7JPt2z
THYK/cA1f/DYT56clWZO972GbvjOwmHop4LqAe902mRBxEo966EFsaBqwk8zzexW
nPZ29CXHc3doU0wXhIPzch5d18Mp4y1RRjybOODc89QJSBataMU9rIHNTF6rePxw
gL4/k/zrGiCRCRJ+iblTL/WIhm0ENfNJMTxlbMHxPYH4aiHarVBXpXPkiZ/85knD
XNcnTUTZKEtY6xJExiC+5IHDjdnWz/V2d8MkQx+o7TPbwgF/SVcYcp0GPJG6RkLD
rkNz7Wn9gck=
-----END CERTIFICATE-----"""

        /**
         * Builds an OkHttpClient that trusts all system root CAs plus DEÜ's official
         * GlobalSign R46 intermediate CAs (`GlobalSign GCC R46 OV TLS CA 2025` & `GlobalSign Root R46`),
         * fixing `CertPathValidatorException` on `obs.deu.edu.tr` without disabling TLS verification.
         */
        fun buildDefaultObsHttpClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)

            try {
                val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(null as KeyStore?)
                }
                val defaultTm = defaultTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()

                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                }
                defaultTm?.acceptedIssuers?.forEachIndexed { index, cert ->
                    keyStore.setCertificateEntry("system_$index", cert)
                }

                val certFactory = CertificateFactory.getInstance("X.509")
                listOf(
                    "globalsign_gcc_r46_ov_tls_ca_2025" to GLOBALSIGN_GCC_R46_OV_TLS_CA_2025_PEM,
                    "globalsign_root_r46" to GLOBALSIGN_ROOT_R46_PEM
                ).forEach { (alias, pem) ->
                    ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)).use { stream ->
                        keyStore.setCertificateEntry(alias, certFactory.generateCertificate(stream))
                    }
                }

                val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(keyStore)
                }
                val trustManager = customTmf.trustManagers.filterIsInstance<X509TrustManager>().first()
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), null)
                }
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            } catch (_: Exception) {
                // Fall back to platform default TLS stack if keystore initialization is unavailable
            }

            return builder.build()
        }
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
