package me.bmax.apatch.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * How the app builds its colours out of one seed: which palette style spreads the seed over the
 * whole scheme, and which Material specification the tones are picked from.
 *
 * Both are the reader's choice and both survive a restart, so they live in the same place as the
 * seed itself and are read straight from there by the theme.
 */
@Immutable
internal data class ThemeColorSchemeState(
    val style: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    val spec: ThemeColorSpec = defaultThemeColorSpec(Build.VERSION.SDK_INT),
)

/**
 * The palette choice, kept in preferences and handed out as a state flow.
 *
 * The theme and the screen that changes it both read [state], so they can never disagree about
 * which palette is in force — the same shape the wallpaper colours are kept in.
 */
internal object ThemeColorScheme {
    const val StyleKey = "theme_color_style"
    const val SpecKey = "theme_color_spec"

    private val _state = MutableStateFlow(readState())

    val state: StateFlow<ThemeColorSchemeState> = _state.asStateFlow()

    fun setStyle(style: ThemePaletteStyle) {
        if (style == _state.value.style) return
        APApplication.sharedPreferences.edit { putString(StyleKey, style.name) }
        _state.update { it.copy(style = style) }
    }

    fun setSpec(spec: ThemeColorSpec) {
        if (spec == _state.value.spec) return
        APApplication.sharedPreferences.edit { putString(SpecKey, spec.name) }
        _state.update { it.copy(spec = spec) }
    }

    /**
     * A style or spec this version does not know about is a value written by some other version,
     * so it is ignored and the default takes over rather than leaving the app without a palette.
     */
    private fun readState(): ThemeColorSchemeState {
        val prefs = APApplication.sharedPreferences
        return ThemeColorSchemeState(
            style = paletteStyleFromName(prefs.getString(StyleKey, null)),
            spec = colorSpecFromName(prefs.getString(SpecKey, null), Build.VERSION.SDK_INT),
        )
    }
}

/** The palette choice, observed from anywhere that shows it or applies it. */
@Composable
internal fun rememberThemeColorSchemeState(): ThemeColorSchemeState =
    ThemeColorScheme.state.collectAsState().value

/**
 * The spec the app is on until the reader picks one.
 *
 * It follows the platform, exactly as Miuix does: Android 16 and up build their own colours to the
 * 2025 spec, and everything below it to the 2021 one. A phone that has never opened this setting
 * therefore keeps the palette its own version draws, and the row below it never names a spec the
 * app is not really on.
 *
 * The 2025 tones are only implemented for four of the nine styles; a style without them falls back
 * to 2021 at the point the colours are built, and the screen says so.
 */
internal fun defaultThemeColorSpec(sdkInt: Int): ThemeColorSpec =
    if (sdkInt >= 36) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021

internal fun paletteStyleFromName(name: String?): ThemePaletteStyle =
    ThemePaletteStyle.entries.firstOrNull { it.name == name } ?: ThemePaletteStyle.TonalSpot

internal fun colorSpecFromName(name: String?, sdkInt: Int): ThemeColorSpec =
    ThemeColorSpec.entries.firstOrNull { it.name == name } ?: defaultThemeColorSpec(sdkInt)

/**
 * Miuix has a 2025 implementation for four of the nine styles and quietly builds the other five to
 * the 2021 spec instead. Nothing outside Miuix can ask it which is which, so the list is mirrored
 * here: it is what lets the screen say out loud that a choice has no effect, and what the preview
 * is drawn with. If Miuix ever moves, this is the one place to follow it.
 */
internal val ThemePaletteStyle.supportsSpec2025: Boolean
    get() = when (this) {
        ThemePaletteStyle.TonalSpot,
        ThemePaletteStyle.Neutral,
        ThemePaletteStyle.Vibrant,
        ThemePaletteStyle.Expressive,
        -> true

        else -> false
    }

/** The spec that will really be used, which is the 2021 one whenever the style has no 2025 form. */
internal fun ThemeColorSpec.effectiveFor(style: ThemePaletteStyle): ThemeColorSpec =
    if (this == ThemeColorSpec.Spec2025 && !style.supportsSpec2025) {
        ThemeColorSpec.Spec2021
    } else {
        this
    }

/**
 * The style's own name, and the only label it gets.
 *
 * A palette style is named after what it does to the colour — TonalSpot, FruitSalad, Monochrome —
 * and a translation of those names says less than the names do, so Miuix's names are shown as they
 * are in every language. The line under the name is the translated half, and it is what explains
 * the choice.
 */
internal val ThemePaletteStyle.displayName: String
    get() = name

@get:StringRes
internal val ThemePaletteStyle.summary: Int
    get() = when (this) {
        ThemePaletteStyle.TonalSpot -> R.string.theme_style_tonal_spot_summary
        ThemePaletteStyle.Neutral -> R.string.theme_style_neutral_summary
        ThemePaletteStyle.Vibrant -> R.string.theme_style_vibrant_summary
        ThemePaletteStyle.Expressive -> R.string.theme_style_expressive_summary
        ThemePaletteStyle.Rainbow -> R.string.theme_style_rainbow_summary
        ThemePaletteStyle.FruitSalad -> R.string.theme_style_fruit_salad_summary
        ThemePaletteStyle.Monochrome -> R.string.theme_style_monochrome_summary
        ThemePaletteStyle.Fidelity -> R.string.theme_style_fidelity_summary
        ThemePaletteStyle.Content -> R.string.theme_style_content_summary
    }

@get:StringRes
internal val ThemeColorSpec.label: Int
    get() = when (this) {
        ThemeColorSpec.Spec2021 -> R.string.theme_spec_2021
        ThemeColorSpec.Spec2025 -> R.string.theme_spec_2025
    }

@get:StringRes
internal val ThemeColorSpec.summary: Int
    get() = when (this) {
        ThemeColorSpec.Spec2021 -> R.string.theme_spec_2021_summary
        ThemeColorSpec.Spec2025 -> R.string.theme_spec_2025_summary
    }
