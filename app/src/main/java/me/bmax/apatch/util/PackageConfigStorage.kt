package me.bmax.apatch.util

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Shares a POSIX record lock with APD; never lock the CSV inode that gets replaced. */
internal object PackageConfigStorage {
    fun <T> withLock(file: File, block: () -> T): T {
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create package configuration directory" }
        RandomAccessFile(File(parent, "package_config.lock"), "rw").use { lockFile ->
            lockFile.channel.use { channel ->
                channel.lock().use { return block() }
            }
        }
    }

    fun writeAtomically(file: File, content: String) {
        val temp = File.createTempFile("package_config-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temp).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temp.renameTo(file)) { "Cannot replace package configuration" }
        } finally {
            temp.delete()
        }
    }
}
