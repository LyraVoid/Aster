package me.bmax.apatch.ui.screen

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PatchPermissionsTest {

    @Test
    fun androidPRequestsReadAndWriteStorage() {
        assertEquals(
            listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ),
            legacyStoragePermissions(sdkInt = 28),
        )
    }

    @Test
    fun androidQRequestsReadAndWriteStorage() {
        assertEquals(
            listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ),
            legacyStoragePermissions(sdkInt = 29),
        )
    }

    @Test
    fun androidRAndAboveNoLongerRequestWriteStorage() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            legacyStoragePermissions(sdkInt = 30),
        )
    }

    @Test
    fun androidSRequestsReadStorageOnly() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            legacyStoragePermissions(sdkInt = 32),
        )
    }

    @Test
    fun androidTiramisuAndAboveRequestNoLegacyStoragePermission() {
        assertEquals(
            emptyList<String>(),
            legacyStoragePermissions(sdkInt = 33),
        )
    }
}
