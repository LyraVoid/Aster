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
 */
internal object ModuleRepoPreferences {
    private const val SourceKey = "online_module_source"
    private const val RepositoryUrlKey = "custom_repo_url"

    fun source(): ModuleSource = ModuleSource.fromValue(
        APApplication.sharedPreferences.getString(SourceKey, null)
    )

    fun setSource(source: ModuleSource) {
        APApplication.sharedPreferences.edit { putString(SourceKey, source.value) }
    }

    fun repositoryUrl(): String = APApplication.sharedPreferences.getString(RepositoryUrlKey, "").orEmpty()

    fun setRepositoryUrl(url: String) {
        APApplication.sharedPreferences.edit { putString(RepositoryUrlKey, url) }
    }
}
