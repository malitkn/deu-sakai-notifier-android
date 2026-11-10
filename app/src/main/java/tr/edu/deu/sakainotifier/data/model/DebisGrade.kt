package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject

/**
 * Represents parsed option item from `<select name="ders">` dropdown.
 * Value format: B_BA+_1_9634_3_46_
 */
data class DebisCourseOption(
    val rawValue: String,
    val status: String,            // e.g. "B" (Başarılı), "K" (Kaldı), "" (Devam ediyor)
    val letterGrade: String?,      // e.g. "BA", "AA", null if not yet announced
    val sube: String,              // e.g. "1"
    val courseCode: String,        // e.g. "9634"
    val credit: String,            // e.g. "3"
    val mevcut: String,            // e.g. "46"
    val courseName: String,        // e.g. "Türk Dili II"
    val termId: String = "324"     // Active ogretim_donemi_id extracted from page (fallback "324")
)

/**
 * Detailed grades and evaluation results for an individual course.
 */
data class CourseGrade(
    val courseCode: String,
    val courseName: String,
    val credit: String = "",
    val sube: String = "1",
    val vize: String? = null,
    val final: String? = null,
    val butunleme: String? = null,
    val letterGrade: String? = null,
    val status: String? = null,
    val classAverage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("courseCode", courseCode)
            put("courseName", courseName)
            put("credit", credit)
            put("sube", sube)
            put("vize", vize ?: JSONObject.NULL)
            put("final", final ?: JSONObject.NULL)
            put("butunleme", butunleme ?: JSONObject.NULL)
            put("letterGrade", letterGrade ?: JSONObject.NULL)
            put("status", status ?: JSONObject.NULL)
            put("classAverage", classAverage ?: JSONObject.NULL)
            put("lastUpdated", lastUpdated)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): CourseGrade {
            return CourseGrade(
                courseCode = json.optString("courseCode", ""),
                courseName = json.optString("courseName", ""),
                credit = json.optString("credit", ""),
                sube = json.optString("sube", "1"),
                vize = if (json.isNull("vize")) null else json.optString("vize", null),
                final = if (json.isNull("final")) null else json.optString("final", null),
                butunleme = if (json.isNull("butunleme")) null else json.optString("butunleme", null),
                letterGrade = if (json.isNull("letterGrade")) null else json.optString("letterGrade", null),
                status = if (json.isNull("status")) null else json.optString("status", null),
                classAverage = if (json.isNull("classAverage")) null else json.optString("classAverage", null),
                lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Representation of a detected grade difference between polling cycles.
 */
data class GradeDifference(
    val courseCode: String,
    val courseName: String,
    val evaluationType: String,    // "Vize", "Final", "Bütünleme", "Yarıyıl Sonu Başarı Notu"
    val oldValue: String?,
    val newValue: String,
    val letterGrade: String? = null,
    val status: String? = null
)

/**
 * Holds captcha image bytes and session cookie for native user resolution.
 */
data class DebisCaptchaSession(
    val sessionId: String,         // PHPSESSID cookie value
    val captchaImageBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DebisCaptchaSession
        if (sessionId != other.sessionId) return false
        if (!captchaImageBytes.contentEquals(other.captchaImageBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + captchaImageBytes.contentHashCode()
        return result
    }
}
