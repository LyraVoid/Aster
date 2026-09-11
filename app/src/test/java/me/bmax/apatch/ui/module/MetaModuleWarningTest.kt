package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaModuleWarningTest {

    @Test
    fun returnsNullWhenNoModuleNeedsMounting() {
        assertNull(
            resolveMetaModuleWarning(
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
            MetaModuleWarning.NOT_INSTALLED,
            resolveMetaModuleWarning(
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
            MetaModuleWarning.PENDING_REMOVAL,
            resolveMetaModuleWarning(
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
            MetaModuleWarning.DISABLED,
            resolveMetaModuleWarning(
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
            resolveMetaModuleWarning(
                requiresMount = true,
                metaModulePropPresent = true,
                metaModuleRemoved = false,
                metaModuleDisabled = false,
            ),
        )
    }
}
