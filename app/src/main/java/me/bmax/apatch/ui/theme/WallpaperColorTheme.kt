package me.bmax.apatch.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.home.HomeWallpaperFiles
import me.bmax.apatch.ui.home.HomeWallpaperState

/**
 * App colours derived from the Home wallpaper.
 *
 * The whole theme is seeded from one colour, and Miuix builds a TonalSpot palette that keeps only
 * the *hue* of that seed. So the job here is small and honest: find the hue the wallpaper is
 * actually made of, and hand it over. The result follows the photo without turning the app into a
 * collage of every colour inside it.
 *
 * The derived seed outranks both the system colour and the preset list, because it is the most
 * specific choice the user can make. The other two stay in preferences untouched, so switching
 * this off restores whatever they had.
 */
internal object WallpaperColorTheme {
    const val EnabledKey = "use_wallpaper_color_theme"
    const val SeedKey = "wallpaper_color_seed"
    private const val SeedRevisionKey = "wallpaper_color_seed_revision"

    fun isEnabled(): Boolean = APApplication.sharedPreferences.getBoolean(EnabledKey, false)

    /** Seeded colour, or 0 when no wallpaper has been analysed yet. */
    fun seed(): Int = APApplication.sharedPreferences.getInt(SeedKey, 0)

    fun setEnabled(enabled: Boolean) {
        APApplication.sharedPreferences.edit { putBoolean(EnabledKey, enabled) }
        refreshTheme.value = true
    }

    /**
     * Keeps the derived seed in step with the stored wallpaper. Safe to call on every wallpaper
     * state change: it only does work when the revision moved.
     */
    fun sync(context: Context, state: HomeWallpaperState) {
        val prefs = APApplication.sharedPreferences
        val file = HomeWallpaperFiles
            .resolve(context.filesDir, state.imagePath)
            ?.takeIf { it.isFile }

        if (file == null) {
            if (prefs.contains(SeedKey) || prefs.contains(SeedRevisionKey)) {
                prefs.edit {
                    remove(SeedKey)
                    remove(SeedRevisionKey)
                }
                refreshTheme.postValue(true)
            }
            return
        }
        if (prefs.getLong(SeedRevisionKey, -1L) == state.revision && prefs.contains(SeedKey)) {
            return
        }
        val seed = runCatching { extractSeed(file) }.getOrNull()
        prefs.edit {
            if (seed == null) remove(SeedKey) else putInt(SeedKey, seed)
            putLong(SeedRevisionKey, state.revision)
        }
        refreshTheme.postValue(true)
    }
}

@Composable
internal fun rememberWallpaperColorEnabled(): State<Boolean> =
    rememberPreference(WallpaperColorTheme.EnabledKey, false) { prefs, key, default ->
        prefs.getBoolean(key, default)
    }

@Composable
internal fun rememberWallpaperColorSeed(): State<Int> =
    rememberPreference(WallpaperColorTheme.SeedKey, 0) { prefs, key, default ->
        prefs.getInt(key, default)
    }

