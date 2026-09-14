package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

class ModuleShortcutRequestTest {
    private fun resolve(
        scheme: String? = MODULE_SHORTCUT_SCHEME,
        host: String? = MODULE_SHORTCUT_HOST_ACTION,
        moduleId: String? = "zygisk_lsposed",
        token: String? = TOKEN,
        expectedToken: String = TOKEN,
    ) = resolveModuleShortcutRequest(scheme, host, moduleId, token, expectedToken)

    @Test fun acceptsTheTwoHostsTheManagerBuilds() {
        assertEquals(ModuleShortcutRequest.ExecuteAction("zygisk_lsposed"), resolve())
        assertEquals(ModuleShortcutRequest.OpenWebUi("zygisk_lsposed"), resolve(host = MODULE_SHORTCUT_HOST_WEBUI))
    }

    @Test fun rejectsAnythingThatIsNotOneOfTheManagersOwnShortcuts() {
        assertNull(resolve(scheme = "ksu"))
        assertNull(resolve(scheme = null))
        assertNull(resolve(host = "install"))
        assertNull(resolve(host = null))
        assertNull(resolve(moduleId = null))
        assertNull(resolve(moduleId = ""))
    }

    @Test fun requiresTheTokenThisInstallGenerated() {
        assertNull(resolve(token = null))
        assertNull(resolve(token = ""))
        assertNull(resolve(token = TOKEN.dropLast(1)))
        assertNull(resolve(token = TOKEN.uppercase()))
        // An install whose token could not be read must not accept every token instead.
        assertNull(resolve(expectedToken = ""))
    }

    @Test fun keepsForeignIdsAwayFromTheRootShell() {
        for (id in listOf("../etc", "a/b", "a;id", "a b", "a\nb", "$(id)", "`id`", "'", "..", ".", "模组")) {
            assertFalse(id, isModuleShortcutId(id))
            assertNull(id, resolve(moduleId = id))
        }
        for (id in listOf("zygisk_lsposed", "fix-signal-oneplus13T", "onx-1.2_3")) {
            assertTrue(id, isModuleShortcutId(id))
        }
        assertFalse(isModuleShortcutId("a".repeat(129)))
    }

    @Test fun spellsTheTokenOutAsHex() {
        val token = moduleShortcutToken(ByteArray(MODULE_SHORTCUT_TOKEN_BYTES) { 0xAB.toByte() })
        assertEquals(64, token.length)
        assertEquals(64, moduleShortcutToken(ByteArray(MODULE_SHORTCUT_TOKEN_BYTES)).length)
        assertEquals("ab", moduleShortcutToken(byteArrayOf(0xAB.toByte())))
        assertEquals("00", moduleShortcutToken(byteArrayOf(0)))
    }

    @Test fun handsThePendingRequestOverExactlyOnce() {
        ModuleShortcutRequests.consume()
        assertNull(ModuleShortcutRequests.pending.value)

        val request = ModuleShortcutRequest.ExecuteAction("zygisk_lsposed")
        ModuleShortcutRequests.publish(request)
        assertEquals(request, ModuleShortcutRequests.pending.value)

        ModuleShortcutRequests.consume()
        assertNull(ModuleShortcutRequests.pending.value)

        // The same shortcut can be tapped again: publish/consume must not conflate the two.
        ModuleShortcutRequests.publish(request)
        assertEquals(request, ModuleShortcutRequests.pending.value)
        ModuleShortcutRequests.consume()
    }

    @Test fun prefersTheIconOfTheKindThatWasAskedFor() {
        assertEquals("/a.png", defaultShortcutIconPath("/a.png", "/w.png", ModuleShortcutKind.Action))
        assertEquals("/w.png", defaultShortcutIconPath("/a.png", "/w.png", ModuleShortcutKind.WebUi))
        // A module with only one of the two icons still gets a picture, not the app icon.
        assertEquals("/w.png", defaultShortcutIconPath("", "/w.png", ModuleShortcutKind.Action))
        assertEquals("/a.png", defaultShortcutIconPath("/a.png", "", ModuleShortcutKind.WebUi))
        assertEquals("/a.png", defaultShortcutIconPath("  /a.png  ", "", ModuleShortcutKind.Action))
        assertEquals("", defaultShortcutIconPath("", "", ModuleShortcutKind.Action))
        assertEquals("", defaultShortcutIconPath("   ", "  ", ModuleShortcutKind.WebUi))
    }
}
