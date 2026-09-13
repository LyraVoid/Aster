package me.bmax.apatch.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledApdStateTest {
    @Test
    fun `identical binaries keep the patch installed`() {
        assertEquals(
            InstalledApdState.INSTALLED,
            resolveInstalledApdState(
                bundledSha256 = "6407ae54",
                installedSha256 = "6407ae54",
                installedVersion = 11349,
            ),
        )
    }

    @Test
    fun `a newer manager version on its own asks for no update`() {
        // The version code moved, the patch binary did not: there is nothing to install.
        assertEquals(
            InstalledApdState.INSTALLED,
            resolveInstalledApdState(
                bundledSha256 = "6407ae54",
                installedSha256 = "6407ae54",
                installedVersion = 11300,
            ),
        )
    }

    @Test
    fun `a different binary is what asks for an update`() {
        assertEquals(
            InstalledApdState.NEED_UPDATE,
            resolveInstalledApdState(
                bundledSha256 = "6407ae54",
                installedSha256 = "0f1e2d3c",
                installedVersion = 11349,
            ),
        )
    }

    @Test
    fun `an unreadable hash falls back to the version the patch answers with`() {
        assertEquals(
            InstalledApdState.INSTALLED,
            resolveInstalledApdState(
                bundledSha256 = "6407ae54",
                installedSha256 = "",
                installedVersion = 11349,
            ),
        )
    }

    @Test
    fun `an unreadable hash of a silent patch means it is not installed`() {
        assertEquals(
            InstalledApdState.NOT_INSTALLED,
            resolveInstalledApdState(
                bundledSha256 = "6407ae54",
                installedSha256 = "",
                installedVersion = 0,
            ),
        )
    }

    @Test
    fun `a manager without a bundled patch never claims the patch is current`() {
        assertEquals(
            InstalledApdState.NEED_UPDATE,
            resolveInstalledApdState(
                bundledSha256 = "",
                installedSha256 = "0f1e2d3c",
                installedVersion = 11349,
            ),
        )
    }
}
