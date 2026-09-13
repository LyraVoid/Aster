package me.bmax.apatch.ui.screen

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.home.LocalHomeWallpaperViewModel
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.theme.DefaultPresetColor
import me.bmax.apatch.ui.theme.LocalThemeModeState
import me.bmax.apatch.ui.theme.PresetColorKey
import me.bmax.apatch.ui.theme.SystemDynamicColorKey
import me.bmax.apatch.ui.theme.ThemeColorScheme
import me.bmax.apatch.ui.theme.ThemeColorSource
import me.bmax.apatch.ui.theme.displayName
import me.bmax.apatch.ui.theme.effectiveFor
import me.bmax.apatch.ui.theme.isDefaultPalette
import me.bmax.apatch.ui.theme.label
import me.bmax.apatch.ui.theme.presetThemeSeed
import me.bmax.apatch.ui.theme.rememberSystemPaletteSeed
import me.bmax.apatch.ui.theme.rememberThemeColorSchemeState
import me.bmax.apatch.ui.theme.rememberWallpaperColorThemeState
import me.bmax.apatch.ui.theme.resolveThemeColorChoice
import me.bmax.apatch.ui.theme.selectPresetColor
import me.bmax.apatch.ui.theme.selectThemeColorSource
import me.bmax.apatch.ui.theme.summary
import me.bmax.apatch.ui.theme.supportsSpec2025
import me.bmax.apatch.ui.theme.themeColorSourceOf
import me.bmax.apatch.ui.theme.themeColorSourcesOffered
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * Everything about the palette the app paints itself with: which colour it starts from, how that
 * colour is spread over the scheme, and which Material specification the tones are taken from.
 *
 * All of it lives here rather than in the appearance section, because these are one decision seen
 * from four sides, and the sides have to be looked at together: the source is picked here, the
 * colour that source holds is picked here, and both are judged against a live preview above them.
 *
 * The preview is not a picture of a phone: it is a card built out of the app's own components
 * inside the candidate palette, so what it shows is what the app will look like, down to the
 * buttons.
 */
@Destination<RootGraph>
@Composable
fun ThemeColorScreen(navigator: DestinationsNavigator) {
    val prefs = APApplication.sharedPreferences
    val scheme = rememberThemeColorSchemeState()
    val isDark = LocalThemeModeState.current.isDark

    // The same facts the theme resolves its colour from, read here so the source list and the
    // preview describe the palette that is actually in force.
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val wallpaperViewModel = LocalHomeWallpaperViewModel.current
    val wallpaperState = if (wallpaperViewModel == null) {
        null
    } else {
        wallpaperViewModel.uiState.collectAsStateWithLifecycle().value
    }
    val hasWallpaper = wallpaperState?.hasImage == true
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // The Home wallpaper only exists on the panoramic Home, so on any other home the source is not
    // offered at all and the palette is built from one of the two sources that are.
    val panoramaHome = rememberGlobalLayout().value == GlobalLayout.Panorama

    // The wallpaper half of the choice is observable state of its own; the other two are plain
    // preferences, so they are read once and read back after every tap.
    var systemDynamicStored by rememberSaveable {
        mutableStateOf(prefs.getBoolean(SystemDynamicColorKey, true))
    }
    var presetColor by rememberSaveable {
        mutableStateOf(prefs.getString(PresetColorKey, DefaultPresetColor) ?: DefaultPresetColor)
    }
    val source = themeColorSourceOf(
        panoramaHome = panoramaHome,
        wallpaperEnabled = wallpaperColorTheme.enabled,
        systemDynamicEnabled = systemDynamicStored,
        dynamicColorSupported = dynamicColorSupported,
    )
    val systemDynamic = dynamicColorSupported && systemDynamicStored
    val presetSeed = presetThemeSeed(presetColor)
    val systemSeed = rememberSystemPaletteSeed(enabled = systemDynamic)

    val colorChoice = resolveThemeColorChoice(
        panoramaHome = panoramaHome,
        wallpaperEnabled = wallpaperColorTheme.enabled,
        wallpaperSeed = wallpaperColorTheme.seed,
        systemDynamicEnabled = systemDynamic,
        systemSeed = systemSeed,
        paletteChosen = !scheme.isDefaultPalette(),
        presetSeed = presetSeed,
    )
    // A style the reader picked has to be shown on a real colour, so the preview falls back to the
    // platform's own seed even while the app itself is leaving the palette to the platform.
    val previewSeed = colorChoice.seed.takeIf { it != 0 } ?: systemSeed ?: presetSeed

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.theme_color_title,
                scrollBehavior = scrollBehavior,
                navigator = navigator,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {
            item(key = "preview") {
                SmallTitle(text = stringResource(R.string.theme_color_preview))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                ) {
                    ThemeColorPreviewCard(
                        seed = previewSeed,
                        style = scheme.style,
                        spec = scheme.spec.effectiveFor(scheme.style),
                        title = scheme.style.displayName,
                        summary = stringResource(scheme.style.summary),
                        dark = isDark,
                    )
                }
            }

            item(key = "source") {
                SettingsSectionCard(title = stringResource(R.string.theme_color_source_title)) {
                    themeColorSourcesOffered(panoramaHome).forEach { option ->
                        RadioButtonPreference(
                            title = stringResource(option.label),
                            summary = stringResource(
                                sourceSummary(
                                    source = option,
                                    dynamicColorSupported = dynamicColorSupported,
                                    hasWallpaper = hasWallpaper,
                                    wallpaperFailed = wallpaperColorTheme.failed,
                                )
                            ),
                            selected = source == option,
                            enabled = sourceAvailable(option, dynamicColorSupported, hasWallpaper),
                            radioButtonLocation = RadioButtonLocation.End,
                            startAction = { SettingsIcon(sourceIcon(option)) },
                            onClick = {
                                selectThemeColorSource(option)
                                systemDynamicStored = prefs.getBoolean(SystemDynamicColorKey, true)
                            },
                        )
                    }
                }
            }

            item(key = "palette") {
                SettingsSectionCard(title = stringResource(R.string.theme_color_title)) {
                    OverlayDropdownPreference(
                        entry = themeColorStyleEntry(
                            selectedStyle = scheme.style,
                            seed = previewSeed,
                            spec = scheme.spec,
                            dark = isDark,
                        ) { style -> ThemeColorScheme.setStyle(style) },
                        title = stringResource(R.string.theme_color_style),
                        summary = stringResource(scheme.style.summary),
                        startAction = { SettingsIcon(MiuixIcons.GridView) },
                    )

                    val specs = ThemeColorSpec.entries
                    OverlayDropdownPreference(
                        title = stringResource(R.string.theme_color_spec),
                        summary = stringResource(colorSpecSummary(scheme.spec, scheme.style)),
                        items = specs.map { stringResource(it.label) },
                        selectedIndex = specs.indexOf(scheme.spec).coerceAtLeast(0),
                        startAction = { SettingsIcon(MiuixIcons.Theme) },
                        onSelectedIndexChange = { index -> ThemeColorScheme.setSpec(specs[index]) },
                    )

                    // The colour list is a menu like the two above it rather than a wall of
                    // swatches: the row says which colour is in use, and the list is where the
                    // other eighteen are. Each row carries the colour itself, because that is the
                    // only thing that tells them apart.
                    OverlayDropdownPreference(
                        entry = themeColorPresetEntry(presetColor) { colorName ->
                            selectPresetColor(colorName)
                            presetColor = colorName
                            systemDynamicStored = prefs.getBoolean(SystemDynamicColorKey, true)
                        },
                        title = stringResource(R.string.theme_color_preset_title),
                        summary = stringResource(R.string.theme_color_preset_summary),
                        startAction = { SettingsIcon(MiuixIcons.Tune) },
                        maxHeight = ThemeColorPresetMenuHeight,
                    )
                }
            }
        }
    }
}

