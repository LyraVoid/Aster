package me.bmax.apatch.ui.kernelmodule

import org.junit.Assert.assertEquals
import org.junit.Test

class KPModuleStatusTest {

    @Test
    fun embeddedModuleIsNotReportedAsFileLoaded() {
        assertEquals(
            listOf(KPModuleStatus.EMBEDDED),
            resolveKPModuleStatuses(
                loadSource = "embedded",
                loaded = true,
                installed = false,
                disabled = false,
            ),
        )
    }

    @Test
    fun currentSessionOnlyModuleReportsLoaded() {
        assertEquals(
            listOf(KPModuleStatus.LOADED),
            resolveKPModuleStatuses(
                loadSource = "file",
                loaded = true,
                installed = false,
                disabled = false,
            ),
        )
    }

    @Test
    fun loadedPersistentModuleReportsBothStates() {
        assertEquals(
            listOf(KPModuleStatus.LOADED, KPModuleStatus.INSTALLED),
            resolveKPModuleStatuses(
                loadSource = "file",
                loaded = true,
                installed = true,
                disabled = false,
            ),
        )
    }

    @Test
    fun disabledPersistentModuleReportsDisabledInsteadOfInstalled() {
        assertEquals(
            listOf(KPModuleStatus.LOADED, KPModuleStatus.DISABLED),
            resolveKPModuleStatuses(
                loadSource = "file",
                loaded = true,
                installed = true,
                disabled = true,
            ),
        )
    }

    @Test
    fun persistentModuleThatIsNotCurrentlyLoadedReportsInstalled() {
        assertEquals(
            listOf(KPModuleStatus.INSTALLED),
            resolveKPModuleStatuses(
                loadSource = "",
                loaded = false,
                installed = true,
                disabled = false,
            ),
        )
    }

    @Test
    fun disabledFlagWithoutPersistentInstallDoesNotCreateDisabledStatus() {
        assertEquals(
            emptyList<KPModuleStatus>(),
            resolveKPModuleStatuses(
                loadSource = "",
                loaded = false,
                installed = false,
                disabled = true,
            ),
        )
    }
}
