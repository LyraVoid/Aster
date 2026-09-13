package me.bmax.apatch.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.repo.ExploreRepository
import me.bmax.apatch.ui.repo.ModuleRepoDefaults
import me.bmax.apatch.ui.repo.RepoModule
import me.bmax.apatch.ui.repo.filterModules
import me.bmax.apatch.ui.repo.parseExploreRepositories
import me.bmax.apatch.ui.repo.parseRepoModules
import me.bmax.apatch.ui.repo.resolveModulesUrl
import me.bmax.apatch.util.FolkApiClient

/**
 * Magisk style repositories: the reader picks a repository (bundled, listed by the cluster index or
 * typed in) and gets the modules that repository publishes, version history included.
 */
class RepoModuleViewModel : ViewModel() {
    var modules by mutableStateOf<List<RepoModule>>(emptyList())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var repositories by mutableStateOf<List<ExploreRepository>>(emptyList())
        private set

    var isReposLoading by mutableStateOf(false)
        private set

    /** Set while the repository of a module opened from the list is still being read. */
    var isDetailLoading by mutableStateOf(false)
        private set

    var selectedModule by mutableStateOf<RepoModule?>(null)
        private set

    private var allModules: List<RepoModule> = emptyList()
    private var loadedRepositoryUrl: String? = null

    private val bundledRepositories = listOf(
        ExploreRepository(
            name = apApp.getString(R.string.online_module_default_repo),
            url = ModuleRepoDefaults.GmrRepositoryUrl,
            description = apApp.getString(R.string.online_module_default_repo_summary),
        )
    )

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        modules = filterModules(allModules, query) { "${it.name}\n${it.description}" }
    }

    fun resetModules() {
        modules = emptyList()
        allModules = emptyList()
        loadedRepositoryUrl = null
        errorMessage = null
    }

    fun fetchModules(repositoryUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            errorMessage = null
            val parsed = loadModules(repositoryUrl)
            if (parsed != null) {
                allModules = parsed
                loadedRepositoryUrl = repositoryUrl
                onSearchQueryChange(searchQuery)
            }
            isRefreshing = false
        }
    }

    /**
     * Detail pages are opened by id, and a page can be restored long after the list that opened it
     * is gone, so the repository is read again when it is not the one already in memory. The client
     * caches the response, so this normally costs nothing.
     */
    fun selectModule(repositoryUrl: String, moduleId: String) {
        if (loadedRepositoryUrl == repositoryUrl && allModules.isNotEmpty()) {
            selectedModule = allModules.firstOrNull { it.id == moduleId }
            return
        }
        isDetailLoading = true
        // A page can be reused for another module, so the previous one must not linger on screen.
        if (selectedModule?.id != moduleId) selectedModule = null
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = loadModules(repositoryUrl)
            if (parsed != null) {
                allModules = parsed
                loadedRepositoryUrl = repositoryUrl
            }
            selectedModule = parsed?.firstOrNull { it.id == moduleId }
            isDetailLoading = false
        }
    }

    fun fetchRepositories() {
        viewModelScope.launch(Dispatchers.IO) {
            isReposLoading = true
            try {
                FolkApiClient.fetchJson(ModuleRepoDefaults.ClusterIndexUrl).fold(
                    onSuccess = { repositories = parseExploreRepositories(it, bundledRepositories) },
                    onFailure = { repositories = bundledRepositories },
                )
            } catch (e: Exception) {
                repositories = bundledRepositories
            } finally {
                isReposLoading = false
            }
        }
    }

    private suspend fun loadModules(repositoryUrl: String): List<RepoModule>? =
        try {
            FolkApiClient.fetchJson(resolveModulesUrl(repositoryUrl)).fold(
                onSuccess = { parseRepoModules(it, repositoryUrl) },
                onFailure = {
                    errorMessage = it.message ?: it.javaClass.simpleName
                    null
                },
            )
        } catch (e: Exception) {
            errorMessage = e.message ?: e.javaClass.simpleName
            null
        }
}
