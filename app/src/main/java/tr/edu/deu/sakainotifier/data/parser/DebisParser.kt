package tr.edu.deu.sakainotifier.data.parser

import org.json.JSONArray
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.DebisCourseOption
import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure Kotlin HTML & JSON Parser for DEÜ OBS (`obs.deu.edu.tr`) Grade Pages.
 * Avoids heavy external scraping dependencies for maximum mobile battery & memory efficiency.
 */
object DebisParser {

    private val TR_LOCALE = Locale("tr", "TR")

    private val OPTION_REGEX = Pattern.compile(
        """<option\s+[^>]*value=["']([^"']+)["'][^>]*>(.*?)</option>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val TERM_SELECT_REGEX = Pattern.compile(
        """<select[^>]*name=["'](?:donem|ogretim_donemi_id)["'][^>]*>(.*?)</select>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val ANY_TERM_OPTION_REGEX = Pattern.compile(
        """<option[^>]*value=["'](\d+)["'][^>]*>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val ROW_REGEX = Pattern.compile(
        """<tr[^>]*>(.*?)</tr>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val CELL_REGEX = Pattern.compile(
        """<t[dh][^>]*>(.*?)</t[dh]>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    private val JSON_COURSE_OPTION_REGEX = Pattern.compile(
        """"optionValue"\s*:\s*"([^"]*)"\s*,\s*"optionDisplay"\s*:\s*"([^"]*)""""
    )

    /**
     * Extracts the active/latest semester ID (`donem` or `ogretim_donemi_id`) from the grade page HTML.
     * Picks the highest numeric option value (e.g. "333" over "324"). Defaults to "333".
     */
    fun parseActiveTermId(html: String): String {
        val selectMatcher = TERM_SELECT_REGEX.matcher(html)
        if (selectMatcher.find()) {
            val scopeHtml = selectMatcher.group(1) ?: ""
            val anyMatcher = ANY_TERM_OPTION_REGEX.matcher(scopeHtml)
            val termIds = mutableListOf<String>()
            while (anyMatcher.find()) {
                val id = anyMatcher.group(1)?.trim()
                if (!id.isNullOrBlank()) {
                    termIds.add(id)
                }
            }
            val latestTermId = termIds.maxByOrNull { it.toIntOrNull() ?: 0 }
            if (!latestTermId.isNullOrBlank()) {
                return latestTermId
            }
        }
        return "333"
    }

    /**
     * Parses JSON response returned by `notgoruntuleme/index.ajax.php` (`islem=dersgetir&donem=<id>`).
     * Sample item: `{"optionValue":"1128_194_1","optionDisplay":"KİM 1201 Kimya I."}`.
     * Uses the full `optionValue` as `courseCode` so courses sharing a department prefix never collide.
     */
    fun parseCourseOptionsJson(jsonStr: String, termId: String = "333"): List<DebisCourseOption> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<DebisCourseOption>()
        try {
            val jsonArray = JSONArray(trimmed)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                buildCourseOptionFromPair(
                    rawValue = obj.optString("optionValue", ""),
                    rawDisplay = obj.optString("optionDisplay", ""),
                    termId = termId
                )?.let(results::add)
            }
        } catch (_: Exception) {
            // Fall through to regex fallback when org.json is unavailable or stubbed in JVM unit tests.
        }

        if (results.isEmpty()) {
            val matcher = JSON_COURSE_OPTION_REGEX.matcher(trimmed)
            while (matcher.find()) {
                buildCourseOptionFromPair(
                    rawValue = matcher.group(1).orEmpty(),
                    rawDisplay = matcher.group(2).orEmpty(),
                    termId = termId
                )?.let(results::add)
            }
        }
        return results
    }

    private fun buildCourseOptionFromPair(
        rawValue: String,
        rawDisplay: String,
        termId: String
    ): DebisCourseOption? {
        val cleanValue = rawValue.trim()
        val cleanDisplay = cleanHtmlEntities(rawDisplay).trim()
        if (cleanValue.isEmpty() || cleanValue == "0" || cleanDisplay.equals("Seçiniz", ignoreCase = true)) {
            return null
        }

        val sube = cleanValue.substringAfterLast("_", "1").ifBlank { "1" }
        return DebisCourseOption(
            rawValue = cleanValue,
            status = "",
            letterGrade = null,
            sube = sube,
            courseCode = cleanValue,
            credit = "",
            mevcut = "",
            courseName = cleanDisplay,
            termId = termId
        )
    }

    /**
     * Extracts courses from JSON array or `<select name="ders">` dropdown HTML.
     */
    fun parseCourseSelectOptions(html: String): List<DebisCourseOption> {
        if (html.trimStart().startsWith("[")) {
            return parseCourseOptionsJson(html)
        }

        val activeTermId = parseActiveTermId(html)
        val results = mutableListOf<DebisCourseOption>()
        val matcher = OPTION_REGEX.matcher(html)

        while (matcher.find()) {
            val rawValue = matcher.group(1)?.trim() ?: continue
            val rawText = cleanHtmlEntities(matcher.group(2) ?: "").trim()

            if (!rawValue.contains("_") || rawValue.equals("0", ignoreCase = true)) {
                continue
            }

            val parts = rawValue.split("_")
            if (parts.size >= 6) {
                val status = parts[0].trim()
                val letterGradeRaw = parts[1].replace("+", " ").trim()
                val letterGrade = if (letterGradeRaw.isNotEmpty()) letterGradeRaw else null
                val sube = parts[2].trim()
                val courseCode = parts[3].trim()
                val credit = parts[4].trim()
                val mevcut = parts[5].trim()
                val cleanName = cleanCourseName(rawText)

                results.add(
                    DebisCourseOption(
                        rawValue = rawValue,
                        status = status,
                        letterGrade = letterGrade,
                        sube = sube,
                        courseCode = courseCode,
                        credit = credit,
                        mevcut = mevcut,
                        courseName = cleanName,
                        termId = activeTermId
                    )
                )
            } else {
                val sube = rawValue.substringAfterLast("_", "1").ifBlank { "1" }
                results.add(
                    DebisCourseOption(
                        rawValue = rawValue,
                        status = "",
                        letterGrade = null,
                        sube = sube,
                        courseCode = rawValue,
                        credit = "",
                        mevcut = "",
                        courseName = cleanCourseName(rawText),
                        termId = activeTermId
                    )
                )
            }
        }

        return results
    }

    /**
     * Parses course metadata & evaluations table returned from OBS `notgoruntuleme/index.ajax.php` (`islem=notgetir`).
     */
    fun parseGradeTable(
        html: String,
        courseOption: DebisCourseOption
    ): CourseGrade {
        var vize: String? = null
        var finalNote: String? = null
        var butunleme: String? = null
        var letterGrade: String? = courseOption.letterGrade
        var status: String? = courseOption.status.ifEmpty { null }
        var classAverage: String? = null
        var extractedCredit = courseOption.credit
        var extractedSube = courseOption.sube
        val evaluations = linkedMapOf<String, String>()

        val rowMatcher = ROW_REGEX.matcher(html)
        while (rowMatcher.find()) {
            val rowContent = rowMatcher.group(1) ?: continue
            val cells = extractCells(rowContent)
            if (cells.size < 2) continue

            val firstCell = cells[0].trim()
            val firstLower = firstCell.lowercase(TR_LOCALE)

            // Skip header and metadata rows; optionally capture credit/branch from metadata table
            if (isMetadataOrHeaderRow(firstLower)) {
                if (firstLower.contains("akts kredi") || (firstLower.contains("toplam kredi") && extractedCredit.isBlank())) {
                    val creditVal = cells.getOrNull(1)?.trim().orEmpty()
                    if (creditVal.isNotBlank()) extractedCredit = creditVal
                } else if (firstLower.contains("şube adı") && extractedSube.isBlank()) {
                    val subeVal = cells.getOrNull(1)?.trim().orEmpty()
                    if (subeVal.isNotBlank()) extractedSube = subeVal
                }
                continue
            }

            val yourScore = cells.last().trim()
            val announced = if (cells.size >= 5) {
                val announceDate = cells[1].trim()
                isAnnounced(announceDate) && isAnnounced(yourScore)
            } else {
                isAnnounced(yourScore)
            }

            if (!announced) continue

            if (firstLower.contains("başarı notu")) {
                letterGrade = yourScore
                if (cells.size >= 4) {
                    val avgCandidate = cells[cells.size - 2].trim()
                    if (avgCandidate.isNotEmpty() && avgCandidate.all { it.isDigit() || it == '.' || it == ',' }) {
                        classAverage = avgCandidate
                    }
                }
            } else {
                evaluations[firstCell] = yourScore
                when {
                    firstLower.contains("vize") || firstLower.contains("ara sınav") -> {
                        vize = yourScore
                    }
                    firstLower.contains("final") || firstLower.contains("yarıyıl sonu sınavı") -> {
                        finalNote = yourScore
                    }
                    firstLower.contains("bütünleme") -> {
                        butunleme = yourScore
                    }
                }
            }
        }

        // Check overall status banner (e.g. "BAŞARILI" or "BAŞARISIZ" at page footer)
        if (html.contains("BAŞARISIZ", ignoreCase = true)) {
            status = "K"
        } else if (html.contains("BAŞARILI", ignoreCase = true)) {
            status = "B"
        }

        return CourseGrade(
            courseCode = courseOption.courseCode,
            courseName = courseOption.courseName,
            credit = extractedCredit,
            sube = extractedSube,
            termId = courseOption.termId,
            evaluations = evaluations,
            vize = vize,
            final = finalNote,
            butunleme = butunleme,
            letterGrade = letterGrade,
            status = status,
            classAverage = classAverage,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun isMetadataOrHeaderRow(firstLower: String): Boolean {
        return firstLower.contains("değerlendirme adı") ||
            firstLower.contains("birim adı") ||
            firstLower.contains("bölüm adı") ||
            firstLower.contains("ders adı") ||
            firstLower.contains("şube adı") ||
            firstLower.contains("toplam kredi") ||
            firstLower.contains("akts kredi") ||
            firstLower.contains("tekrar sayısı") ||
            firstLower.contains("öğretim elemanı")
    }

    private fun extractCells(rowHtml: String): List<String> {
        val cells = mutableListOf<String>()
        val cellMatcher = CELL_REGEX.matcher(rowHtml)
        while (cellMatcher.find()) {
            val content = cellMatcher.group(1) ?: ""
            cells.add(cleanHtmlEntities(stripTags(content)).trim())
        }
        return cells
    }

    private fun isAnnounced(text: String): Boolean {
        val normalized = text.lowercase(TR_LOCALE).trim()
        if (normalized.isEmpty()) return false
        if (normalized.contains("ilan edilmemiş") || normalized.contains("ilan edilmedi") || normalized == "-") {
            return false
        }
        return true
    }

    private fun stripTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanCourseName(rawText: String): String {
        var cleaned = rawText
        if (cleaned.startsWith("-") || cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim()
        }
        return cleaned
    }
}
