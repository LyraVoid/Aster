package me.bmax.apatch.ui.module

import androidx.annotation.StringRes
import java.text.Collator
import me.bmax.apatch.R

/**
 * What a module is, in the order the list should read. The pieces the rest of the system is built
 * on come first, so the list reads roughly like the boot order instead of the alphabet.
 *
 * The stored string of each group is what the priority picker writes, so a value written by a
 * newer version is ignored rather than breaking the order.
 */
internal enum class ModuleSortGroup(
    val value: String,
    @param:StringRes val label: Int,
    @param:StringRes val summary: Int,
) {
    /** A module other modules hang off; a phone can only have one. */
    MetaModule(
        "metamodule",
        R.string.apm_sort_group_metamodule,
        R.string.apm_sort_group_metamodule_summary,
    ),

    /** A Zygisk implementation, which brings the whole Zygisk family with it. */
    ZygiskImplementation(
        "zygisk",
        R.string.apm_sort_group_zygisk,
        R.string.apm_sort_group_zygisk_summary,
    ),

    /** LSPosed, which a lot of people install an APatch setup for in the first place. */
    LSPosed(
        "lsposed",
        R.string.apm_sort_group_lsposed,
        R.string.apm_sort_group_lsposed_summary,
    ),

    /** A module that injects through the Zygisk loader without providing it. */
    ZygiskConsumer(
        "zygisk_module",
        R.string.apm_sort_group_zygisk_module,
        R.string.apm_sort_group_zygisk_module_summary,
    ),

    /** A module with a screen of its own, so it is more likely to be opened than read. */
    WebUi(
        "webui",
        R.string.apm_sort_group_webui,
        R.string.apm_sort_group_webui_summary,
    ),

    /** A module with a script to run. */
    ActionScript(
        "action",
        R.string.apm_sort_group_action,
        R.string.apm_sort_group_action_summary,
    ),

    /** Everything else, which cannot be ticked because there is nothing to rank about it. */
    Plain(
        "plain",
        R.string.apm_sort_group_plain,
        R.string.apm_sort_group_plain_summary,
    ),
}

/**
 * The groups a reader can ask to see first, in the order they are ranked while all of them are
 * ticked. [ModuleSortGroup.Plain] is absent: it is what is left over, not something to rank.
 */
internal val ModuleSortPriorityGroups: List<ModuleSortGroup> = listOf(
    ModuleSortGroup.MetaModule,
    ModuleSortGroup.ZygiskImplementation,
    ModuleSortGroup.LSPosed,
    ModuleSortGroup.ZygiskConsumer,
    ModuleSortGroup.WebUi,
    ModuleSortGroup.ActionScript,
)

/** The ids the Zygisk implementations are published under. */
private val ZygiskImplementationIds = setOf(
    "zygisksu",
    "zygisknext",
    "rezygisk",
    "neozygisk",
    "shirokozygisk",

    // Not in the list the other module managers ship, but it is one: it says so in its own
    // description, and it loads the modules under it the same way.
    "onyxzygisk",
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
    val isZygiskConsumer: Boolean = false,
)

internal fun ModuleSortFacts.sortGroup(): ModuleSortGroup = when {
    metaModule -> ModuleSortGroup.MetaModule
    isZygiskImplementation(id) -> ModuleSortGroup.ZygiskImplementation
    isLSPosed(name) -> ModuleSortGroup.LSPosed
    isZygiskConsumer -> ModuleSortGroup.ZygiskConsumer
    hasWebUi -> ModuleSortGroup.WebUi
    hasActionScript -> ModuleSortGroup.ActionScript
    else -> ModuleSortGroup.Plain
}

/**
 * Orders the list by the ticked groups in [priorities], then by id in the reader's own alphabet.
 * A group that was not ticked does not disappear; its modules fall in with everything else below
 * the ticked ones, still in the alphabet among themselves.
 */
internal fun moduleSortComparator(
    collator: Collator,
    priorities: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet(),
): Comparator<ModuleSortFacts> {
    val ranking = ModuleSortPriorityGroups.filter { it in priorities }
    return compareBy<ModuleSortFacts> { facts ->
        ranking.indexOf(facts.sortGroup()).takeIf { it >= 0 } ?: ranking.size
    }.thenBy(collator) { it.id }
}

/**
 * Reads and writes the ticked groups. Both ends are pure so the stored shape stays testable
 * without a device: an empty string is a reader who unticked everything, while no stored value
 * at all is a reader who has never opened the picker, and gets all of them.
 */
internal object ModuleSortPriorityStore {
    const val Key = "apm_sort_groups"

    val Default: Set<ModuleSortGroup> = ModuleSortPriorityGroups.toSet()

    fun encode(groups: Set<ModuleSortGroup>): String =
        ModuleSortPriorityGroups.filter { it in groups }.joinToString(",") { it.value }

    fun decode(stored: String?): Set<ModuleSortGroup> {
        if (stored == null) return Default

        val tokens = stored.split(',').map(String::trim).filter(String::isNotEmpty)
        val known = tokens.mapNotNullTo(mutableSetOf()) { token ->
            ModuleSortPriorityGroups.firstOrNull { it.value == token }
        }

        // A value holding nothing we know is not a reader's choice; it is a preference from some
        // other version of this screen, and all of them is the safer reading.
        return if (known.isEmpty() && tokens.isNotEmpty()) Default else known
    }
}
