package me.bmax.apatch.ui.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val TTF = byteArrayOf(0, 1, 0, 0)
private val OTTO = "OTTO".toByteArray(Charsets.US_ASCII)
private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
private val EMPTY = ByteArray(0)

class ExternalFileTest {
    @Test fun readsZipHeaders() {
        assertTrue(isZipHeader(ZIP))
        assertFalse(isZipHeader(TTF))
        assertFalse(isZipHeader(EMPTY))
        assertFalse(isZipHeader(byteArrayOf(0x50, 0x4B, 0x03)))
    }

    @Test fun aFontIsAFontWhateverItIsCalled() {
        for (header in listOf(TTF, OTTO)) {
            assertEquals(ExternalFileKind.Font, classifyExternalFile(header, 20 * 1024 * 1024))
        }
    }

    @Test fun everyZipGoesToTheApdModuleInstaller() {
        assertEquals(ExternalFileKind.Module, classifyExternalFile(ZIP, 2 * 1024 * 1024))
        // Do not inspect entries with Android ZipInputStream: ZIP-XZ (method 95) must reach APD.
        assertEquals(ExternalFileKind.Module, classifyExternalFile(ZIP, 1024))
    }

    @Test fun aSmallUnknownFileIsLeftToKptools() {
        assertEquals(ExternalFileKind.KernelModule, classifyExternalFile(PNG, 64 * 1024))
        assertEquals(ExternalFileKind.KernelModule, classifyExternalFile(PNG, MAX_KERNEL_MODULE_BYTES))
    }

    @Test fun aFileTooBigToBeAKernelModuleIsRefused() {
        assertEquals(ExternalFileKind.Unknown, classifyExternalFile(PNG, MAX_KERNEL_MODULE_BYTES + 1))
        assertEquals(ExternalFileKind.Unknown, classifyExternalFile(PNG, -1))
        assertEquals(ExternalFileKind.Unknown, classifyExternalFile(EMPTY, 0))
    }
}
