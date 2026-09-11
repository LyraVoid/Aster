package me.bmax.apatch.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
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
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CompactNavigationBreakpoint = 600.dp

@Composable
fun AsterAppShell(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    capabilities: AsterNavigationCapabilities,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useCompactShell = maxWidth < CompactNavigationBreakpoint
        val railState = rememberNavigationRailState()

        LaunchedEffect(useCompactShell) {
            if (!useCompactShell) {
                railState.expand()
            }
        }

        BackHandler(enabled = useCompactShell && railState.isExpanded) {
            railState.collapse()
        }

        if (useCompactShell) {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(start = NavigationRailDefaults.MinWidth)

            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
                    content(contentModifier)
                }

                AnimatedVisibility(
                    visible = railState.isExpanded,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.windowDimming.copy(alpha = 0.32f))
                            .clickable { railState.collapse() }
                    )
                }

                AsterNavigationRail(
                    navController = navController,
                    capabilities = capabilities,
                    state = railState,
                    modifier = Modifier.align(Alignment.CenterStart),
                    collapseAfterNavigation = true,
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                AsterNavigationRail(
                    navController = navController,
                    capabilities = capabilities,
                    state = railState,
                    collapseAfterNavigation = false,
                )
                CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
                    content(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

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
                modifier = Modifier
                    .alpha(if (disabledReason == null) 1f else 0.42f)
                    .then(
                        if (disabledReason == null) {
                            Modifier
                        } else {
                            Modifier.semantics {
                                stateDescription = disabledReason
                            }
                        }
                    ),
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
