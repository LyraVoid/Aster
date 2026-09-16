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
    /**
     * Where this row sat when the list was loaded: 0 allowed, 1 excluded, 2 everything else.
     *
     * Deliberately not derived from [isAllowed] at draw time. The allowed rows come first, so
     * sorting on the live state moved a row to another section the moment its switch moved — and
     * because a lazy list follows the row it is anchored to, toggling the top row scrolled the
     * reader into the untouched section below, where every switch is off. That reads as "the tap
     * turned everything off". The order is settled when the list loads, and again on a refresh.
     */
    val sortRank: Int,
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
