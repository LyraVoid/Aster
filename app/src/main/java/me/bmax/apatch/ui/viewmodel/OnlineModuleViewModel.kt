package me.bmax.apatch.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.repo.ModuleRepoDefaults
import me.bmax.apatch.ui.repo.OnlineModule
import me.bmax.apatch.ui.repo.filterModules
import me.bmax.apatch.ui.repo.parseOfficialModules
import me.bmax.apatch.util.FolkApiClient

/** The index answers without a token for now, so the query keeps its shape and sends an empty one. */
private const val API_TOKEN = ""

/**
 * The manager's own module index: a flat list of modules with one download url each.
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

    private var allModules: List<OnlineModule> = emptyList()

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        modules = filterModules(allModules, query) { "${it.name}\n${it.description}" }
    }

    fun fetchModules() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            errorMessage = null
            try {
                val language = if (apApp.resources.configuration.locales[0].language == "zh") "zh" else "en"
                val url = "${ModuleRepoDefaults.OfficialModulesUrl}&lang=$language&token=$API_TOKEN"
                FolkApiClient.fetchJson(url).fold(
                    onSuccess = { json ->
                        val parsed = parseOfficialModules(json, language)
                        allModules = parsed
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
}
