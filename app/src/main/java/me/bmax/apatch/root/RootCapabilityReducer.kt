package me.bmax.apatch.root

internal object RootCapabilityReducer {
    fun checking(
        previous: RootCapabilitySnapshot,
        sessionId: Long,
    ): RootCapabilitySnapshot = previous.copy(
        phase = RootCheckPhase.CHECKING,
        freshness = RootFreshness.REFRESHING,
        sessionId = sessionId,
        kernelPatch = previous.kernelPatch,
        androidPatch = previous.androidPatch,
        rootAccess = previous.rootAccess,
        error = null,
    )

    fun applyProbe(
        previous: RootCapabilitySnapshot,
        result: RootCapabilityProbeResult,
        sessionId: Long,
        checkedAt: Long,
    ): RootCapabilitySnapshot {
        val mode = result.modeOverride ?: deriveMode(result)
        val isReliable = result.isReliable()
        return RootCapabilitySnapshot(
            phase = RootCheckPhase.READY,
            freshness = when {
                isReliable -> RootFreshness.FRESH
                previous.lastGoodAt != null -> RootFreshness.STALE
                else -> RootFreshness.FAILED
            },
            sessionId = sessionId,
            kernelPatch = result.kernelPatch,
            androidPatch = result.androidPatch,
            rootAccess = result.rootAccess,
            mode = mode,
            attention = deriveAttention(result, mode),
            details = result.details,
            checkedAt = checkedAt,
            lastGoodAt = if (isReliable) checkedAt else previous.lastGoodAt,
            error = result.error,
        )
    }

    fun fail(
        previous: RootCapabilitySnapshot,
        error: RootCheckError,
        sessionId: Long,
        checkedAt: Long,
    ): RootCapabilitySnapshot {
        val hasLastGood = previous.lastGoodAt != null
        return previous.copy(
            phase = RootCheckPhase.FAILED,
            freshness = if (hasLastGood) RootFreshness.STALE else RootFreshness.FAILED,
            sessionId = sessionId,
            attention = previous.attention + RootAttention.CHECK_FAILED,
            checkedAt = checkedAt,
            error = error,
        )
    }

    private fun deriveMode(result: RootCapabilityProbeResult): RootMode = when {
        result.kernelPatch == RootLayerState.UNAVAILABLE -> RootMode.NONE
        result.kernelPatch.isUsable() && result.androidPatch.isUsable() -> RootMode.FULL_APATCH
        result.kernelPatch.isUsable() -> RootMode.KERNEL_PATCH_ONLY
        else -> RootMode.UNKNOWN
    }

    private fun deriveAttention(
        result: RootCapabilityProbeResult,
        mode: RootMode,
    ): Set<RootAttention> = buildSet {
        if (result.error != null || result.rootAccess == RootAccessProbeState.ERROR) {
            add(RootAttention.CHECK_FAILED)
        }
        if (result.details.hasReadError) {
            add(RootAttention.CHECK_FAILED)
        }
        if (mode == RootMode.NONE) {
            add(RootAttention.NEED_INSTALL)
        }
        if (mode == RootMode.KERNEL_PATCH_ONLY && result.androidPatch == RootLayerState.UNAVAILABLE) {
            add(RootAttention.NEED_APATCH_INSTALL)
        }
        if (result.kernelPatch == RootLayerState.NEED_UPDATE || result.androidPatch == RootLayerState.NEED_UPDATE) {
            add(RootAttention.NEED_UPDATE)
        }
        if (result.kernelPatch == RootLayerState.NEED_REBOOT) {
            add(RootAttention.NEED_REBOOT)
        }
        if (
            result.kernelPatch == RootLayerState.BUSY ||
            result.androidPatch == RootLayerState.BUSY
        ) {
            add(RootAttention.BUSY)
        }
    }
}

private fun RootCapabilityProbeResult.isReliable(): Boolean =
    error == null &&
        rootAccess != RootAccessProbeState.ERROR &&
        !details.hasReadError

internal fun RootLayerState.isUsable(): Boolean = when (this) {
    RootLayerState.AVAILABLE,
    RootLayerState.NEED_UPDATE,
    RootLayerState.NEED_REBOOT,
    RootLayerState.BUSY,
    -> true

    else -> false
}
