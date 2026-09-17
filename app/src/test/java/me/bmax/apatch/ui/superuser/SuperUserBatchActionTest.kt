package me.bmax.apatch.ui.superuser

import me.bmax.apatch.Natives
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel.BatchAction
import me.bmax.apatch.util.PkgConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperUserBatchActionTest {
    private fun config(allow: Int = 0, exclude: Int = 0) = PkgConfig.Config(
        pkg = "com.example.app",
        allow = allow,
        exclude = exclude,
        profile = Natives.Profile(uid = 10001, scontext = "u:r:untrusted_app:s0"),
    )

    @Test
    fun excludedAppsReturnToNormalAndNoLongerNeedAPersistedOverride() {
        val original = config(exclude = 1)
        val normal = BatchAction.NORMAL.configFor(original, 10001)

        assertEquals(0, normal.allow)
        assertEquals(0, normal.exclude)
        assertTrue(normal.isDefault())
        assertEquals(1, original.exclude)
    }

    @Test
    fun normalClearsBothFlagsForEveryPreviousMode() {
        listOf(config(), config(allow = 1), config(exclude = 1), config(allow = 1, exclude = 1))
            .forEach { previous ->
                assertTrue(BatchAction.NORMAL.configFor(previous, 10001).isDefault())
            }
    }

    @Test
    fun grantingRootRemovesExclusionAndExcludingRevokesRoot() {
        val granted = BatchAction.GRANT_ROOT.configFor(config(exclude = 1), 10001)
        assertEquals(1, granted.allow)
        assertEquals(0, granted.exclude)

        val excluded = BatchAction.EXCLUDE.configFor(granted, 10001)
        assertEquals(0, excluded.allow)
        assertEquals(1, excluded.exclude)
        assertFalse(excluded.isDefault())
    }

    @Test
    fun consecutiveModeChangesKeepThePackageAndTargetUid() {
        var current = config()
        listOf(BatchAction.EXCLUDE, BatchAction.NORMAL, BatchAction.GRANT_ROOT, BatchAction.NORMAL)
            .forEach { action ->
                current = action.configFor(current, 1010001)
                assertEquals("com.example.app", current.pkg)
                assertEquals(1010001, current.profile.uid)
            }
        assertTrue(current.isDefault())
    }
}
