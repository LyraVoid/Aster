package me.bmax.apatch.ui.home

import me.bmax.apatch.root.RootAttention
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.RootLayerState
import me.bmax.apatch.root.RootMode
import me.bmax.apatch.root.RootCapabilitySnapshot
import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.util.LatestVersionInfo

internal object HomeStateMapper {
    fun map(
        capability: RootCapabilitySnapshot,
        environment: HomeDeviceEnvironment?,
        showBackupWarning: Boolean,
        update: HomeUpdateState,
    ): HomeUiState {
        val conclusion = resolveConclusion(capability, environment)
        return HomeUiState(
            capability = capability,
            environment = environment,
            conclusion = conclusion,
            primaryAction = resolvePrimaryAction(capability, environment),
            deviceDensity = resolveDeviceDensity(capability, conclusion),
            showBackupWarning = showBackupWarning,
            update = update,
        )
    }

    fun resolveUpdateState(
        enabled: Boolean,
        currentVersionCode: Long,
        latest: LatestVersionInfo,
    ): HomeUpdateState {
        if (!enabled) {
            return HomeUpdateState.Disabled
        }
        if (latest.versionCode <= 0 || latest.downloadUrl.isBlank()) {
            return HomeUpdateState.Failed
        }
        return if (latest.versionCode.toLong() > currentVersionCode) {
            HomeUpdateState.Available(
                versionCode = latest.versionCode,
                downloadUrl = latest.downloadUrl,
                changelog = latest.changelog,
            )
        } else {
            HomeUpdateState.UpToDate
        }
    }

    private fun resolveConclusion(
        capability: RootCapabilitySnapshot,
        environment: HomeDeviceEnvironment?,
    ): HomeConclusion = when {
        capability.phase == RootCheckPhase.NOT_STARTED ||
            capability.phase == RootCheckPhase.CHECKING -> HomeConclusion.CHECKING

        capability.phase == RootCheckPhase.FAILED -> HomeConclusion.CHECK_FAILED
        capability.attention.contains(RootAttention.CHECK_FAILED) -> HomeConclusion.CHECK_FAILED
        environment?.jailbreakActive == true || capability.mode == RootMode.JAILBREAK ->
            HomeConclusion.JAILBREAK

        capability.attention.contains(RootAttention.BUSY) -> HomeConclusion.BUSY
        capability.attention.contains(RootAttention.NEED_REBOOT) -> HomeConclusion.NEED_REBOOT
        capability.attention.contains(RootAttention.NEED_UPDATE) -> HomeConclusion.NEED_UPDATE
        capability.mode == RootMode.NONE -> HomeConclusion.NOT_INSTALLED
        capability.mode == RootMode.KERNEL_PATCH_ONLY -> HomeConclusion.KERNEL_PATCH_ONLY
        capability.mode == RootMode.FULL_APATCH -> HomeConclusion.FULL_APATCH
        else -> HomeConclusion.UNKNOWN
    }

    private fun resolvePrimaryAction(
        capability: RootCapabilitySnapshot,
        environment: HomeDeviceEnvironment?,
    ): HomePrimaryAction = when {
        capability.phase == RootCheckPhase.NOT_STARTED ||
            capability.phase == RootCheckPhase.CHECKING -> HomePrimaryAction.NONE

        capability.phase == RootCheckPhase.FAILED -> HomePrimaryAction.RETRY_CHECK
        capability.rootAccess == RootAccessProbeState.ERROR ||
            capability.attention.contains(RootAttention.CHECK_FAILED) ->
            HomePrimaryAction.RETRY_CHECK

        capability.attention.contains(RootAttention.BUSY) -> HomePrimaryAction.NONE
        environment?.jailbreakActive == true || capability.mode == RootMode.JAILBREAK ->
            HomePrimaryAction.SOFT_REBOOT

        capability.attention.contains(RootAttention.NEED_REBOOT) ->
            HomePrimaryAction.REBOOT

        capability.attention.contains(RootAttention.NEED_UPDATE) -> when {
            capability.kernelPatch == RootLayerState.NEED_UPDATE ->
                HomePrimaryAction.UPDATE_KERNEL_PATCH

            capability.androidPatch == RootLayerState.NEED_UPDATE ->
                HomePrimaryAction.UPDATE_APATCH

            else -> HomePrimaryAction.RETRY_CHECK
        }

        capability.attention.contains(RootAttention.NEED_INSTALL) ->
            HomePrimaryAction.INSTALL_KERNEL_PATCH

        capability.attention.contains(RootAttention.NEED_APATCH_INSTALL) ->
            HomePrimaryAction.INSTALL_APATCH

        else -> HomePrimaryAction.NONE
    }

    private fun resolveDeviceDensity(
        capability: RootCapabilitySnapshot,
        conclusion: HomeConclusion,
    ): HomeDeviceDensity {
        val quiet = capability.phase == RootCheckPhase.READY &&
            capability.attention.isEmpty() &&
            conclusion == HomeConclusion.FULL_APATCH
        return if (quiet) HomeDeviceDensity.COMPACT else HomeDeviceDensity.DIAGNOSTIC
    }
}