/**
 * Whether a listed source can be picked right now.
 *
 * A source that is listed but cannot be used keeps its row and says why, rather than disappearing,
 * so the reader can see that the choice is there and what it is waiting for. The wallpaper is the
 * one source that is not listed at all outside the panoramic home, because there is no picture on
 * screen to take a colour from.
 */
private fun sourceAvailable(
    source: ThemeColorSource,
    dynamicColorSupported: Boolean,
    hasWallpaper: Boolean,
): Boolean = when (source) {
    ThemeColorSource.System -> dynamicColorSupported
    ThemeColorSource.Wallpaper -> hasWallpaper
    ThemeColorSource.Preset -> true
}

/** What each source does, or why it cannot be picked at all. */
@StringRes
private fun sourceSummary(
    source: ThemeColorSource,
    dynamicColorSupported: Boolean,
    hasWallpaper: Boolean,
    wallpaperFailed: Boolean,
): Int = when (source) {
    ThemeColorSource.System -> if (dynamicColorSupported) {
        R.string.theme_color_source_system_summary
    } else {
        R.string.theme_color_source_system_unavailable
    }

    ThemeColorSource.Wallpaper -> when {
        !hasWallpaper -> R.string.settings_wallpaper_color_theme_no_wallpaper
        wallpaperFailed -> R.string.settings_wallpaper_color_theme_failed
        else -> R.string.theme_color_source_wallpaper_summary
    }

    ThemeColorSource.Preset -> R.string.theme_color_source_preset_summary
}

private fun sourceIcon(source: ThemeColorSource): ImageVector = when (source) {
    ThemeColorSource.System -> MiuixIcons.Photos
    ThemeColorSource.Wallpaper -> MiuixIcons.Background
    ThemeColorSource.Preset -> MiuixIcons.Tune
}

/**
 * A style with no 2025 form is not given one here either: the row says the choice has no effect
 * rather than pretending the app is on a spec it cannot build.
 */
@StringRes
private fun colorSpecSummary(spec: ThemeColorSpec, style: ThemePaletteStyle): Int =
    if (spec == ThemeColorSpec.Spec2025 && !style.supportsSpec2025) {
        R.string.theme_spec_2025_unsupported
    } else {
        spec.summary
    }

