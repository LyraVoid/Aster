package me.bmax.apatch.ui.repo

import androidx.core.content.edit
import me.bmax.apatch.APApplication

/** Which index the module store is reading from. */
enum class ModuleSource(val value: String) {
    Official("official"),
    Cluster("cluster"),
    Custom("custom");

    companion object {
        val Default = Official

        /** Anything unrecognised falls back to the bundled index rather than an empty page. */
        fun fromValue(value: String?): ModuleSource =
            entries.firstOrNull { it.value == value } ?: Default
    }
}

/**
 * The store remembers what the reader last chose, so reopening it does not send them back to the
 * official index every time. The repository url is shared by the cluster and the custom source:
 * both end up as "the repository in use".
 *
 * The two stores keep separate answers. They are asked for different packages, so an address that
 * suits one of them means nothing to the other, and the manager's own modules keep the keys that
 * were in use before there was a second store.
 */
internal object ModuleRepoPreferences {
    private const val SourceKey = "online_module_source"
    private const val RepositoryUrlKey = "custom_repo_url"
    private const val KernelSourceKey = "online_kpm_source"
    private const val KernelRepositoryUrlKey = "custom_kpm_repo_url"

    fun source(forKernelModules: Boolean): ModuleSource {
        val key = if (forKernelModules) KernelSourceKey else SourceKey
        val stored = ModuleSource.fromValue(APApplication.sharedPreferences.getString(key, null))
        // Kernel modules have no cluster to pick from, so a leftover cluster choice falls back.
        return if (forKernelModules && stored == ModuleSource.Cluster) ModuleSource.Default else stored
    }

    fun setSource(forKernelModules: Boolean, source: ModuleSource) {
        val key = if (forKernelModules) KernelSourceKey else SourceKey
        APApplication.sharedPreferences.edit { putString(key, source.value) }
    }

    fun repositoryUrl(forKernelModules: Boolean): String {
        val key = if (forKernelModules) KernelRepositoryUrlKey else RepositoryUrlKey
        return APApplication.sharedPreferences.getString(key, "").orEmpty()
    }

    fun setRepositoryUrl(forKernelModules: Boolean, url: String) {
        val key = if (forKernelModules) KernelRepositoryUrlKey else RepositoryUrlKey
        APApplication.sharedPreferences.edit { putString(key, url) }
    }
}
