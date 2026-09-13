package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModuleMountWarningTest {

    @Test
    fun returnsNullWhenNoModuleNeedsMounting() {
        assertNull(
            resolveModuleMountWarning(
                requiresMount = false,
                metaModulePropPresent = false,
                metaModuleRemoved = false,
                metaModuleDisabled = false,
            ),
        )
    }

    @Test
    fun reportsMissingMetaModule() {
        assertEquals(
            ModuleMountWarning.NOT_INSTALLED,
            resolveModuleMountWarning(
                requiresMount = true,
                metaModulePropPresent = false,
                metaModuleRemoved = false,
                metaModuleDisabled = false,
            ),
        )
    }

    @Test
    fun reportsPendingRemovalBeforeDisabledState() {
        assertEquals(
            ModuleMountWarning.PENDING_REMOVAL,
            resolveModuleMountWarning(
                requiresMount = true,
                metaModulePropPresent = true,
                metaModuleRemoved = true,
                metaModuleDisabled = true,
            ),
        )
    }

    @Test
    fun reportsDisabledMetaModule() {
        assertEquals(
            ModuleMountWarning.DISABLED,
            resolveModuleMountWarning(
                requiresMount = true,
                metaModulePropPresent = true,
                metaModuleRemoved = false,
                metaModuleDisabled = true,
            ),
        )
    }

    @Test
    fun returnsNullWhenMetaModuleIsHealthy() {
        assertNull(
            resolveModuleMountWarning(
                requiresMount = true,
                metaModulePropPresent = true,
                metaModuleRemoved = false,
                metaModuleDisabled = false,
            ),
        )
    }
    @Test
    fun magicMountSupersedesEveryMetamoduleState() {
        listOf(false, true).forEach { present ->
            listOf(false, true).forEach { removed ->
                listOf(false, true).forEach { disabled ->
                    assertNull(resolveModuleMountWarning(
                        requiresMount = true,
                        metaModulePropPresent = present,
                        metaModuleRemoved = removed,
                        metaModuleDisabled = disabled,
                        magicMountEnabled = true,
                    ))
                }
            }
        }
    }

    @Test
    fun turningMagicMountOffRestoresTheMissingBackendWarning() {
        assertEquals(ModuleMountWarning.NOT_INSTALLED, resolveModuleMountWarning(
            requiresMount = true,
            metaModulePropPresent = false,
            metaModuleRemoved = false,
            metaModuleDisabled = false,
            magicMountEnabled = false,
        ))
    }

}