/**
 * The candidate palette, drawn with the app's own components. Nothing here is tappable in earnest:
 * the buttons answer with their press colours so both ends of the scheme can be judged.
 */
@Composable
private fun ThemeColorPreviewCard(
    seed: Int,
    style: ThemePaletteStyle,
    spec: ThemeColorSpec,
    title: String,
    summary: String,
    dark: Boolean,
) {
    val controller = remember(seed, style, spec, dark) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = Color(seed),
            colorSpec = spec,
            paletteStyle = style,
        )
    }

    MiuixTheme(controller = controller) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeColorSwatch(MiuixTheme.colorScheme.primary)
                    ThemeColorSwatch(MiuixTheme.colorScheme.secondaryContainer)
                    ThemeColorSwatch(MiuixTheme.colorScheme.tertiaryContainer)
                    ThemeColorSwatch(MiuixTheme.colorScheme.errorContainer)
                    ThemeColorSwatch(MiuixTheme.colorScheme.surfaceContainerHigh)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = stringResource(R.string.theme_color_preview_action),
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                    TextButton(
                        text = stringResource(R.string.theme_color_preview_action_secondary),
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The styles, each one shown as itself.
 *
 * A style is a way of spreading a colour, and its name says nothing about what that looks like, so
 * every row carries three dots in the colours it would build. They are worked out by the same code
 * that would paint the app with it, from the same seed the preview above is drawn with, so the list
 * is a comparison rather than nine names.
 *
 * Three colours are enough to tell them apart: the two accents the style takes from the seed, and
 * the third hue, which is the one a style moves furthest away. The scheme has no plain third role
 * to ask for — Miuix keeps only its container — so the container stands in for it.
 */
@Composable
private fun themeColorStyleEntry(
    selectedStyle: ThemePaletteStyle,
    seed: Int,
    spec: ThemeColorSpec,
    dark: Boolean,
    onSelect: (ThemePaletteStyle) -> Unit,
): DropdownEntry = DropdownEntry(
    ThemePaletteStyle.entries.map { style ->
        DropdownItem(
            text = style.displayName,
            selected = style == selectedStyle,
            icon = { cellModifier ->
                ThemeColorStyleDots(
                    modifier = cellModifier,
                    seed = seed,
                    style = style,
                    // The palette a style cannot build to 2025 is built to 2021 in the app too, and
                    // the dots have to go with same one the app would use.
                    spec = spec.effectiveFor(style),
                    dark = dark,
                )
            },
            onClick = { onSelect(style) },
        )
    },
)

/**
 * The three colours of one style: the palette it makes out of [seed], drawn small.
 *
 * Nothing here is a picture of a palette. A nested theme is asked for the colours of this style,
 * which is exactly what the app asks for when it paints itself.
 */
@Composable
private fun ThemeColorStyleDots(
    modifier: Modifier,
    seed: Int,
    style: ThemePaletteStyle,
    spec: ThemeColorSpec,
    dark: Boolean,
) {
    val controller = remember(seed, style, spec, dark) {
        ThemeController(
            colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = Color(seed),
            colorSpec = spec,
            paletteStyle = style,
        )
    }

    MiuixTheme(controller = controller) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThemeColorStyleDotGap),
        ) {
            ThemeColorStyleDot(MiuixTheme.colorScheme.primary)
            ThemeColorStyleDot(MiuixTheme.colorScheme.secondary)
            ThemeColorStyleDot(MiuixTheme.colorScheme.tertiaryContainer)
        }
    }
}

@Composable
private fun ThemeColorStyleDot(color: Color) {
    Box(
        modifier = Modifier
            .size(ThemeColorStyleDotSize)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * How tall the colour menu is allowed to get before it scrolls. Nineteen colours are more rows than
 * a popup should be tall, and the list is scrollable anyway.
 */
private val ThemeColorPresetMenuHeight = 380.dp

/** One of the three dots that stand for a style, and the gap that keeps them apart. */
private val ThemeColorStyleDotSize = 12.dp
private val ThemeColorStyleDotGap = 4.dp

/** The colour dot in the menu: the one thing that tells nineteen colours apart. */
private val ThemeColorPresetDot = 20.dp

/**
 * The colours a preset palette can be built from, as a menu.
 *
 * Choosing one both fills the palette with it and puts the preset source in charge, because a tap
 * on a colour is a wish to see it rather than a wish to keep looking at the old one.
 *
 * Named the way a value-returning composable is named, so it reads as the list it hands over.
 */
@Composable
private fun themeColorPresetEntry(
    selectedColor: String,
    onSelect: (String) -> Unit,
): DropdownEntry = DropdownEntry(
    colorsList().map { preset ->
        DropdownItem(
            text = stringResource(preset.nameId),
            selected = preset.name == selectedColor,
            icon = { cellModifier ->
                Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(ThemeColorPresetDot)
                            .clip(CircleShape)
                            .background(Color(presetThemeSeed(preset.name))),
                    )
                }
            },
            onClick = { onSelect(preset.name) },
        )
    },
)

@Composable
private fun ThemeColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
    )
}
