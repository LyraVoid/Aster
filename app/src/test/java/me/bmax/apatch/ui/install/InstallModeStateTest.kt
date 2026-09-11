package me.bmax.apatch.ui.install

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallModeStateTest {

    @Test
    fun withoutRootOnlyFileSelectionIsAvailable() {
        val state = resolveInstallModeState(
            rootAvailable = false,
            isAbDevice = true,
            jailbreakBlocked = false,
        )

        assertEquals(listOf(InstallMethodType.SelectFile), state.methods)
        assertEquals(true, state.showRootWarning)
        assertEquals(false, state.showJailbreakWarning)
    }

    @Test
    fun rootedAbDeviceCanInstallDirectlyOrToInactiveSlot() {
        val state = resolveInstallModeState(
            rootAvailable = true,
            isAbDevice = true,
            jailbreakBlocked = false,
        )

        assertEquals(
            listOf(
                InstallMethodType.SelectFile,
                InstallMethodType.DirectInstall,
                InstallMethodType.InactiveSlot,
            ),
            state.methods,
        )
        assertEquals(false, state.showRootWarning)
    }

    @Test
    fun rootedNonAbDeviceHidesInactiveSlotInstall() {
        val state = resolveInstallModeState(
            rootAvailable = true,
            isAbDevice = false,
            jailbreakBlocked = false,
        )

        assertEquals(
            listOf(
                InstallMethodType.SelectFile,
                InstallMethodType.DirectInstall,
            ),
            state.methods,
        )
    }

    @Test
    fun jailbreakModeBlocksEveryInstallMethod() {
        val state = resolveInstallModeState(
            rootAvailable = true,
            isAbDevice = true,
            jailbreakBlocked = true,
        )

        assertEquals(emptyList<InstallMethodType>(), state.methods)
        assertEquals(true, state.showJailbreakWarning)
    }
}
