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
            ),
            resolveSettingsFeatureAvailability(
                kPatchReady = false,
                aPatchReady = false,
                nightFollowSystem = true,
            ),
        )
    }

    @Test
    fun kernelPatchOnlyKeepsResetSuPath() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = false,
            nightFollowSystem = true,
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
            nightFollowSystem = true,
        )

        assertEquals(true, result.webViewDebugging)
        assertEquals(false, result.resetSuPath)
    }

    @Test
    fun fullRuntimeExposesAllCompatibilityFeatures() {
        val result = resolveSettingsFeatureAvailability(
            kPatchReady = true,
            aPatchReady = true,
            nightFollowSystem = true,
        )

        assertEquals(true, result.globalNamespace)
        assertEquals(true, result.sucompat)
        assertEquals(true, result.selinuxHide)
        assertEquals(true, result.webViewDebugging)
        assertEquals(true, result.resetSuPath)
    }

}
