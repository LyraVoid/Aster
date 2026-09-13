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

/**
 * Top level layout family. [Panorama] owns its chrome (wallpaper scene on Home, floating
 * navigation elsewhere); [Standard] keeps the classic bottom bar or sidebar shell.
 */
enum class GlobalLayout(val value: String, @param:StringRes val label: Int) {
    Panorama("panorama", R.string.global_layout_panorama),
    Standard("standard", R.string.global_layout_standard);

    companion object {
        fun fromValue(value: String?): GlobalLayout =
            entries.firstOrNull { it.value == value } ?: Standard
    }
}

private const val GlobalLayoutKey = "global_layout"

@Composable
fun rememberGlobalLayout(): State<GlobalLayout> {
    val preferences = APApplication.sharedPreferences
    val state = remember(preferences) {
        mutableStateOf(GlobalLayout.fromValue(preferences.getString(GlobalLayoutKey, null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == GlobalLayoutKey || key == null) {
                state.value = GlobalLayout.fromValue(prefs.getString(GlobalLayoutKey, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = GlobalLayout.fromValue(preferences.getString(GlobalLayoutKey, null))
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setGlobalLayout(layout: GlobalLayout) {
    APApplication.sharedPreferences.edit { putString(GlobalLayoutKey, layout.value) }
}

// Persist visual choices independently of the standard navigation layout.
@Composable
fun rememberVisualFlag(key: String, default: Boolean): State<Boolean> {
    val prefs = APApplication.sharedPreferences
    val state = remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, default)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
            if (changed == key || changed == null) state.value = p.getBoolean(key, default)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
fun setVisualFlag(key: String, value: Boolean) {
    APApplication.sharedPreferences.edit { putBoolean(key, value) }
}

/**
 * Some visual choices have more than two answers, such as the shape of the scene clock. They are
 * stored the same way as the flags above and read back as the raw string, which the caller turns
 * into its own type so that an unknown stored value can fall back to its default.
 */
@Composable
fun rememberVisualChoice(key: String, default: String): State<String> {
    val prefs = APApplication.sharedPreferences
    val state = remember(prefs, key) { mutableStateOf(prefs.getString(key, null) ?: default) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
            if (changed == key || changed == null) state.value = p.getString(key, null) ?: default
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setVisualChoice(key: String, value: String) {
    APApplication.sharedPreferences.edit { putString(key, value) }
}
val LocalFloatingNavigationInset = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.unit.Dp(0f) }
val LocalSceneProgress = androidx.compose.runtime.compositionLocalOf { 1f }
