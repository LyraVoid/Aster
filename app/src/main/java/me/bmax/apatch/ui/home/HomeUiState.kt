package me.bmax.apatch.ui.home

import me.bmax.apatch.root.RootCapabilitySnapshot

enum class HomeConclusion {
    CHECKING,
    CHECK_FAILED,
    NOT_INSTALLED,
    KERNEL_PATCH_ONLY,
    FULL_APATCH,
    NEED_UPDATE,
    NEED_REBOOT,
    BUSY,
    JAILBREAK,
    UNKNOWN,
}

enum class HomePrimaryAction {
    NONE,
    RETRY_CHECK,
    INSTALL_KERNEL_PATCH,
    UPDATE_KERNEL_PATCH,
    INSTALL_APATCH,
    UPDATE_APATCH,
    REBOOT,
    SOFT_REBOOT,
}

enum class HomeDeviceDensity {
    COMPACT,
    DIAGNOSTIC,
}

enum class HomeSelinuxStatus {
    UNKNOWN,
    ENFORCING,
    PERMISSIVE,
    DISABLED,
}

data class HomeDeviceEnvironment(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val androidApi: Int,
    val isPreview: Boolean,
    val kernelRelease: String,
    val fingerprint: String,
    val primaryAbi: String,
    val kmi: String?,
    val selinuxStatus: HomeSelinuxStatus,
    val jailbreakActive: Boolean,
    val managerVersionName: String,
    val managerVersionCode: Long,
)

sealed interface HomeUpdateState {
    data object Disabled : HomeUpdateState

    data object Idle : HomeUpdateState

    data object Checking : HomeUpdateState

    data object UpToDate : HomeUpdateState

    data object Failed : HomeUpdateState

    data class Available(
        val versionCode: Int,
        val downloadUrl: String,
        val changelog: String,
    ) : HomeUpdateState
}

data class HomeUiState(
    val capability: RootCapabilitySnapshot = RootCapabilitySnapshot(),
    val environment: HomeDeviceEnvironment? = null,
    val conclusion: HomeConclusion = HomeConclusion.CHECKING,
    val primaryAction: HomePrimaryAction = HomePrimaryAction.NONE,
    val deviceDensity: HomeDeviceDensity = HomeDeviceDensity.DIAGNOSTIC,
    val showBackupWarning: Boolean = false,
    val update: HomeUpdateState = HomeUpdateState.Idle,
)

sealed interface HomeEvent {
    data class JailbreakResult(val success: Boolean) : HomeEvent
}
