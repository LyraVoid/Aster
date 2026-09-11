package me.bmax.apatch.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationModeTest {
    @Test
    fun automaticLayoutChangesAtCompactBreakpoint() {
        assertTrue(NavigationMode.Auto.usesBottomNavigation(599f))
        assertFalse(NavigationMode.Auto.usesBottomNavigation(600f))
        assertFalse(NavigationMode.Auto.usesBottomNavigation(840f))
    }

    @Test
    fun explicitChoiceOverridesWindowWidth() {
        listOf(320f, 600f, 1200f).forEach { width ->
            assertTrue(NavigationMode.Bottom.usesBottomNavigation(width))
            assertFalse(NavigationMode.Sidebar.usesBottomNavigation(width))
        }
    }

    @Test
    fun missingOrUnknownPreferenceFallsBackToAutomatic() {
        assertEquals(NavigationMode.Auto, NavigationMode.fromValue(null))
        assertEquals(NavigationMode.Auto, NavigationMode.fromValue("unknown"))
        NavigationMode.entries.forEach { mode ->
            assertEquals(mode, NavigationMode.fromValue(mode.value))
        }
    }
}
