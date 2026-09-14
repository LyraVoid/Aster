package me.bmax.apatch.ui.module

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.util.getRootShell

internal const val MAX_MODULE_ICON_BYTES = 1024 * 1024

// APD supplies canonical absolute paths. Also validate here for older daemons that
// forward module.prop verbatim. No URLs, parent traversal or sibling directories.
internal fun isModuleIconPath(path: String): Boolean {
    val prefix = "/data/adb/modules/"
    if (!path.startsWith(prefix) || path.any { it.code < 32 || it == '\\' }) return false
    val parts = path.removePrefix(prefix).split('/')
    return parts.size >= 2 && parts.none { it.isEmpty() || it == "." || it == ".." }
}

internal fun moduleIconSampleSize(width: Int, height: Int): Int? {
    if (width !in 1..4096 || height !in 1..4096) return null
    var sample = 1
    while (maxOf(width, height) / sample > 128) sample *= 2
    return sample
}

// Kept although nothing on the module card draws a module-supplied icon any more, so a
// later avatar or shortcut surface can reuse the validated loader instead of a second one.
@Composable
internal fun rememberModuleIcon(path: String, revision: Long): ImageBitmap? = key(path, revision) {
    val bitmap by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) { loadModuleIcon(path) }
    }
    bitmap
}

private fun loadModuleIcon(path: String): ImageBitmap? {
    if (!isModuleIconPath(path)) return null
    return runCatching {
        val rootShell = getRootShell()
        val canonical = ArrayList<String>()
        val quotedPath = "'" + path.replace("'", "'\"'\"'") + "'"
        val resolved = rootShell.newJob().add("readlink -f -- $quotedPath").to(canonical, null).exec()
        if (!resolved.isSuccess || canonical.singleOrNull() != path) return null
        val file = SuFile(path).apply { shell = rootShell }
        // Reject symlinks anywhere in the supplied canonical path, including the
        // module directory. Recheck at load time in case it changed since listing.
        if (!file.isFile || file.length() !in 1..MAX_MODULE_ICON_BYTES.toLong()) {
            return null
        }
        val bytes = SuFileInputStream.open(file).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_MODULE_ICON_BYTES + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_MODULE_ICON_BYTES) return null
            }
            output.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType !in setOf("image/png", "image/jpeg", "image/webp")) return null
        val sample = moduleIconSampleSize(bounds.outWidth, bounds.outHeight) ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        })?.asImageBitmap()
    }.getOrNull()
}
