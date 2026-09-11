package me.bmax.apatch.ui.webui

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiPackageApiTest {

    private val systemApp = packageSnapshot(
        packageName = "com.android.settings",
        appLabel = "Settings",
        isSystem = true,
        uid = 1000,
    )
    private val userApp = packageSnapshot(
        packageName = "com.example.app",
        appLabel = "Example",
        isSystem = false,
        uid = 10_123,
    )

    @Test
    fun listPackages_filtersSystemUserAndAllInSortedOrder() {
        val apps = listOf(userApp, systemApp)

        assertEquals(
            listOf("com.android.settings"),
            WebUiPackageApi.listPackages(apps, "system"),
        )
        assertEquals(
            listOf("com.example.app"),
            WebUiPackageApi.listPackages(apps, "user"),
        )
        assertEquals(
            listOf("com.android.settings", "com.example.app"),
            WebUiPackageApi.listPackages(apps, "all"),
        )
        assertEquals(
            listOf("com.android.settings", "com.example.app"),
            WebUiPackageApi.listPackages(apps, null),
        )
        assertEquals(
            listOf("com.android.settings", "com.example.app"),
            WebUiPackageApi.listPackages(apps, "unsupported"),
        )
    }

    @Test
    fun listPackages_treatsMissingApplicationInfoAsUserPackage() {
        val unresolvedApp = packageSnapshot(
            packageName = "com.example.unresolved",
            appLabel = "Unresolved",
            isSystem = null,
            uid = null,
        )

        assertEquals(
            listOf("com.example.unresolved"),
            WebUiPackageApi.listPackages(listOf(unresolvedApp), "user"),
        )
    }

    @Test
    fun getPackagesInfo_preservesRequestOrderAndMarksMissingPackages() {
        val result = WebUiPackageApi.getPackagesInfo(
            apps = listOf(systemApp, userApp),
            packageNames = listOf("com.example.app", "com.missing.app", "com.android.settings"),
        )

        assertEquals(
            listOf(
                WebUiPackageInfo.Found(userApp),
                WebUiPackageInfo.Missing("com.missing.app"),
                WebUiPackageInfo.Found(systemApp),
            ),
            result,
        )
    }

    @Test
    fun listPackagesJson_returnsStringArray() {
        val json = JSONArray(
            WebUiPackageApi.listPackagesJson(
                apps = listOf(userApp, systemApp),
                type = "all",
            ),
        )

        assertEquals(2, json.length())
        assertEquals("com.android.settings", json.getString(0))
        assertEquals("com.example.app", json.getString(1))
    }

    @Test
    fun getPackagesInfoJson_matchesKernelSuPackageInfoContract() {
        val result = JSONArray(
            WebUiPackageApi.getPackagesInfoJson(
                apps = listOf(
                    packageSnapshot(
                        packageName = "com.example.large",
                        versionName = null,
                        versionCode = 4_294_967_296L,
                        appLabel = "Large",
                        isSystem = false,
                        uid = 10_321,
                    ),
                ),
                packageNamesJson = """["com.example.large","com.missing"]""",
            ),
        )

        val found = result.getJSONObject(0)
        assertEquals("com.example.large", found.getString("packageName"))
        assertEquals("", found.getString("versionName"))
        assertEquals(4_294_967_296L, found.getLong("versionCode"))
        assertEquals("Large", found.getString("appLabel"))
        assertFalse(found.getBoolean("isSystem"))
        assertEquals(10_321, found.getInt("uid"))

        val missing = result.getJSONObject(1)
        assertEquals("com.missing", missing.getString("packageName"))
        assertEquals(WebUiPackageApi.MissingPackageError, missing.getString("error"))
        assertFalse(missing.has("uid"))
    }

    @Test
    fun getPackagesInfoJson_emitsNullForUnresolvedApplicationInfo() {
        val unresolvedApp = packageSnapshot(
            packageName = "com.example.unresolved",
            appLabel = "Unresolved",
            isSystem = null,
            uid = null,
        )

        val result = JSONArray(
            WebUiPackageApi.getPackagesInfoJson(
                apps = listOf(unresolvedApp),
                packageNamesJson = """["com.example.unresolved"]""",
            ),
        ).getJSONObject(0)

        assertTrue(result.isNull("isSystem"))
        assertTrue(result.isNull("uid"))
    }

    @Test
    fun malformedOrEmptyPackageInputReturnsEmptyJsonArray() {
        assertEquals("[]", WebUiPackageApi.getPackagesInfoJson(listOf(userApp), "not-json"))
        assertEquals("[]", WebUiPackageApi.getPackagesInfoJson(listOf(userApp), null))
        assertEquals("[]", WebUiPackageApi.getPackagesInfoJson(listOf(userApp), "  "))
        assertEquals(emptyList<String>(), WebUiPackageApi.parsePackageNames("{}"))
    }

    private fun packageSnapshot(
        packageName: String,
        versionName: String? = "1.0",
        versionCode: Long = 1L,
        appLabel: String,
        isSystem: Boolean?,
        uid: Int?,
    ): WebUiPackageSnapshot = WebUiPackageSnapshot(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        appLabel = appLabel,
        isSystem = isSystem,
        uid = uid,
    )
}
