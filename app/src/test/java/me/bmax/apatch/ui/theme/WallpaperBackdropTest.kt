package me.bmax.apatch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the app decides whether the status bar icons over the scene have to be dark.
 *
 * It used to decide from the fact that the scene was the scene, which is a guess about the
 * wallpaper. A bright photo is the case the guess gets wrong: it stays bright under the scene's
 * scrim, and white icons on it cannot be seen.
 */
class WallpaperBackdropTest {

    @Test
    fun luminanceRunsFromBlackToWhite() {
        assertEquals(0f, relativeLuminance(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, relativeLuminance(0xFFFFFFFF.toInt()), 0.001f)
        // Vivid is not the same as bright: this is the colour the reported wallpaper derived.
        assertTrue(relativeLuminance(0xFF398FC5.toInt()) < 0.4f)
    }

    @Test
    fun onlyTheTopOfTheWallpaperIsMeasured() {
        // A bright sky over a dark subject. The average of the whole picture would call this one
        // dark and put white icons on the sky, which is what this reading exists to avoid.
        val width = 4
        val height = 9
        val pixels = IntArray(width * height) { index ->
            if (index < width * 3) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }

        assertTrue(topAverageLuminance(pixels, width, height) > 0.9f)
    }

    @Test
    fun aDarkTopDoesNotReadAsLight() {
        val dark = topAverageLuminance(IntArray(300) { 0xFF202020.toInt() }, 10, 30)

        assertFalse(backdropReadsLight(dark))
    }

    @Test
    fun aBrightTopReadsAsLightEvenThroughTheScrim() {
        val bright = topAverageLuminance(IntArray(300) { 0xFFFFFFFF.toInt() }, 10, 30)

        assertTrue(backdropReadsLight(bright))
    }

    @Test
    fun theScrimIsTheDifferenceThatMatters() {
        // This grey would pass on its own and does not pass once the scrim is over it. The icons
        // have to be chosen from what ends up behind them, not from the file.
        val raw = relativeLuminance(0xFFC4C4C4.toInt())

        assertTrue("the raw colour is light", raw > 0.5f)
        assertFalse("through the scrim it is not", backdropReadsLight(raw))
    }

    @Test
    fun anEmptyReadingIsNoReading() {
        assertEquals(0f, topAverageLuminance(IntArray(0), 0, 0), 0.001f)
        assertEquals(0f, topAverageLuminance(IntArray(0), 10, 10), 0.001f)
    }
}
