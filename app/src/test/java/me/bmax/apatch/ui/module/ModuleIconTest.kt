package me.bmax.apatch.ui.module

import org.junit.Assert.*
import org.junit.Test

class ModuleIconTest {
    @Test fun acceptsOnlyPathsWithinOneModule() {
        assertTrue(isModuleIconPath("/data/adb/modules/example/icons/action.png"))
        for (path in listOf("icons/a.png", "https://example.com/a.png", "/data/adb/modules-other/a.png", "/data/adb/modules/a", "/data/adb/modules/a/../b/icon.png", "/data/adb/modules/a//icon.png", "/data/adb/modules/a/./icon.png", "/data/adb/modules/a/\nicon.png")) {
            assertFalse(path, isModuleIconPath(path))
        }
    }
    @Test fun boundsDecodedMemoryAndRejectsInvalidDimensions() {
        assertEquals(1, moduleIconSampleSize(20, 20))
        assertEquals(32, moduleIconSampleSize(4096, 4096))
        assertEquals(16, moduleIconSampleSize(1, 2048))
        assertNull(moduleIconSampleSize(0, 20))
        assertNull(moduleIconSampleSize(-1, 20))
        assertNull(moduleIconSampleSize(20, 4097))
        assertNull(moduleIconSampleSize(Int.MAX_VALUE, Int.MAX_VALUE))
    }
    @Test fun shortcutIconsAreDecodedToTheirOwnLongestSide() {
        assertEquals(1, moduleIconSampleSize(512, 512, MODULE_SHORTCUT_ICON_MAX_SIDE))
        assertEquals(8, moduleIconSampleSize(4096, 1024, MODULE_SHORTCUT_ICON_MAX_SIDE))
        assertEquals(8, moduleIconSampleSize(4096, 4096, MODULE_SHORTCUT_ICON_MAX_SIDE))
        assertEquals(4, moduleIconSampleSize(2048, 2048, MODULE_SHORTCUT_ICON_MAX_SIDE))
        // A preview and a shortcut icon of the same image must not share a sampling factor.
        assertEquals(32, moduleIconSampleSize(4096, 4096))
        assertNull(moduleIconSampleSize(20, 20, 0))
        assertNull(moduleIconSampleSize(20, 20, 4097))
    }
}
