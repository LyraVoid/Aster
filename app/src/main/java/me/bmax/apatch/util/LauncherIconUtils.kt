package me.bmax.apatch.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import me.bmax.apatch.APApplication

/**
 * Which of the two desktop entries is the one switched on: the Aster mark, or the APatch mark the
 * app grew out of. Both are an alias of the same activity and only one of them is enabled, so
 * choosing is a matter of saying which.
 *
 * The alias name is resolved against the code package while the component lives under the install
 * identity, and in this app those two are not the same string, so the halves are built separately.
 */
object LauncherIconUtils {
    const val USE_ALT_ICON = "use_alt_icon"

    private const val DEFAULT_ALIAS = ".ui.MainActivityDefault"
    private const val ALT_ALIAS = ".ui.MainActivityAlias"

    fun updateLauncherState(context: Context) {
        val useAlt = APApplication.sharedPreferences.getBoolean(USE_ALT_ICON, false)
        val codePackage = APApplication::class.java.name.substringBeforeLast('.')
        val defaultComponent = ComponentName(context.packageName, codePackage + DEFAULT_ALIAS)
        val altComponent = ComponentName(context.packageName, codePackage + ALT_ALIAS)
        val target = if (useAlt) altComponent else defaultComponent
        val manager = context.packageManager

        runCatching {
            listOf(defaultComponent, altComponent).forEach { component ->
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
}
