package me.bmax.apatch.ui.install

import androidx.compose.runtime.Immutable

@Immutable
data class InstallModeState(
    val methods: List<InstallMethodType>,
    val showJailbreakWarning: Boolean,
    val showRootWarning: Boolean,
)

enum class InstallMethodType {
    SelectFile,
    DirectInstall,
    InactiveSlot,
}

fun resolveInstallModeState(
    rootAvailable: Boolean,
    isAbDevice: Boolean,
    jailbreakBlocked: Boolean,
): InstallModeState {
    val methods = if (jailbreakBlocked) {
        emptyList()
    } else {
        buildList {
            add(InstallMethodType.SelectFile)
            if (rootAvailable) {
                add(InstallMethodType.DirectInstall)
                if (isAbDevice) {
                    add(InstallMethodType.InactiveSlot)
                }
            }
        }
    }

    return InstallModeState(
        methods = methods,
        showJailbreakWarning = jailbreakBlocked,
        showRootWarning = !rootAvailable,
    )
}
