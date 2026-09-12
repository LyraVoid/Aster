package me.bmax.apatch.ui.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsFeatureAvailability(
    val globalNamespace: Boolean,
    val sucompat: Boolean,
    val selinuxHide: Boolean,
    val webViewDebugging: Boolean,
    val resetSuPath: Boolean,
    val nightTheme: Boolean,
    val customColor: Boolean,
)

fun resolveSettingsFeatureAvailability(
    kPatchReady: Boolean,
    aPatchReady: Boolean,
    dynamicColorSupported: Boolean,
    nightFollowSystem: Boolean,
    useSystemDynamicColor: Boolean,
    useWallpaperColor: Boolean,
): SettingsFeatureAvailability {
    val fullRootRuntime = kPatchReady && aPatchReady
    return SettingsFeatureAvailability(
        globalNamespace = fullRootRuntime,
        sucompat = fullRootRuntime,
        selinuxHide = fullRootRuntime,
        webViewDebugging = aPatchReady,
        resetSuPath = kPatchReady,
        nightTheme = !nightFollowSystem,
        // Both the system colour and the preset list are overridden by the wallpaper colours, so
        // they step aside while it is on, the same way the preset list already steps aside for the
        // system colour.
        customColor = (!dynamicColorSupported || !useSystemDynamicColor) && !useWallpaperColor,
    )
}
