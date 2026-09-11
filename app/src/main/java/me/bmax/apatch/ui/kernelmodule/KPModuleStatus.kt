package me.bmax.apatch.ui.kernelmodule

enum class KPModuleStatus {
    EMBEDDED,
    LOADED,
    INSTALLED,
    DISABLED,
}

fun resolveKPModuleStatuses(
    loadSource: String,
    loaded: Boolean,
    installed: Boolean,
    disabled: Boolean,
): List<KPModuleStatus> = buildList {
    if (loadSource == "embedded") {
        add(KPModuleStatus.EMBEDDED)
    } else if (loaded) {
        add(KPModuleStatus.LOADED)
    }

    if (installed) {
        add(if (disabled) KPModuleStatus.DISABLED else KPModuleStatus.INSTALLED)
    }
}
