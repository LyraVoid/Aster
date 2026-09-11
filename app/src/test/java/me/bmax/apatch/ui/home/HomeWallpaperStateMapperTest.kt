package me.bmax.apatch.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWallpaperStateMapperTest {
    @Test
    fun `disabled wallpaper keeps a stored selection without loading it`() {
        val state = HomeWallpaperStateMapper.disabled(
            imagePath = "home_wallpaper/wallpaper.image",
            revision = 42L,
            crop = HomeWallpaperCrop(zoom = 9f, biasX = -8f, biasY = 4f),
        )

        assertFalse(state.enabled)
        assertFalse(state.isReady)
        assertEquals(HomeWallpaperPhase.DISABLED, state.phase)
        assertTrue(state.hasImage)
        assertEquals(HomeWallpaperMaxZoom, state.crop.zoom)
        assertEquals(-1f, state.crop.biasX)
        assertEquals(1f, state.crop.biasY)
    }

    @Test
    fun `enabled wallpaper without a selection is missing`() {
        val state = HomeWallpaperStateMapper.resolveEnabled(
            imagePath = null,
            revision = 0L,
            crop = HomeWallpaperCrop.Default,
            fileExists = false,
            fileInfo = null,
        )

        assertTrue(state.enabled)
        assertEquals(HomeWallpaperPhase.MISSING, state.phase)
        assertFalse(state.hasImage)
    }

    @Test
    fun `enabled wallpaper reports a missing stored file`() {
        val state = HomeWallpaperStateMapper.resolveEnabled(
            imagePath = "home_wallpaper/wallpaper.image",
            revision = 1L,
            crop = HomeWallpaperCrop.Default,
            fileExists = false,
            fileInfo = HomeWallpaperFileInfo(width = 100, height = 100),
        )

        assertEquals(HomeWallpaperPhase.MISSING, state.phase)
        assertTrue(state.hasImage)
    }

    @Test
    fun `enabled wallpaper reports an unreadable image as an error`() {
        val state = HomeWallpaperStateMapper.resolveEnabled(
            imagePath = "home_wallpaper/wallpaper.image",
            revision = 1L,
            crop = HomeWallpaperCrop.Default,
            fileExists = true,
            fileInfo = null,
        )

        assertEquals(HomeWallpaperPhase.ERROR, state.phase)
    }

    @Test
    fun `enabled readable wallpaper is ready`() {
        val state = HomeWallpaperStateMapper.resolveEnabled(
            imagePath = "home_wallpaper/wallpaper.image",
            revision = 7L,
            crop = HomeWallpaperCrop(zoom = 1.5f, biasX = 0.25f, biasY = -0.25f),
            fileExists = true,
            fileInfo = HomeWallpaperFileInfo(width = 1440, height = 3120),
        )

        assertTrue(state.enabled)
        assertTrue(state.isReady)
        assertEquals(HomeWallpaperPhase.READY, state.phase)
        assertEquals(1440, state.imageWidth)
        assertEquals(3120, state.imageHeight)
        assertEquals(7L, state.revision)
    }

    @Test
    fun `crop values replace non finite input with safe defaults`() {
        val crop = HomeWallpaperCrop(
            zoom = Float.NaN,
            biasX = Float.POSITIVE_INFINITY,
            biasY = Float.NEGATIVE_INFINITY,
        ).normalized()

        assertEquals(HomeWallpaperMinZoom, crop.zoom)
        assertEquals(-1f, crop.biasX)
        assertEquals(-1f, crop.biasY)
    }
}
