package me.bmax.apatch.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CompactNavigationBreakpoint = CompactNavigationWidthDp.dp

@Composable
fun AsterAppShell(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    capabilities: AsterNavigationCapabilities,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val navigationMode by rememberNavigationMode()
    // Page fades expose the shell; keep its background opaque and in sync with the app theme.
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
    ) {
        val useBottomNavigation = navigationMode.usesBottomNavigation(maxWidth.value)
        val useCompactShell = maxWidth < CompactNavigationBreakpoint
        val railState = rememberNavigationRailState()

        LaunchedEffect(useBottomNavigation, useCompactShell) {
            if (useBottomNavigation || useCompactShell) railState.collapse() else railState.expand()
        }
        BackHandler(enabled = !useBottomNavigation && useCompactShell && railState.isExpanded) {
            railState.collapse()
        }

        // Keep the NavHost at one composition location across layout changes.
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!useBottomNavigation && !useCompactShell) {
                    AsterNavigationRail(navController, capabilities, railState, collapseAfterNavigation = false)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
                        content(
                            Modifier.fillMaxSize()
                                .padding(start = if (!useBottomNavigation && useCompactShell) NavigationRailDefaults.MinWidth else 0.dp)
                                .then(
                                    if (useBottomNavigation) Modifier.consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                    else Modifier
                                )
                        )
                    }
                    NavigationScrim(
                        visible = !useBottomNavigation && useCompactShell && railState.isExpanded,
                        onDismiss = { railState.collapse() },
                    )
                    if (!useBottomNavigation && useCompactShell) {
                        AsterNavigationRail(
                            navController, capabilities, railState,
                            modifier = Modifier.align(Alignment.CenterStart),
                            collapseAfterNavigation = true,
                        )
                    }
                }
            }
            if (useBottomNavigation) {
                AsterBottomNavigation(navController, capabilities)
            }
        }
    }
}

@Composable
private fun NavigationScrim(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(MiuixTheme.colorScheme.windowDimming.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun AsterBottomNavigation(
    navController: NavHostController,
    capabilities: AsterNavigationCapabilities,
) {
    val navigator = navController.rememberDestinationsNavigator()
    NavigationBar(
        modifier = Modifier.background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        PrimaryDestination.entries.forEach { destination ->
            val selected by navController.isRouteOnBackStackAsState(destination.direction)
            val disabledReason = navigationDisabledReason(destination, capabilities)
            NavigationBarItem(
                selected = selected,
                onClick = { navigatePrimary(navigator, destination, selected) },
                icon = destination.icon,
                label = stringResource(destination.label),
                enabled = disabledReason == null,
                badge = if (disabledReason == null) null else {
                    { Badge { Text("!") } }
                },
                modifier = navigationItemModifier(disabledReason),
            )
        }
    }
}

private fun navigationItemModifier(disabledReason: String?): Modifier = Modifier
    .alpha(if (disabledReason == null) 1f else 0.42f)
    .then(
        if (disabledReason == null) Modifier else Modifier.semantics {
            stateDescription = disabledReason
        }
    )

@Composable
private fun AsterNavigationRail(
    navController: NavHostController,
    capabilities: AsterNavigationCapabilities,
    state: NavigationRailState,
    modifier: Modifier = Modifier,
    collapseAfterNavigation: Boolean,
) {
    val navigator = navController.rememberDestinationsNavigator()

    NavigationRail(
        modifier = modifier,
        state = state,
        minWidth = NavigationRailDefaults.MinWidth,
        expandedWidth = NavigationRailDefaults.ExpandedWidth,
        expandContentDescription = stringResource(R.string.navigation_expand),
        collapseContentDescription = stringResource(R.string.navigation_collapse),
    ) {
        PrimaryDestination.entries.forEach { destination ->
            val isCurrentDestination by navController.isRouteOnBackStackAsState(destination.direction)
            val disabledReason = navigationDisabledReason(destination, capabilities)

            NavigationRailItem(
                selected = isCurrentDestination,
                onClick = {
                    navigatePrimary(
                        navigator = navigator,
                        destination = destination,
                        isCurrentDestination = isCurrentDestination,
                    )
                    if (collapseAfterNavigation) {
                        state.collapse()
                    }
                },
                icon = destination.icon,
                label = stringResource(destination.label),
                enabled = disabledReason == null,
                badge = if (disabledReason == null) {
                    null
                } else {
                    {
                        Badge {
                            Text("!")
                        }
                    }
                },
                modifier = navigationItemModifier(disabledReason),
            )
        }
    }
}

@Composable
private fun navigationDisabledReason(
    destination: PrimaryDestination,
    capabilities: AsterNavigationCapabilities,
): String? = when {
    destination.kernelPatchRequired && !capabilities.kernelPatchChecked ->
        stringResource(R.string.navigation_checking_root)

    destination.kernelPatchRequired && !capabilities.kernelPatchReady ->
        stringResource(R.string.navigation_kernel_patch_required)

    destination.androidPatchRequired && !capabilities.androidPatchReady ->
        stringResource(R.string.navigation_android_patch_required)

    else -> null
}

private fun navigatePrimary(
    navigator: DestinationsNavigator,
    destination: PrimaryDestination,
    isCurrentDestination: Boolean,
) {
    if (isCurrentDestination) {
        navigator.popBackStack(destination.direction, false)
    }
    navigator.navigate(destination.direction) {
        popUpTo(NavGraphs.root) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
