package me.bmax.apatch.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.core.content.edit
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.home.HomeWallpaperFiles
import me.bmax.apatch.ui.home.HomeWallpaperState
import me.bmax.apatch.ui.shell.SceneBackdropScrimAlpha
import me.bmax.apatch.util.LauncherIconUtils

/** What the app knows about the wallpaper derived colours right now. */
@Immutable
internal data class WallpaperColorThemeState(
    val enabled: Boolean = false,
    /** The colour read out of the wallpaper, or 0 when there is none yet. */
    val seed: Int = 0,
    /** True while the wallpaper is being read. */
    val deriving: Boolean = false,
    /** True when the last attempt read nothing usable out of the wallpaper. */
    val failed: Boolean = false,
    /**
     * Whether the wallpaper reads as light once the scene's scrim is over it.
     *
     * The status bar icons sit on exactly that, so they are chosen from this rather than from the
     * fact that the scene is the scene. A bright photo under a 24% scrim is still bright, and white
     * icons on it cannot be seen. False until a wallpaper has been read.
     */
    val backdropIsLight: Boolean = false,
)

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
 *
 * Everything that shows or applies this lives off [state]; the preferences underneath are only the
 * storage, so the theme and both switches can never disagree about what the current colour is.
 */
internal object WallpaperColorTheme {
    const val EnabledKey = "use_wallpaper_color_theme"
    const val SeedKey = "wallpaper_color_seed"

    /** Whether the wallpaper behind the scene's scrim reads as light. See the state for why. */
    private const val BackdropLightKey = "wallpaper_color_backdrop_light"

    /**
     * Which wallpaper the stored seed was read from, as "<slot>:<revision>". Both facts matter:
     * the same picture can be chosen for both themes, and the dark theme's picture is a different
     * one, so a revision on its own would let a seed follow the wrong photo.
     */
    private const val SeedSourceKey = "wallpaper_color_source"

    /** Written by an earlier version, which only knew about one wallpaper. */
    private const val LegacySeedRevisionKey = "wallpaper_color_seed_revision"
    private const val Tag = "WallpaperColor"

    /**
     * Reading an image can fail for reasons that pass on their own (a decode that runs out of
     * memory while the app is still warming up, a file that is not readable yet). A single failure
     * used to leave the app without a colour until the next restart, so it is worth retrying.
     */
    private const val DeriveAttempts = 3
    private const val DeriveRetryDelayMillis = 200L

    private val _state = MutableStateFlow(readState())

    val state: StateFlow<WallpaperColorThemeState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        APApplication.sharedPreferences.edit { putBoolean(EnabledKey, enabled) }
        _state.update { it.copy(enabled = enabled) }
    }

    /**
     * Keeps the derived seed in step with the wallpaper that is on screen. Safe to call on every
     * wallpaper state change: it only does work when the picture moved on or the last attempt came
     * up empty.
     */
    suspend fun sync(context: Context, wallpaper: HomeWallpaperState) {
        val file = wallpaperFile(context, wallpaper)
        val source = file?.let { wallpaperSource(wallpaper) }
        if (source == null) {
            // No wallpaper to follow any more, so there is nothing to report either.
            clearSeed()
            _state.update { it.copy(failed = false) }
            return
        }
        val prefs = APApplication.sharedPreferences
        // The third condition is what carries this reading to an install that is otherwise up to
        // date: a seed without it was taken before the backdrop was measured, and the wallpaper has
        // not moved since, so without it the reading would never be taken at all.
        val upToDate = prefs.getString(SeedSourceKey, null) == source &&
            prefs.getInt(SeedKey, 0) != 0 &&
            prefs.contains(BackdropLightKey)
        if (upToDate) {
            _state.update { it.copy(failed = false) }
            return
        }
        derive(file, source)
    }

    /**
     * Reads the wallpaper again, ignoring the revision it was last derived from. This is the way
     * back when a derivation failed, so the user does not have to restart the app.
     */
    suspend fun regenerate(context: Context, wallpaper: HomeWallpaperState): Boolean {
        val file = wallpaperFile(context, wallpaper)
        if (file == null) {
            clearSeed()
            _state.update { it.copy(failed = false) }
            return false
        }
        return derive(file, wallpaperSource(wallpaper))
    }

    private fun wallpaperSource(wallpaper: HomeWallpaperState): String =
        "${wallpaper.activeSlot.name}:${wallpaper.revision}"

    private fun wallpaperFile(context: Context, wallpaper: HomeWallpaperState): File? =
        HomeWallpaperFiles
            .resolve(context.filesDir, wallpaper.imagePath)
            ?.takeIf { it.isFile && it.length() > 0L }

    private suspend fun derive(file: File, source: String): Boolean {
        _state.update { it.copy(deriving = true, failed = false) }
        var attempted: WallpaperReading? = null
        for (attempt in 0 until DeriveAttempts) {
            attempted = runCatching { extractSeed(file) }
                .onFailure { Log.w(Tag, "could not read the home wallpaper (attempt ${attempt + 1})", it) }
                .getOrNull()
            if (attempted != null) break
            if (attempt < DeriveAttempts - 1) {
                delay(DeriveRetryDelayMillis * (attempt + 1))
            }
        }

        val reading = attempted
        if (reading == null) {
            // The theme falls back to the next priority; the source is deliberately not recorded,
            // so the next launch (or a tap on retry) has another go.
            Log.w(Tag, "no colour usable as a theme in the home wallpaper")
            clearSeed()
            _state.update { it.copy(deriving = false, failed = true) }
            return false
        }

        APApplication.sharedPreferences.edit {
            putInt(SeedKey, reading.seed)
            putString(SeedSourceKey, source)
            putBoolean(BackdropLightKey, reading.backdropIsLight)
            remove(LegacySeedRevisionKey)
        }
        _state.update {
            it.copy(
                seed = reading.seed,
                backdropIsLight = reading.backdropIsLight,
                deriving = false,
                failed = false,
            )
        }
        // The icon follows this colour while the wallpaper is what the app is painted from, and the
        // picture can be read long after the page that chose it was closed.
        LauncherIconUtils.refreshForColorChange()
        return true
    }

    private fun clearSeed() {
        val prefs = APApplication.sharedPreferences
        if (!prefs.contains(SeedKey) &&
            !prefs.contains(SeedSourceKey) &&
            !prefs.contains(BackdropLightKey) &&
            !prefs.contains(LegacySeedRevisionKey)
        ) {
            return
        }
        prefs.edit {
            remove(SeedKey)
            remove(SeedSourceKey)
            remove(BackdropLightKey)
            remove(LegacySeedRevisionKey)
        }
        _state.update { it.copy(seed = 0, backdropIsLight = false) }
        LauncherIconUtils.refreshForColorChange()
    }

    private fun readState(): WallpaperColorThemeState {
        val prefs = APApplication.sharedPreferences
        return WallpaperColorThemeState(
            enabled = prefs.getBoolean(EnabledKey, false),
            seed = prefs.getInt(SeedKey, 0),
            backdropIsLight = prefs.getBoolean(BackdropLightKey, false),
        )
    }
}

