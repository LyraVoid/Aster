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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
import me.bmax.apatch.R
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SuperUserViewModel : ViewModel() {
    companion object {
        private const val TAG = "SuperUserViewModel"
        private const val PREF_SORT_BY = "su_sort_by"
        private val appsLock = Any()
        private val appListLoadGate = AppListLoadGate()
        var apps by mutableStateOf<List<AppInfo>>(emptyList())

        const val RANK_ALLOWED = 0
        const val RANK_EXCLUDED = 1
        const val RANK_OTHER = 2

        /** The group a row belongs to, decided from the state at the moment the list was loaded. */
        fun rankOf(allow: Int, exclude: Int): Int = when {
            allow != 0 -> RANK_ALLOWED
            exclude == 1 -> RANK_EXCLUDED
            else -> RANK_OTHER
        }

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
        val config: PkgConfig.Config,
        /**
         * The row's place in the list, settled when the list is loaded rather than read from
         * [config] as it changes. See SuperUserItem.sortRank for what depending on the live state
         * did: toggling the top row scrolled the reader into a screen of switches that were off.
         */
        val sortRank: Int = RANK_OTHER,
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

    /**
     * Things the reader has to be told, as opposed to state the screen renders. A refused
     * configuration write belongs here: the row keeps showing what is on disk, so without a word
     * from this flow the tap simply looks like it was never registered.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    /**
     * Turns a refused configuration write into something the reader can act on.
     *
     * The reason is appended on purpose. A write is refused for a concrete one — the file cannot be
     * read in full, root is not available, the lock will not clear — and naming it is the difference
     * between a report we can fix and a report that says only "tapping does nothing".
     */
    private fun reportConfigResult(error: Throwable?) {
        if (error == null) return
        val what = apApp.getString(R.string.su_config_write_failed)
        val why = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        _messages.tryEmit("$what\n$why")
    }

    // -- Picking apps to act on together --------------------------------------

    /**
     * Whether the page is picking apps rather than switching them.
     *
     * The switches are per app, and a reader who wants the same thing for five of them otherwise
     * reaches for five switches — which is exactly when they would rather pick the five first and
     * change them once. Selection mode is that second shape: the rows pick, and the bar above them
     * acts on the lot.
     */
    var isSelectionMode by mutableStateOf(false)
        private set

    private val selection = mutableStateMapOf<Int, Boolean>()

    val selectedCount: Int by derivedStateOf { selection.values.count { it } }

    fun enterSelectionMode() {
        isSelectionMode = true
        selection.clear()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selection.clear()
    }

    fun toggleSelection(uid: Int) {
        selection[uid] = selection[uid] != true
    }

    fun isUidSelected(uid: Int): Boolean = selection[uid] == true

    /** The UIDs to act on, in the order the list shows them. */
    fun selectedUids(): List<Int> =
        synchronized(appsLock) { apps }.map { it.uid }.filter { isUidSelected(it) }.distinct()

    enum class BatchAction {
        GRANT_ROOT, NORMAL, EXCLUDE;

        internal fun configFor(config: PkgConfig.Config, uid: Int): PkgConfig.Config =
            config.copy(
                allow = if (this == GRANT_ROOT) 1 else 0,
                exclude = if (this == EXCLUDE) 1 else 0,
                profile = config.profile.copy(
                    uid = uid,
                    scontext = if (this == GRANT_ROOT) {
                        APApplication.MAGISK_SCONTEXT
                    } else {
                        APApplication.DEFAULT_SCONTEXT
                    },
                ),
            )
    }

    /** One batch at a time: two of them would interleave their reads and their writes. */
    private val batchInFlight = AtomicBoolean(false)

    /**
     * Applies one action to every picked app at once.
     *
     * The whole set goes through [PkgConfig.changeConfigs] as a single transaction, so the file and
     * the kernel end up agreeing about all of it or about none of it. A batch taken one entry at a
     * time could stop halfway and leave the reader with no way to tell which half had taken.
     *
     * Rows are collected by UID, because that is what the file holds: several packages sharing one
     * take the change together, which is the same rule a single switch follows.
     */
    fun applyBatch(action: BatchAction, uids: List<Int>) {
        val wanted = uids.toSet()
        val targets = synchronized(appsLock) { apps }
            .filter { it.uid in wanted }
            .distinctBy { it.uid }
        if (targets.isEmpty()) return
        if (!batchInFlight.compareAndSet(false, true)) return

        val newConfigs = targets.map { app -> action.configFor(app.config, app.uid) }

        PkgConfig.changeConfigs(
            configs = newConfigs,
            apply = {
                newConfigs.forEach { config ->
                    val uid = config.profile.uid
                    if (config.allow == 1) {
                        Natives.grantSu(uid, 0, config.profile.scontext)
                    } else {
                        Natives.revokeSu(uid)
                    }
                    // Normal mode also removes any previous kernel exclusion.
                    Natives.setUidExclude(uid, config.exclude)
                }
                updateAppConfigs(newConfigs)
            },
            onResult = { error ->
                reportConfigResult(error)
                batchInFlight.set(false)
            },
        )
    }

    /**
     * The bulk form of [updateAppConfig]: every package sharing a UID takes the new state, or a
     * stale row of a sibling package could put back what was just written.
     */
    private fun updateAppConfigs(newConfigs: List<PkgConfig.Config>) {
        val byUid = newConfigs.associateBy { it.profile.uid }
        synchronized(appsLock) {
            apps = apps.map { app ->
                val config = byUid[app.uid] ?: return@map app
                app.copy(config = config.copy(pkg = app.packageName))
            }
        }
    }

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
        // Rank, not the live allow/exclude state: see AppInfo.sortRank.
        val comparator = compareBy<AppInfo> { it.sortRank }
            .then(compareBy(collator, AppInfo::label))
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

    suspend fun ensureAppListLoaded() {
        appListLoadGate.ensureLoaded(
            hasData = { apps.isNotEmpty() },
            load = ::fetchAppListLocked,
        )
    }

    suspend fun fetchAppList() {
        appListLoadGate.reload(::fetchAppListLocked)
    }

    private suspend fun fetchAppListLocked() {
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
                val uidResult = Natives.suUidsResult()
                if (!uidResult.isSuccess) {
                    Log.e(TAG, "Failed to read authorized UIDs: rc=${uidResult.rc}")
                    _errorMessage.value = apApp.getString(R.string.su_error_read_uids)
                    return@withContext
                }
                val uids = uidResult.uids.toList()
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
                        config = config,
                        // Settled here, at the one place the list is built. A later switch may
                        // change config.allow, and the row must stay where the reader left it.
                        sortRank = rankOf(config.allow, config.exclude),
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
        PkgConfig.changeConfig(
            newConfig,
            apply = {
                if (granted) {
                    Natives.grantSu(app.uid, 0, newConfig.profile.scontext)
                    Natives.setUidExclude(app.uid, 0)
                } else {
                    Natives.revokeSu(app.uid)
                }
                updateAppConfig(app, newConfig)
            },
            onResult = ::reportConfigResult,
        )
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
        PkgConfig.changeConfig(
            newConfig,
            apply = {
                if (excluded) {
                    Natives.revokeSu(app.uid)
                }
                Natives.setUidExclude(app.uid, newConfig.exclude)
                updateAppConfig(app, newConfig)
            },
            onResult = ::reportConfigResult,
        )
    }

    fun setExcluded(item: SuperUserItem, excluded: Boolean) {
        val target = item.rawAppInfo ?: synchronized(appsLock) {
            apps.find { it.uid == item.uid }
        } ?: return
        setExcluded(target, excluded)
    }
}
