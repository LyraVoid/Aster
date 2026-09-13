package me.bmax.apatch.ui.module

import java.text.Collator

/**
 * What a module is, in the order the list should read. The pieces the rest of the system is built
 * on come first, so the list reads roughly like the boot order instead of the alphabet.
 */
internal enum class ModuleSortGroup {
    /** A module other modules hang off; a phone can only have one. */
    MetaModule,

    /** A Zygisk implementation, which brings the whole Zygisk family with it. */
    ZygiskImplementation,

    /** LSPosed, which a lot of people install an APatch setup for in the first place. */
    LSPosed,

    /** A module with a screen of its own, so it is more likely to be opened than read. */
    WebUi,

    /** A module with a script to run. */
    ActionScript,

    /** Everything else. */
    Plain,
}

/** The ids the Zygisk implementations are published under. */
private val ZygiskImplementationIds = setOf(
    "zygisksu",
    "zygisknext",
    "rezygisk",
    "neozygisk",
    "shirokozygisk",
)

internal fun isZygiskImplementation(id: String): Boolean =
    id.lowercase() in ZygiskImplementationIds

internal fun isLSPosed(name: String): Boolean = name.contains("LSPosed", ignoreCase = true)

/**
 * The facts the order is decided from. A module carries them; this file reads no disk.
 *
 * There is deliberately no `enabled` here. A module that is switched off keeps the place its kind
 * earns, because sinking it to the bottom would move it out from under the finger that just
 * toggled it — which is exactly when someone is looking for it.
 */
internal data class ModuleSortFacts(
    val id: String,
    val name: String,
    val metaModule: Boolean,
    val hasWebUi: Boolean,
    val hasActionScript: Boolean,
)

internal fun ModuleSortFacts.sortGroup(): ModuleSortGroup = when {
    metaModule -> ModuleSortGroup.MetaModule
    isZygiskImplementation(id) -> ModuleSortGroup.ZygiskImplementation
    isLSPosed(name) -> ModuleSortGroup.LSPosed
    hasWebUi -> ModuleSortGroup.WebUi
    hasActionScript -> ModuleSortGroup.ActionScript
    else -> ModuleSortGroup.Plain
}

/** Orders the list by [sortGroup], then by id in the reader's own alphabet. */
internal fun moduleSortComparator(collator: Collator): Comparator<ModuleSortFacts> =
    compareBy<ModuleSortFacts> { it.sortGroup().ordinal }
        .thenBy(collator) { it.id }
