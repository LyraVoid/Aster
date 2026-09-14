package me.bmax.apatch.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import java.io.File

/**
 * The font the reader may put the app in.
 *
 * The picked file is copied into the app's own storage instead of being referred to by its uri:
 * that uri is a permission which can be taken away, and the file can be moved or deleted after it
 * was picked, so the manager would sooner or later be holding a font it cannot read. Copying also
 * leaves the feature exactly one file to look at, which is what makes "is there a font?" a
 * question with one answer.
 */
internal object CustomFont {
    private const val TAG = "CustomFont"
    private const val ENABLED_KEY = "custom_font_enabled"
    private const val TITLE_KEY = "custom_font_title"

    private const val DIRECTORY = "fonts"

    /**
     * The one file a custom font lives in. The extension is nominal: what a file is gets decided by
     * its header and by the platform's loader, not by its name.
     */
    private const val SLOT = "app-font"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var state by mutableStateOf(CustomFontState())
        private set

    /**
     * The family the app is drawn in, or null while it is drawn in the platform's own. Loading it
     * is disk work, so it arrives through [load] and [importFont] rather than from composition.
     */
    var family by mutableStateOf<FontFamily?>(null)
        private set

    private var started = false

    /** Reads what was chosen last time and loads the font behind it. Called once, at startup. */
    fun load(context: Context) {
        if (started) return
        started = true
        val prefs = APApplication.sharedPreferences
        val stored = CustomFontState(
            enabled = prefs.getBoolean(ENABLED_KEY, false),
            title = prefs.getString(TITLE_KEY, null),
        )
        // A font that is on but no longer on disk is one this app cannot draw with, so the stored
        // answer is corrected here instead of being handed to the theme as it stands.
        val resolved = resolveCustomFontState(stored.enabled, stored.title, slot(context).isFile)
        if (resolved != stored) {
            Log.i(TAG, "custom font was on without a file; turning it off")
            persist(resolved)
        }
        state = resolved
        if (resolved.enabled) loadFamily(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        // On without a file is a state this app cannot honour; the row that offers the switch is
        // what keeps that from happening, and this is the second lock on the same door.
        if (enabled && !state.picked) return
        val updated = state.copy(enabled = enabled)
        persist(updated)
        if (enabled) loadFamily(context) else family = null
    }

    /**
     * Copies the picked file into the app's slot and puts the app in it, answering whether that
     * worked. Nothing is committed until the file has passed both the header check and the
     * platform's own loader, so a mis-picked zip cannot become the font the app draws itself in.
     */
    suspend fun importFont(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val target = slot(context)
        val staged = File(target.parentFile, "$SLOT.part")
        val imported = runCatching {
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: error("no stream for $uri")
            if (!isFontHeader(staged.readHeader())) error("not a font file")
            // Throws when the platform cannot read it, which is the only check that counts.
            Typeface.createFromFile(staged)
            if (!staged.renameTo(target)) error("cannot move the font into place")
        }.onFailure {
            Log.w(TAG, "importing $uri failed", it)
        }.isSuccess
        if (!imported) staged.delete()
        if (imported) {
            persist(CustomFontState(enabled = true, title = fileNameOf(context, uri)))
            loadFamily(context)
        }
        imported
    }

    /** Deletes the font and puts the app back in the platform's own, which is the switch off. */
    fun clear(context: Context) {
        slot(context).delete()
        persist(CustomFontState())
        family = null
    }

    private fun loadFamily(context: Context) {
        val file = slot(context)
        scope.launch {
            val loaded = loadFontFamily(file)
            // A font that went away while the app ran must not leave the app drawing in it.
            if (loaded == null && state.enabled) {
                persist(state.copy(enabled = false))
            }
            family = loaded
        }
    }

    private fun persist(newState: CustomFontState) {
        state = newState
        APApplication.sharedPreferences.edit {
            putBoolean(ENABLED_KEY, newState.enabled)
            putString(TITLE_KEY, newState.title)
        }
    }

    private fun slot(context: Context): File = File(File(context.filesDir, DIRECTORY), SLOT)
}

/**
 * What the reader chose. The two are kept together so they can never be read half-updated, and
 * [picked] is the question the appearance row asks: has a font of the reader's own been put on
 * disk, whether or not it is the one in use.
 */
@Immutable
internal data class CustomFontState(
    val enabled: Boolean = false,
    val title: String? = null,
) {
    val picked: Boolean get() = title != null
}

/**
 * What the stored choice actually means on this launch. A font that is on but no longer on disk is
 * one the app cannot draw with, so the answer is corrected rather than handed on as it stands.
 */
internal fun resolveCustomFontState(enabled: Boolean, title: String?, hasFile: Boolean): CustomFontState =
    if (enabled && !hasFile) CustomFontState() else CustomFontState(enabled = enabled, title = title)

/**
 * Whether these first bytes are the start of a font. Only the flavours the platform's loader reads
 * are taken: TrueType outlines, OpenType with CFF outlines, PostScript wrapped in sfnt, and font
 * collections.
 */
internal fun isFontHeader(header: ByteArray): Boolean {
    if (header.size < 4) return false
    if (header[0] == 0.toByte() && header[1] == 1.toByte() &&
        header[2] == 0.toByte() && header[3] == 0.toByte()
    ) {
        return true
    }
    val tag = String(header, 0, 4, Charsets.US_ASCII)
    return tag == "OTTO" || tag == "ttcf" || tag == "true" || tag == "typ1"
}

private const val FONT_HEADER_BYTES = 4

// Read by hand: InputStream.readNBytes only exists from API 33, and this app runs from 26.
private fun File.readHeader(): ByteArray {
    val header = ByteArray(FONT_HEADER_BYTES)
    return inputStream().use { input ->
        var read = 0
        while (read < header.size) {
            val count = input.read(header, read, header.size - read)
            if (count <= 0) break
            read += count
        }
        header.copyOf(read)
    }
}

private fun loadFontFamily(file: File): FontFamily? = runCatching {
    if (!file.isFile) return@runCatching null
    FontFamily(Typeface.createFromFile(file))
}.onFailure {
    Log.w(CustomFontTag, "loading ${file.name} failed", it)
}.getOrNull()

private const val CustomFontTag = "CustomFont"

/** What to call the picked font in the UI. Never blank: the row always has something to show. */
private fun fileNameOf(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: uri.toString()
}
