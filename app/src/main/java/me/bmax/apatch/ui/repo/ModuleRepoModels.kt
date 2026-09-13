package me.bmax.apatch.ui.repo

/**
 * A module offered by the manager's own index. That index only carries what is needed to fetch a
 * zip, so there is no version history or readme to show.
 */
data class OnlineModule(
    val name: String,
    val version: String,
    val url: String,
    val description: String,
)

/** One module described by a Magisk style repository index. */
data class RepoModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val license: String = "",
    val homepage: String = "",
    val source: String = "",
    val support: String = "",
    val versions: List<RepoVersion> = emptyList(),
) {
    /**
     * The release the download buttons grab.
     *
     * Repositories order their history every which way, so the entry the module itself points at —
     * then the highest version code — decides which one counts as current.
     */
    val latestRelease: RepoVersion?
        get() {
            val downloadable = versions.filter { it.zipUrl.isNotBlank() }
            return downloadable.firstOrNull { it.versionCode == versionCode }
                ?: downloadable.maxByOrNull { it.versionCode }
        }
}

/** One downloadable release of a [RepoModule]. */
data class RepoVersion(
    val version: String,
    val versionCode: Long,
    val zipUrl: String,
    val changelog: String = "",
    val timestamp: Double = 0.0,
)

/** A repository the reader can browse, either bundled or listed by the cluster index. */
data class ExploreRepository(
    val name: String,
    val url: String,
    val description: String = "",
    val modulesCount: Int = 0,
    val cover: String = "",
)
