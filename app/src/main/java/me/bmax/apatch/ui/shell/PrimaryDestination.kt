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
