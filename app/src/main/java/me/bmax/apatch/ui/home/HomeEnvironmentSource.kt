package me.bmax.apatch.ui.home

import android.os.Build
import android.system.Os
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getKmi
import me.bmax.apatch.util.isJailbreakMode
import me.bmax.apatch.util.querySELinuxStatus

internal fun interface HomeEnvironmentSource {
    suspend fun load(): HomeDeviceEnvironment
}

internal object AndroidHomeEnvironmentSource : HomeEnvironmentSource {
    override suspend fun load(): HomeDeviceEnvironment = withContext(Dispatchers.IO) {
        val managerVersion = Version.getManagerVersion()
        HomeDeviceEnvironment(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            androidApi = Build.VERSION.SDK_INT,
            isPreview = Build.VERSION.PREVIEW_SDK_INT != 0,
            kernelRelease = runCatching { Os.uname().release }.getOrDefault("unknown"),
            fingerprint = Build.FINGERPRINT,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            kmi = getKmi(),
            selinuxStatus = readSELinuxStatus(),
            jailbreakActive = isJailbreakMode(),
            managerVersionName = managerVersion.first,
            managerVersionCode = managerVersion.second,
        )
    }

    private fun readSELinuxStatus(): HomeSelinuxStatus {
        val (success, output) = querySELinuxStatus()
        if (!success) {
            return if (output.endsWith("Permission denied")) {
                HomeSelinuxStatus.ENFORCING
            } else {
                HomeSelinuxStatus.UNKNOWN
            }
        }
        return when (output.trim().lowercase(Locale.ROOT)) {
            "enforcing" -> HomeSelinuxStatus.ENFORCING
            "permissive" -> HomeSelinuxStatus.PERMISSIVE
            "disabled" -> HomeSelinuxStatus.DISABLED
            else -> HomeSelinuxStatus.UNKNOWN
        }
    }
}

internal fun HomeDeviceEnvironment.displayName(): String {
    val manufacturerName = manufacturer.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    val brandName = brand.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return buildString {
        append(manufacturerName)
        if (!brand.equals(manufacturer, ignoreCase = true)) {
            append(' ')
            append(brandName)
        }
        if (model.isNotBlank()) {
            append(' ')
            append(model)
        }
    }.trim()
}

internal fun HomeDeviceEnvironment.androidVersion(): String = buildString {
    append(androidRelease)
    if (isPreview) {
        append(" Preview")
    }
    append(" (API ")
    append(androidApi)
    append(')')
}
