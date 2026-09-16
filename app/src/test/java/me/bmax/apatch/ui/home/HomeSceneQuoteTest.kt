package me.bmax.apatch.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSceneQuoteTest {

    private val builtIn = listOf("one", "two", "three")

    @Test
    fun anUnwrittenQuoteFallsBackToTheLinesTheAppShipsWith() {
        assertEquals("one", HomeSceneQuote.resolve(null, builtIn, dayOfYear = 0))
        assertEquals("two", HomeSceneQuote.resolve("", builtIn, dayOfYear = 1))
        assertEquals("three", HomeSceneQuote.resolve("   \n\n  ", builtIn, dayOfYear = 2))
    }

    @Test
    fun theReadersOwnLinesAreUsedWhenThereAreAny() {
        assertEquals("mine", HomeSceneQuote.resolve("mine", builtIn, dayOfYear = 0))
    }

    @Test
    fun severalLinesTakeTurnsOneADay() {
        val custom = "first\nsecond\nthird"
        assertEquals("first", HomeSceneQuote.resolve(custom, builtIn, dayOfYear = 0))
        assertEquals("second", HomeSceneQuote.resolve(custom, builtIn, dayOfYear = 1))
        assertEquals("third", HomeSceneQuote.resolve(custom, builtIn, dayOfYear = 2))
        assertEquals("first", HomeSceneQuote.resolve(custom, builtIn, dayOfYear = 3))
    }

    @Test
    fun parseTrimsLinesAndDropsTheEmptyOnes() {
        // What a text field ends up holding after someone types, adds a blank line and gives up.
        assertEquals(
            listOf("alpha", "beta"),
            HomeSceneQuote.parse("  alpha  \n\n   \nbeta\n"),
        )
    }

    @Test
    fun parseOfNothingIsNoLines() {
        assertEquals(emptyList<String>(), HomeSceneQuote.parse(null))
        assertEquals(emptyList<String>(), HomeSceneQuote.parse("\n \n"))
    }

    @Test
    fun anEmptySetOfEverythingIsAnEmptyLineRatherThanACrash() {
        assertEquals("", HomeSceneQuote.resolve(null, emptyList(), dayOfYear = 7))
        assertEquals("", HomeSceneQuote.resolve("  ", emptyList(), dayOfYear = 7))
    }

    @Test
    fun aNegativeDayStillLandsInsideTheList() {
        // Not a day the calendar produces, but the index must not be the thing that fails.
        assertEquals("three", HomeSceneQuote.resolve(null, builtIn, dayOfYear = -1))
    }
}
