package me.bmax.apatch.ui.module

import com.topjohnwu.superuser.io.SuFile

/**
 * The ids of the modules that inject through the Zygisk loader without providing it — the ones
 * carrying a `zygisk` directory of their own, which the implementation under them reads on boot.
 *
 * SuFile can throw when the main root shell failed to initialize, so an unknown answer comes back
 * as an empty set: not knowing leaves the order as it was instead of failing the module screen.
 */
internal fun probeZygiskConsumerIds(moduleIds: List<String>): Set<String> {
    if (moduleIds.isEmpty()) return emptySet()

    return runCatching {
        moduleIds.filterTo(mutableSetOf()) { moduleId ->
            SuFile.open("/data/adb/modules/$moduleId/zygisk").isDirectory
        }
    }.getOrDefault(emptySet())
}
