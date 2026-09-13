package me.bmax.apatch.ui.shell

import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scene clock follows the phone's 12/24-hour setting. These cover the numbers only: which
 * marker a locale writes is a question for the locale, so the tests ask that a marker exists and
 * that it tells the two halves of the day apart rather than spelling it out.
 */
class SceneClockTimeTest {

    @Test
    fun `a 24 hour phone keeps the padded form`() {
        val time = LocalTime.of(15, 7)

        assertEquals("15:07", sceneTimeText(time, is24Hour = true))
        assertEquals("15", sceneHourText(time, is24Hour = true))
        assertNull(sceneMeridiemText(time, is24Hour = true, Locale.US))
    }

    @Test
    fun `a 12 hour phone drops the pad and adds a marker`() {
        val time = LocalTime.of(15, 7)

        assertEquals("3:07", sceneTimeText(time, is24Hour = false))
        assertEquals("3", sceneHourText(time, is24Hour = false))
        val meridiem = sceneMeridiemText(time, is24Hour = false, Locale.US)
        assertTrue("expected a marker after noon", !meridiem.isNullOrBlank())
    }

    @Test
    fun `midnight and noon do not turn into zero`() {
        assertEquals("12", sceneHourText(LocalTime.of(0, 5), is24Hour = false))
        assertEquals("12:00", sceneTimeText(LocalTime.of(12, 0), is24Hour = false))
        assertEquals("12", sceneHourText(LocalTime.of(12, 0), is24Hour = false))

        // The 24-hour shape is untouched by any of this.
        assertEquals("00", sceneHourText(LocalTime.of(0, 5), is24Hour = true))
        assertEquals("00:05", sceneTimeText(LocalTime.of(0, 5), is24Hour = true))
    }

    @Test
    fun `the marker tells the halves of the day apart`() {
        for (locale in listOf(Locale.US, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)) {
            val morning = sceneMeridiemText(LocalTime.of(3, 7), is24Hour = false, locale)
            val afternoon = sceneMeridiemText(LocalTime.of(15, 7), is24Hour = false, locale)

            assertTrue("no marker for $locale", !morning.isNullOrBlank())
            assertTrue("no marker for $locale", !afternoon.isNullOrBlank())
            assertNotEquals("same marker for both halves in $locale", morning, afternoon)
        }
    }

    @Test
    fun `both shapes read the same minute`() {
        val time = LocalTime.of(9, 5)

        assertEquals("09:05", sceneTimeText(time, is24Hour = true))
        assertEquals("9:05", sceneTimeText(time, is24Hour = false))
        assertEquals("9", sceneHourText(time, is24Hour = false))
    }
}
