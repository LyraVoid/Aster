package me.bmax.apatch.ui.home

import me.bmax.apatch.root.RootAttention
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.RootLayerState
import me.bmax.apatch.root.RootMode
import me.bmax.apatch.root.RootCapabilitySnapshot
import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.isUsable
import me.bmax.apatch.util.LatestVersionInfo

internal object HomeStateMapper {
    fun map(
        capability: RootCapabilitySnapshot,
        environment: HomeDeviceEnvironment?,
        showBackupWarning: Boolean,
        update: HomeUpdateState,
        apmCount: Int = 0,
        kpmCount: Int = 0,
        blockKernelPatchUpdate: Boolean = false,
        blockAndroidPatchUpdate: Boolean = false,
    ): HomeUiState {
        val visibleCapability = capability.withBlockedUpdatesHidden(
            blockKernelPatchUpdate = blockKernelPatchUpdate,
            blockAndroidPatchUpdate = blockAndroidPatchUpdate,
        )
        val conclusion = resolveConclusion(visibleCapability, environment)
        return HomeUiState(
            capability = visibleCapability,
            environment = environment,
            conclusion = conclusion,
            primaryAction = resolvePrimaryAction(visibleCapability, environment),
            deviceDensity = resolveDeviceDensity(visibleCapability, conclusion),
            showBackupWarning = showBackupWarning,
            update = update,
            apmCount = apmCount,
            kpmCount = kpmCount,
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
        capability.kernelPatch.isUsable() &&
            capability.rootAccess != RootAccessProbeState.AVAILABLE -> HomePrimaryAction.RETRY_CHECK
        environment?.jailbreakActive == true || capability.mode == RootMode.JAILBREAK ->
            HomePrimaryAction.SOFT_REBOOT

        capability.attention.contains(RootAttention.NEED_REBOOT) ->
            HomePrimaryAction.REBOOT

        capability.attention.contains(RootAttention.NEED_UPDATE) -> when (resolveUpdateLayer(capability)) {
            HomeUpdateLayer.KERNEL_PATCH -> HomePrimaryAction.UPDATE_KERNEL_PATCH
            HomeUpdateLayer.ANDROID_PATCH -> HomePrimaryAction.UPDATE_APATCH
            null -> HomePrimaryAction.RETRY_CHECK
        }

        capability.attention.contains(RootAttention.NEED_INSTALL) ->
            HomePrimaryAction.INSTALL_KERNEL_PATCH

        // Covers the attention flag as well as every other unusable system patch (unreadable
        // version, probe error). Gating on the flag alone left a device with a working kernel and
        // an unreadable system patch with no way to install it: the module page is hidden and no
        // button was offered.
        capability.kernelPatch.isUsable() && !capability.androidPatch.isUsable() ->
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

/**
 * Hide only the update prompt a reader opted out of. The layer stays usable so module access,
 * security actions and uninstall do not disappear with the notification.
 */
internal fun RootCapabilitySnapshot.withBlockedUpdatesHidden(
    blockKernelPatchUpdate: Boolean,
    blockAndroidPatchUpdate: Boolean,
): RootCapabilitySnapshot {
    val visibleKernelPatch = if (
        blockKernelPatchUpdate && kernelPatch == RootLayerState.NEED_UPDATE
    ) {
        RootLayerState.AVAILABLE
    } else {
        kernelPatch
    }
    val visibleAndroidPatch = if (
        blockAndroidPatchUpdate && androidPatch == RootLayerState.NEED_UPDATE
    ) {
        RootLayerState.AVAILABLE
    } else {
        androidPatch
    }
    val visibleAttention = attention.toMutableSet().apply {
        if (
            visibleKernelPatch != RootLayerState.NEED_UPDATE &&
            visibleAndroidPatch != RootLayerState.NEED_UPDATE
        ) {
            remove(RootAttention.NEED_UPDATE)
        }
    }
    return copy(
        kernelPatch = visibleKernelPatch,
        androidPatch = visibleAndroidPatch,
        attention = visibleAttention,
    )
}

/**
 * The layer a pending update belongs to.
 *
 * [RootAttention.NEED_UPDATE] is raised by either layer, which makes the conclusion alone a
 * statement about "something" being behind. Everything that names a patch - the work card, the status
 * strip, the primary action - has to go through here instead, or it reports an update for the layer
 * it happens to talk about.
 */
internal enum class HomeUpdateLayer {
    KERNEL_PATCH,
    ANDROID_PATCH,
}

/**
 * Which layer is behind, or null when both are current.
 *
 * Both layers can be behind at once. Installing the kernel patch comes first and the primary action
 * offers it first, so the kernel patch wins the tie and the card names the same layer as the button.
 */
internal fun resolveUpdateLayer(capability: RootCapabilitySnapshot): HomeUpdateLayer? = when {
    capability.kernelPatch == RootLayerState.NEED_UPDATE -> HomeUpdateLayer.KERNEL_PATCH
    capability.androidPatch == RootLayerState.NEED_UPDATE -> HomeUpdateLayer.ANDROID_PATCH
    else -> null
}

/**
 * The versions a pending update would move, or null when the installed side cannot be read.
 *
 * The two layers are measured differently - the kernel patch by its KernelPatch version, the system
 * patch by the patch build the manager was compiled against - so both sides of the arrow have to come
 * from the same layer. A sentinel ("0", "unknown") means the read failed; printing it would claim an
 * update for a layer that may well be current, which is exactly how the work card ended up announcing
 * a kernel patch update while only the system patch was behind.
 */
internal fun resolveUpdateVersions(
    layer: HomeUpdateLayer,
    installedKernelPatch: String,
    builtKernelPatch: String,
    installedSystemPatch: String,
    managerVersionCode: Long,
): Pair<String, String>? = when (layer) {
    HomeUpdateLayer.KERNEL_PATCH -> readableVersions(installedKernelPatch, builtKernelPatch)
    HomeUpdateLayer.ANDROID_PATCH -> readableVersions(installedSystemPatch, managerVersionCode.toString())
}

private fun readableVersions(from: String, to: String): Pair<String, String>? =
    if (from.isReadVersion() && to.isReadVersion()) from to to else null

private fun String.isReadVersion(): Boolean =
    isNotBlank() && this != "0" && !equals("unknown", ignoreCase = true)

/** A detected patch is not proof that this manager has a usable root session. */
internal fun HomeUiState.needsRootAccess(): Boolean =
    capability.phase == RootCheckPhase.READY && capability.kernelPatch.isUsable() &&
        capability.rootAccess != RootAccessProbeState.AVAILABLE

/**
 * Whether the uninstall entry has anything to do. Removing either layer needs a root session, and at
 * least one layer has to be there; otherwise the entry would only open a dialog whose every action
 * is disabled.
 */
internal fun HomeUiState.canUninstallAnything(): Boolean =
    capability.rootAccess == RootAccessProbeState.AVAILABLE &&
        (capability.kernelPatch.isUsable() ||
            capability.androidPatch == RootLayerState.AVAILABLE)
