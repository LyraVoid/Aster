package me.bmax.apatch.ui.module

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleListScrollPolicyTest {
    @Test
    fun initialLoadDoesNotRequestScroll() {
        assertFalse(shouldScrollToTopAfterModuleLoad(-1, 12))
    }

    @Test
    fun unchangedCountDoesNotRequestScroll() {
        assertFalse(shouldScrollToTopAfterModuleLoad(12, 12))
    }

    @Test
    fun addedModuleRequestsScroll() {
        assertTrue(shouldScrollToTopAfterModuleLoad(12, 13))
    }

    @Test
    fun removedModuleDoesNotRequestScroll() {
        assertFalse(shouldScrollToTopAfterModuleLoad(13, 12))
    }
}
