package me.bmax.apatch.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class NavigationEntryPreferences(
    val showKpm: Boolean = true,
    val showSuperUser: Boolean = true,
    val showApm: Boolean = true,
) {
    fun shows(destination: PrimaryDestination): Boolean = when (destination) {
        PrimaryDestination.KModule -> showKpm
        PrimaryDestination.SuperUser -> showSuperUser
        PrimaryDestination.AModule -> showApm
        PrimaryDestination.Home, PrimaryDestination.Settings -> true
    }
}

@Composable
fun rememberNavigationEntryPreferences(): NavigationEntryPreferences {
    val kpm by rememberVisualFlag("show_nav_kpm", true)
    val superUser by rememberVisualFlag("show_nav_superuser", true)
    val apm by rememberVisualFlag("show_nav_apm", true)
    return NavigationEntryPreferences(kpm, superUser, apm)
}

// Every navigation surface and the pager must use the same ordered list.
val LocalPrimaryDestinations = staticCompositionLocalOf<State<List<PrimaryDestination>>> {
    mutableStateOf(listOf(PrimaryDestination.Home, PrimaryDestination.Settings))
}

/** Lazy layout can query an outgoing index while its cached item range is being replaced. */
internal fun primaryPageKey(destinations: List<PrimaryDestination>, index: Int): String =
    destinations.getOrNull(index)?.name ?: "removed-page:$index"

