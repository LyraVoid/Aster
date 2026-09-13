package me.bmax.apatch.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color.parseColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Where the one colour the whole scheme is built from comes from.
 *
 * Only the source is a reader's choice; the colour itself is whatever that source holds, so this
 * is also what the screen says out loud above the preview.
 */
internal enum class ThemeColorSource {
    /** The Home wallpaper the app is already showing. */
    Wallpaper,

    /** The platform's own Material You palette. */
    System,

    /** One of the preset colours, which needs no permission and no wallpaper to exist. */
    Preset,
}

@get:StringRes
internal val ThemeColorSource.label: Int
    get() = when (this) {
        ThemeColorSource.Wallpaper -> R.string.theme_color_source_wallpaper
        ThemeColorSource.System -> R.string.theme_color_source_system
        ThemeColorSource.Preset -> R.string.theme_color_source_preset
    }

/**
 * The preference that decides whether the platform palette is used at all. Read by the theme and by
 * the screen that offers the choice, so it lives here rather than in either of them.
 */
internal const val SystemDynamicColorKey = "use_system_color_theme"

/** The preset colour the palette is built from when neither of the other sources is in charge. */
internal const val PresetColorKey = "custom_color"

/** The colour a preset name falls back to, so an unknown name is never a broken theme. */
internal const val DefaultPresetColor = "blue"

/**
 * The sources in the order they are offered: what follows the phone first, the colour of the
 * reader's own last, because it is the only one that needs a further choice underneath it.
 */
internal val themeColorSourceOrder: List<ThemeColorSource> = listOf(
    ThemeColorSource.System,
    ThemeColorSource.Wallpaper,
    ThemeColorSource.Preset,
)

/**
 * The sources the reader can be offered right now.
 *
 * The Home wallpaper only exists on the panoramic Home, which is the one that has a scene to put a
 * picture behind; everywhere else there is nothing to take a colour from, so the source is left
 * out rather than offered as a choice that cannot be made.
 */
internal fun themeColorSourcesOffered(panoramaHome: Boolean): List<ThemeColorSource> =
    themeColorSourceOrder.filter { it != ThemeColorSource.Wallpaper || panoramaHome }

/**
 * Which source the stored preferences add up to.
 *
 * This reads the *choice* rather than the colour that came of it: a wallpaper whose colour has not
 * been read yet is still the source the reader picked, and the page must not fall back to another
 * one while the picture is being read.
 *
 * A wallpaper the app is not showing is not a source either, however its switch is left: a
 * preference for a picture that is nowhere on screen describes nothing.
 */
internal fun themeColorSourceOf(
    panoramaHome: Boolean,
    wallpaperEnabled: Boolean,
    systemDynamicEnabled: Boolean,
    dynamicColorSupported: Boolean,
): ThemeColorSource = when {
    panoramaHome && wallpaperEnabled -> ThemeColorSource.Wallpaper
    systemDynamicEnabled && dynamicColorSupported -> ThemeColorSource.System
    else -> ThemeColorSource.Preset
}

/** What a source asks of the two preferences underneath it. */
@Immutable
internal data class ThemeColorSourceSwitches(
    val wallpaper: Boolean,
    val system: Boolean,
)

/**
 * The preferences a source writes.
 *
 * The wallpaper does not replace the system palette, it outranks it, so that preference is left
 * where it was and going back to the system palette finds the choice that was already made. What
 * the phone can offer is not judged here: a source that cannot be used is not offered at all.
 */
internal fun themeColorSourceSwitches(
    source: ThemeColorSource,
    systemDynamicEnabled: Boolean,
): ThemeColorSourceSwitches = when (source) {
    ThemeColorSource.System -> ThemeColorSourceSwitches(wallpaper = false, system = true)
    ThemeColorSource.Wallpaper -> ThemeColorSourceSwitches(
        wallpaper = true,
        system = systemDynamicEnabled,
    )

    ThemeColorSource.Preset -> ThemeColorSourceSwitches(wallpaper = false, system = false)
}

/**
 * Puts a source in charge.
 *
 * The wallpaper colours are started or stopped through their own store, which is also what keeps
 * the derived colour in step with the picture on screen; the theme then re-reads the preferences it
 * was built from, because the two flags are not observable state on their own.
 */
internal fun selectThemeColorSource(source: ThemeColorSource) {
    val prefs = APApplication.sharedPreferences
    val switches = themeColorSourceSwitches(
        source = source,
        systemDynamicEnabled = prefs.getBoolean(SystemDynamicColorKey, true),
    )
    WallpaperColorTheme.setEnabled(switches.wallpaper)
    prefs.edit { putBoolean(SystemDynamicColorKey, switches.system) }
    refreshTheme.value = true
}

