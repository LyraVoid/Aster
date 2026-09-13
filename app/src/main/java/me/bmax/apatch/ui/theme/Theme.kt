package me.bmax.apatch.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.MutableLiveData
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.webui.MonetColorsProvider
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode }, navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}

val refreshTheme = MutableLiveData(false)

/**
 * Resolved theme facts for screens that need the manager's own dark/monet
 * decision instead of the raw system configuration.
 */
@Immutable
data class ThemeModeState(
    val isDark: Boolean,
    val isDynamicColor: Boolean,
)

val LocalThemeModeState = compositionLocalOf { ThemeModeState(isDark = false, isDynamicColor = true) }

@Composable
fun APatchTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences

    var darkThemeFollowSys by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_follow_sys",
                true
            )
        )
    }
    var nightModeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_enabled",
                false
            )
        )
    }
    // Dynamic color is available on Android 12+, and custom 1t!
    var dynamicColor by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
                SystemDynamicColorKey,
                true
            ) else false
        )
    }
    var customColorScheme by remember {
        mutableStateOf(prefs.getString(PresetColorKey, DefaultPresetColor))
    }
    // Highest priority of the three: it is the most specific choice the user can make, and the
    // system colour and the preset list below are left untouched so switching it off restores
    // whatever they had.
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val useWallpaperColor = wallpaperColorTheme.enabled
    val wallpaperColorSeed = wallpaperColorTheme.seed
    val themeColorScheme = rememberThemeColorSchemeState()
    // The Home wallpaper is the picture behind the panoramic scene, so it is only in effect while
    // that scene is the home in use. Switching layouts is read here rather than ignored, so the
    // palette the reader sees in the settings is the palette the app is painted with.
    val panoramaHome = rememberGlobalLayout().value == GlobalLayout.Panorama

    val refreshThemeObserver by refreshTheme.observeAsState(false)
    if (refreshThemeObserver == true) {
        darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
        nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
        dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
            SystemDynamicColorKey,
            true
        ) else false
        customColorScheme = prefs.getString(PresetColorKey, DefaultPresetColor)
        refreshTheme.postValue(false)
    }

    val darkTheme = if (darkThemeFollowSys) {
        isSystemInDarkTheme()
    } else {
        nightModeEnabled
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    val miuixColorSchemeMode = when {
        darkThemeFollowSys -> ColorSchemeMode.MonetSystem
        darkTheme -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetLight
    }
    // A palette of the reader's own only means anything when it is built from a seed of the
    // reader's own, so the system seed is read only once they have chosen one. Left alone, the key
    // colour below stays null and Miuix draws the platform's colours as they are, down to the
    // style and the spec — the palette this app had before the choice existed.
    val paletteChosen = !themeColorScheme.isDefaultPalette()
    val systemColorSeed = rememberSystemPaletteSeed(enabled = dynamicColor && paletteChosen)
    val themeColorChoice = resolveThemeColorChoice(
        panoramaHome = panoramaHome,
        wallpaperEnabled = useWallpaperColor,
        wallpaperSeed = wallpaperColorSeed,
        systemDynamicEnabled = dynamicColor,
        systemSeed = systemColorSeed,
        paletteChosen = paletteChosen,
        presetSeed = presetThemeSeed(customColorScheme),
    )
    // Seed zero is how the resolver says "let the platform decide"; a real one is the colour the
    // style and the spec are applied to.
    val miuixKeyColor = themeColorChoice.seed.takeIf { it != 0 }?.let { argb -> Color(argb) }
    val miuixThemeController = remember(
        miuixColorSchemeMode,
        miuixKeyColor,
        themeColorScheme.style,
        themeColorScheme.spec,
    ) {
        ThemeController(
            colorSchemeMode = miuixColorSchemeMode,
            keyColor = miuixKeyColor,
            colorSpec = themeColorScheme.spec,
            paletteStyle = themeColorScheme.style,
        )
    }

    MiuixTheme(controller = miuixThemeController) {
        CompositionLocalProvider(
            LocalThemeModeState provides ThemeModeState(
                isDark = darkTheme,
                isDynamicColor = dynamicColor,
            )
        ) {
            MonetColorsProvider.UpdateCss()
            content()
        }
    }
}

/** The preset colour as a seed, and the last resort when no other source can offer one. */
internal fun presetThemeSeed(colorName: String?): Int =
    (LegacyMiuixThemeSeeds[colorName] ?: LegacyMiuixThemeSeeds.getValue("blue")).toArgb()

private val LegacyMiuixThemeSeeds = mapOf(
    "amber" to Color(0xFFFFC107),
    "blue_grey" to Color(0xFF607D8B),
    "blue" to Color(0xFF2196F3),
    "brown" to Color(0xFF795548),
    "cyan" to Color(0xFF00BCD4),
    "deep_orange" to Color(0xFFFF5722),
    "deep_purple" to Color(0xFF673AB7),
    "green" to Color(0xFF4CAF50),
    "indigo" to Color(0xFF3F51B5),
    "light_blue" to Color(0xFF03A9F4),
    "light_green" to Color(0xFF8BC34A),
    "lime" to Color(0xFFCDDC39),
    "orange" to Color(0xFFFF9800),
    "pink" to Color(0xFFE91E63),
    "purple" to Color(0xFF9C27B0),
    "red" to Color(0xFFF44336),
    "sakura" to Color(0xFFE88AA6),
    "teal" to Color(0xFF009688),
    "yellow" to Color(0xFFFFD600),
)
