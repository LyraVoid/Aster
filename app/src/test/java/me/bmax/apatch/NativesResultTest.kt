package me.bmax.apatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativesResultTest {
    @Test
    fun `su path result distinguishes success empty and failure`() {
        val success = Natives.SuPathResult(rc = 12L, path = "/system/bin/su")
        val empty = Natives.SuPathResult(rc = 0L, path = "")
        val failed = Natives.SuPathResult(rc = -1L, path = "")

        assertTrue(success.isSuccess)
        assertEquals("/system/bin/su", success.valueOrNull)
        assertFalse(empty.isSuccess)
        assertNull(empty.valueOrNull)
        assertFalse(failed.isSuccess)
        assertNull(failed.valueOrNull)
    }

    @Test
    fun `su uid result treats zero as success and negative rc as failure`() {
        val emptySuccess = Natives.SuUidsResult(rc = 0L, uids = intArrayOf())
        val failed = Natives.SuUidsResult(rc = -1L, uids = intArrayOf())

        assertTrue(emptySuccess.isSuccess)
        assertFalse(failed.isSuccess)
    }
}
