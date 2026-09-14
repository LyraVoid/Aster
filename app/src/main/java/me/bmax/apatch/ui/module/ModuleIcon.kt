package me.bmax.apatch.ui.module

import android.graphics.Bitmap
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

/** Longest side an icon is decoded to when it is only shown inside the app. */
internal const val MODULE_ICON_PREVIEW_MAX_SIDE = 128

/**
 * Longest side an icon is decoded to before the launcher turns it into a shortcut icon. The
 * launcher masks and rescales what it is given, so this only has to cover the densest launcher
 * icon there is (108dp at 3x is 324px); 512 is what the managers this was ported from hand over.
 */
internal const val MODULE_SHORTCUT_ICON_MAX_SIDE = 512

// APD supplies canonical absolute paths. Also validate here for older daemons that
// forward module.prop verbatim. No URLs, parent traversal or sibling directories.
internal fun isModuleIconPath(path: String): Boolean {
    val prefix = "/data/adb/modules/"
    if (!path.startsWith(prefix) || path.any { it.code < 32 || it == '\\' }) return false
    val parts = path.removePrefix(prefix).split('/')
    return parts.size >= 2 && parts.none { it.isEmpty() || it == "." || it == ".." }
}

internal fun moduleIconSampleSize(
    width: Int,
    height: Int,
    maxSide: Int = MODULE_ICON_PREVIEW_MAX_SIDE,
): Int? {
    if (width !in 1..4096 || height !in 1..4096 || maxSide !in 1..4096) return null
    var sample = 1
    while (maxOf(width, height) / sample > maxSide) sample *= 2
    return sample
}

// Kept although nothing on the module card draws a module-supplied icon any more, so a
// later avatar or shortcut surface can reuse the validated loader instead of a second one.
@Composable
internal fun rememberModuleIcon(
    path: String,
    revision: Long,
    maxSide: Int = MODULE_ICON_PREVIEW_MAX_SIDE,
): ImageBitmap? = key(path, revision, maxSide) {
    val bitmap by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) { loadModuleBitmap(path, maxSide)?.asImageBitmap() }
    }
    bitmap
}

/**
 * Decodes a module-supplied image, re-checking it against the same rules APD applied, and returns
 * it cropped to a square no larger than [maxSide]. This reads through the root shell and therefore
 * blocks: call it off the main thread.
 */
internal fun loadModuleBitmap(path: String, maxSide: Int = MODULE_ICON_PREVIEW_MAX_SIDE): Bitmap? {
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
        val sample = moduleIconSampleSize(bounds.outWidth, bounds.outHeight, maxSide) ?: return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: return null
        squareModuleIcon(decoded, maxSide)
    }.getOrNull()
}

/**
 * Center-crops to a square and shrinks it to [maxSide]. A launcher icon is square and a module's
 * own image need not be, and letterboxing is the launcher's call to make, not ours.
 */
internal fun squareModuleIcon(bitmap: Bitmap, maxSide: Int): Bitmap {
    val side = minOf(bitmap.width, bitmap.height)
    val square = if (bitmap.width == bitmap.height) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            .also { if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle() }
    }
    if (side <= maxSide) return square
    return Bitmap.createScaledBitmap(square, maxSide, maxSide, true)
        .also { if (it !== square && !square.isRecycled) square.recycle() }
}
