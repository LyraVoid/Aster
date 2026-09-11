package me.bmax.apatch.ui.module

import com.topjohnwu.superuser.io.SuFile

enum class MetaModuleWarning {
    NOT_INSTALLED,
    PENDING_REMOVAL,
    DISABLED,
}

internal fun resolveMetaModuleWarning(
    requiresMount: Boolean,
    metaModulePropPresent: Boolean,
    metaModuleRemoved: Boolean,
    metaModuleDisabled: Boolean,
): MetaModuleWarning? = when {
    !requiresMount -> null
    !metaModulePropPresent -> MetaModuleWarning.NOT_INSTALLED
    metaModuleRemoved -> MetaModuleWarning.PENDING_REMOVAL
    metaModuleDisabled -> MetaModuleWarning.DISABLED
    else -> null
}

internal fun probeMetaModuleWarning(moduleIds: List<String>): MetaModuleWarning? {
    if (moduleIds.isEmpty()) return null

    // SuFile can throw when the main root shell failed to initialize. An unknown
    // result must degrade to no warning instead of crashing the module screen.
    return runCatching {
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
            resolveMetaModuleWarning(
                requiresMount = true,
                metaModulePropPresent = SuFile.open("$metaDir/module.prop").isFile,
                metaModuleRemoved = SuFile.open("$metaDir/remove").isFile,
                metaModuleDisabled = SuFile.open("$metaDir/disable").isFile,
            )
        }
    }.getOrNull()
}
