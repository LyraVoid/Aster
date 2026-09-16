package me.bmax.apatch.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import me.bmax.apatch.APApplication
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.currentGlobalLayout
import me.bmax.apatch.ui.theme.PresetColorKey
import me.bmax.apatch.ui.theme.SystemDynamicColorKey
import me.bmax.apatch.ui.theme.ThemeColorSource
import me.bmax.apatch.ui.theme.WallpaperColorTheme
import me.bmax.apatch.ui.theme.presetColorNames
import me.bmax.apatch.ui.theme.presetThemeSeed
import me.bmax.apatch.ui.theme.themeColorSourceOf
import kotlin.math.abs

/**
 * Which of the desktop entries is the one switched on.
 *
 * The mark and the colour are two separate wishes, and the desktop can only be told about them
 * through the component it launches: a launcher reads the icon out of the APK's own resources with
 * the *system's* configuration, so it can never see a preference of ours. Every mark therefore
 * exists once per colour, each behind its own alias, and choosing is a matter of saying which alias
 * is the live one.
 *
 * The alias name is resolved against the code package while the component lives under the install
 * identity, and in this app those two are not the same string, so the halves are built separately.
 */
object LauncherIconUtils {
    /** Which mark the desktop shows: the APatch one when true, the Aster one when false. */
    const val USE_ALT_ICON = "use_alt_icon"

    /** Whether the icon is painted in the app's own colour or in the platform palette's. */
    const val FOLLOW_APP_COLORS = "launcher_icon_app_colors"

    private const val ASTER_MARK = "MainActivityDefault"
    private const val APATCH_MARK = "MainActivityAlias"

    /** No preset at all: the pair whose name carries no suffix, painted by the platform palette. */
    private const val PLATFORM = ""

    /**
     * A wallpaper with almost no colour in it has no hue to match a preset by, so the platform
     * palette is left in charge rather than a colour being invented for a grey picture.
     */
    private const val MIN_WALLPAPER_SATURATION = 0.08f

    fun updateLauncherState(context: Context) {
        val target = componentName(context, colourInForce(), alt = altInUse())
        val manager = context.packageManager
        // Whether the desktop is already showing what was asked for is asked of the package
        // manager and not of a note we kept: the preferences can be written from outside this app,
        // and a note would then describe a state that never happened. This is also what keeps a
        // cold start cheap — the sweep below is forty calls.
        val alreadyOn = runCatching {
            manager.getComponentEnabledSetting(target) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
        if (alreadyOn) return

        runCatching {
            everyComponent(context).forEach { component ->
                manager.setComponentEnabledSetting(
                    component,
                    if (component == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    fun applySaved(context: Context) = updateLauncherState(context)

    /**
     * Re-reads the choices after one of them moved.
     *
     * The colour can change on a page that knows nothing about the desktop, so the refresh is
     * triggered from where the choice is stored rather than from the screen that shows it. A no-op
     * unless the icon is following the app at all, which is also what keeps it off the paths that
     * run before the application exists.
     */
    fun refreshForColorChange() {
        val context = runCatching { apApp }.getOrNull() ?: return
        if (!APApplication.sharedPreferences.getBoolean(FOLLOW_APP_COLORS, false)) return
        updateLauncherState(context)
    }

    private fun altInUse(): Boolean =
        APApplication.sharedPreferences.getBoolean(USE_ALT_ICON, false)

    /**
     * Which colour the desktop should be painted in.
     *
     * Following the platform is where this started and stays the answer while the app is painted by
     * the platform too. Otherwise it is whatever the app itself is painted with: the chosen preset
     * by name, and for a wallpaper the preset its hue is nearest to — the app builds its own palette
     * from that hue alone, and a launcher icon cannot be given a colour that is worked out at
     * runtime.
     */
    private fun colourInForce(): String {
        val prefs = APApplication.sharedPreferences
        if (!prefs.getBoolean(FOLLOW_APP_COLORS, false)) return PLATFORM
        val source = themeColorSourceOf(
            panoramaHome = currentGlobalLayout() == GlobalLayout.Panorama,
            wallpaperEnabled = WallpaperColorTheme.state.value.enabled,
            systemDynamicEnabled = prefs.getBoolean(SystemDynamicColorKey, true),
            dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
        return when (source) {
            ThemeColorSource.System -> PLATFORM

            ThemeColorSource.Preset -> prefs.getString(PresetColorKey, null)
                ?.takeIf { it in presetColorNames }
                ?: PLATFORM

            ThemeColorSource.Wallpaper -> nearestPreset(WallpaperColorTheme.state.value.seed)
        }
    }

    private fun nearestPreset(seed: Int): String {
        if (seed == 0) return PLATFORM
        val wallpaper = FloatArray(3)
        Color.colorToHSV(seed, wallpaper)
        if (wallpaper[1] < MIN_WALLPAPER_SATURATION) return PLATFORM
        return presetColorNames.minByOrNull { name ->
            val preset = FloatArray(3)
            Color.colorToHSV(presetThemeSeed(name), preset)
            hueApart(wallpaper[0], preset[0])
        } ?: PLATFORM
    }

    /** Hue is a circle, so the two ends of it are a hair apart rather than a whole turn. */
    private fun hueApart(first: Float, second: Float): Float {
        val apart = abs(first - second) % 360f
        return if (apart > 180f) 360f - apart else apart
    }

    private fun componentName(context: Context, colour: String, alt: Boolean): ComponentName {
        val mark = if (alt) APATCH_MARK else ASTER_MARK
        return ComponentName(
            context.packageName,
            codePackage() + ".ui." + mark + suffixOf(colour),
        )
    }

    /** `deep_purple` names the alias `DeepPurple`, so the two written halves read alike. */
    private fun suffixOf(colour: String): String =
        colour.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    private fun codePackage(): String =
        APApplication::class.java.name.substringBeforeLast('.')

    private fun everyComponent(context: Context): List<ComponentName> = buildList {
        add(componentName(context, PLATFORM, alt = false))
        add(componentName(context, PLATFORM, alt = true))
        presetColorNames.forEach { colour ->
            add(componentName(context, colour, alt = false))
            add(componentName(context, colour, alt = true))
        }
    }
}
