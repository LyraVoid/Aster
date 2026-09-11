package me.bmax.apatch.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ApdVersionResultTest {
    @Test
    fun `successful output maps to version`() {
        assertEquals(
            ApdVersionResult.Available(11300),
            mapApdVersionResult(
                exitCode = 0,
                out = listOf("11300"),
                err = emptyList(),
            ),
        )
    }

    @Test
    fun `missing binary maps to missing instead of zero`() {
        assertEquals(
            ApdVersionResult.Missing(127),
            mapApdVersionResult(
                exitCode = 127,
                out = emptyList(),
                err = listOf("/data/adb/apd: not found"),
            ),
        )
    }

    @Test
    fun `command failure maps to error instead of missing`() {
        assertEquals(
            ApdVersionResult.Error(1),
            mapApdVersionResult(
                exitCode = 1,
                out = emptyList(),
                err = listOf("permission denied"),
            ),
        )
    }

    @Test
    fun `successful but malformed output maps to error`() {
        assertEquals(
            ApdVersionResult.Error(0),
            mapApdVersionResult(
                exitCode = 0,
                out = listOf("unknown"),
                err = emptyList(),
            ),
        )
    }
}
