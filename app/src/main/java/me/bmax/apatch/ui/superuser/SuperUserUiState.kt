package me.bmax.apatch.ui.superuser

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel

@Immutable
data class SuperUserItem(
    val packageName: String,
    val uid: Int,
    val label: String,
    val pinyin: String,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val isAllowed: Boolean,
    val isExcluded: Boolean,
    val profileUid: Int,
    val profileToUid: Int,
    val profileScontext: String,
    val packageInfo: PackageInfo? = null,
    val rawAppInfo: SuperUserViewModel.AppInfo? = null,
)

@Immutable
data class SuperUserUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isKernelPatchReady: Boolean = true,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val sortBy: SuperUserSort = SuperUserSort.DEFAULT,
    val items: List<SuperUserItem> = emptyList(),
    val totalAppCount: Int = 0,
    val allowedUidCount: Int = 0,
    val errorMessage: String? = null,
)
