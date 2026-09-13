package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleSortPriorityStoreTest {

    @Test
    fun `a reader who never opened the picker gets every kind`() {
        assertEquals(ModuleSortPriorityStore.Default, ModuleSortPriorityStore.decode(null))
        assertEquals(ModuleSortPriorityGroups.toSet(), ModuleSortPriorityStore.Default)
    }

    @Test
    fun `unticking everything is remembered as unticking everything`() {
        // The stored value is an empty string, which is a choice, and must not be read back as
        // "never stored" — that would quietly tick everything again on the next launch.
        assertEquals(emptySet<ModuleSortGroup>(), ModuleSortPriorityStore.decode(""))
        assertEquals(emptySet<ModuleSortGroup>(), ModuleSortPriorityStore.decode(" , "))
        assertEquals("", ModuleSortPriorityStore.encode(emptySet()))
    }

    @Test
    fun `the stored value survives a round trip`() {
        val groups = setOf(ModuleSortGroup.LSPosed, ModuleSortGroup.ZygiskConsumer)

        assertEquals(groups, ModuleSortPriorityStore.decode(ModuleSortPriorityStore.encode(groups)))
    }

    @Test
    fun `the stored value is written in ranking order`() {
        assertEquals(
            "metamodule,webui",
            ModuleSortPriorityStore.encode(
                setOf(ModuleSortGroup.WebUi, ModuleSortGroup.MetaModule),
            ),
        )
    }

    @Test
    fun `a value holding nothing we know is not a reader's choice`() {
        // Some other version of this screen wrote it. All of them is the safer reading than none.
        assertEquals(
            ModuleSortPriorityStore.Default,
            ModuleSortPriorityStore.decode("quantum,metamodule2"),
        )
    }

    @Test
    fun `a value we only half know keeps the half we know`() {
        assertEquals(
            setOf(ModuleSortGroup.WebUi),
            ModuleSortPriorityStore.decode("webui,quantum"),
        )
    }

    @Test
    fun `the plain group is never stored`() {
        assertEquals("", ModuleSortPriorityStore.encode(setOf(ModuleSortGroup.Plain)))
    }
}
