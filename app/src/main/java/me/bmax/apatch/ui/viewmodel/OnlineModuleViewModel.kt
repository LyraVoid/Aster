package me.bmax.apatch.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.repo.ModuleRepoDefaults
import me.bmax.apatch.ui.repo.OnlineModule
import me.bmax.apatch.ui.repo.filterModules
import me.bmax.apatch.ui.repo.parseOfficialModules
import me.bmax.apatch.ui.screen.MODULE_TYPE
import me.bmax.apatch.util.FolkApiClient

/** The index answers without a token for now, so the query keeps its shape and sends an empty one. */
private const val API_TOKEN = ""

/**
 * The manager's own module indexes: a flat list of modules with one download url each, one index per
 * kind of module.
 *
 * The index takes an optional token. FolkPatch derives a real one in native code from a build time
 * secret (see its `security.cpp`); this build ships no secret yet, and the index answers the same
 * request without one.
 */
class OnlineModuleViewModel : ViewModel() {
    var modules by mutableStateOf<List<OnlineModule>>(emptyList())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    /** The index the list on screen came from, so the same one is not fetched twice. */
    private var loadedIndexUrl = ""

    private var allModules: List<OnlineModule> = emptyList()

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        modules = filterModules(allModules, query) { "${it.name}\n${it.description}" }
    }

    /**
     * Reads the index for [moduleType]. A [customIndexUrl] replaces the bundled index, which is how
     * a reader reaches a list of their own; both answer the same shape and are asked the same way.
     */
    fun fetchModules(moduleType: MODULE_TYPE, customIndexUrl: String = "", force: Boolean = false) {
        val indexUrl = indexUrl(moduleType, customIndexUrl)
        if (!force && indexUrl == loadedIndexUrl && allModules.isNotEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            if (indexUrl != loadedIndexUrl) {
                // Another index answers here from now on, and nothing of the old one may stay on screen.
                loadedIndexUrl = indexUrl
                allModules = emptyList()
                onSearchQueryChange(searchQuery)
            }
            isRefreshing = true
            errorMessage = null
            try {
                FolkApiClient.fetchJson(indexUrl).fold(
                    onSuccess = { json ->
                        allModules = parseOfficialModules(json, language())
                        onSearchQueryChange(searchQuery)
                    },
                    onFailure = { errorMessage = it.message ?: it.javaClass.simpleName },
                )
            } catch (e: Exception) {
                errorMessage = e.message ?: e.javaClass.simpleName
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun indexUrl(moduleType: MODULE_TYPE, customIndexUrl: String): String {
        val base = customIndexUrl.trim().ifEmpty {
            when (moduleType) {
                MODULE_TYPE.KPM -> ModuleRepoDefaults.KernelModulesUrl
                MODULE_TYPE.APM -> ModuleRepoDefaults.OfficialModulesUrl
            }
        }
        return base.toUri().buildUpon()
            .appendQueryParameter("lang", language())
            .appendQueryParameter("token", API_TOKEN)
            .build()
            .toString()
    }

    private fun language(): String =
        if (apApp.resources.configuration.locales[0].language == "zh") "zh" else "en"
}
