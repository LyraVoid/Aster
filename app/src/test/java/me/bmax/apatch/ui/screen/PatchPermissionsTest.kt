package me.bmax.apatch.ui.screen

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The write into Downloads is the only thing in this app that needs a storage permission, and only
 * below Android 10, where that folder is written as a path. From Android 10 on the write goes
 * through MediaStore, and nothing the pickers do needs a permission at all: they are Storage Access
 * Framework and photo-picker ones, which hand back a grant for the file that was picked.
 */
class PatchPermissionsTest {

    @Test
    fun androidPieNeedsWriteStorageForTheDownloadsWrite() {
        assertEquals(
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            legacyStoragePermissions(sdkInt = 28),
        )
    }

    @Test
    fun androidTenWritesThroughMediaStoreAndNeedsNothing() {
        assertEquals(
            emptyList<String>(),
            legacyStoragePermissions(sdkInt = 29),
        )
    }

    @Test
    fun androidThirteenAndAboveNeedNothing() {
        assertEquals(
            emptyList<String>(),
            legacyStoragePermissions(sdkInt = 33),
        )
    }
}