/** Preference backed state that also updates when the same key is written from elsewhere. */
@Composable
private fun <T> rememberPreference(
    key: String,
    default: T,
    read: (SharedPreferences, String, T) -> T,
): State<T> {
    val prefs = APApplication.sharedPreferences
    val state = remember(prefs, key) { mutableStateOf(read(prefs, key, default)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, name ->
            if (name == key || name == null) state.value = read(changed, key, default)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        state.value = read(prefs, key, default)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/** Long edge of the decoded sample. Everything else is thrown away before we look at colours. */
private const val SampleEdge = 96

/** Pixels this transparent are not part of the wallpaper anyone sees. */
private const val MinAlpha = 200

/** Below this saturation a pixel is a shade of grey, and its hue means nothing. */
private const val MinSaturation = 0.05f

/** Hue buckets the wallpaper votes into. */
private const val HueBuckets = 24
private const val HueBucketDegrees = 360f / HueBuckets

/**
 * Evidence a bucket needs before it may repaint the app, in the same units as the vote weight
 * (~13 fully saturated mid-tone pixels). Stray colourful pixels in a photo should not decide.
 */
private const val MinSupportWeight = 12.0

/** Miuix only reads the hue, but the stored colour is also shown as a swatch, so keep it usable. */
private const val SeedSaturationFloor = 0.55f
private const val SeedLightness = 0.5f

private fun extractSeed(file: File): Int? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        dominantSeedColor(pixels)
    } finally {
        bitmap.recycle()
    }
}

private fun sampleSize(width: Int, height: Int): Int {
    var size = 1
    while (width / size > SampleEdge * 2 || height / size > SampleEdge * 2) {
        size *= 2
    }
    return size
}

/**
 * The wallpaper's dominant hue, as a ready to use colour.
 *
 * Every colourful pixel votes for a hue bucket, weighted by how saturated it is and how close to
 * mid brightness it sits. Whites, blacks and greys weigh almost nothing, so a portrait on a white
 * background is decided by the subject rather than by the background. Returns null when the image
 * holds no colour worth following, in which case the caller keeps the previous seed and the theme
 * falls back to the next priority.
 */
internal fun dominantSeedColor(pixels: IntArray): Int? {
    val weight = DoubleArray(HueBuckets)
    val hueX = DoubleArray(HueBuckets)
    val hueY = DoubleArray(HueBuckets)
    val saturation = DoubleArray(HueBuckets)

    for (argb in pixels) {
        if (argb ushr 24 < MinAlpha) continue
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        val high = max(red, max(green, blue))
        val low = min(red, min(green, blue))
        val span = high - low
        if (span == 0) continue
        val pixelSaturation = span.toFloat() / high
        if (pixelSaturation < MinSaturation) continue

        val hue = when (high) {
            red -> ((green - blue).toFloat() / span) * 60f
            green -> (((blue - red).toFloat() / span) + 2f) * 60f
            else -> (((red - green).toFloat() / span) + 4f) * 60f
        }.let { if (it < 0f) it + 360f else it }

        // Prefer saturated and not-too-dark, not-too-bright pixels.
        val value = high / 255f
        val midness = (1f - abs(value - 0.55f) * 1.2f).coerceAtLeast(0.05f)
        val vote = pixelSaturation.toDouble() * pixelSaturation * midness

        val bucket = (hue / HueBucketDegrees).toInt().coerceIn(0, HueBuckets - 1)
        val radians = hue.toDouble() * PI / 180.0
        weight[bucket] += vote
        hueX[bucket] += cos(radians) * vote
        hueY[bucket] += sin(radians) * vote
        saturation[bucket] += vote * pixelSaturation
    }

    var best = -1
    var bestWeight = 0.0
    for (bucket in 0 until HueBuckets) {
        if (weight[bucket] > bestWeight) {
            bestWeight = weight[bucket]
            best = bucket
        }
    }
    if (best < 0 || bestWeight < MinSupportWeight) return null

    // Averaging the hues of the winning bucket as vectors keeps it correct even for a bucket that
    // straddles the 0/360 wrap.
    val hue = (atan2(hueY[best], hueX[best]) * 180.0 / PI + 360.0) % 360.0
    val saturationOf = (saturation[best] / bestWeight).toFloat().coerceIn(SeedSaturationFloor, 1f)
    return hslToColor(hue.toFloat(), saturationOf, SeedLightness)
}

private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val sector = hue / 60f
    val second = chroma * (1f - abs(sector % 2f - 1f))
    val (red, green, blue) = when (sector.toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val offset = lightness - chroma / 2f
    val r = ((red + offset) * 255f).toInt().coerceIn(0, 255)
    val g = ((green + offset) * 255f).toInt().coerceIn(0, 255)
    val b = ((blue + offset) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
