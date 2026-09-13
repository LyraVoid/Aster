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
        isZygiskConsumer: Boolean = false,
    ) = ModuleSortFacts(
        id = id,
        name = name,
        metaModule = metaModule,
        hasWebUi = hasWebUi,
        hasActionScript = hasActionScript,
        isZygiskConsumer = isZygiskConsumer,
    )

    private fun sortedIds(vararg facts: ModuleSortFacts): List<String> =
        facts.toList().sortedWith(moduleSortComparator(collator)).map { it.id }

    private fun sortedIds(
        priorities: Set<ModuleSortGroup>,
        vararg facts: ModuleSortFacts,
    ): List<String> = facts.toList()
        .sortedWith(moduleSortComparator(collator, priorities))
        .map { it.id }

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

        // Published outside the list the other module managers keep, but a Zygisk implementation
        // all the same, and the one this phone runs into.
        assertEquals(true, isZygiskImplementation("onyxzygisk"))
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


    @Test
    fun `a zygisk module is ranked under the loader it uses`() {
        // It is not the loader, so it does not stand with it; it is a module that injects through
        // it, so it stands above everything that only ships a screen or a script.
        val ids = sortedIds(
            module("plain"),
            module("webui", hasWebUi = true),
            module("consumer", isZygiskConsumer = true),
        )

        assertEquals(listOf("consumer", "webui", "plain"), ids)
    }

    @Test
    fun `carrying more than one thing puts a module in its highest group`() {
        val ids = sortedIds(
            module("consumer", hasWebUi = true, hasActionScript = true, isZygiskConsumer = true),
            module("webui", hasWebUi = true, hasActionScript = true),
            module("action", hasActionScript = true),
        )

        assertEquals(listOf("consumer", "webui", "action"), ids)
    }

    @Test
    fun `the loader stands above the modules that use it`() {
        val ids = sortedIds(
            module("zygisknext", isZygiskConsumer = false),
            module("consumer", isZygiskConsumer = true),
        )

        assertEquals(listOf("zygisknext", "consumer"), ids)
    }

    @Test
    fun `an unticked kind falls in with everything else`() {
        val priorities: Set<ModuleSortGroup> =
            ModuleSortPriorityGroups.toSet() - ModuleSortGroup.WebUi

        val ids = sortedIds(
            priorities,
            module("beta", hasWebUi = true),
            module("alpha", hasWebUi = true),
            module("zygisknext"),
        )

        // The WebUI modules are no longer lifted, so they sort with the unticked remainder —
        // which is the alphabet, and the loader's own kind still comes first.
        assertEquals(listOf("zygisknext", "alpha", "beta"), ids)
    }

    @Test
    fun `ticking one kind leaves the others in the alphabet`() {
        val priorities = setOf(ModuleSortGroup.LSPosed)

        val ids = sortedIds(
            priorities,
            module("plain"),
            module("action", hasActionScript = true),
            module("webui", hasWebUi = true),
            module("lsposed", name = "LSPosed"),
        )

        assertEquals(listOf("lsposed", "action", "plain", "webui"), ids)
    }

    @Test
    fun `unticking everything is the alphabet`() {
        val ids = sortedIds(
            emptySet<ModuleSortGroup>(),
            module("charlie", hasWebUi = true),
            module("alpha", metaModule = true),
            module("bravo", hasActionScript = true),
        )

        assertEquals(listOf("alpha", "bravo", "charlie"), ids)
    }

    @Test
    fun `the plain group cannot be ticked`() {
        assertEquals(false, ModuleSortGroup.Plain in ModuleSortPriorityGroups)
        assertEquals(
            true,
            ModuleSortGroup.entries.all {
                it in ModuleSortPriorityGroups || it == ModuleSortGroup.Plain
            },
        )
    }
}
