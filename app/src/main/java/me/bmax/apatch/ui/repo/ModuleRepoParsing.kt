package me.bmax.apatch.ui.repo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the module store looks by default.
 *
 * [OfficialModulesUrl] and [KernelModulesUrl] are the manager's own indexes; both answer an array
 * of modules in the active language, one per kind of module. [ClusterIndexUrl] lists community
 * repositories, each of which publishes a Magisk style `json/modules.json` under its own base url.
 */
object ModuleRepoDefaults {
    const val OfficialModulesUrl = "https://folk.mysqil.com/api/modules?type=apm"
    const val KernelModulesUrl = "https://folk.mysqil.com/api/modules?type=kpm"
    const val ClusterIndexUrl = "https://mmrl.dev/api/repositories.json"
    const val GmrRepositoryUrl = "https://gr.dergoogler.com/gmr/"
}

/**
 * Repositories are addressed by their base url, but the file itself is optional: a url that already
 * names a json file is used as is, otherwise the index is expected at `json/modules.json`.
 */
internal fun resolveModulesUrl(repositoryUrl: String): String {
    if (repositoryUrl.endsWith(".json")) return repositoryUrl
    val base = if (repositoryUrl.endsWith("/")) repositoryUrl else "$repositoryUrl/"
    return "${base}json/modules.json"
}

/** Only http(s) repositories are accepted, both in the dialog and while parsing an index. */
internal fun isSafeRepositoryUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

/**
 * Reads the manager's own index. Entries without an http(s) url are dropped rather than handed to
 * the downloader, and the description follows the language the index was asked for.
 */
internal fun parseOfficialModules(json: String, language: String): List<OnlineModule> {
    val array = JSONArray(json)
    val modules = ArrayList<OnlineModule>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val url = obj.text("url")
        if (!isSafeRepositoryUrl(url)) continue

        val description = if (language == "zh") {
            obj.text("description")
        } else {
            obj.text("description_en").ifEmpty { obj.text("description") }
        }

        modules.add(
            OnlineModule(
                name = obj.text("name"),
                version = obj.text("version"),
                url = url,
                description = description,
                needsParameter = obj.optInt("parameter", 0) == 1,
            )
        )
    }
    return modules
}

/** Reads a Magisk style index; relative zip urls are resolved against the repository it came from. */
internal fun parseRepoModules(json: String, repositoryUrl: String): List<RepoModule> {
    val root = JSONObject(json)
    val array = root.optJSONArray("modules") ?: JSONArray()
    val base = if (repositoryUrl.endsWith("/")) repositoryUrl else "$repositoryUrl/"

    val modules = ArrayList<RepoModule>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val track = obj.optJSONObject("track") ?: JSONObject()
        val versionsArray = obj.optJSONArray("versions") ?: JSONArray()
        val versions = ArrayList<RepoVersion>(versionsArray.length())
        for (j in 0 until versionsArray.length()) {
            val versionObj = versionsArray.optJSONObject(j) ?: continue
            val zipUrl = versionObj.text("zipUrl")
            versions.add(
                RepoVersion(
                    version = versionObj.text("version"),
                    versionCode = versionObj.optLong("versionCode", 0L),
                    zipUrl = if (zipUrl.isEmpty() || zipUrl.startsWith("http")) zipUrl else base + zipUrl,
                    changelog = versionObj.text("changelog"),
                    timestamp = versionObj.optDouble("timestamp", 0.0),
                )
            )
        }

        modules.add(
            RepoModule(
                id = obj.text("id"),
                name = obj.text("name"),
                version = obj.text("version"),
                versionCode = obj.optLong("versionCode", 0L),
                author = obj.text("author"),
                description = obj.text("description"),
                // Indexes disagree on where the links live: some nest them under track, some put
                // them at the top level, so both are read and the nested one wins.
                license = track.text("license").ifEmpty { obj.text("license") },
                homepage = track.text("homepage").ifEmpty { obj.text("homepage") },
                source = track.text("source").ifEmpty { obj.text("source") },
                support = track.text("support").ifEmpty { obj.text("support") },
                versions = versions,
            )
        )
    }
    return modules
}

/**
 * Reads the cluster index. The bundled default repository is kept in front and never duplicated,
 * so an empty or unreachable cluster still leaves the reader with something to browse.
 */
internal fun parseExploreRepositories(
    json: String,
    defaults: List<ExploreRepository>,
): List<ExploreRepository> {
    val array = JSONArray(json)
    val repositories = ArrayList<ExploreRepository>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val url = obj.text("url")
        if (url.isEmpty()) continue
        repositories.add(
            ExploreRepository(
                name = obj.text("name"),
                url = url,
                description = obj.text("description"),
                modulesCount = obj.optInt("modules_count", 0),
                cover = obj.text("cover"),
            )
        )
    }
    val known = repositories.mapTo(HashSet()) { it.url }
    return defaults.filter { it.url !in known } + repositories
}

/** Case insensitive search over the name and the description, which is what a reader scans. */
internal fun <T> filterModules(modules: List<T>, query: String, text: (T) -> String): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return modules
    return modules.filter { text(it).contains(trimmed, ignoreCase = true) }
}

/**
 * `optString` renders an explicit json null as the text "null" on Android but as "" elsewhere, so
 * every optional field goes through here instead.
 */
private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
