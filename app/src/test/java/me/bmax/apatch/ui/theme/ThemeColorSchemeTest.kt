package me.bmax.apatch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class ThemeColorSchemeTest {
    private val presetSeed = 0xFF123456.toInt()
    private val systemSeed = 0xFFABCDEF.toInt()
    private val wallpaperSeed = 0xFF654321.toInt()

    private fun choiceOf(
        panoramaHome: Boolean = true,
        wallpaperEnabled: Boolean = false,
        wallpaperSeed: Int = 0,
        systemDynamicEnabled: Boolean = false,
        systemSeed: Int? = null,
        paletteChosen: Boolean = false,
    ) = resolveThemeColorChoice(
        panoramaHome = panoramaHome,
        wallpaperEnabled = wallpaperEnabled,
        wallpaperSeed = wallpaperSeed,
        systemDynamicEnabled = systemDynamicEnabled,
        systemSeed = systemSeed,
        paletteChosen = paletteChosen,
        presetSeed = presetSeed,
    )

    @Test
    fun theDefaultSpecFollowsThePlatform() {
        // Android 16 builds its own palette to the 2025 spec and everything below it to the 2021
        // one; the app starts on the same spec so an install that never opens this setting keeps
        // the palette its own version draws.
        assertEquals(ThemeColorSpec.Spec2021, defaultThemeColorSpec(26))
        assertEquals(ThemeColorSpec.Spec2021, defaultThemeColorSpec(35))
        assertEquals(ThemeColorSpec.Spec2025, defaultThemeColorSpec(36))
        assertEquals(ThemeColorSpec.Spec2025, defaultThemeColorSpec(37))
    }

    @Test
    fun storedNamesAreReadBackAndUnknownOnesIgnored() {
        assertEquals(ThemePaletteStyle.TonalSpot, paletteStyleFromName(null))
        assertEquals(ThemePaletteStyle.TonalSpot, paletteStyleFromName(""))
        assertEquals(ThemePaletteStyle.TonalSpot, paletteStyleFromName("Chartreuse"))
        assertEquals(ThemePaletteStyle.FruitSalad, paletteStyleFromName("FruitSalad"))

        assertEquals(ThemeColorSpec.Spec2025, colorSpecFromName(null, 36))
        assertEquals(ThemeColorSpec.Spec2021, colorSpecFromName(null, 35))
        assertEquals(ThemeColorSpec.Spec2025, colorSpecFromName("Chartreuse", 36))
        // A chosen spec is honoured on every version, both ways round.
        assertEquals(ThemeColorSpec.Spec2025, colorSpecFromName("Spec2025", 30))
        assertEquals(ThemeColorSpec.Spec2021, colorSpecFromName("Spec2021", 36))
    }

    @Test
    fun onlyFourStylesHaveA2025Form() {
        val withA2025Form = setOf(
            ThemePaletteStyle.TonalSpot,
            ThemePaletteStyle.Neutral,
            ThemePaletteStyle.Vibrant,
            ThemePaletteStyle.Expressive,
        )
        ThemePaletteStyle.entries.forEach { style ->
            assertEquals(
                "$style has the wrong 2025 support",
                style in withA2025Form,
                style.supportsSpec2025,
            )
        }
    }

    @Test
    fun aStyleWithoutA2025FormFallsBackTo2021() {
        assertEquals(
            ThemeColorSpec.Spec2021,
            ThemeColorSpec.Spec2025.effectiveFor(ThemePaletteStyle.Fidelity),
        )
        assertEquals(
            ThemeColorSpec.Spec2025,
            ThemeColorSpec.Spec2025.effectiveFor(ThemePaletteStyle.TonalSpot),
        )
        assertEquals(
            ThemeColorSpec.Spec2021,
            ThemeColorSpec.Spec2021.effectiveFor(ThemePaletteStyle.TonalSpot),
        )
    }

    @Test
    fun anUntouchedPaletteStaysOnThePlatformPalette() {
        // Unit tests run without a platform version, which the helper reads as the oldest one.
        val untouched = ThemeColorSchemeState(spec = defaultThemeColorSpec(0))
        assertTrue(untouched.isDefaultPalette())
        assertFalse(untouched.copy(style = ThemePaletteStyle.Rainbow).isDefaultPalette())
        assertFalse(untouched.copy(spec = ThemeColorSpec.Spec2025).isDefaultPalette())
    }

    @Test
    fun wallpaperOutranksEveryOtherSource() {
        val choice = choiceOf(
            wallpaperEnabled = true,
            wallpaperSeed = wallpaperSeed,
            systemDynamicEnabled = true,
            systemSeed = systemSeed,
            paletteChosen = true,
        )
        assertEquals(ThemeColorSource.Wallpaper, choice.source)
        assertEquals(wallpaperSeed, choice.seed)
    }

    @Test
    fun aWallpaperTheAppIsNotShowingIsNotASource() {
        // Turning the panoramic home off leaves the picture behind it unshown, so its colour stops
        // being what the app is painted from — the switch it was turned on with is not forgotten.
        val choice = choiceOf(
            panoramaHome = false,
            wallpaperEnabled = true,
            wallpaperSeed = wallpaperSeed,
            systemDynamicEnabled = true,
            systemSeed = systemSeed,
            paletteChosen = true,
        )
        assertEquals(ThemeColorSource.System, choice.source)
        assertEquals(systemSeed, choice.seed)
    }

    @Test
    fun aWallpaperWithoutAColourLeavesTheSystemPaletteInCharge() {
        val choice = choiceOf(
            wallpaperEnabled = true,
            wallpaperSeed = 0,
            systemDynamicEnabled = true,
            systemSeed = systemSeed,
            paletteChosen = true,
        )
        assertEquals(ThemeColorSource.System, choice.source)
        assertEquals(systemSeed, choice.seed)
    }

    @Test
    fun theUntouchedSystemPaletteIsLeftToThePlatform() {
        // Seed zero is what tells the theme to use the platform's own colours, which is the look
        // the app had before any of this could be chosen.
        val choice = choiceOf(systemDynamicEnabled = true, systemSeed = systemSeed)
        assertEquals(ThemeColorSource.System, choice.source)
        assertEquals(0, choice.seed)
    }

    @Test
    fun aChosenPaletteIsBuiltFromTheSystemSeed() {
        val choice = choiceOf(
            systemDynamicEnabled = true,
            systemSeed = systemSeed,
            paletteChosen = true,
        )
        assertEquals(systemSeed, choice.seed)
    }

    @Test
    fun anUnreadableSystemSeedIsNotGuessed() {
        val choice = choiceOf(systemDynamicEnabled = true, systemSeed = null, paletteChosen = true)
        assertEquals(ThemeColorSource.System, choice.source)
        assertEquals(0, choice.seed)
    }

    @Test
    fun withoutDynamicColourThePresetTakesOver() {
        val choice = choiceOf(wallpaperEnabled = true, wallpaperSeed = 0, paletteChosen = true)
        assertEquals(ThemeColorSource.Preset, choice.source)
        assertEquals(presetSeed, choice.seed)
    }

    @Test
    fun everyStyleAndSpecIsNamedAndExplainedApart() {
        val styleLabels = ThemePaletteStyle.entries.map { it.displayName }
        val styleSummaries = ThemePaletteStyle.entries.map { it.summary }
        assertEquals(ThemePaletteStyle.entries.size, styleLabels.toSet().size)
        assertEquals(ThemePaletteStyle.entries.size, styleSummaries.toSet().size)

        val specLabels = ThemeColorSpec.entries.map { it.label }
        assertEquals(ThemeColorSpec.entries.size, specLabels.toSet().size)
        assertNotEquals(ThemeColorSpec.Spec2021.summary, ThemeColorSpec.Spec2025.summary)

        val sourceLabels = ThemeColorSource.entries.map { it.label }
        assertEquals(ThemeColorSource.entries.size, sourceLabels.toSet().size)
    }
}
