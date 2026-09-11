package me.bmax.apatch.root

import me.bmax.apatch.APApplication
import org.junit.Assert.assertEquals
import org.junit.Test

class RootCapabilityProjectionTest {
    @Test
    fun `native unavailable is a confirmed negative result`() {
        val result = RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = false,
            rootProbeSucceeded = false,
            kernelPatchState = APApplication.State.UNKNOWN_STATE,
            androidPatchState = APApplication.State.UNKNOWN_STATE,
        )

        assertEquals(RootLayerState.UNAVAILABLE, result.kernelPatch)
        assertEquals(RootLayerState.BLOCKED, result.androidPatch)
        assertEquals(RootAccessProbeState.UNAVAILABLE, result.rootAccess)
    }

    @Test
    fun `root probe failure does not invent apatch state`() {
        val result = RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = false,
            kernelPatchState = APApplication.State.KERNELPATCH_INSTALLED,
            androidPatchState = APApplication.State.UNKNOWN_STATE,
        )

        assertEquals(RootLayerState.AVAILABLE, result.kernelPatch)
        assertEquals(RootLayerState.UNKNOWN, result.androidPatch)
        assertEquals(RootAccessProbeState.ERROR, result.rootAccess)
        assertEquals(RootCheckError.ROOT_PROBE_FAILED, result.error)
    }

    @Test
    fun `full apatch core state projects both layers`() {
        val result = RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = true,
            kernelPatchState = APApplication.State.KERNELPATCH_INSTALLED,
            androidPatchState = APApplication.State.ANDROIDPATCH_INSTALLED,
        )

        assertEquals(RootLayerState.AVAILABLE, result.kernelPatch)
        assertEquals(RootLayerState.AVAILABLE, result.androidPatch)
        assertEquals(RootAccessProbeState.AVAILABLE, result.rootAccess)
    }

    @Test
    fun `kernel patch only core state does not pretend apatch is installed`() {
        val result = RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = true,
            kernelPatchState = APApplication.State.KERNELPATCH_INSTALLED,
            androidPatchState = APApplication.State.ANDROIDPATCH_NOT_INSTALLED,
        )

        assertEquals(RootLayerState.AVAILABLE, result.kernelPatch)
        assertEquals(RootLayerState.UNAVAILABLE, result.androidPatch)
    }

    @Test
    fun `busy core states remain busy`() {
        val result = RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = true,
            kernelPatchState = APApplication.State.KERNELPATCH_UNINSTALLING,
            androidPatchState = APApplication.State.ANDROIDPATCH_UNINSTALLING,
        )

        assertEquals(RootLayerState.BUSY, result.kernelPatch)
        assertEquals(RootLayerState.BUSY, result.androidPatch)
    }
}
