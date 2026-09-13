package me.bmax.apatch.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.module.ModuleMountWarning
import me.bmax.apatch.ui.module.ModuleSortFacts
import me.bmax.apatch.ui.module.ModuleSortGroup
import me.bmax.apatch.ui.module.ModuleSortPriorityGroups
import me.bmax.apatch.ui.module.ModuleSortPriorityStore
import me.bmax.apatch.ui.module.moduleSortComparator
import me.bmax.apatch.ui.module.probeModuleMountWarning
import me.bmax.apatch.ui.module.probeZygiskConsumerIds
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.hasMagisk
import me.bmax.apatch.util.listModules
import me.bmax.apatch.util.magicMountChanges
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

class APModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
        private var cachedMagiskPresent by mutableStateOf(false)
        private var cachedModuleMountWarning by mutableStateOf<ModuleMountWarning?>(null)
        private var cachedLoadFailed by mutableStateOf(false)

        // Filled by a probe on every refresh, and read by the order below.
        private var zygiskConsumers by mutableStateOf<Set<String>>(emptySet())
    }

    init {
        // Main pages can remain composed behind Settings. Refresh the banner as soon as
        // the mount switch succeeds, without waiting for another module/network refresh.
        viewModelScope.launch(Dispatchers.IO) {
            magicMountChanges.collect { refreshMountWarning() }
        }
    }

    private fun refreshMountWarning() {
        cachedModuleMountWarning = probeModuleMountWarning(
            modules.filter { it.enabled && !it.remove }.map(ModuleInfo::id),
        )
    }

    data class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
        val metamodule: Boolean,
        val updateInfo: ModuleUpdateInfo? = null,
        // Pinyin of `name`, precomputed at load time; per-keystroke conversion in
        // the search filter dropped frames on the main thread.
        val pinyin: String = "",
    )

    data class ModuleUpdateInfo(
        val version: String,
        val versionCode: Int,
        val zipUrl: String,
        val changelog: String,
    )

    var search by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
        private set

    private val collator = Collator.getInstance(Locale.getDefault())

    /**
     * Which kinds of module the reader asked to see first. Held on the instance, because it is
     * only this screen that sorts, and read back from the preferences so a reader who reopens the
     * screen gets the order they left.
     */
    internal var sortPriorities by mutableStateOf(ModuleSortPriorityStore.decode(storedSortPriorities()))
        private set

    val moduleList by derivedStateOf {
        val comparator = compareBy<ModuleInfo, ModuleSortFacts>(
            moduleSortComparator(collator, sortPriorities),
        ) { it.sortFacts() }

        modules.filter {
            it.id.contains(search, true) || it.name.contains(search, true) ||
                it.pinyin.contains(search, true)
        }.sortedWith(comparator)
    }

    private fun ModuleInfo.sortFacts() = ModuleSortFacts(
        id = id,
        name = name,
        metaModule = metamodule,
        hasWebUi = hasWebUi,
        hasActionScript = hasActionScript,
        isZygiskConsumer = id in zygiskConsumers,
    )

    internal fun setSortPriorities(groups: Set<ModuleSortGroup>) {
        sortPriorities = groups
        runCatching {
            APApplication.sharedPreferences.edit()
                .putString(ModuleSortPriorityStore.Key, ModuleSortPriorityStore.encode(groups))
                .apply()
        }
    }

    private fun storedSortPriorities(): String? = runCatching {
        APApplication.sharedPreferences.getString(ModuleSortPriorityStore.Key, null)
    }.getOrNull()

    val totalModuleCount: Int
        get() = modules.size

    val isMagiskPresent: Boolean
        get() = cachedMagiskPresent

    val moduleMountWarning: ModuleMountWarning?
        get() = cachedModuleMountWarning

    val hasLoadError: Boolean
        get() = cachedLoadFailed

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            cachedLoadFailed = false

            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {
                cachedMagiskPresent = runCatching { hasMagisk() }.getOrDefault(false)

                val result = listModules()

                Log.i(TAG, "result: $result")

                val array = JSONArray(result)
                modules = (0 until array.length())
                    .asSequence()
                    .map { array.getJSONObject(it) }
                    .map { obj ->
                        val name = obj.optString("name")
                        ModuleInfo(
                            obj.getString("id"),

                            name,
                            obj.optString("author", "Unknown"),
                            obj.optString("version", "Unknown"),
                            obj.optInt("versionCode", 0),
                            obj.optString("description"),
                            obj.getBoolean("enabled"),
                            obj.getBoolean("update"),
                            obj.getBoolean("remove"),
                            obj.optString("updateJson"),
                            obj.getBooleanCompat("web"),
                            obj.getBooleanCompat("action"),
                            obj.getBooleanCompat("metamodule"),
                            pinyin = HanziToPinyin.getInstance().toPinyinString(name) ?: ""
                        )
                    }.toList()
                refreshMountWarning()
                zygiskConsumers = probeZygiskConsumerIds(modules.map(ModuleInfo::id))
                isRefreshing = false

                // One network round trip per enabled module; running them
                // concurrently makes the total latency the slowest response
                // instead of the sum of all responses.
                modules = modules.map { module ->
                    async {
                        if (module.enabled && module.updateJson.isNotEmpty() && !module.update && !module.remove) {
                            module.copy(updateInfo = runCatching { checkUpdate(module) }.getOrNull())
                        } else module
                    }
                }.awaitAll()
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                cachedLoadFailed = true
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(m: ModuleInfo): ModuleUpdateInfo? {
        if (m.updateJson.isEmpty() || m.remove || m.update || !m.enabled) {
            return null
        }
        // download updateJson
        val result = kotlin.runCatching {
            val url = m.updateJson
            Log.i(TAG, "checkUpdate url: $url")
            val response = apApp.okhttpClient
                .newCall(
                    okhttp3.Request.Builder()
                        .url(url)
                        .build()
                ).execute()
            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body.string()
            } else {
                ""
            }
        }.getOrDefault("")
        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return null
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return null

        val version = sanitizeVersionString(updateJson.optString("version", ""))
        val versionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (versionCode <= m.versionCode || zipUrl.isEmpty()) {
            return null
        }

        return ModuleUpdateInfo(version, versionCode, zipUrl, changelog)
    }
}

private fun JSONObject.getBooleanCompat(key: String, default: Boolean = false): Boolean {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        null -> default
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        is Number -> value.toInt() != 0
        else -> default
    }
}
