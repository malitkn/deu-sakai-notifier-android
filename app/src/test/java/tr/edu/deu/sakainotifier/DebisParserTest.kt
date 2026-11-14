package tr.edu.deu.sakainotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.DebisCourseOption
import tr.edu.deu.sakainotifier.data.parser.DebisParser

class DebisParserTest {

    @Test
    fun testParseActiveTermIdFromObsDonemSelect() {
        val obsHtml = """
            <select name='donem' id='donem' onchange='change_donem()' class='form-control'>
                <option value=''>Dönem Seçiniz</option>
                <option value='333'>2026-2027 Güz</option>
                <option value='324'>2025-2026 Bahar</option>
            </select>
        """.trimIndent()

        assertEquals("333", DebisParser.parseActiveTermId(obsHtml))
    }

    @Test
    fun testParseCourseOptionsJson() {
        val jsonResponse = """
            [
                {"optionValue":"","optionDisplay":"Seçiniz"},
                {"optionValue":"9634_8_2","optionDisplay":"BDE 1003 Beden Eğitimi"},
                {"optionValue":"1128_194_1","optionDisplay":"KİM 1201 Kimya I."},
                {"optionValue":"1128_383_1","optionDisplay":"FİZ 1013 FİZİK I ( Mekanik )"}
            ]
        """.trimIndent()

        val options = DebisParser.parseCourseOptionsJson(jsonResponse, termId = "333")
        assertEquals(3, options.size)

        assertEquals("9634_8_2", options[0].courseCode)
        assertEquals("2", options[0].sube)
        assertEquals("BDE 1003 Beden Eğitimi", options[0].courseName)
        assertEquals("333", options[0].termId)

        // Courses sharing department prefix 1128 must keep distinct full courseCode keys
        assertEquals("1128_194_1", options[1].courseCode)
        assertEquals("1128_383_1", options[2].courseCode)
        assertTrue(options[1].courseCode != options[2].courseCode)
    }

