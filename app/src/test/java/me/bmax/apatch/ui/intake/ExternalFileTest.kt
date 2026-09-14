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
            assertEquals(
                ExternalFileKind.Font,
                classifyExternalFile(header, zipHasModule = false, sizeBytes = 20 * 1024 * 1024),
            )
        }
    }

    @Test fun aZipIsAModuleOnlyWhenItCarriesOne() {
        assertEquals(
            ExternalFileKind.Module,
            classifyExternalFile(ZIP, zipHasModule = true, sizeBytes = 2 * 1024 * 1024),
        )
        // A zip that carries no module.prop is refused rather than passed on: a zip is never a
        // kernel module, so there is nothing else it could be here.
        assertEquals(
            ExternalFileKind.Unknown,
            classifyExternalFile(ZIP, zipHasModule = false, sizeBytes = 1024),
        )
    }

    @Test fun aSmallUnknownFileIsLeftToKptools() {
        assertEquals(
            ExternalFileKind.KernelModule,
            classifyExternalFile(PNG, zipHasModule = false, sizeBytes = 64 * 1024),
        )
        assertEquals(
            ExternalFileKind.KernelModule,
            classifyExternalFile(PNG, zipHasModule = false, sizeBytes = MAX_KERNEL_MODULE_BYTES),
        )
    }

    @Test fun aFileTooBigToBeAKernelModuleIsRefused() {
        assertEquals(
            ExternalFileKind.Unknown,
            classifyExternalFile(PNG, zipHasModule = false, sizeBytes = MAX_KERNEL_MODULE_BYTES + 1),
        )
        // A provider that cannot say how big the file is must not be read as "small".
        assertEquals(
            ExternalFileKind.Unknown,
            classifyExternalFile(PNG, zipHasModule = false, sizeBytes = -1),
        )
        assertEquals(
            ExternalFileKind.Unknown,
            classifyExternalFile(EMPTY, zipHasModule = false, sizeBytes = 0),
        )
    }
}
