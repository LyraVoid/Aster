package me.bmax.apatch.ui.module

import com.topjohnwu.superuser.io.SuFile
import me.bmax.apatch.util.isMagicMountEnabled

enum class ModuleMountWarning {
    NOT_INSTALLED,
    PENDING_REMOVAL,
    DISABLED,
}

internal fun resolveModuleMountWarning(
    requiresMount: Boolean,
    metaModulePropPresent: Boolean,
    metaModuleRemoved: Boolean,
    metaModuleDisabled: Boolean,
    magicMountEnabled: Boolean = false,
): ModuleMountWarning? = when {
    !requiresMount || magicMountEnabled -> null
    !metaModulePropPresent -> ModuleMountWarning.NOT_INSTALLED
    metaModuleRemoved -> ModuleMountWarning.PENDING_REMOVAL
    metaModuleDisabled -> ModuleMountWarning.DISABLED
    else -> null
}

internal fun probeModuleMountWarning(moduleIds: List<String>): ModuleMountWarning? {
    if (moduleIds.isEmpty()) return null

    // SuFile can throw when the main root shell failed to initialize. An unknown
    // result must degrade to no warning instead of crashing the module screen.
    return runCatching {
        if (isMagicMountEnabled()) return@runCatching null
        val requiresMount = moduleIds.any { moduleId ->
            val moduleDir = "/data/adb/modules/$moduleId"
            val hasSystem = SuFile.open("$moduleDir/system").isDirectory
            val isSkipped = SuFile.open("$moduleDir/skip_mount").isFile
            hasSystem && !isSkipped
        }

        if (!requiresMount) {
            null
        } else {
            val metaDir = "/data/adb/metamodule"
            resolveModuleMountWarning(
                requiresMount = true,
                metaModulePropPresent = SuFile.open("$metaDir/module.prop").isFile,
                metaModuleRemoved = SuFile.open("$metaDir/remove").isFile,
                metaModuleDisabled = SuFile.open("$metaDir/disable").isFile,
            )
        }
    }.getOrNull()
}
