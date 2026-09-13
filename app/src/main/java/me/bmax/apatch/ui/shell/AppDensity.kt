package me.bmax.apatch.ui.shell

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import me.bmax.apatch.APApplication

/**
 * How large this app draws itself.
 *
 * The size is the app's own, not the device's: it is carried on the Context every Activity is built
 * from, so the screens, the dialogs and the insets are drawn at the density chosen here while the
 * system, and every other app on the device, stays at the density the device asks for. That is also
 * why the choice costs neither root nor a reboot, and why it takes one rebuild of the Activity in
 * use to take effect: the Context a window was built from cannot be exchanged under it.
 */
internal object AppDensity {
    /** The density the device asks for, which is also the state of never having chosen one. */
    const val FollowSystem = -1

    /**
     * The range the slider offers when the device's own density sits inside it. It reaches wider
     * than any phone asks for and stops well short of the point where the page stops fitting.
     */
    const val Min = 320
    const val Max = 720

    /** The density in use, or [FollowSystem] while the device's own is the one in use. */
    fun read(): Int = APApplication.sharedPreferences.getInt(AppDensityKey, FollowSystem)

    fun set(density: Int) {
        val value = if (density == FollowSystem) FollowSystem else density.coerceIn(Min, Max)
        APApplication.sharedPreferences.edit { putInt(AppDensityKey, value) }
    }

    /**
     * The Context an Activity should be built from: the one it was given, or a copy of it carrying
     * the density chosen here. Nothing else of the configuration is disturbed, so the language, the
     * night mode and the rest stay the device's to decide.
     */
    fun wrap(base: Context): Context {
        val density = read()
        if (density == FollowSystem) return base
        val configuration = Configuration(base.resources.configuration)
        if (configuration.densityDpi == density) return base
        configuration.densityDpi = density
        return base.createConfigurationContext(configuration)
    }
}

private const val AppDensityKey = "app_density"

/**
 * The density in use, read from the preferences the way the other visual choices are, so that the
 * rows that set it can say what it is set to without a restart of their own.
 */
@Composable
fun rememberAppDensity(): State<Int> {
    val preferences = APApplication.sharedPreferences
    val state = remember(preferences) {
        mutableIntStateOf(preferences.getInt(AppDensityKey, AppDensity.FollowSystem))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == AppDensityKey || key == null) {
                state.intValue = prefs.getInt(AppDensityKey, AppDensity.FollowSystem)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.intValue = preferences.getInt(AppDensityKey, AppDensity.FollowSystem)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * The density the device asks for, read from the application's own resources: the screens carry the
 * density chosen here, the application does not, so this is what following the system would mean.
 */
@Composable
fun rememberDeviceDensity(): Int {
    val context = LocalContext.current.applicationContext
    return remember(context) { context.resources.configuration.densityDpi }
}
