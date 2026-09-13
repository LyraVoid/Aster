package me.bmax.apatch.ui.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRepoParsingTest {

    @Test
    fun `official index is parsed and unsafe urls are dropped`() {
        val json = """
            [
              {"name":"Alpha","version":"1.2","url":"https://example.com/a.zip","description":"甲"},
              {"name":"Beta","version":"2.0","url":"file:///etc/passwd","description":"乙"},
              {"name":"Gamma","version":"3.0","url":"https://example.com/g.zip",
               "description":"丙","description_en":"C"}
            ]
        """.trimIndent()

        val zh = parseOfficialModules(json, "zh")
        assertEquals(listOf("Alpha", "Gamma"), zh.map { it.name })
        assertEquals("丙", zh[1].description)

        val en = parseOfficialModules(json, "en")
        assertEquals("C", en[1].description)
    }

    @Test
    fun `official index tolerates missing optional fields`() {
        val json = """[{"name":"Alpha","version":"1.0","url":"https://example.com/a.zip"}]"""
        val modules = parseOfficialModules(json, "en")
        assertEquals(1, modules.size)
        assertEquals("", modules[0].description)
    }

    @Test
    fun `repository modules keep their links and resolve relative zips`() {
        val json = """
            {
              "modules": [
                {
                  "id": "alpha", "name": "Alpha", "version": "2.0", "versionCode": 20,
                  "author": "someone", "description": "does things",
                  "track": {
                    "license": "GPL-3.0", "homepage": "https://alpha.dev",
                    "source": "https://github.com/x/alpha", "support": "https://t.me/alpha"
                  },
                  "versions": [
                    {"version":"2.0","versionCode":20,"zipUrl":"https://cdn.example.com/2.0.zip","timestamp":1700000000},
                    {"version":"1.0","versionCode":10,"zipUrl":"1.0.zip"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val modules = parseRepoModules(json, "https://repo.example.com/base")
        assertEquals(1, modules.size)
        val module = modules[0]
        assertEquals("alpha", module.id)
        assertEquals("GPL-3.0", module.license)
        assertEquals("https://github.com/x/alpha", module.source)
        assertEquals(2, module.versions.size)
        assertEquals("https://cdn.example.com/2.0.zip", module.versions[0].zipUrl)
        assertEquals(
            "https://repo.example.com/base/1.0.zip",
            module.versions[1].zipUrl,
        )
    }

    @Test
    fun `links are read from the top level when the index keeps them there`() {
        val json = """
            {
              "modules": [
                {
                  "id": "bki", "name": "BKI", "version": "1.6.1", "author": "@T3SL4",
                  "support": "https://github.com/x/bki/issues",
                  "track": {"source": "https://github.com/x/bki", "type": "Onsite"},
                  "versions": [{"version":"1.6.1","zipUrl":"https://cdn.example.com/bki.zip"}]
                }
              ]
            }
        """.trimIndent()

        val module = parseRepoModules(json, "https://gr.example.com/gmr/").single()
        assertEquals("https://github.com/x/bki", module.source)
        assertEquals("https://github.com/x/bki/issues", module.support)
        assertEquals("", module.license)
    }

    @Test
    fun `repository modules survive a missing track block`() {
        val json = """{"modules":[{"id":"a","name":"A","version":"1.0","versions":[]}]}"""
        val module = parseRepoModules(json, "https://repo.example.com").single()
        assertEquals("", module.homepage)
        assertEquals("", module.support)
        assertTrue(module.versions.isEmpty())
        assertEquals(null, module.latestRelease)
    }

    @Test
    fun `cluster index keeps the bundled repository in front`() {
        val json = """
            [
              {"name":"Community","url":"https://c.example.com/","description":"many","modules_count":42},
              {"name":"Broken","modules_count":1}
            ]
        """.trimIndent()
        val defaults = listOf(
            ExploreRepository(name = "Default", url = ModuleRepoDefaults.GmrRepositoryUrl)
        )

        val repositories = parseExploreRepositories(json, defaults)
        assertEquals(listOf("Default", "Community"), repositories.map { it.name })
        assertEquals(42, repositories[1].modulesCount)
    }

    @Test
    fun `a repository the cluster also lists is not offered twice`() {
        val json = """
            [{"name":"Community","url":"${ModuleRepoDefaults.GmrRepositoryUrl}","modules_count":7}]
        """.trimIndent()
        val defaults = listOf(
            ExploreRepository(name = "Default", url = ModuleRepoDefaults.GmrRepositoryUrl)
        )

        val repositories = parseExploreRepositories(json, defaults)
        assertEquals(listOf("Community"), repositories.map { it.name })
        assertEquals(7, repositories.single().modulesCount)
    }

    @Test
    fun `repository url is completed with the index path`() {
        assertEquals(
            "https://example.com/repo/json/modules.json",
            resolveModulesUrl("https://example.com/repo/"),
        )
        assertEquals(
            "https://example.com/repo/json/modules.json",
            resolveModulesUrl("https://example.com/repo"),
        )
        assertEquals(
            "https://example.com/repo/index.json",
            resolveModulesUrl("https://example.com/repo/index.json"),
        )
    }

    @Test
    fun `only http repositories pass the dialog check`() {
        assertTrue(isSafeRepositoryUrl("https://example.com/"))
        assertTrue(isSafeRepositoryUrl("http://example.com/"))
        assertFalse(isSafeRepositoryUrl("example.com"))
        assertFalse(isSafeRepositoryUrl("file:///sdcard/repo"))
        assertFalse(isSafeRepositoryUrl(""))
    }

    @Test
    fun `unknown and missing source values fall back to the official index`() {
        assertEquals(ModuleSource.Official, ModuleSource.fromValue(null))
        assertEquals(ModuleSource.Official, ModuleSource.fromValue(""))
        assertEquals(ModuleSource.Official, ModuleSource.fromValue("nonsense"))
        assertEquals(ModuleSource.Cluster, ModuleSource.fromValue("cluster"))
        assertEquals(ModuleSource.Custom, ModuleSource.fromValue("custom"))
    }

    @Test
    fun `a missing release date is left out instead of rendered as 1970`() {
        assertEquals("", formatReleaseDate(0.0))
        assertEquals("", formatReleaseDate(-1.0))
        val formatted = formatReleaseDate(1700000000.0)
        assertEquals(10, formatted.length)
        assertTrue(formatted.startsWith("2023"))
    }

    @Test
    fun `search ignores case and trims the query`() {
        val modules = listOf("Alpha", "beta", "Gamma")
        assertEquals(modules, filterModules(modules, "  ", { it }))
        assertEquals(listOf("Alpha"), filterModules(modules, " alP ", { it }))
        assertTrue(filterModules(modules, "zzz", { it }).isEmpty())
    }

    @Test
    fun `the release the module calls current wins over the list order`() {
        // Repositories list versions oldest first, newest first, or by nothing at all.
        val newestLast = """
            {"modules":[{"id":"a","name":"A","version":"v3","versionCode":300,"versions":[
              {"version":"v1","versionCode":100,"zipUrl":"https://r/a/v1.zip"},
              {"version":"v3","versionCode":300,"zipUrl":"https://r/a/v3.zip"}]}]}
        """.trimIndent()
        assertEquals("v3", parseRepoModules(newestLast, "https://r/").single().latestRelease?.version)

        val newestFirst = """
            {"modules":[{"id":"a","name":"A","version":"v3","versionCode":300,"versions":[
              {"version":"v3","versionCode":300,"zipUrl":"https://r/a/v3.zip"},
              {"version":"v1","versionCode":100,"zipUrl":"https://r/a/v1.zip"}]}]}
        """.trimIndent()
        assertEquals("v3", parseRepoModules(newestFirst, "https://r/").single().latestRelease?.version)
    }

    @Test
    fun `an unknown current version still offers the highest one`() {
        val json = """
            {"modules":[{"id":"a","name":"A","version":"v9","versionCode":900,"versions":[
              {"version":"v1","versionCode":100,"zipUrl":"https://r/a/v1.zip"},
              {"version":"v2","versionCode":200,"zipUrl":""}]}]}
        """.trimIndent()
        val release = parseRepoModules(json, "https://r/").single().latestRelease
        assertEquals("v1", release?.version)
    }
}