/**
 * The wallpaper colour choice, observed from anywhere that shows it or applies it. Not tied to a
 * lifecycle: this is a single app wide preference, not a stream that needs pausing.
 */
@Composable
internal fun rememberWallpaperColorThemeState(): WallpaperColorThemeState =
    WallpaperColorTheme.state.collectAsState().value

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

/** What one reading of the wallpaper came up with. */
private data class WallpaperReading(val seed: Int, val backdropIsLight: Boolean)

private fun extractSeed(file: File): WallpaperReading? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        // Half the bytes of the default ARGB_8888. The sample is small either way, but the reading
        // happens while the app is starting and other copies of the wallpaper are being decoded.
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val seed = dominantSeedColor(pixels) ?: return null
        WallpaperReading(
            seed = seed,
            backdropIsLight = backdropReadsLight(
                topAverageLuminance(pixels, bitmap.width, bitmap.height),
            ),
        )
    } finally {
        bitmap.recycle()
    }
}

/**
 * How bright the top of the wallpaper reads, 0 to 1.
 *
 * Only the top is measured, because that is the part that ends up behind the status bar. A photo's
 * brightness is rarely even — a bright sky over a dark subject is the common shape — and the average
 * of the whole picture would call that one dark and put white icons on the sky.
 */
internal fun topAverageLuminance(pixels: IntArray, width: Int, height: Int): Float {
    if (width <= 0 || height <= 0 || pixels.isEmpty()) return 0f
    val rows = (height / 3).coerceAtLeast(1)
    val count = (rows * width).coerceAtMost(pixels.size)
    if (count <= 0) return 0f
    var total = 0f
    for (index in 0 until count) {
        total += relativeLuminance(pixels[index])
    }
    return total / count
}

/**
 * Relative luminance of an sRGB colour, 0 to 1 — the same measure the platform's own
 * `Color.luminance` returns, worked out here so the rule can be read without a device.
 */
internal fun relativeLuminance(color: Int): Float {
    val red = linearChannel(color shr 16 and 0xFF)
    val green = linearChannel(color shr 8 and 0xFF)
    val blue = linearChannel(color and 0xFF)
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

private fun linearChannel(value: Int): Float {
    val channel = value / 255f
    return if (channel <= 0.04045f) {
        channel / 12.92f
    } else {
        ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
}

/**
 * Whether the scene's backdrop reads as light, which is what decides the colour of the status bar
 * icons. The wallpaper is measured through the scrim the scene lays over it: what matters is what is
 * behind the icons, not what the file looks like on its own.
 */
internal fun backdropReadsLight(topLuminance: Float): Boolean =
    topLuminance * (1f - SceneBackdropScrimAlpha) > BackdropLightThreshold

/** Above this the icons have to be dark, because white ones cannot be seen on the backdrop. */
private const val BackdropLightThreshold = 0.5f

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
