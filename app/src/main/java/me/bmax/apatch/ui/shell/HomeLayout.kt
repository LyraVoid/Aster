package me.bmax.apatch.ui.shell

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

/**
 * How the standard Home lays its head out, which is the shape the state is read in and where the
 * module counts stand beside it.
 *
 * [Large] is the card the page has always been headed by: one tall card for the state with the
 * counts stacked down its right. [Compact] is the same card laid across the page on its own, with
 * the working mode at its foot, the way the home screen of KernelSU draws it. [List] reads the state
 * as a row that carries the action it asks for at its end, which is the shape the page's other cards
 * speak in. Only [Large] shows the module counts; the tab that owns them is a tap away in both of
 * the others.
 *
 * The three differ in the head alone; everything below it, the backup warning, the system patch,
 * the device information and the link, is the same page in all of them. The label names the layout
 * and the summary says what living with it looks like, so the list can be offered wherever the
 * choice is made, the way a destination carries its own label.
 */
enum class HomeLayout(
    val value: String,
    @param:StringRes val label: Int,
    @param:StringRes val summary: Int,
) {
    Large("large", R.string.home_layout_large, R.string.home_layout_large_summary),
    Compact("compact", R.string.home_layout_compact, R.string.home_layout_compact_summary),
    List("list", R.string.home_layout_list, R.string.home_layout_list_summary);

    companion object {
        fun fromValue(value: String?): HomeLayout =
            entries.firstOrNull { it.value == value } ?: Large
    }
}

private const val HomeLayoutKey = "home_layout"

/**
 * The layout in use, read from the preferences the same way the layout family is, so that a change
 * made from the appearance sheet reaches the page without a restart.
 */
@Composable
fun rememberHomeLayout(): State<HomeLayout> {
    val preferences = APApplication.sharedPreferences
    val state = remember(preferences) {
        mutableStateOf(HomeLayout.fromValue(preferences.getString(HomeLayoutKey, null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == HomeLayoutKey || key == null) {
                state.value = HomeLayout.fromValue(prefs.getString(HomeLayoutKey, null))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = HomeLayout.fromValue(preferences.getString(HomeLayoutKey, null))
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

fun setHomeLayout(layout: HomeLayout) {
    APApplication.sharedPreferences.edit { putString(HomeLayoutKey, layout.value) }
}

/**
 * The layouts, offered as one choice with three answers. The appearance page and the Home sheet
 * both open this dialog, so the row that names the layout in use and the list behind it cannot
 * drift apart.
 */
@Composable
fun HomeLayoutDialog(
    show: Boolean,
    selected: HomeLayout,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.home_layout_title),
        onDismissRequest = onDismissRequest,
    ) {
        HomeLayout.entries.forEach { layout ->
            RadioButtonPreference(
                title = stringResource(layout.label),
                summary = stringResource(layout.summary),
                selected = selected == layout,
                onClick = {
                    onDismissRequest()
                    setHomeLayout(layout)
                },
            )
        }
    }
}
