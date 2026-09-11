package me.bmax.apatch.root

enum class RootCheckPhase {
    NOT_STARTED,
    CHECKING,
    READY,
    FAILED,
}

enum class RootFreshness {
    NEVER_LOADED,
    REFRESHING,
    FRESH,
    STALE,
    FAILED,
}

enum class RootLayerState {
    UNKNOWN,
    CHECKING,
    UNAVAILABLE,
    AVAILABLE,
    NEED_UPDATE,
    NEED_REBOOT,
    BUSY,
    ERROR,
    BLOCKED,
}

enum class RootAccessProbeState {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    ERROR,
    BLOCKED,
}

enum class RootMode {
    UNKNOWN,
    NONE,
    KERNEL_PATCH_ONLY,
    FULL_APATCH,
    JAILBREAK,
}

enum class RootAttention {
    NEED_INSTALL,
    NEED_APATCH_INSTALL,
    NEED_UPDATE,
    NEED_REBOOT,
    BUSY,
    CHECK_FAILED,
}

enum class RootCheckError {
    ROOT_PROBE_FAILED,
    CORE_STATE_READ_FAILED,
    NATIVE_CHECK_FAILED,
    UNEXPECTED,
}

enum class RootDetailState {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
    ERROR,
}

data class RootCapabilityDetails(
    val suPath: String? = null,
    val suPathState: RootDetailState = RootDetailState.UNKNOWN,
    val androidPatchVersion: Int? = null,
    val androidPatchVersionState: RootDetailState = RootDetailState.UNKNOWN,
) {
    val hasReadError: Boolean
        get() = suPathState == RootDetailState.ERROR ||
            androidPatchVersionState == RootDetailState.ERROR
}

data class RootCapabilitySnapshot(
    val phase: RootCheckPhase = RootCheckPhase.NOT_STARTED,
    val freshness: RootFreshness = RootFreshness.NEVER_LOADED,
    val sessionId: Long? = null,
    val kernelPatch: RootLayerState = RootLayerState.UNKNOWN,
    val androidPatch: RootLayerState = RootLayerState.UNKNOWN,
    val rootAccess: RootAccessProbeState = RootAccessProbeState.UNKNOWN,
    val mode: RootMode = RootMode.UNKNOWN,
    val attention: Set<RootAttention> = emptySet(),
    val details: RootCapabilityDetails = RootCapabilityDetails(),
    val checkedAt: Long? = null,
    val lastGoodAt: Long? = null,
    val error: RootCheckError? = null,
)

data class RootInitializationSnapshot(
    val phase: RootCheckPhase = RootCheckPhase.NOT_STARTED,
    val sessionId: Long = 0L,
    val kernelPatchDetected: Boolean? = null,
    val rootProbeSucceeded: Boolean? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val details: RootCapabilityDetails = RootCapabilityDetails(),
    val error: RootCheckError? = null,
)

internal data class RootCapabilityProbeResult(
    val kernelPatch: RootLayerState,
    val androidPatch: RootLayerState,
    val rootAccess: RootAccessProbeState,
    val details: RootCapabilityDetails = RootCapabilityDetails(),
    val modeOverride: RootMode? = null,
    val error: RootCheckError? = null,
)
