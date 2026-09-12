package me.bmax.apatch.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAvailabilityTest {

    @Test
    fun noRuntimeHidesAllRootSpecificFeatures() {
        assertEquals(
            SettingsFeatureAvailability(
                globalNamespace = false,
                sucompat = false,
                selinuxHide = false,
                webViewDebugging = false,
                resetSuPath = false,
                nightTheme = false,
                customColor = false,
            ),
            resolveSettingsFeatureAvailability(
                kPatchReady = false,
                aPatchReady = false,
                dynamicColorSupported = true,
                nightFollowSystem = true,
                useSystemDynamicColor = true,
            useWallpaperColor = false,
            ),
        )
    }

    @Test
    fun kernelPatchOnlyKeepsResetSuPath() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = false,
            dynamicColorSupported = true,
            nightFollowSystem = true,
            useSystemDynamicColor = true,
            useWallpaperColor = false,
        )

        assertEquals(false, result.globalNamespace)
        assertEquals(false, result.sucompat)
        assertEquals(false, result.selinuxHide)
        assertEquals(false, result.webViewDebugging)
        assertEquals(true, result.resetSuPath)
    }

    @Test
    fun androidPatchKeepsWebDebuggingEvenWithoutKernelPatchFlag() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = false,
            aPatchReady = true,
            dynamicColorSupported = true,
            nightFollowSystem = true,
            useSystemDynamicColor = true,
            useWallpaperColor = false,
        )

        assertEquals(true, result.webViewDebugging)
        assertEquals(false, result.resetSuPath)
    }

    @Test
    fun fullRuntimeExposesAllCompatibilityFeatures() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            dynamicColorSupported = true,
            nightFollowSystem = true,
            useSystemDynamicColor = true,
            useWallpaperColor = false,
        )

        assertEquals(true, result.globalNamespace)
        assertEquals(true, result.sucompat)
        assertEquals(true, result.selinuxHide)
        assertEquals(true, result.webViewDebugging)
        assertEquals(true, result.resetSuPath)
    }

    @Test
    fun manualNightModeAndUnsupportedDynamicColorShowThemeChoices() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            dynamicColorSupported = false,
            nightFollowSystem = false,
            useSystemDynamicColor = true,
            useWallpaperColor = false,
        )

        assertEquals(true, result.nightTheme)
        assertEquals(true, result.customColor)
    }

    @Test
    fun systemNightModeAndDynamicColorHideRedundantChoices() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            dynamicColorSupported = true,
            nightFollowSystem = true,
            useSystemDynamicColor = true,
            useWallpaperColor = false,
        )

        assertEquals(false, result.nightTheme)
        assertEquals(false, result.customColor)
    }

    @Test
    fun wallpaperColoursTakeThePresetListAway() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            dynamicColorSupported = false,
            nightFollowSystem = true,
            useSystemDynamicColor = false,
            useWallpaperColor = true,
        )

        assertEquals(false, result.customColor)
    }

    @Test
    fun switchingWallpaperColoursOffBringsThePresetListBack() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            dynamicColorSupported = true,
            nightFollowSystem = true,
            useSystemDynamicColor = false,
            useWallpaperColor = false,
        )

        assertEquals(true, result.customColor)
    }
}
