package me.bmax.apatch.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.OutputStream

/**
 * Hands a file to the reader in the public Downloads folder.
 *
 * From Android 10 on that folder can only be written through MediaStore: the platform stopped
 * letting an app write it as a path, and the legacy flag in the manifest is ignored there. Below
 * it the folder is a path like any other and writing it needs the storage permission, which the
 * screen asks for at the moment of the write.
 */
internal object Downloads {
    private const val Tag = "Downloads"

    /**
     * Writes [displayName] and answers where it ended up, or null when it could not be written.
     *
     * From Android 10 on the answer is the name rather than a path: MediaStore owns the file, and
     * the row it was given is what the reader can actually go and find.
     */
    fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        body: (OutputStream) -> Unit,
    ): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeThroughMediaStore(context, displayName, mimeType, body)
        } else {
            writeToDownloadsPath(displayName, body)
        }
    }.onFailure {
        Log.w(Tag, "could not write $displayName into Downloads", it)
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeThroughMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        body: (OutputStream) -> Unit,
    ): String {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            // Held back until the bytes are all there, so nothing ever reads a half-written file.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending),
        ) { "MediaStore refused a row for $displayName" }

        return try {
            checkNotNull(resolver.openOutputStream(uri)) { "no stream for $uri" }.use(body)
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            displayName
        } catch (t: Throwable) {
            // A row that was never published would otherwise be left behind as a pending file.
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun writeToDownloadsPath(displayName: String, body: (OutputStream) -> Unit): String {
        val directory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, displayName)
        file.outputStream().use(body)
        return file.absolutePath
    }
}
