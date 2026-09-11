package me.bmax.apatch.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.Version

internal fun interface RootCapabilityProbe {
    suspend fun probe(): RootCapabilityProbeResult
}

internal object AndroidRootCapabilityProbe : RootCapabilityProbe {
    override suspend fun probe(): RootCapabilityProbeResult = withContext(Dispatchers.IO) {
        val kernelPatchDetected = runCatching {
            Natives.nativeReady(APApplication.superKey)
        }.getOrElse {
            return@withContext RootCapabilityProbeResult(
                kernelPatch = RootLayerState.ERROR,
                androidPatch = RootLayerState.BLOCKED,
                rootAccess = RootAccessProbeState.ERROR,
                error = RootCheckError.NATIVE_CHECK_FAILED,
            )
        }

        if (!kernelPatchDetected) {
            return@withContext RootCapabilityProbeResult(
                kernelPatch = RootLayerState.UNAVAILABLE,
                androidPatch = RootLayerState.BLOCKED,
                rootAccess = RootAccessProbeState.UNAVAILABLE,
            )
        }

        val rootProbeSucceeded = runCatching {
            Natives.su(0, null)
        }.getOrElse {
            return@withContext RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNKNOWN,
                rootAccess = RootAccessProbeState.ERROR,
                error = RootCheckError.ROOT_PROBE_FAILED,
            )
        }

        if (!rootProbeSucceeded) {
            return@withContext RootCapabilityProbeResult(
                kernelPatch = RootLayerState.AVAILABLE,
                androidPatch = RootLayerState.UNKNOWN,
                rootAccess = RootAccessProbeState.ERROR,
                error = RootCheckError.ROOT_PROBE_FAILED,
            )
        }

        RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = true,
            kernelPatchState = APApplication.kpStateLiveData.value,
            androidPatchState = APApplication.apStateLiveData.value,
            suPath = runCatching { Natives.suPath().takeIf(String::isNotBlank) }.getOrNull(),
            androidPatchVersion = runCatching { Version.installedApdVUInt() }.getOrNull(),
        )
    }
}

internal object RootCapabilityProjection {
    fun fromInitialization(
        initialization: RootInitializationSnapshot,
        kernelPatchState: APApplication.State?,
        androidPatchState: APApplication.State?,
    ): RootCapabilityProbeResult {
        val kernelPatchDetected = initialization.kernelPatchDetected ?: return unknownProbe()
        val rootProbeSucceeded = initialization.rootProbeSucceeded ?: return unknownProbe()
        return fromCoreState(
            kernelPatchDetected = kernelPatchDetected,
            rootProbeSucceeded = rootProbeSucceeded,
            kernelPatchState = kernelPatchState,
            androidPatchState = androidPatchState,
            error = initialization.error,
        )
    }

    fun fromCoreState(
        kernelPatchDetected: Boolean,
        rootProbeSucceeded: Boolean,
        kernelPatchState: APApplication.State?,
        androidPatchState: APApplication.State?,
        suPath: String? = null,
        androidPatchVersion: Int? = null,
        error: RootCheckError? = null,
    ): RootCapabilityProbeResult {
        if (!kernelPatchDetected) {
            return RootCapabilityProbeResult(
                kernelPatch = RootLayerState.UNAVAILABLE,
                androidPatch = RootLayerState.BLOCKED,
                rootAccess = RootAccessProbeState.UNAVAILABLE,
            )
        }

        val kernelPatch = mapKernelPatchState(kernelPatchState)
        if (!rootProbeSucceeded) {
            return RootCapabilityProbeResult(
                kernelPatch = kernelPatch,
                androidPatch = RootLayerState.UNKNOWN,
                rootAccess = RootAccessProbeState.ERROR,
                error = error ?: RootCheckError.ROOT_PROBE_FAILED,
            )
        }

        return RootCapabilityProbeResult(
            kernelPatch = kernelPatch,
            androidPatch = mapAndroidPatchState(androidPatchState),
            rootAccess = RootAccessProbeState.AVAILABLE,
            details = RootCapabilityDetails(
                suPath = suPath,
                androidPatchVersion = androidPatchVersion,
            ),
            error = error,
        )
    }

    private fun mapKernelPatchState(state: APApplication.State?): RootLayerState = when (state) {
        APApplication.State.KERNELPATCH_NEED_UPDATE -> RootLayerState.NEED_UPDATE
        APApplication.State.KERNELPATCH_NEED_REBOOT -> RootLayerState.NEED_REBOOT
        APApplication.State.KERNELPATCH_UNINSTALLING -> RootLayerState.BUSY
        APApplication.State.KERNELPATCH_INSTALLED,
        APApplication.State.ANDROIDPATCH_NOT_INSTALLED,
        APApplication.State.ANDROIDPATCH_INSTALLED,
        APApplication.State.ANDROIDPATCH_INSTALLING,
        APApplication.State.ANDROIDPATCH_NEED_UPDATE,
        APApplication.State.ANDROIDPATCH_UNINSTALLING,
        -> RootLayerState.AVAILABLE

        APApplication.State.UNKNOWN_STATE,
        null,
        -> RootLayerState.AVAILABLE
    }

    private fun mapAndroidPatchState(state: APApplication.State?): RootLayerState = when (state) {
        APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> RootLayerState.UNAVAILABLE
        APApplication.State.ANDROIDPATCH_INSTALLED -> RootLayerState.AVAILABLE
        APApplication.State.ANDROIDPATCH_INSTALLING,
        APApplication.State.ANDROIDPATCH_UNINSTALLING,
        -> RootLayerState.BUSY

        APApplication.State.ANDROIDPATCH_NEED_UPDATE -> RootLayerState.NEED_UPDATE
        APApplication.State.UNKNOWN_STATE,
        null,
        -> RootLayerState.ERROR

        APApplication.State.KERNELPATCH_INSTALLED,
        APApplication.State.KERNELPATCH_NEED_UPDATE,
        APApplication.State.KERNELPATCH_NEED_REBOOT,
        APApplication.State.KERNELPATCH_UNINSTALLING,
        -> RootLayerState.ERROR
    }

    private fun unknownProbe(): RootCapabilityProbeResult = RootCapabilityProbeResult(
        kernelPatch = RootLayerState.UNKNOWN,
        androidPatch = RootLayerState.UNKNOWN,
        rootAccess = RootAccessProbeState.UNKNOWN,
        error = RootCheckError.CORE_STATE_READ_FAILED,
    )
}
