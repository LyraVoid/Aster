package me.bmax.apatch.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot

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

    @Test
    fun everyPreferenceCombinationPreservesAnchorsAndCapabilityGates() {
        listOf(false, true).forEach { kpm ->
            listOf(false, true).forEach { superUser ->
                listOf(false, true).forEach { apm ->
                    val preferences = NavigationEntryPreferences(kpm, superUser, apm)
                    listOf(false, true).forEach { kernel ->
                        listOf(false, true).forEach { android ->
                            val entries = visiblePrimaryDestinations(capabilities(kernel, android), preferences)
                            assertEquals(PrimaryDestination.Home, entries.first())
                            assertEquals(PrimaryDestination.Settings, entries.last())
                            assertEquals(kernel && kpm, PrimaryDestination.KModule in entries)
                            assertEquals(kernel && superUser, PrimaryDestination.SuperUser in entries)
                            assertEquals(kernel && android && apm, PrimaryDestination.AModule in entries)
                            assertEquals(entries.distinct(), entries)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun cachedPagerKeysTrackExpansionAndContractionWithoutRecomposition() {
        val anchors = listOf(PrimaryDestination.Home, PrimaryDestination.Settings)
        val pages = mutableStateOf(anchors)
        // Retain the same callbacks while capabilities or preferences update the list.
        val pageCount = { pages.value.size }
        val key = { index: Int -> primaryPageKey(pages.value, index) }
        val keys = derivedStateOf { (0 until pageCount()).map(key) }
        assertEquals(listOf("Home", "Settings"), keys.value)

        Snapshot.withMutableSnapshot { pages.value = PrimaryDestination.entries }
        assertEquals(PrimaryDestination.entries.map { it.name }, keys.value)

        Snapshot.withMutableSnapshot { pages.value = anchors }
        assertEquals(listOf("Home", "Settings"), keys.value)
        // A retiring lazy-layout range can still contain the previous larger indices.
        val retiringKeys = (0 until 5).map(key)
        assertEquals(5, retiringKeys.distinct().size)
        assertEquals("removed-page:2", retiringKeys[2])
    }

    private fun capabilities(kernel: Boolean, android: Boolean) =
        AsterNavigationCapabilities(kernelPatchReady = kernel, androidPatchReady = android)
}
