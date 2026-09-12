package me.bmax.apatch.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWallpaperSlotTest {
    private fun ready(revision: Long) = HomeWallpaperSlotState(
        phase = HomeWallpaperPhase.READY,
        imagePath = "home_wallpaper/wallpaper.image",
        imageWidth = 1440,
        imageHeight = 3120,
        revision = revision,
    )

    private fun state(
        light: HomeWallpaperSlotState = HomeWallpaperSlotState(),
        night: HomeWallpaperSlotState = HomeWallpaperSlotState(),
        nightEnabled: Boolean = false,
        darkTheme: Boolean = false,
    ) = HomeWallpaperState(
        enabled = true,
        light = light,
        night = night,
        nightEnabled = nightEnabled,
        darkTheme = darkTheme,
    )

    @Test
    fun `the light wallpaper serves both themes until the dark one is switched on`() {
        val light = ready(1L)
        val night = ready(2L)

        val inLight = state(light = light, night = night, darkTheme = false)
        val inDark = state(light = light, night = night, darkTheme = true)

        assertEquals(HomeWallpaperSlot.LIGHT, inDark.activeSlot)
        assertEquals(1L, inLight.visible.revision)
        assertEquals(1L, inDark.visible.revision)
    }

    @Test
    fun `the dark theme takes the dark wallpaper once it is switched on`() {
        val lightState = state(
            light = ready(1L),
            night = ready(2L),
            nightEnabled = true,
            darkTheme = true,
        )

        assertEquals(HomeWallpaperSlot.NIGHT, lightState.activeSlot)
        assertEquals(2L, lightState.visible.revision)
        assertEquals(2L, lightState.revision)
    }

    @Test
    fun `the dark theme keeps the light wallpaper when no dark one is chosen`() {
        val dark = state(light = ready(1L), nightEnabled = true, darkTheme = true)

        assertEquals(HomeWallpaperSlot.LIGHT, dark.activeSlot)
        assertEquals(1L, dark.visible.revision)
    }

    @Test
    fun `an unreadable dark wallpaper falls back instead of leaving an empty scene`() {
        val broken = HomeWallpaperSlotState(
            phase = HomeWallpaperPhase.MISSING,
            imagePath = "home_wallpaper/wallpaper.night.image",
            revision = 3L,
        )
        val dark = state(
            light = ready(1L),
            night = broken,
            nightEnabled = true,
            darkTheme = true,
        )

        assertFalse(dark.nightAvailable)
        assertEquals(HomeWallpaperSlot.LIGHT, dark.activeSlot)
        assertTrue(dark.isReady)
    }

    @Test
    fun `each wallpaper keeps its own crop and size`() {
        val light = ready(1L).copy(crop = HomeWallpaperCrop(zoom = 1.5f))
        val night = ready(2L).copy(
            imagePath = "home_wallpaper/wallpaper.night.image",
            imageWidth = 1080,
            imageHeight = 2400,
            crop = HomeWallpaperCrop(zoom = 2.5f, biasX = 0.5f),
        )
        val dark = state(light = light, night = night, nightEnabled = true, darkTheme = true)

        assertEquals(1080, dark.imageWidth)
        assertEquals(2.5f, dark.crop.zoom)
        assertEquals(0.5f, dark.crop.biasX)
        assertEquals(1.5f, dark.slot(HomeWallpaperSlot.LIGHT).crop.zoom)
    }

    @Test
    fun `switching the wallpaper feature off leaves nothing to show`() {
        val off = HomeWallpaperState(
            enabled = false,
            light = HomeWallpaperStateMapper.disabled(
                imagePath = "home_wallpaper/wallpaper.image",
                revision = 1L,
                crop = HomeWallpaperCrop.Default,
            ),
            nightEnabled = true,
        )

        assertFalse(off.isReady)
        assertEquals(HomeWallpaperPhase.DISABLED, off.phase)
    }
}
