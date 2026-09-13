package me.bmax.apatch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorSourceTest {

    @Test
    fun theWallpaperIsTheSourceWhenItIsOn() {
        assertEquals(
            ThemeColorSource.Wallpaper,
            themeColorSourceOf(
                panoramaHome = true,
                wallpaperEnabled = true,
                systemDynamicEnabled = true,
                dynamicColorSupported = true,
            ),
        )
    }

    @Test
    fun theSystemPaletteIsTheSourceWhileItIsTheOneAskedFor() {
        assertEquals(
            ThemeColorSource.System,
            themeColorSourceOf(
                panoramaHome = true,
                wallpaperEnabled = false,
                systemDynamicEnabled = true,
                dynamicColorSupported = true,
            ),
        )
    }

    @Test
    fun thePresetColourIsWhatIsLeft() {
        assertEquals(
            ThemeColorSource.Preset,
            themeColorSourceOf(
                panoramaHome = true,
                wallpaperEnabled = false,
                systemDynamicEnabled = false,
                dynamicColorSupported = true,
            ),
        )
    }

    @Test
    fun aPhoneWithoutAPlatformPaletteCannotBeOnTheSystemSource() {
        // The stored flag may be true from a phone that had one, so the capability is checked
        // rather than trusted: the list would otherwise show a source that paints nothing.
        assertEquals(
            ThemeColorSource.Preset,
            themeColorSourceOf(
                panoramaHome = true,
                wallpaperEnabled = false,
                systemDynamicEnabled = true,
                dynamicColorSupported = false,
            ),
        )
    }

    @Test
    fun aWallpaperWhoseColourIsNotReadYetIsStillTheChosenSource() {
        // The source list reads the choice, not the colour that came of it: the page must not
        // jump to another source while the picture is being read.
        assertEquals(
            ThemeColorSource.Wallpaper,
            themeColorSourceOf(
                panoramaHome = true,
                wallpaperEnabled = true,
                systemDynamicEnabled = false,
                dynamicColorSupported = false,
            ),
        )
    }

    @Test
    fun aHomeWithoutThePanoramicSceneHasNoWallpaperToTakeAColourFrom() {
        // The wallpaper is the picture behind the scene, so with the scene off the preference
        // describes a picture that is nowhere on screen and the next source takes over.
        assertEquals(
            ThemeColorSource.System,
            themeColorSourceOf(
                panoramaHome = false,
                wallpaperEnabled = true,
                systemDynamicEnabled = true,
                dynamicColorSupported = true,
            ),
        )
        assertEquals(
            ThemeColorSource.Preset,
            themeColorSourceOf(
                panoramaHome = false,
                wallpaperEnabled = true,
                systemDynamicEnabled = false,
                dynamicColorSupported = true,
            ),
        )
    }

    @Test
    fun theWallpaperIsNotOfferedWithoutThePanoramicHome() {
        assertEquals(
            listOf(ThemeColorSource.System, ThemeColorSource.Preset),
            themeColorSourcesOffered(panoramaHome = false),
        )
        assertEquals(themeColorSourceOrder, themeColorSourcesOffered(panoramaHome = true))
    }

    @Test
    fun choosingTheSystemPaletteTurnsTheWallpaperOff() {
        assertEquals(
            ThemeColorSourceSwitches(wallpaper = false, system = true),
            themeColorSourceSwitches(
                source = ThemeColorSource.System,
                systemDynamicEnabled = false,
            ),
        )
    }

    @Test
    fun theWallpaperKeepsTheSystemChoiceThatWasMadeUnderneathIt() {
        // The wallpaper outranks the system palette rather than replacing it, so going back to
        // the system palette finds the choice that was already there.
        assertEquals(
            ThemeColorSourceSwitches(wallpaper = true, system = true),
            themeColorSourceSwitches(
                source = ThemeColorSource.Wallpaper,
                systemDynamicEnabled = true,
            ),
        )
    }

    @Test
    fun thePresetColourTakesBothSwitchesAway() {
        assertEquals(
            ThemeColorSourceSwitches(wallpaper = false, system = false),
            themeColorSourceSwitches(
                source = ThemeColorSource.Preset,
                systemDynamicEnabled = true,
            ),
        )
    }

    @Test
    fun theOrderOfferedPutsTheChoiceWithNoSubChoiceLast() {
        assertEquals(ThemeColorSource.entries.size, themeColorSourceOrder.size)
        assertEquals(ThemeColorSource.entries.toSet(), themeColorSourceOrder.toSet())
        assertEquals(ThemeColorSource.Preset, themeColorSourceOrder.last())
    }
}
