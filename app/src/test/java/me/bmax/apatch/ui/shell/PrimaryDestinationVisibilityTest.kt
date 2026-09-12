package me.bmax.apatch.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryDestinationVisibilityTest {
    private fun visible(kernelPatchReady: Boolean, androidPatchReady: Boolean) =
        visiblePrimaryDestinations(
            AsterNavigationCapabilities(
                kernelPatchReady = kernelPatchReady,
                androidPatchReady = androidPatchReady,
            )
        )

    @Test
    fun withoutKernelPatchOnlyHomeAndSettingsRemain() {
        assertEquals(
            listOf(PrimaryDestination.Home, PrimaryDestination.Settings),
            visible(kernelPatchReady = false, androidPatchReady = false),
        )
    }

    @Test
    fun kernelPatchWithoutAndroidPatchHidesTheSystemModulePage() {
        val entries = visible(kernelPatchReady = true, androidPatchReady = false)
        assertEquals(
            listOf(
                PrimaryDestination.Home,
                PrimaryDestination.KModule,
                PrimaryDestination.SuperUser,
                PrimaryDestination.Settings,
            ),
            entries,
        )
        assertFalse(PrimaryDestination.AModule in entries)
    }

    @Test
    fun bothPatchesReadyShowEveryEntry() {
        assertEquals(PrimaryDestination.entries, visible(kernelPatchReady = true, androidPatchReady = true))
    }

    @Test
    fun homeAndSettingsNeverDependOnCapabilities() {
        listOf(false, true).forEach { kernel ->
            listOf(false, true).forEach { android ->
                val entries = visible(kernel, android)
                assertTrue(PrimaryDestination.Home in entries)
                assertTrue(PrimaryDestination.Settings in entries)
            }
        }
    }

    @Test
    fun androidPatchPageRequiresBothLayers() {
        assertFalse(PrimaryDestination.AModule.isVisible(capabilities(kernel = false, android = true)))
        assertFalse(PrimaryDestination.AModule.isVisible(capabilities(kernel = true, android = false)))
        assertTrue(PrimaryDestination.AModule.isVisible(capabilities(kernel = true, android = true)))
    }

    private fun capabilities(kernel: Boolean, android: Boolean) =
        AsterNavigationCapabilities(kernelPatchReady = kernel, androidPatchReady = android)
}
