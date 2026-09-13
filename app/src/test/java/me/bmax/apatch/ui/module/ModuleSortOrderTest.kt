package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

class ModuleSortOrderTest {

    private val collator = Collator.getInstance(Locale.ROOT)

    private fun module(
        id: String,
        name: String = id,
        metaModule: Boolean = false,
        hasWebUi: Boolean = false,
        hasActionScript: Boolean = false,
    ) = ModuleSortFacts(
        id = id,
        name = name,
        metaModule = metaModule,
        hasWebUi = hasWebUi,
        hasActionScript = hasActionScript,
    )

    private fun sortedIds(vararg facts: ModuleSortFacts): List<String> =
        facts.toList().sortedWith(moduleSortComparator(collator)).map { it.id }

    @Test
    fun `the groups run from the system outwards`() {
        val ids = sortedIds(
            module("plain"),
            module("action", hasActionScript = true),
            module("webui", hasWebUi = true),
            module("lsposed", name = "LSPosed"),
            module("zygisknext"),
            module("metamodule", metaModule = true),
        )

        assertEquals(
            listOf("metamodule", "zygisknext", "lsposed", "webui", "action", "plain"),
            ids,
        )
    }

    @Test
    fun `every published zygisk implementation is recognised`() {
        val ids = listOf("zygisksu", "zygisknext", "rezygisk", "neozygisk", "shirokozygisk")

        ids.forEach { id ->
            assertEquals("$id should be a zygisk implementation", true, isZygiskImplementation(id))
        }
        assertEquals(true, isZygiskImplementation("ZygiskNext"))
        assertEquals(false, isZygiskImplementation("zygisk-helper"))
    }

    @Test
    fun `lsposed is found by name whatever it is capitalised like`() {
        assertEquals(true, isLSPosed("LSPosed"))
        assertEquals(true, isLSPosed("lsposed (fork)"))
        assertEquals(true, isLSPosed("LSPosed Manager"))
        assertEquals(false, isLSPosed("Sposedly not LSP osed"))
    }

    @Test
    fun `the group beats the alphabet`() {
        // "aaa" would be the first line of a plain alphabetical list; it is not, because what the
        // module is comes before what it is called.
        val ids = sortedIds(
            module("aaa"),
            module("zzz", hasWebUi = true),
        )

        assertEquals(listOf("zzz", "aaa"), ids)
    }

    @Test
    fun `modules of one group keep the alphabet among themselves`() {
        val ids = sortedIds(
            module("charlie", hasWebUi = true),
            module("alpha", hasWebUi = true),
            module("bravo", hasWebUi = true),
        )

        assertEquals(listOf("alpha", "bravo", "charlie"), ids)
    }

    @Test
    fun `a switched off module keeps the place its kind earns`() {
        // A disabled module is simply absent from the facts the order is built from, so a zygisk
        // implementation that has been switched off is still listed above a plain module that has
        // not. Sinking it to the bottom would move it out from under the finger that just toggled
        // it.
        val ids = sortedIds(
            module("plain"),
            module("zygisknext"),
        )

        assertEquals(listOf("zygisknext", "plain"), ids)
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<String>(), sortedIds())
    }
}
