package me.bmax.apatch.ui.intake

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.ui.screen.extractModuleId
import me.bmax.apatch.ui.theme.isFontHeader

/**
 * What the manager is willing to be handed from outside: a file opened or shared by another app.
 *
 * What a file is gets decided by reading it, not by trusting its name: the platform hands fonts,
 * kernel modules and plenty of other binaries over as `application/octet-stream`, and a name is
 * only ever as good as whoever typed it. The kind that cannot be decided by reading — "small, and
 * not a font or a zip" — is left to kptools at install time, which is the only thing that can read
 * a kernel module.
 */
internal enum class ExternalFileKind {
    /** Drawn in: the first bytes are a font the platform's loader reads. */
    Font,

    /** Unpacked: a zip carrying a module.prop. */
    Module,

    /** Small enough to be a kernel module, and neither of the above: kptools decides. */
    KernelModule,

    /** Nothing this manager can take. */
    Unknown,
}

internal data class ExternalFile(
    val uri: Uri,
    val name: String,
    val kind: ExternalFileKind,
)

/**
 * Kernel modules are kilobytes to a few megabytes. Anything past this is not one, and is too big to
 * be copied into the app's cache just to find out.
 */
internal const val MAX_KERNEL_MODULE_BYTES = 8L * 1024 * 1024

/**
 * What this file is, from what it says about itself.
 *
 * A zip that carries no module.prop is refused rather than passed on: a zip is never a kernel
 * module, so there is nothing else it could be here.
 */
internal fun classifyExternalFile(
    header: ByteArray,
    zipHasModule: Boolean,
    sizeBytes: Long,
): ExternalFileKind = when {
    isFontHeader(header) -> ExternalFileKind.Font
    isZipHeader(header) && zipHasModule -> ExternalFileKind.Module
    isZipHeader(header) -> ExternalFileKind.Unknown
    sizeBytes in 1..MAX_KERNEL_MODULE_BYTES -> ExternalFileKind.KernelModule
    else -> ExternalFileKind.Unknown
}

internal fun isZipHeader(header: ByteArray): Boolean {
    if (header.size < 4) return false
    val tag = ((header[0].toInt() and 0xFF) shl 24) or ((header[1].toInt() and 0xFF) shl 16) or
        ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
    return tag == ZIP_HEADER
}

private const val ZIP_HEADER = 0x504B0304

/** Reads just enough of a file to know what it is. Blocking I/O: call it off the main thread. */
internal object ExternalFileReader {
    private const val TAG = "ExternalFile"
    private const val HEADER_BYTES = 12

    suspend fun read(context: Context, uri: Uri): ExternalFile? = withContext(Dispatchers.IO) {
        runCatching {
            val header = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(HEADER_BYTES)
                var read = 0
                while (read < bytes.size) {
                    val count = input.read(bytes, read, bytes.size - read)
                    if (count <= 0) break
                    read += count
                }
                bytes.copyOf(read)
            } ?: error("no stream for $uri")
            val name = fileNameOf(context, uri)
            ExternalFile(
                uri = uri,
                name = name,
                kind = classifyExternalFile(
                    header = header,
                    // A zip that cannot be read is not a module: reading it must not cost the
                    // manager the file, only the answer.
                    zipHasModule = isZipHeader(header) &&
                        runCatching { extractModuleId(context, uri) }.getOrNull() != null,
                    sizeBytes = sizeOf(context, uri),
                ),
            )
        }.onFailure {
            Log.w(TAG, "reading $uri failed", it)
        }.getOrNull()
    }

    private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                cursor.getLong(column)
            } else {
                null
            }
        }
    }.getOrNull() ?: runCatching {
        // Providers that do not answer with a size still answer with the file itself.
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
    }.getOrNull() ?: -1L

    private fun fileNameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: uri.toString()
}

