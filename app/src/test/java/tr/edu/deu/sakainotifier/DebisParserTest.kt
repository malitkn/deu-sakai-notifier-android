package tr.edu.deu.sakainotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.edu.deu.sakainotifier.data.model.DebisCourseOption
import tr.edu.deu.sakainotifier.data.parser.DebisParser

class DebisParserTest {

    @Test
    fun testParseCourseSelectOptions() {
        val sampleHtml = """
            <select name="ders">
                <option value="">Seçiniz</option>
                <option value="B_BA+_1_9634_3_46_"> - Türk Dili II</option>
                <option value="K_FF+_2_8812_4_60_">Matematik II</option>
                <option value="__1_7743_2_30_">Yabancı Dil II</option>
            </select>
        """.trimIndent()

        val options = DebisParser.parseCourseSelectOptions(sampleHtml)
        assertEquals(3, options.size)

        // 1. Completed course with passing grade
        val turkDili = options[0]
        assertEquals("B_BA+_1_9634_3_46_", turkDili.rawValue)
        assertEquals("B", turkDili.status)
        assertEquals("BA", turkDili.letterGrade)
        assertEquals("1", turkDili.sube)
        assertEquals("9634", turkDili.courseCode)
        assertEquals("3", turkDili.credit)
        assertEquals("46", turkDili.mevcut)
        assertEquals("Türk Dili II", turkDili.courseName)

        // 2. Failed course
        val matematik = options[1]
        assertEquals("K", matematik.status)
        assertEquals("FF", matematik.letterGrade)
        assertEquals("8812", matematik.courseCode)
        assertEquals("4", matematik.credit)

        // 3. Pending course without letter grade yet
        val yabanciDil = options[2]
        assertEquals("", yabanciDil.status)
        assertNull(yabanciDil.letterGrade)
        assertEquals("7743", yabanciDil.courseCode)
    }

    @Test
    fun testParseGradeTableWithAnnouncedGrades() {
        // Matches the user's real screenshot: Vize 80, Final 88, Başarı Notu BA, Büt İLAN EDİLMEMİŞ
        val tableHtml = """
            <table class="not_tablo">
                <tr>
                    <th>DEĞERLENDİRME ADI</th>
                    <th>İLAN TARİHİ</th>
                    <th>SINIF ORT.*</th>
                    <th>ORT** 2</th>
                    <th>SİZİN NOTUNUZ</th>
                </tr>
                <tr>
                    <td>Vize</td>
                    <td>29/04/2026</td>
                    <td>76</td>
                    <td></td>
                    <td>80</td>
                </tr>
                <tr>
                    <td>Final</td>
                    <td>18/06/2026</td>
                    <td>69</td>
                    <td></td>
                    <td>88</td>
                </tr>
                <tr>
                    <td>Yarıyıl Sonu Başarı Notu</td>
                    <td>18/06/2026</td>
                    <td></td>
                    <td>73</td>
                    <td>BA</td>
                </tr>
                <tr>
                    <td>Bütünleme Notu</td>
                    <td>İLAN EDİLMEMİŞ</td>
                    <td></td>
                    <td></td>
                    <td>İLAN EDİLMEMİŞ</td>
                </tr>
                <tr>
                    <td>Bütünleme Sonu Başarı Notu</td>
                    <td>İLAN EDİLMEMİŞ</td>
                    <td></td>
                    <td></td>
                    <td>İLAN EDİLMEMİŞ</td>
                </tr>
            </table>
            <div class="durum_kutusu">BAŞARILI</div>
        """.trimIndent()

        val courseOption = DebisCourseOption(
            rawValue = "B_BA+_1_9634_3_46_",
            status = "B",
            letterGrade = "BA",
            sube = "1",
            courseCode = "9634",
            credit = "3",
            mevcut = "46",
            courseName = "Türk Dili II"
        )

        val parsedGrade = DebisParser.parseGradeTable(tableHtml, courseOption)

        assertEquals("9634", parsedGrade.courseCode)
        assertEquals("Türk Dili II", parsedGrade.courseName)
        assertEquals("80", parsedGrade.vize)
        assertEquals("88", parsedGrade.final)
        assertNull("Bütünleme ilan edilmemiş olmalı", parsedGrade.butunleme)
        assertEquals("BA", parsedGrade.letterGrade)
        assertEquals("B", parsedGrade.status)
        assertEquals("73", parsedGrade.classAverage)
    }

    @Test
    fun testParseGradeTableWithPendingFinal() {
        val tableHtml = """
            <table>
                <tr><td>Vize</td><td>10/04/2026</td><td>60</td><td></td><td>75</td></tr>
                <tr><td>Final</td><td>İLAN EDİLMEMİŞ</td><td></td><td></td><td>İLAN EDİLMEMİŞ</td></tr>
                <tr><td>Yarıyıl Sonu Başarı Notu</td><td>İLAN EDİLMEMİŞ</td><td></td><td></td><td>İLAN EDİLMEMİŞ</td></tr>
            </table>
        """.trimIndent()

        val courseOption = DebisCourseOption(
            rawValue = "__1_1020_3_40_",
            status = "",
            letterGrade = null,
            sube = "1",
            courseCode = "1020",
            credit = "3",
            mevcut = "40",
            courseName = "Fizik II"
        )

        val parsedGrade = DebisParser.parseGradeTable(tableHtml, courseOption)

        assertEquals("75", parsedGrade.vize)
        assertNull("Final henüz açıklanmamış olmalı", parsedGrade.final)
        assertNull("Harf notu henüz açıklanmamış olmalı", parsedGrade.letterGrade)
    }

    @Test
    fun testParseActiveTermIdFromHtml() {
        val htmlWithSelectedTerm = """
            <select name="ogretim_donemi_id">
                <option value="323">2024-2025 Bahar</option>
                <option value="325" selected>2025-2026 Bahar</option>
            </select>
            <select name="ders">
                <option value="B_BA+_1_9634_3_46_"> - Türk Dili II</option>
            </select>
        """.trimIndent()

        assertEquals("325", DebisParser.parseActiveTermId(htmlWithSelectedTerm))
        val options = DebisParser.parseCourseSelectOptions(htmlWithSelectedTerm)
        assertEquals(1, options.size)
        assertEquals("325", options[0].termId)
    }
}
