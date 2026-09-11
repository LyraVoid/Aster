package me.bmax.apatch.ui.home

import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.RootCapabilitySnapshot
import me.bmax.apatch.root.RootCheckPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRootMigrationGateTest {
    @Test
    fun `migration waits until root access is ready`() {
        assertFalse(
            canMigrateStockBootBackup(
                RootCapabilitySnapshot(
                    phase = RootCheckPhase.CHECKING,
                    rootAccess = RootAccessProbeState.AVAILABLE,
                )
            )
        )
        assertFalse(
            canMigrateStockBootBackup(
                RootCapabilitySnapshot(
                    phase = RootCheckPhase.READY,
                    rootAccess = RootAccessProbeState.ERROR,
                )
            )
        )
        assertTrue(
            canMigrateStockBootBackup(
                RootCapabilitySnapshot(
                    phase = RootCheckPhase.READY,
                    rootAccess = RootAccessProbeState.AVAILABLE,
                )
            )
        )
    }
}
