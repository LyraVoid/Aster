package me.bmax.apatch.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFontTest {
    @Test fun acceptsTheKindsOfFontThePlatformReads() {
        assertTrue("TrueType outlines", isFontHeader(byteArrayOf(0, 1, 0, 0)))
        assertTrue("OpenType with CFF outlines", isFontHeader("OTTO".toByteArray(Charsets.US_ASCII)))
        assertTrue("font collection", isFontHeader("ttcf".toByteArray(Charsets.US_ASCII)))
        assertTrue("Apple TrueType", isFontHeader("true".toByteArray(Charsets.US_ASCII)))
        assertTrue("PostScript in sfnt", isFontHeader("typ1".toByteArray(Charsets.US_ASCII)))
    }

    @Test fun refusesAnythingElseTheReaderCouldHavePicked() {
        val notFonts = listOf(
            byteArrayOf(),
            byteArrayOf(0, 1, 0),
            byteArrayOf(0, 1, 0, 1),
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), // PNG
            "PK\u0003\u0004".toByteArray(Charsets.US_ASCII), // zip
            "wOFF".toByteArray(Charsets.US_ASCII),
            "<svg".toByteArray(Charsets.US_ASCII),
        )
        for (header in notFonts) {
            assertFalse(header.joinToString(" ") { "%02x".format(it) }, isFontHeader(header))
        }
    }

    @Test fun aFontThatIsGoneIsNotAFontThatIsOn() {
        // The stored answer says "on" and there is no file: the app cannot draw with it, so the
        // answer is corrected rather than handed to the theme as it stands.
        assertEquals(
            CustomFontState(),
            resolveCustomFontState(enabled = true, title = "NotoSans.ttf", hasFile = false),
        )
        assertEquals(
            CustomFontState(enabled = true, title = "NotoSans.ttf"),
            resolveCustomFontState(enabled = true, title = "NotoSans.ttf", hasFile = true),
        )
        // Off but picked is a font the reader may turn back on, so the file is remembered.
        assertEquals(
            CustomFontState(enabled = false, title = "NotoSans.ttf"),
            resolveCustomFontState(enabled = false, title = "NotoSans.ttf", hasFile = true),
        )
        assertEquals(
            CustomFontState(enabled = false, title = null),
            resolveCustomFontState(enabled = false, title = null, hasFile = false),
        )
    }

    @Test fun pickedIsWhatTheAppearanceRowAsks() {
        assertTrue(CustomFontState(enabled = false, title = "NotoSans.ttf").picked)
        assertFalse(CustomFontState(enabled = true, title = null).picked)
        assertFalse(CustomFontState().picked)
    }
}
