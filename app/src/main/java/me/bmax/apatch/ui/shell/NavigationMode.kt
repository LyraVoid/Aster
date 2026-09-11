package me.bmax.apatch.ui.shell

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import me.bmax.apatch.APApplication
import me.bmax.apatch.R

internal const val CompactNavigationWidthDp = 600f

enum class NavigationMode(val value: String, @param:StringRes val label: Int) {
    Auto("auto", R.string.navigation_mode_auto),
    Bottom("bottom", R.string.navigation_mode_bottom),
    Sidebar("sidebar", R.string.navigation_mode_sidebar);

    fun usesBottomNavigation(widthDp: Float): Boolean =
        this == Bottom || (this == Auto && widthDp < CompactNavigationWidthDp)

    companion object {
        fun fromValue(value: String?): NavigationMode = entries.firstOrNull { it.value == value } ?: Auto
    }
}

private const val NavigationModeKey = "navigation_mode"

@Composable
fun rememberNavigationMode(): State<NavigationMode> {
    val preferences = APApplication.sharedPreferences
    val state = remember(preferences) {
        mutableStateOf(NavigationMode.fromValue(preferences.getString(NavigationModeKey, null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == NavigationModeKey || key == null) {
                state.value = NavigationMode.fromValue(prefs.getString(NavigationModeKey, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = NavigationMode.fromValue(preferences.getString(NavigationModeKey, null))
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setNavigationMode(mode: NavigationMode) {
    APApplication.sharedPreferences.edit { putString(NavigationModeKey, mode.value) }
}
