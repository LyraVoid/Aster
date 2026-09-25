package me.bmax.apatch.ui.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsFeatureAvailability(
    val globalNamespace: Boolean,
    val sucompat: Boolean,
    val selinuxHide: Boolean,
    val selinuxMode: Boolean,
    val webViewDebugging: Boolean,
    val resetSuPath: Boolean,
    val nightTheme: Boolean,
)

fun resolveSettingsFeatureAvailability(
    kPatchReady: Boolean,
    aPatchReady: Boolean,
    nightFollowSystem: Boolean,
): SettingsFeatureAvailability {
    val fullRootRuntime = kPatchReady && aPatchReady
    return SettingsFeatureAvailability(
        globalNamespace = fullRootRuntime,
        sucompat = fullRootRuntime,
        selinuxHide = fullRootRuntime,
        selinuxMode = fullRootRuntime,
        webViewDebugging = aPatchReady,
        resetSuPath = kPatchReady,
        nightTheme = !nightFollowSystem,
    )
}
