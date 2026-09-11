package me.bmax.apatch.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.util.ApdVersionResult
import me.bmax.apatch.util.Version

internal fun interface RootCapabilityProbe {
    suspend fun probe(): RootCapabilityProbeResult
}

internal data class RootDetailRead<T>(
    val value: T? = null,
    val state: RootDetailState = RootDetailState.UNKNOWN,
)

internal fun mapSuPathRead(rc: Long, path: String?): RootDetailRead<String> = when {
    rc >= 0L && !path.isNullOrBlank() -> RootDetailRead(
        value = path,
        state = RootDetailState.AVAILABLE,
    )

    rc >= 0L -> RootDetailRead(state = RootDetailState.UNAVAILABLE)
    else -> RootDetailRead(state = RootDetailState.ERROR)
}

internal fun Natives.SuPathResult.toRootDetailRead(): RootDetailRead<String> =
    mapSuPathRead(rc = rc, path = path)

internal fun mapApdVersionRead(result: ApdVersionResult): RootDetailRead<Int> = when (result) {
    is ApdVersionResult.Available -> RootDetailRead(
        value = result.version,
        state = RootDetailState.AVAILABLE,
    )

    is ApdVersionResult.Missing -> RootDetailRead(
        state = RootDetailState.UNAVAILABLE,
    )

    is ApdVersionResult.Error -> RootDetailRead(
        state = RootDetailState.ERROR,
    )
}

internal fun ApdVersionResult.toRootDetailRead(): RootDetailRead<Int> =
    mapApdVersionRead(this)

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

        val kernelPatchState = APApplication.kpStateLiveData.value
        val androidPatchState = APApplication.apStateLiveData.value
        val suPathRead = runCatching {
            Natives.suPathResult().toRootDetailRead()
        }.getOrElse {
            RootDetailRead<String>(state = RootDetailState.ERROR)
        }
        val androidPatchVersionRead = if (
            androidPatchState == APApplication.State.ANDROIDPATCH_NOT_INSTALLED
        ) {
            RootDetailRead<Int>(state = RootDetailState.UNAVAILABLE)
        } else {
            runCatching {
                Version.probeInstalledApdVersion().toRootDetailRead()
            }.getOrElse {
                RootDetailRead<Int>(state = RootDetailState.ERROR)
            }
        }

        RootCapabilityProjection.fromCoreState(
            kernelPatchDetected = true,
            rootProbeSucceeded = true,
            kernelPatchState = kernelPatchState,
            androidPatchState = androidPatchState,
            suPath = suPathRead.value,
            suPathState = suPathRead.state,
            androidPatchVersion = androidPatchVersionRead.value,
            androidPatchVersionState = androidPatchVersionRead.state,
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
            suPath = initialization.details.suPath,
            suPathState = initialization.details.suPathState,
            androidPatchVersion = initialization.details.androidPatchVersion,
            androidPatchVersionState = initialization.details.androidPatchVersionState,
            error = initialization.error,
        )
    }

    fun fromCoreState(
        kernelPatchDetected: Boolean,
        rootProbeSucceeded: Boolean,
        kernelPatchState: APApplication.State?,
        androidPatchState: APApplication.State?,
        suPath: String? = null,
        suPathState: RootDetailState = if (suPath != null) {
            RootDetailState.AVAILABLE
        } else {
            RootDetailState.UNKNOWN
        },
        androidPatchVersion: Int? = null,
        androidPatchVersionState: RootDetailState = if (androidPatchVersion != null) {
            RootDetailState.AVAILABLE
        } else {
            RootDetailState.UNKNOWN
        },
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
                suPathState = suPathState,
                androidPatchVersion = androidPatchVersion,
                androidPatchVersionState = androidPatchVersionState,
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