    @Test
    fun testParseGradeTableWithQuizAndVize() {
        val tableHtml = """
            <table class='table table-bordered'>
                <tr><td><b>Ders Adı</b></td><td>KİM 1201 Kimya I.</td></tr>
                <tr><td><b>Şube Adı</b></td><td>1.Şube</td></tr>
                <tr><td><b>AKTS Kredi</b></td><td>5</td></tr>
            </table>
            <table id='ogrnottable' class='table table-bordered table-striped'>
                <thead>
                    <tr>
                        <td align='left'><b>DEĞERLENDİRME ADI</b></td>
                        <td align='center'><b>İLAN TARİHİ</b></td>
                        <td align='center'><b>SINIF ORT.</b></td>
                        <td align='center'><b>ORT 2</b></td>
                        <td align='center'><b>SİZİN NOTUNUZ</b></td>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td align='left'>Vize</td>
                        <td align='center'>13/11/2026</td>
                        <td align='center'>37.2</td>
                        <td align='center'></td>
                        <td align='center'>70</td>
                    </tr>
                    <tr>
                        <td align='left'>Quiz</td>
                        <td align='center'>13/11/2026</td>
                        <td align='center'>52.86</td>
                        <td align='center'></td>
                        <td align='center'>90</td>
                    </tr>
                    <tr>
                        <td align='left'>Final</td>
                        <td align='center'>İLAN EDİLMEMİŞ</td>
                        <td align='center'></td>
                        <td align='center'></td>
                        <td align='center'></td>
                    </tr>
                    <tr>
                        <td align='left'>Yarıyıl Sonu Başarı Notu</td>
                        <td align='center'>İLAN EDİLMEMİŞ</td>
                        <td align='center'></td>
                        <td align='center'></td>
                        <td align='center'></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val courseOption = DebisCourseOption(
            rawValue = "1128_194_1",
            courseCode = "1128_194_1",
            courseName = "KİM 1201 Kimya I.",
            sube = "1",
            termId = "333"
        )

        val parsed = DebisParser.parseGradeTable(tableHtml, courseOption)

        assertEquals("1128_194_1", parsed.courseCode)
        assertEquals("333", parsed.termId)
        assertEquals("5", parsed.credit)
        assertEquals("70", parsed.evaluations["Vize"])
        assertEquals("90", parsed.evaluations["Quiz"])
        assertEquals("70", parsed.vize)
        assertNull("Final henüz ilan edilmemiş olmalı", parsed.final)
        assertNull("Harf notu henüz ilan edilmemiş olmalı", parsed.letterGrade)
    }

    @Test
    fun testParseGradeTableWithFinalAndLetterGrade() {
        val tableHtml = """
            <table id='ogrnottable' class='table table-bordered table-striped'>
                <thead>
                    <tr>
                        <td><b>DEĞERLENDİRME ADI</b></td>
                        <td><b>İLAN TARİHİ</b></td>
                        <td><b>SINIF ORT.</b></td>
                        <td><b>ORT 2</b></td>
                        <td><b>SİZİN NOTUNUZ</b></td>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td align='left'>Vize</td>
                        <td align='center'>29/04/2026</td>
                        <td align='center'>76.84</td>
                        <td align='center'></td>
                        <td align='center'>80</td>
                    </tr>
                    <tr>
                        <td align='left'>Final</td>
                        <td align='center'>18/06/2026</td>
                        <td align='center'>69.66</td>
                        <td align='center'></td>
                        <td align='center'>88</td>
                    </tr>
                    <tr>
                        <td align='left'>Yarıyıl Sonu Başarı Notu</td>
                        <td align='center'>18/06/2026</td>
                        <td align='center'></td>
                        <td align='center'>73.04</td>
                        <td align='center'>BA </td>
                    </tr>
                    <tr>
                        <td align='left'>Bütünleme Notu</td>
                        <td align='center'>İLAN EDİLMEMİŞ</td>
                        <td align='center'></td>
                        <td align='center'></td>
                        <td align='center'></td>
                    </tr>
                </tbody>
            </table>
            <table class='table'>
                <tr><td colspan='5'><B>BAŞARILI</B></td></tr>
            </table>
        """.trimIndent()

        val courseOption = DebisCourseOption(
            rawValue = "9634_3_46",
            courseCode = "9634_3_46",
            courseName = "TDL 1002 Türk Dili II",
            sube = "1",
            termId = "324"
        )

        val parsed = DebisParser.parseGradeTable(tableHtml, courseOption)

        assertEquals("80", parsed.vize)
        assertEquals("88", parsed.final)
        assertNull("Bütünleme ilan edilmemiş olmalı", parsed.butunleme)
        assertEquals("BA", parsed.letterGrade)
        assertEquals("73.04", parsed.classAverage)
        assertEquals("B", parsed.status)
    }

    @Test
    fun testCourseGradeJsonSerializationWithEvaluations() {
        val midTermOnly = CourseGrade(
            courseCode = "1128_194_1",
            courseName = "KİM 1201 Kimya I.",
            credit = "5",
            sube = "1",
            termId = "333",
            evaluations = linkedMapOf("Vize" to "70", "Quiz" to "90"),
            vize = "70"
        )

        assertEquals("Vize: 70  •  Quiz: 90  •  Final: —", midTermOnly.formatEvaluationsSummary())

        val withFinalAndLetter = CourseGrade(
            courseCode = "9634_3_46",
            courseName = "TDL 1002 Türk Dili II",
            credit = "2",
            sube = "1",
            termId = "324",
            evaluations = linkedMapOf("Vize" to "80", "Final" to "88"),
            vize = "80",
            final = "88",
            letterGrade = "BA"
        )

        assertEquals("Vize: 80  •  Final: 88", withFinalAndLetter.formatEvaluationsSummary())

        val json = midTermOnly.toJsonObject()
        val encodedCourseCode: String? = json.optString("courseCode")
        if (!encodedCourseCode.isNullOrEmpty()) {
            val restored = CourseGrade.fromJsonObject(json)
            assertEquals("1128_194_1", restored.courseCode)
            assertEquals("333", restored.termId)
            assertEquals("70", restored.evaluations["Vize"])
            assertEquals("90", restored.evaluations["Quiz"])
            assertEquals("70", restored.vize)
            assertEquals("Vize: 70  •  Quiz: 90  •  Final: —", restored.formatEvaluationsSummary())
        }
    }
}
