package me.bmax.apatch.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.IAPRootService
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.root.RootCapabilityRepository
import me.bmax.apatch.root.isUsable
import me.bmax.apatch.services.RootServices
import me.bmax.apatch.ui.superuser.SuperUserItem
import me.bmax.apatch.ui.superuser.SuperUserSort
import me.bmax.apatch.ui.superuser.SuperUserStateMapper
import me.bmax.apatch.ui.superuser.SuperUserUiState
import me.bmax.apatch.util.APatchCli
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.PkgConfig
import java.text.Collator
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SuperUserViewModel : ViewModel() {
    companion object {
        private const val TAG = "SuperUserViewModel"
        private const val PREF_SORT_BY = "su_sort_by"
        private val appsLock = Any()
        var apps by mutableStateOf<List<AppInfo>>(emptyList())

        fun getAppIconDrawable(context: Context, packageName: String): Drawable? {
            val appList = synchronized(appsLock) { apps }
            val appDetail = appList.find { it.packageName == packageName }
            return appDetail?.packageInfo?.applicationInfo?.loadIcon(context.packageManager)
        }
    }

    @Parcelize
    data class AppInfo(
        val label: String,
        val pinyin: String,
        val packageInfo: PackageInfo,
        val config: PkgConfig.Config
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo!!.uid
    }

    private val _search = MutableStateFlow("")
    private val _showSystemApps = MutableStateFlow(false)
    private val _sortBy = MutableStateFlow(loadSortPreference())
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var search: String
        get() = _search.value
        set(value) {
            _search.value = value
        }

    var showSystemApps: Boolean
        get() = _showSystemApps.value
        set(value) {
            _showSystemApps.value = value
        }

    var isRefreshing: Boolean
        get() = _isRefreshing.value
        private set(value) {
            _isRefreshing.value = value
        }

    private val collator = Collator.getInstance(Locale.getDefault())

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.config.allow != 0 -> 0
                it.config.exclude == 1 -> 1
                else -> 2
            }
        }.then(compareBy(collator, AppInfo::label))
        apps.sortedWith(comparator)
    }

    val appList: List<AppInfo>
        get() {
            val query = _search.value.lowercase()
            return sortedList.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase()
                    .contains(query) || it.pinyin.contains(query)
            }.filter {
                it.uid == 2000 // Always show shell
                        || _showSystemApps.value || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
            }.filter {
                it.packageName != apApp.packageName
            }
        }

    private val appsFlow = snapshotFlow { apps }
    private val filterFlow = combine(_search, _showSystemApps, _sortBy) { query, showSystem, sort ->
        Triple(query, showSystem, sort)
    }
    private val statusFlow = combine(_isRefreshing, _isLoading, _errorMessage) { refreshing, loading, error ->
        Triple(refreshing, loading, error)
    }

    val uiState: StateFlow<SuperUserUiState> = combine(
        appsFlow,
        filterFlow,
        statusFlow,
        RootCapabilityRepository.snapshot,
    ) { currentApps, (currentSearch, currentShowSystem, currentSort), (currentRefreshing, currentLoading, currentError), currentCapability ->
        val mappedItems = currentApps.map { SuperUserStateMapper.fromAppInfo(it) }
        val filteredItems = SuperUserStateMapper.filterAndSort(
            items = mappedItems,
            searchQuery = currentSearch,
            showSystemApps = currentShowSystem,
            sortBy = currentSort,
            managerPackageName = apApp.packageName,
            collator = collator,
        )
        SuperUserUiState(
            isLoading = currentLoading,
            isRefreshing = currentRefreshing,
            isKernelPatchReady = currentCapability.kernelPatch.isUsable(),
            searchQuery = currentSearch,
            showSystemApps = currentShowSystem,
            sortBy = currentSort,
            items = filteredItems,
            totalAppCount = mappedItems.size,
            allowedUidCount = SuperUserStateMapper.countAllowedUids(mappedItems),
            errorMessage = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SuperUserUiState(
            isLoading = apps.isEmpty(),
            isRefreshing = false,
            isKernelPatchReady = RootCapabilityRepository.snapshot.value.kernelPatch.isUsable(),
            searchQuery = "",
            showSystemApps = false,
            sortBy = loadSortPreference(),
            items = emptyList(),
            totalAppCount = 0,
            allowedUidCount = 0,
            errorMessage = null,
        ),
    )

    private fun loadSortPreference(): SuperUserSort {
        val name = APApplication.sharedPreferences.getString(PREF_SORT_BY, SuperUserSort.DEFAULT.name)
        return SuperUserSort.fromString(name)
    }

    private fun persistSortPreference(sort: SuperUserSort) {
        APApplication.sharedPreferences.edit().putString(PREF_SORT_BY, sort.name).apply()
    }

    fun updateSearch(query: String) {
        _search.value = query
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun updateSort(sortBy: SuperUserSort) {
        _sortBy.value = sortBy
        persistSortPreference(sortBy)
    }

    private suspend inline fun connectRootService(
        crossinline onDisconnect: () -> Unit = {}
    ): Pair<IBinder, ServiceConnection> = suspendCoroutine {
        val connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                onDisconnect()
            }

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                it.resume(binder as IBinder to this)
            }
        }
        val intent = Intent(apApp, RootServices::class.java)
        val task = RootServices.bindOrTask(
            intent,
            Shell.EXECUTOR,
            connection,
        )
        val shell = APatchCli.SHELL
        task?.let { it1 -> shell.execTask(it1) }
    }

    private fun stopRootService() {
        val intent = Intent(apApp, RootServices::class.java)
        RootServices.stop(intent)
    }

    suspend fun fetchAppList() {
        if (apps.isEmpty()) {
            _isLoading.value = true
        }
        _isRefreshing.value = true
        _errorMessage.value = null

        try {
            val result = connectRootService {
                Log.w(TAG, "RootService disconnected")
            }

            withContext(Dispatchers.IO) {
                val binder = result.first
                val allPackages = IAPRootService.Stub.asInterface(binder).getPackages(0)

                withContext(Dispatchers.Main) {
                    stopRootService()
                }
                val uids = Natives.suUids().toList()
                Log.d(TAG, "all allows: $uids")

                var configs: HashMap<Int, PkgConfig.Config> = HashMap()
                thread {
                    Natives.su()
                    configs = PkgConfig.readConfigs()
                }.join()

                Log.d(TAG, "all configs: $configs")

                val newApps = allPackages.list.map {
                    val appInfo = it.applicationInfo
                    val uid = appInfo!!.uid
                    val actProfile = if (uids.contains(uid)) Natives.suProfile(uid) else null
                    val config = configs.getOrDefault(
                        uid, PkgConfig.Config(appInfo.packageName, Natives.isUidExcluded(uid), 0, Natives.Profile(uid = uid))
                    )
                    config.allow = 0

                    // from kernel
                    if (actProfile != null) {
                        config.allow = 1
                        config.profile = actProfile
                    }
                    val label = appInfo.loadLabel(apApp.packageManager).toString()
                    AppInfo(
                        label = label,
                        // Pinyin is only needed for search filtering; converting is
                        // expensive, so do it once here instead of per keystroke.
                        pinyin = HanziToPinyin.getInstance().toPinyinString(label),
                        packageInfo = it,
                        config = config
                    )
                }

                withContext(Dispatchers.Main) {
                    synchronized(appsLock) {
                        apps = newApps
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch app list", e)
            _errorMessage.value = e.localizedMessage ?: "Failed to fetch application list"
        } finally {
            isRefreshing = false
            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fetchAppList()
        }
    }

    // Replaces the app's config wholesale so the snapshot state holding `apps`
    // invalidates and the UI recomposes; mutating Config fields in place would
    // leave the list showing stale grant/exclude state after a refresh.
    private fun updateAppConfig(app: AppInfo, newConfig: PkgConfig.Config) {
        synchronized(appsLock) {
            // Grant/exclude are per-UID operations; every package sharing the
            // UID must show the new state, or its stale row could overwrite it.
            apps = apps.map {
                if (it.uid == app.uid) it.copy(config = newConfig.copy(pkg = it.packageName)) else it
            }
        }
    }

    fun setRootGranted(app: AppInfo, granted: Boolean) {
        val config = app.config
        val newConfig = if (granted) {
            config.copy(
                allow = 1,
                exclude = 0,
                profile = config.profile.copy(uid = app.uid, scontext = APApplication.MAGISK_SCONTEXT)
            )
        } else {
            config.copy(allow = 0, profile = config.profile.copy(uid = app.uid))
        }
        PkgConfig.changeConfig(newConfig)
        if (granted) {
            Natives.grantSu(app.uid, 0, newConfig.profile.scontext)
            Natives.setUidExclude(app.uid, 0)
        } else {
            Natives.revokeSu(app.uid)
        }
        updateAppConfig(app, newConfig)
    }

    fun setRootGranted(item: SuperUserItem, granted: Boolean) {
        val target = item.rawAppInfo ?: synchronized(appsLock) {
            apps.find { it.uid == item.uid }
        } ?: return
        setRootGranted(target, granted)
    }

    fun setExcluded(app: AppInfo, excluded: Boolean) {
        val config = app.config
        val newConfig = if (excluded) {
            config.copy(
                allow = 0,
                exclude = 1,
                profile = config.profile.copy(uid = app.uid, scontext = APApplication.DEFAULT_SCONTEXT)
            )
        } else {
            config.copy(exclude = 0, profile = config.profile.copy(uid = app.uid))
        }
        if (excluded) {
            Natives.revokeSu(app.uid)
        }
        PkgConfig.changeConfig(newConfig)
        Natives.setUidExclude(app.uid, newConfig.exclude)
        updateAppConfig(app, newConfig)
    }

    fun setExcluded(item: SuperUserItem, excluded: Boolean) {
        val target = item.rawAppInfo ?: synchronized(appsLock) {
            apps.find { it.uid == item.uid }
        } ?: return
        setExcluded(target, excluded)
    }
}
