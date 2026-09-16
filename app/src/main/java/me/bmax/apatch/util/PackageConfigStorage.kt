package me.bmax.apatch.util

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Shares a POSIX record lock with APD; never lock the CSV inode that gets replaced. */
internal object PackageConfigStorage {
    /**
     * How long to wait for whoever holds the configuration lock to let go. The channel's blocking
     * [java.nio.channels.FileChannel.lock] takes no timeout, so a holder that never releases would
     * leave the caller waiting with nothing to show the user; waiting is bounded instead, and the
     * timeout is reported like any other failure to write.
     */
    private const val LOCK_TIMEOUT_MS = 5_000L
    private const val LOCK_POLL_MS = 50L

    fun <T> withLock(file: File, block: () -> T): T {
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create package configuration directory" }
        RandomAccessFile(File(parent, "package_config.lock"), "rw").use { lockFile ->
            lockFile.channel.use { channel ->
                val deadline = System.currentTimeMillis() + LOCK_TIMEOUT_MS
                while (true) {
                    val lock = channel.tryLock()
                    if (lock != null) {
                        lock.use { return block() }
                    }
                    check(System.currentTimeMillis() < deadline) {
                        "Timed out waiting for the package configuration lock"
                    }
                    Thread.sleep(LOCK_POLL_MS)
                }
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
