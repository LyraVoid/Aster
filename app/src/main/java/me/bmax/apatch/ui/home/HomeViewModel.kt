package me.bmax.apatch.ui.home

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.RootCapabilitySnapshot
import me.bmax.apatch.root.RootCheckPhase
import me.bmax.apatch.root.RootCapabilityRepository
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.installJailbreak
import me.bmax.apatch.util.listModules
import me.bmax.apatch.util.migrateStockBootBackup
import me.bmax.apatch.util.reboot as rebootDevice
import org.json.JSONArray

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val environment = MutableStateFlow<HomeDeviceEnvironment?>(null)
    private val backupWarning = MutableStateFlow(apApp.getBackupWarningState())
    private val update = MutableStateFlow<HomeUpdateState>(HomeUpdateState.Idle)
    private val updateCheckEnabled = MutableStateFlow(true)
    private val moduleCounts = MutableStateFlow(Pair(0, 0))
    private val mutableEvents = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    private var updateJob: Job? = null

    val events = mutableEvents.asSharedFlow()

    val uiState = combine(
        RootCapabilityRepository.snapshot,
        environment,
        backupWarning,
        update,
        moduleCounts,
    ) { capability, deviceEnvironment, showBackupWarning, updateState, counts ->
        HomeStateMapper.map(
            capability = capability,
            environment = deviceEnvironment,
            showBackupWarning = showBackupWarning,
            update = updateState,
            apmCount = counts.first,
            kpmCount = counts.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeStateMapper.map(
            capability = RootCapabilityRepository.snapshot.value,
            environment = null,
            showBackupWarning = apApp.getBackupWarningState(),
            update = HomeUpdateState.Idle,
        ),
    )

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == CHECK_UPDATE_KEY) {
                val enabled = preferences.getBoolean(CHECK_UPDATE_KEY, true)
                updateCheckEnabled.value = enabled
                if (enabled) {
                    checkForUpdates(force = true)
                } else {
                    updateJob?.cancel()
                    update.value = HomeUpdateState.Disabled
                }
            }
        }

    init {
        val preferences = APApplication.sharedPreferences
        updateCheckEnabled.value = preferences.getBoolean(CHECK_UPDATE_KEY, true)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)

        viewModelScope.launch(Dispatchers.IO) {
            RootCapabilityRepository.snapshot.first(::canMigrateStockBootBackup)
            migrateStockBootBackup()
        }
        viewModelScope.launch {
            environment.value = runCatching {
                AndroidHomeEnvironmentSource.load()
            }.getOrNull()
        }
        checkForUpdates()
        refreshCounts()
    }

    fun refreshCapabilities() {
        RootCapabilityRepository.refresh(force = true)
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val kpm = runCatching { Natives.kernelPatchModuleNum().toInt() }.getOrDefault(0).coerceAtLeast(0)
            val apm = runCatching {
                val list = listModules()
                JSONArray(list).length()
            }.getOrDefault(0).coerceAtLeast(0)
            moduleCounts.value = Pair(apm, kpm)
        }
    }

    fun dismissBackupWarning() {
        apApp.updateBackupWarningState(false)
        backupWarning.value = false
    }

    fun installApatch() {
        APApplication.installApatch()
    }

    fun uninstallApatch() {
        APApplication.uninstallApatch()
    }

    /**
     * Reboot the device, optionally into one of the special modes the CLI understands. The CLI
     * helper is named exactly like this wrapper, so it is imported under an alias; calling the plain
     * name re-entered this function and recursed until the stack overflowed.
     */
    fun reboot(reason: String = "") {
        rebootDevice(reason)
    }

    fun triggerJailbreak() {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { installJailbreak() }.getOrDefault(false)
            }
            mutableEvents.emit(HomeEvent.JailbreakResult(success))
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        if (!force && !updateCheckEnabled.value) {
            update.value = HomeUpdateState.Disabled
            return
        }
        if (!force && updateJob?.isActive == true) {
            return
        }
        if (!force && update.value is HomeUpdateState.UpToDate) {
            return
        }

        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            update.value = HomeUpdateState.Checking
            update.value = withContext(Dispatchers.IO) {
                val latest = runCatching { checkNewVersion() }.getOrNull()
                    ?: return@withContext HomeUpdateState.Failed
                val currentVersionCode = Version.getManagerVersion().second
                HomeStateMapper.resolveUpdateState(
                    enabled = true,
                    currentVersionCode = currentVersionCode,
                    latest = latest,
                )
            }
        }
    }

    override fun onCleared() {
        APApplication.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
        super.onCleared()
    }

    private companion object {
        const val CHECK_UPDATE_KEY = "check_update"
    }
}

internal fun canMigrateStockBootBackup(capability: RootCapabilitySnapshot): Boolean =
    capability.phase == RootCheckPhase.READY &&
        capability.rootAccess == RootAccessProbeState.AVAILABLE
