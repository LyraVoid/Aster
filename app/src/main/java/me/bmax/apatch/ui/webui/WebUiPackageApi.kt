package me.bmax.apatch.ui.webui

import org.json.JSONArray
import org.json.JSONObject

internal data class WebUiPackageSnapshot(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val appLabel: String,
    val isSystem: Boolean?,
    val uid: Int?,
)

internal sealed interface WebUiPackageInfo {
    data class Found(val app: WebUiPackageSnapshot) : WebUiPackageInfo

    data class Missing(val packageName: String) : WebUiPackageInfo
}

internal object WebUiPackageApi {
    const val MissingPackageError = "Package not found or inaccessible"

    fun listPackages(
        apps: List<WebUiPackageSnapshot>,
        type: String?,
    ): List<String> = apps
        .filter { app ->
            when (type?.lowercase()) {
                "system" -> app.isSystem == true
                "user" -> app.isSystem != true
                else -> true
            }
        }
        .map { it.packageName }
        .sorted()

    fun getPackagesInfo(
        apps: List<WebUiPackageSnapshot>,
        packageNames: List<String>,
    ): List<WebUiPackageInfo> {
        val appMap = apps.associateBy { it.packageName }
        return packageNames.map { packageName ->
            appMap[packageName]
                ?.let(WebUiPackageInfo::Found)
                ?: WebUiPackageInfo.Missing(packageName)
        }
    }

    fun listPackagesJson(
        apps: List<WebUiPackageSnapshot>,
        type: String?,
    ): String = JSONArray(listPackages(apps, type)).toString()

    fun getPackagesInfoJson(
        apps: List<WebUiPackageSnapshot>,
        packageNamesJson: String?,
    ): String {
        val packageNames = parsePackageNames(packageNamesJson)
        val jsonArray = JSONArray()

        getPackagesInfo(apps, packageNames).forEach { packageInfo ->
            when (packageInfo) {
                is WebUiPackageInfo.Found -> jsonArray.put(packageInfo.app.toJson())
                is WebUiPackageInfo.Missing -> jsonArray.put(
                    JSONObject()
                        .put("packageName", packageInfo.packageName)
                        .put("error", MissingPackageError),
                )
            }
        }

        return jsonArray.toString()
    }

    fun parsePackageNames(packageNamesJson: String?): List<String> {
        if (packageNamesJson.isNullOrBlank()) return emptyList()

        return runCatching {
            val jsonArray = JSONArray(packageNamesJson)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun WebUiPackageSnapshot.toJson(): JSONObject = JSONObject()
        .put("packageName", packageName)
        .put("versionName", versionName ?: "")
        .put("versionCode", versionCode)
        .put("appLabel", appLabel)
        .put("isSystem", isSystem ?: JSONObject.NULL)
        .put("uid", uid ?: JSONObject.NULL)
}
