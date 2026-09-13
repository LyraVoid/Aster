package me.bmax.apatch.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimeSafetyClientTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun modulePathIsOneLiteralArgumentEvenWithShellMetacharacters() {
        val marker = File(temp.root, "must-not-exist")
        val input = "module/system/media/a ' \$(touch ${marker.path}) `touch ${marker.path}`\n b; false"
        val process = ProcessBuilder("sh", "-c", "printf '%s' " + RuntimeSafetyClient.quote(input)).start()
        assertEquals(input, process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
        assertFalse(marker.exists())
    }
}
