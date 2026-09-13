package me.bmax.apatch.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramcosta.composedestinations.generated.destinations.APModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KPModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import me.bmax.apatch.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings

enum class PrimaryDestination(
    val direction: DirectionDestinationSpec,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val kernelPatchRequired: Boolean,
    val androidPatchRequired: Boolean,
) {
    Home(
        HomeScreenDestination,
        R.string.home,
        MiuixIcons.Home,
        false,
        false
    ),
    KModule(
        KPModuleScreenDestination,
        R.string.kpm,
        MiuixIcons.Layers,
        true,
        false
    ),
    SuperUser(
        SuperUserScreenDestination,
        R.string.su_title,
        MiuixIcons.Lock,
        true,
        false
    ),
    AModule(
        APModuleScreenDestination,
        R.string.apm,
        MiuixIcons.GridView,
        false,
        true
    ),
    Settings(
        SettingScreenDestination,
        R.string.settings,
        MiuixIcons.Settings,
        false,
        false
    )
}

/**
 * Destinations the shell may show for the current capability snapshot. Pages that the device
 * cannot use yet are hidden rather than disabled, so navigation never advertises a dead end.
 *
 * Checking counts as unavailable: entries appear once the probe reports a usable patch instead of
 * flashing in and disappearing again.
 */
internal fun visiblePrimaryDestinations(
    capabilities: AsterNavigationCapabilities,
    preferences: NavigationEntryPreferences = NavigationEntryPreferences(),
): List<PrimaryDestination> = PrimaryDestination.entries.filter {
    it.isVisible(capabilities) && preferences.shows(it)
}

internal fun PrimaryDestination.isVisible(
    capabilities: AsterNavigationCapabilities,
): Boolean = when {
    this == PrimaryDestination.Home || this == PrimaryDestination.Settings -> true
    // Every remaining page needs the kernel layer, so one guard covers KModule and SuperUser
    // before the AndroidPatch-only page is considered.
    !capabilities.kernelPatchReady -> false
    androidPatchRequired -> capabilities.androidPatchReady
    else -> true
}
