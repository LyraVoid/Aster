package me.bmax.apatch.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

class PackageConfigStorageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun atomicReplacementPreservesReadersAndSupportsEmptyList() {
        val file = temp.newFile("package_config")
        file.writeText("old configuration")
        file.inputStream().use { oldReader ->
            PackageConfigStorage.withLock(file) {
                PackageConfigStorage.writeAtomically(file, "pkg,exclude,allow,uid,to_uid,sctx\n")
            }
            assertEquals("old configuration", oldReader.bufferedReader().readText())
        }
        assertEquals("pkg,exclude,allow,uid,to_uid,sctx\n", file.readText())
        assertFalse(temp.root.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun failedReplacementRetainsDestinationAndCleansTempFile() {
        val destination = temp.newFolder("package_config")
        val existing = File(destination, "existing").apply { writeText("keep") }
        assertThrows(IllegalStateException::class.java) {
            PackageConfigStorage.writeAtomically(destination, "new data")
        }
        assertEquals("keep", existing.readText())
        assertFalse(temp.root.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun lockUsesStableSidecarAndReleasesOnFailure() {
        val file = temp.newFile("package_config")
        val lockFile = File(temp.root, "package_config.lock")
        assertThrows(IllegalStateException::class.java) {
            PackageConfigStorage.withLock(file) {
                RandomAccessFile(lockFile, "rw").use { other ->
                    assertThrows(OverlappingFileLockException::class.java) { other.channel.tryLock() }
                }
                error("abort transaction")
            }
        }
        RandomAccessFile(lockFile, "rw").use { other -> other.channel.tryLock().use { assertNotNull(it) } }
    }
}
