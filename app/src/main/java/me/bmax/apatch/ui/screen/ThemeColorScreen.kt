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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.theme.LocalThemeModeState
import me.bmax.apatch.ui.theme.ThemeColorScheme
import me.bmax.apatch.ui.theme.effectiveFor
import me.bmax.apatch.ui.theme.isDefaultPalette
import me.bmax.apatch.ui.theme.label
import me.bmax.apatch.ui.theme.presetThemeSeed
import me.bmax.apatch.ui.theme.rememberSystemPaletteSeed
import me.bmax.apatch.ui.theme.rememberThemeColorSchemeState
import me.bmax.apatch.ui.theme.rememberWallpaperColorThemeState
import me.bmax.apatch.ui.theme.resolveThemeColorChoice
import me.bmax.apatch.ui.theme.supportsSpec2025
import me.bmax.apatch.ui.theme.summary
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * The palette the app paints itself with: which colour it starts from, how that colour is spread
 * over the scheme, and which Material specification the tones are taken from.
 *
 * The choices are made against a live preview, because nine styles and two specs cannot be told
 * apart by their names. The preview is not a picture of a phone: it is a card built out of the
 * app's own components inside the candidate palette, so what it shows is what the app will look
 * like, down to the buttons.
 */
@Destination<RootGraph>
@Composable
fun ThemeColorScreen(navigator: DestinationsNavigator) {
    val prefs = APApplication.sharedPreferences
    val scheme = rememberThemeColorSchemeState()
    val isDark = LocalThemeModeState.current.isDark

    // The same facts the theme resolves its seed from, read here so the preview can be drawn in
    // the palette that is actually in force.
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        prefs.getBoolean("use_system_color_theme", true)
    val presetSeed = presetThemeSeed(prefs.getString("custom_color", "blue"))
    val systemSeed = rememberSystemPaletteSeed(enabled = dynamicColor)

    val colorChoice = resolveThemeColorChoice(
        wallpaperEnabled = wallpaperColorTheme.enabled,
        wallpaperSeed = wallpaperColorTheme.seed,
        systemDynamicEnabled = dynamicColor,
        systemSeed = systemSeed,
        paletteChosen = !scheme.isDefaultPalette(),
        presetSeed = presetSeed,
    )
    // A style the reader picked has to be shown on a real seed, so the preview falls back to the
    // platform's own seed even while the app itself is leaving the palette to the platform.
    val previewSeed = colorChoice.seed.takeIf { it != 0 } ?: systemSeed ?: presetSeed

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.theme_color_title),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
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
                        title = stringResource(scheme.style.label),
                        summary = stringResource(scheme.style.summary),
                        dark = isDark,
                    )
                    Text(
                        text = stringResource(
                            R.string.theme_color_source_summary,
                            stringResource(colorChoice.source.label),
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            item(key = "palette") {
                SettingsSectionCard(title = stringResource(R.string.theme_color_title)) {
                    val styles = ThemePaletteStyle.entries
                    OverlayDropdownPreference(
                        title = stringResource(R.string.theme_color_style),
                        summary = stringResource(scheme.style.summary),
                        items = styles.map { stringResource(it.label) },
                        selectedIndex = styles.indexOf(scheme.style).coerceAtLeast(0),
                        startAction = { SettingsIcon(MiuixIcons.Tune) },
                        onSelectedIndexChange = { index -> ThemeColorScheme.setStyle(styles[index]) },
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
                }
            }
        }
    }
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

@Composable
private fun ThemeColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
    )
}
