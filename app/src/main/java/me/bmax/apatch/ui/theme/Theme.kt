package me.bmax.apatch.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import me.bmax.apatch.ui.webui.MonetColorsProvider
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

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
                "use_system_color_theme",
                true
            ) else false
        )
    }
    var customColorScheme by remember { mutableStateOf(prefs.getString("custom_color", "blue")) }

    val refreshThemeObserver by refreshTheme.observeAsState(false)
    if (refreshThemeObserver == true) {
        darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
        nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
        dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
            "use_system_color_theme",
            true
        ) else false
        customColorScheme = prefs.getString("custom_color", "blue")
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
    val miuixKeyColor = if (dynamicColor) {
        null
    } else {
        LegacyMiuixThemeSeeds[customColorScheme] ?: LegacyMiuixThemeSeeds.getValue("blue")
    }
    val miuixThemeController = remember(miuixColorSchemeMode, miuixKeyColor) {
        ThemeController(
            colorSchemeMode = miuixColorSchemeMode,
            keyColor = miuixKeyColor,
            colorSpec = ThemeColorSpec.Spec2021,
            paletteStyle = ThemePaletteStyle.TonalSpot,
        )
    }

    MiuixTheme(controller = miuixThemeController) {
        MonetColorsProvider.UpdateCss()
        content()
    }
}

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
