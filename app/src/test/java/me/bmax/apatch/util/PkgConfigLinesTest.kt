package me.bmax.apatch.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strict read behind the Superuser switches refuses a file it cannot read in full, and a refusal
 * is reported to the reader rather than swallowed. These cover the half of that rule which decides
 * what "in full" means: differences that came from the file's travels rather than from its content
 * must not count against it.
 */
class PkgConfigLinesTest {

    @Test
    fun byteOrderMarkAndCrlfEndingsComeOff() {
        val lines = PkgConfig.normaliseConfigLines(
            listOf(
                "\uFEFFpkg,exclude,allow,uid,to_uid,sctx\r",
                "com.example.app,0,1,10001,0,u:r:magisk:s0\r",
            ),
        )

        assertEquals(
            listOf(
                "pkg,exclude,allow,uid,to_uid,sctx",
                "com.example.app,0,1,10001,0,u:r:magisk:s0",
            ),
            lines,
        )
    }

    @Test
    fun blankLinesAreDroppedSoTheHeaderIsStillTheFirstLine() {
        val lines = PkgConfig.normaliseConfigLines(
            listOf(
                "",
                "   ",
                "pkg,exclude,allow,uid,to_uid,sctx",
                "",
                "com.example.app,0,1,10001,0,u:r:magisk:s0",
            ),
        )

        assertEquals("pkg,exclude,allow,uid,to_uid,sctx", lines.first())
        assertEquals(2, lines.size)
    }

    @Test
    fun aRecordThatMerelyEndsInCrKeepsItsLastField() {
        // The trailing \r would otherwise land inside scontext and change what the record means.
        val lines = PkgConfig.normaliseConfigLines(
            listOf("com.example.app,0,1,10001,0,u:r:magisk:s0\r"),
        )

        assertEquals("com.example.app,0,1,10001,0,u:r:magisk:s0", lines.single())
        assertEquals("u:r:magisk:s0", lines.single().split(',').last())
    }
}
