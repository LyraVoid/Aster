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

    /** Saving configuration must keep every argument separate and the multi-line
     *  path list intact; nothing here can unmount or apply anything. */
    @Test fun configureArgumentsStaySeparateAndKeepTheMultilinePathList() {
        val paths = "a/system/media/x.zip\nb/product/fonts/y.ttf ' \" \$HOME ; reboot"
        val args = listOf("configure", "--source", paths, "--umount-auto", "on")
        val command = args.joinToString(" ", transform = RuntimeSafetyClient::quote)
        // NUL separated: a newline inside the path list must not look like a split.
        val process = ProcessBuilder("sh", "-c", "printf '%s\\0' $command").start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(0, process.waitFor())
        assertEquals(args, out.split('\u0000').dropLastWhile { it.isEmpty() })
    }
}
