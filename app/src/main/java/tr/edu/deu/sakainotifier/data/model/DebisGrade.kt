package tr.edu.deu.sakainotifier.data.model

import org.json.JSONObject

/**
 * Represents a parsed course option item from OBS `notgoruntuleme/index.ajax.php` (`islem=dersgetir`)
 * or `<select name="ders">` dropdown.
 * Value format in OBS: `<departmentId>_<courseId>_<sube>` (e.g. `1128_194_1`).
 */
data class DebisCourseOption(
    val rawValue: String,
    val status: String = "",        // e.g. "B" (Başarılı), "K" (Kaldı), "" (Devam ediyor)
    val letterGrade: String? = null, // e.g. "BA", "AA", null if not yet announced
    val sube: String = "1",          // e.g. "1"
    val courseCode: String,          // Full unique rawValue e.g. "1128_194_1"
    val credit: String = "",         // e.g. "3"
    val mevcut: String = "",         // e.g. "46"
    val courseName: String,          // e.g. "KİM 1201 Kimya I."
    val termId: String = "333"       // Active donem / term ID extracted from page (fallback "333")
)

/**
 * Detailed grades and evaluation results for an individual course.
 */
data class CourseGrade(
    val courseCode: String,
    val courseName: String,
    val credit: String = "",
    val sube: String = "1",
    val termId: String = "",
    val evaluations: Map<String, String> = emptyMap(),
    val vize: String? = null,
    val final: String? = null,
    val butunleme: String? = null,
    val letterGrade: String? = null,
    val status: String? = null,
    val classAverage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Formats all recorded evaluations (e.g. Vize, Quiz, Ödev, Final, Bütünleme) into a compact single-line summary.
     */
    fun formatEvaluationsSummary(): String {
        if (evaluations.isEmpty()) {
            return "Vize: ${vize ?: "—"}  •  Final: ${final ?: "—"}"
        }
        val base = evaluations.entries.joinToString("  •  ") { "${it.key}: ${it.value}" }
        val hasFinal = evaluations.keys.any {
            it.equals("Final", ignoreCase = true) || it.equals("Yarıyıl Sonu Sınavı", ignoreCase = true)
        }
        return if (hasFinal) base else "$base  •  Final: —"
    }

    fun toJsonObject(): JSONObject {
        val evalJson = JSONObject()
        for ((key, value) in evaluations) {
            evalJson.put(key, value)
        }
        return JSONObject().apply {
            put("courseCode", courseCode)
            put("courseName", courseName)
            put("credit", credit)
            put("sube", sube)
            put("termId", termId)
            put("evaluations", evalJson)
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
            val vizeVal = if (json.isNull("vize")) null else json.optString("vize", null)
            val finalVal = if (json.isNull("final")) null else json.optString("final", null)
            val butunlemeVal = if (json.isNull("butunleme")) null else json.optString("butunleme", null)

            val parsedEvaluations = linkedMapOf<String, String>()
            val evalObj = json.optJSONObject("evaluations")
            if (evalObj != null) {
                val keys = evalObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = evalObj.optString(k, "").trim()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        parsedEvaluations[k] = v
                    }
                }
            } else {
                // Reconstruct evaluations map from legacy snapshots without an evaluations object
                if (!vizeVal.isNullOrBlank()) parsedEvaluations["Vize"] = vizeVal
                if (!finalVal.isNullOrBlank()) parsedEvaluations["Final"] = finalVal
                if (!butunlemeVal.isNullOrBlank()) parsedEvaluations["Bütünleme Notu"] = butunlemeVal
            }

            return CourseGrade(
                courseCode = json.optString("courseCode", ""),
                courseName = json.optString("courseName", ""),
                credit = json.optString("credit", ""),
                sube = json.optString("sube", "1"),
                termId = json.optString("termId", ""),
                evaluations = parsedEvaluations,
                vize = vizeVal,
                final = finalVal,
                butunleme = butunlemeVal,
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
    val evaluationType: String,    // "Vize", "Quiz", "Final", "Bütünleme", "Harf Notu", etc.
    val oldValue: String?,
    val newValue: String,
    val letterGrade: String? = null,
    val status: String? = null
)
