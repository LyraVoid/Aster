package me.bmax.apatch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperColorSeedTest {
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val grey = 0xFF808080.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val transparentRed = 0x00FF0000

    private fun pixels(count: Int, color: Int) = IntArray(count) { color }

    private fun hueOf(color: Int): Float {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val high = maxOf(red, green, blue)
        val low = minOf(red, green, blue)
        val span = high - low
        val hue = when (high) {
            red -> ((green - blue).toFloat() / span) * 60f
            green -> (((blue - red).toFloat() / span) + 2f) * 60f
            else -> (((red - green).toFloat() / span) + 4f) * 60f
        }
        return if (hue < 0f) hue + 360f else hue
    }

    @Test
    fun colourlessWallpaperYieldsNoSeed() {
        assertNull(dominantSeedColor(pixels(400, grey)))
        assertNull(dominantSeedColor(pixels(400, white)))
        assertNull(dominantSeedColor(pixels(400, transparentRed)))
    }

    @Test
    fun dominantHueWins() {
        val seed = dominantSeedColor(pixels(300, red) + pixels(100, blue))
        assertEquals(0f, hueOf(seed!!), 6f)
    }

    @Test
    fun blueWallpaperKeepsItsHue() {
        val seed = dominantSeedColor(pixels(400, blue))
        assertEquals(240f, hueOf(seed!!), 6f)
    }

    @Test
    fun aFewColourfulPixelsDoNotRepaintTheApp() {
        val wallpaper = pixels(1_000, grey) + pixels(3, red)
        assertNull(dominantSeedColor(wallpaper))
    }

    @Test
    fun subjectDecidesOverAWhiteBackground() {
        val wallpaper = pixels(2_000, white) + pixels(200, blue)
        val seed = dominantSeedColor(wallpaper)!!
        assertEquals(240f, hueOf(seed), 6f)
    }

    @Test
    fun huesOnBothSidesOfTheWrapDoNotAverageToTheOppositeColour() {
        // ~356 degrees and ~4 degrees are both red; a naive average would land on cyan.
        val justBelowWrap = 0xFFFF0010.toInt()
        val justAboveWrap = 0xFFFF0A00.toInt()
        val seed = dominantSeedColor(pixels(200, justBelowWrap) + pixels(200, justAboveWrap))!!
        val hue = hueOf(seed)
        assertTrue("expected a red hue, got $hue", hue < 20f || hue > 340f)
        assertNotEquals(180f, hue, 20f)
    }

    @Test
    fun seedIsFullyOpaqueAndVisible() {
        val seed = dominantSeedColor(pixels(400, green))!!
        assertEquals(0xFF, (seed ushr 24) and 0xFF)
        assertEquals(120f, hueOf(seed), 6f)
    }
}
