package tr.edu.deu.sakainotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.edu.deu.sakainotifier.data.model.SakaiNotification
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SakaiNotificationDateTest {

    private val turkeyZone = TimeZone.getTimeZone("GMT+3")

    @Test
    fun testEpochTimestamps() {
        // 1729265400 = 2024-10-18 15:30:00 UTC = 18:30:00 GMT+3
        val epochSeconds = 1729265400L
        val formatted = SakaiNotification.formatPublishedDate(epochSeconds)
        assertNotNull(formatted)
        assertTrue(formatted!!.contains("18:30") || formatted.contains("15:30"))

        val millis = SakaiNotification.parsePublishedMillis(epochSeconds)
        assertEquals(epochSeconds * 1000L, millis)

        val epochMillis = 1729265400000L
        val millisFromMillis = SakaiNotification.parsePublishedMillis(epochMillis)
        assertEquals(epochMillis, millisFromMillis)
    }

    @Test
    fun testTurkishStandardDateFormat() {
        val dateStr = "18.10.2026 14:30"
        val millis = SakaiNotification.parsePublishedMillis(dateStr)
        assertTrue(millis > 0L)

        val formatted = SakaiNotification.formatPublishedDate(dateStr)
        assertEquals("18.10.2026 14:30", formatted)

        val slashStr = "18/10/2026 14:30"
        val slashMillis = SakaiNotification.parsePublishedMillis(slashStr)
        assertEquals(millis, slashMillis)
    }

    @Test
    fun testRelativeTodayAndYesterday() {
        val todayStr = "Bugün 14:30"
        val todayMillis = SakaiNotification.parsePublishedMillis(todayStr)
        assertTrue(todayMillis > 0L)

        val todayFormatted = SakaiNotification.formatPublishedDate(todayStr)
        assertNotNull(todayFormatted)
        assertTrue(todayFormatted!!.endsWith("14:30"))

        val yesterdayStr = "Dün 14:30"
        val yesterdayMillis = SakaiNotification.parsePublishedMillis(yesterdayStr)
        assertTrue(yesterdayMillis > 0L)
        assertTrue("Today millis must be greater than yesterday", todayMillis > yesterdayMillis)

        // Time difference should be exactly ~24 hours (86,400,000 ms)
        val diffHours = (todayMillis - yesterdayMillis) / (1000 * 60 * 60)
        assertEquals(24L, diffHours)
    }

    @Test
    fun testTimeOnlyFormat() {
        // Roundcube outputs just "HH:mm" for today's emails
        val timeStr = "15:45"
        val millis = SakaiNotification.parsePublishedMillis(timeStr)
        assertTrue(millis > 0L)

        val formatted = SakaiNotification.formatPublishedDate(timeStr)
        assertNotNull(formatted)
        assertTrue(formatted!!.endsWith("15:45"))

        val cal = Calendar.getInstance(turkeyZone)
        val todayPrefix = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")).apply {
            timeZone = turkeyZone
        }.format(cal.time)

        assertEquals("$todayPrefix 15:45", formatted)
    }

    @Test
    fun testTurkishMonthNames() {
        val monthStr = "18 Eki 2026 14:30"
        val millis = SakaiNotification.parsePublishedMillis(monthStr)
        assertTrue(millis > 0L)

        val formatted = SakaiNotification.formatPublishedDate(monthStr)
        assertEquals("18.10.2026 14:30", formatted)
    }

    @Test
    fun testChronologicalSortingDoesNotDefaultToZero() {
        val itemRecent = "18.10.2026 14:30"
        val itemOlder = "15.10.2026 10:00"
        val itemOldest = "01.10.2026 09:00"

        val millisRecent = SakaiNotification.parsePublishedMillis(itemRecent)
        val millisOlder = SakaiNotification.parsePublishedMillis(itemOlder)
        val millisOldest = SakaiNotification.parsePublishedMillis(itemOldest)

        assertTrue(millisRecent > millisOlder)
        assertTrue(millisOlder > millisOldest)
        assertTrue(millisOldest > 0L)
    }
}