/**
 * Picks the colour a preset palette is built from — and takes the reader's tap on a colour as the
 * wish to use it, so choosing a colour and putting it in charge are one gesture rather than two.
 */
internal fun selectPresetColor(colorName: String) {
    APApplication.sharedPreferences.edit { putString(PresetColorKey, colorName) }
    selectThemeColorSource(ThemeColorSource.Preset)
}

/**
 * The seed in force, and where it came from.
 *
 * A [seed] of zero means there is nothing for the app to build a palette from, and the platform
 * palette is used exactly as it is. That is the case only while the reader has left the palette
 * alone: a style or a spec of their own has to be applied to a real seed, or the choice would be
 * quietly ignored.
 */
@Immutable
internal data class ThemeColorChoice(
    val source: ThemeColorSource,
    val seed: Int,
)

/**
 * Which colour the app is painted from, in the order the sources outrank each other: the wallpaper
 * the reader put on the home screen is the most specific thing they can point at, then the system
 * palette, then the preset colour.
 *
 * [systemSeed] being null on a phone that has a system palette means the platform did not say what
 * it is; the platform palette is then left to decide for itself rather than being replaced by a
 * guess.
 *
 * The wallpaper is only in the running on the panoramic Home, because that is the only home that
 * shows one: a picture the reader has turned off does not go on colouring the app behind their
 * back.
 */
internal fun resolveThemeColorChoice(
    panoramaHome: Boolean,
    wallpaperEnabled: Boolean,
    wallpaperSeed: Int,
    systemDynamicEnabled: Boolean,
    systemSeed: Int?,
    paletteChosen: Boolean,
    presetSeed: Int,
): ThemeColorChoice = when {
    panoramaHome && wallpaperEnabled && wallpaperSeed != 0 -> ThemeColorChoice(
        source = ThemeColorSource.Wallpaper,
        seed = wallpaperSeed,
    )

    systemDynamicEnabled -> ThemeColorChoice(
        source = ThemeColorSource.System,
        seed = if (paletteChosen) systemSeed ?: 0 else 0,
    )

    else -> ThemeColorChoice(
        source = ThemeColorSource.Preset,
        seed = presetSeed,
    )
}

/**
 * The palette is untouched while it is the one the app draws by itself: the style Material You
 * starts from, built to the spec this Android version builds its own palette to.
 */
internal fun ThemeColorSchemeState.isDefaultPalette(): Boolean =
    style == ThemePaletteStyle.TonalSpot && spec == defaultThemeColorSpec(Build.VERSION.SDK_INT)

/**
 * The seed the platform palette is built from.
 *
 * Android 13 and up record the wallpaper palette the theme picker made in a settings entry, and
 * that entry's seed colour is what the platform would use. Below that — and on a phone whose theme
 * picker never wrote one — the accent colour of the platform palette stands in. Both are readable
 * without a permission, and both are only ever a *seed*: the palette the app draws is still built
 * by Miuix from the style and spec the reader picked.
 */
internal object SystemPaletteSeed {
    const val OverlayPackagesKey = "theme_customization_overlay_packages"
    private const val SystemPaletteKey = "android.theme.customization.system_palette"

    fun read(context: Context): Int? = readRecordedSeed(context) ?: readAccentSeed(context)

    @SuppressLint("InlinedApi", "UseKtx")
    private fun readRecordedSeed(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val recorded = runCatching {
            Settings.Secure.getString(context.contentResolver, OverlayPackagesKey)
        }.getOrNull()
        if (recorded.isNullOrBlank()) return null
        return runCatching {
            val seed = JSONObject(recorded).optString(SystemPaletteKey, "")
            if (seed.isBlank()) null else parseColor(if (seed.startsWith("#")) seed else "#$seed")
        }.getOrNull()
    }

    @SuppressLint("InlinedApi")
    private fun readAccentSeed(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrNull()
    }
}

/**
 * The platform seed, read while composing and read again whenever the platform palette changes, so
 * a wallpaper picked in the system settings reaches the app without a restart.
 *
 * [enabled] is false while the reader has left the palette alone, which is also when the app does
 * not need the seed at all: the platform is drawing those colours itself.
 */
@Composable
internal fun rememberSystemPaletteSeed(enabled: Boolean): Int? {
    val context = LocalContext.current
    var paletteRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val paletteUri = Settings.Secure.getUriFor(SystemPaletteSeed.OverlayPackagesKey)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                paletteRevision++
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(paletteUri, false, observer)
        }.isSuccess
        onDispose {
            if (registered) runCatching { resolver.unregisterContentObserver(observer) }
        }
    }

    return remember(context, enabled, paletteRevision) {
        if (enabled) SystemPaletteSeed.read(context) else null
    }
}
