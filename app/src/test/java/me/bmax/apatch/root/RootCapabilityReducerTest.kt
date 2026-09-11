package me.bmax.apatch.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCapabilityReducerTest {
    @Test
    fun `kernel patch unavailable maps to none and install attention`() {
        val snapshot = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.UNAVAILABLE,
                androidPatch = RootLayerState.BLOCKED,
                rootAccess = RootAccessProbeState.UNAVAILABLE,
            ),
            sessionId = 1L,
            checkedAt = 10L,
        )

        assertEquals(RootCheckPhase.READY, snapshot.phase)
        assertEquals(RootFreshness.FRESH, snapshot.freshness)
        assertEquals(RootMode.NONE, snapshot.mode)
        assertTrue(snapshot.attention.contains(RootAttention.NEED_INSTALL))
    }

    @Test
    fun `root probe failure remains visible instead of becoming unknown`() {
        val snapshot = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNKNOWN,
                rootAccess = RootAccessProbeState.ERROR,
                error = RootCheckError.ROOT_PROBE_FAILED,
            ),
            sessionId = 2L,
            checkedAt = 20L,
        )

        assertEquals(RootMode.KERNEL_PATCH_ONLY, snapshot.mode)
        assertEquals(RootAccessProbeState.ERROR, snapshot.rootAccess)
        assertTrue(snapshot.attention.contains(RootAttention.CHECK_FAILED))
    }

    @Test
    fun `kernel patch only requests apatch installation`() {
        val snapshot = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNAVAILABLE,
                rootAccess = RootAccessProbeState.AVAILABLE,
            ),
            sessionId = 3L,
            checkedAt = 30L,
        )

        assertEquals(RootMode.KERNEL_PATCH_ONLY, snapshot.mode)
        assertTrue(snapshot.attention.contains(RootAttention.NEED_APATCH_INSTALL))
    }

    @Test
    fun `full apatch ready is quiet`() {
        val snapshot = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.AVAILABLE,
                rootAccess = RootAccessProbeState.AVAILABLE,
            ),
            sessionId = 4L,
            checkedAt = 40L,
        )

        assertEquals(RootMode.FULL_APATCH, snapshot.mode)
        assertTrue(snapshot.attention.isEmpty())
    }

    @Test
    fun `need reboot and need update remain distinct attention`() {
        val snapshot = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.NEED_REBOOT,
                androidPatch = RootLayerState.NEED_UPDATE,
                rootAccess = RootAccessProbeState.AVAILABLE,
            ),
            sessionId = 5L,
            checkedAt = 50L,
        )

        assertTrue(snapshot.attention.contains(RootAttention.NEED_REBOOT))
        assertTrue(snapshot.attention.contains(RootAttention.NEED_UPDATE))
    }

    @Test
    fun `failed refresh preserves last good snapshot as stale`() {
        val good = RootCapabilityReducer.applyProbe(
            previous = RootCapabilitySnapshot(),
            result = RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.AVAILABLE,
                rootAccess = RootAccessProbeState.AVAILABLE,
            ),
            sessionId = 6L,
            checkedAt = 60L,
        )
        val failed = RootCapabilityReducer.fail(
            previous = good,
            error = RootCheckError.UNEXPECTED,
            sessionId = 7L,
            checkedAt = 70L,
        )

        assertEquals(RootCheckPhase.FAILED, failed.phase)
        assertEquals(RootFreshness.STALE, failed.freshness)
        assertEquals(RootMode.FULL_APATCH, failed.mode)
        assertEquals(60L, failed.lastGoodAt)
        assertTrue(failed.attention.contains(RootAttention.CHECK_FAILED))
    }
}
