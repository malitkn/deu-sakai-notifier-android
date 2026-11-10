package tr.edu.deu.sakainotifier.data.parser

import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.DebisCourseOption
import java.util.regex.Pattern

/**
 * Pure Kotlin HTML & String Parser for DEÜ DEBİS Grade Pages.
 * Avoids heavy external scraping dependencies for maximum mobile battery & memory efficiency.
 */
object DebisParser {

    private val OPTION_REGEX = Pattern.compile(
        """<option\s+[^>]*value=["']([^"']+)["'][^>]*>(.*?)</option>""",
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

    /**
     * Extracts courses from `<select name="ders">` dropdown.
     * Values match `B_BA+_1_9634_3_46_` pattern.
     */
    fun parseCourseSelectOptions(html: String): List<DebisCourseOption> {
        val results = mutableListOf<DebisCourseOption>()
        val matcher = OPTION_REGEX.matcher(html)

        while (matcher.find()) {
            val rawValue = matcher.group(1)?.trim() ?: continue
            val rawText = cleanHtmlEntities(matcher.group(2) ?: "").trim()

            // Skip default empty placeholder options (e.g. value="" or "Seçiniz")
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

                // Clean course name (strip leading symbols or codes if duplicated)
                val cleanName = cleanCourseName(rawText, courseCode)

                results.add(
                    DebisCourseOption(
                        rawValue = rawValue,
                        status = status,
                        letterGrade = letterGrade,
                        sube = sube,
                        courseCode = courseCode,
                        credit = credit,
                        mevcut = mevcut,
                        courseName = cleanName
                    )
                )
            }
        }

        return results
    }

    /**
     * Parses grade details table returned from DEBİS POST query.
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

        val rowMatcher = ROW_REGEX.matcher(html)
        while (rowMatcher.find()) {
            val rowContent = rowMatcher.group(1) ?: continue
            val cells = extractCells(rowContent)
            if (cells.size < 2) continue

            val evalName = cells[0].lowercase()
            // Typically: cell 0: DEĞERLENDİRME ADI, cell 1: İLAN TARİHİ, cell 2: SINIF ORT, cell 3: ORT 2, last cell: SİZİN NOTUNUZ
            val yourScore = cells.last().trim()

            when {
                evalName.contains("vize") || evalName.contains("ara sınav") -> {
                    if (isAnnounced(yourScore)) {
                        vize = yourScore
                    }
                }
                evalName.contains("final") || evalName.contains("yarıyıl sonu sınavı") -> {
                    if (isAnnounced(yourScore)) {
                        finalNote = yourScore
                    }
                }
                evalName.contains("bütünleme notu") -> {
                    if (isAnnounced(yourScore)) {
                        butunleme = yourScore
                    }
                }
                evalName.contains("yarıyıl sonu başarı") || evalName.contains("başarı notu") -> {
                    if (isAnnounced(yourScore)) {
                        letterGrade = yourScore
                    }
                    // Check if second-to-last cell has term average (e.g. 73)
                    if (cells.size >= 4) {
                        val avgCandidate = cells[cells.size - 2].trim()
                        if (avgCandidate.isNotEmpty() && avgCandidate.all { it.isDigit() || it == '.' || it == ',' }) {
                            classAverage = avgCandidate
                        }
                    }
                }
            }
        }

        // Check overall status banner (e.g. "BAŞARILI" or "BAŞARISIZ" at page footer)
        if (html.contains("BAŞARILI", ignoreCase = true) && !html.contains("BAŞARISIZ", ignoreCase = true)) {
            status = "B"
        } else if (html.contains("BAŞARISIZ", ignoreCase = true)) {
            status = "K"
        }

        return CourseGrade(
            courseCode = courseOption.courseCode,
            courseName = courseOption.courseName,
            credit = courseOption.credit,
            sube = courseOption.sube,
            vize = vize,
            final = finalNote,
            butunleme = butunleme,
            letterGrade = letterGrade,
            status = status,
            classAverage = classAverage,
            lastUpdated = System.currentTimeMillis()
        )
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
        val normalized = text.lowercase().trim()
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

    private fun cleanCourseName(rawText: String, courseCode: String): String {
        var cleaned = rawText
        if (cleaned.startsWith("-") || cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim()
        }
        return cleaned
    }
}
