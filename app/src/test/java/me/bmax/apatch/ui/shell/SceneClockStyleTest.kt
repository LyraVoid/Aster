package me.bmax.apatch.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneClockStyleTest {
    @Test
    fun everyStoredValueIsReadBackAsItsOwnStyle() {
        SceneClockStyle.entries.forEach { style ->
            assertEquals(style, SceneClockStyle.fromValue(style.value))
        }
    }

    @Test
    fun anUnknownStyleFallsBackToTheShapeTheRailAlwaysDrew() {
        assertEquals(SceneClockStyle.Stacked, SceneClockStyle.Default)
        assertEquals(SceneClockStyle.Stacked, SceneClockStyle.fromValue(null))
        assertEquals(SceneClockStyle.Stacked, SceneClockStyle.fromValue(""))
        assertEquals(SceneClockStyle.Stacked, SceneClockStyle.fromValue("hexagon"))
    }

    @Test
    fun noTwoStylesShareAValue() {
        val values = SceneClockStyle.entries.map { it.value }
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.all { it.isNotBlank() })
    }
}
