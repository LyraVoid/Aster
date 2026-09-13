package me.bmax.apatch.ui.shell

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.bmax.apatch.ui.component.FloatingBottomBar
import me.bmax.apatch.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.Icon
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.home.LocalHomeWallpaperViewModel
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
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
    val globalLayout by rememberGlobalLayout()
    val panorama = globalLayout == GlobalLayout.Panorama
    val wallpaperViewModel = LocalHomeWallpaperViewModel.current
    val wallpaperState = wallpaperViewModel?.let {
        it.uiState.collectAsStateWithLifecycle().value
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val entryPreferences = rememberNavigationEntryPreferences()
    val requestedDestinations = remember(capabilities, entryPreferences) {
        visiblePrimaryDestinations(capabilities, entryPreferences)
    }
    val visibleDestinationsState = remember { mutableStateOf(requestedDestinations) }
    var visibleDestinations by visibleDestinationsState
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { visibleDestinations.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mainPagerState = remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }

    LaunchedEffect(requestedDestinations) {
        if (visibleDestinations != requestedDestinations) {
            mainPagerState.updateDestinations(visibleDestinations, requestedDestinations)
            visibleDestinations = requestedDestinations
        }
    }

    val currentRoute = backStackEntry?.destination?.route
    val isMainScreen = currentRoute == MainScreenDestination.route || currentRoute == null
    val currentDestination = if (isMainScreen) {
        visibleDestinations.getOrNull(mainPagerState.selectedPage) ?: PrimaryDestination.Home
    } else {
        PrimaryDestination.entries.firstOrNull { it.direction.route == currentRoute }
    }
    val onHome = if (isMainScreen) {
        currentDestination == PrimaryDestination.Home
    } else {
        currentRoute == HomeScreenDestination.route
    }
    // Panorama mode owns its chrome: the wallpaper scene on Home, floating navigation everywhere
    // else. Standard mode keeps the classic shell untouched.
    val homeVisibility = if (isMainScreen) {
        homePageVisibility(pagerState.currentPage, pagerState.currentPageOffsetFraction)
    } else if (onHome) 1f else 0f
    val sceneActive = panorama && homeVisibility > 0.5f
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val darkTheme = me.bmax.apatch.ui.theme.LocalThemeModeState.current.isDark
    androidx.compose.runtime.DisposableEffect(activity, sceneActive, darkTheme) {
        val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.isAppearanceLightStatusBars = !sceneActive && !darkTheme
        controller?.isAppearanceLightNavigationBars = !sceneActive && !darkTheme
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val primaryPage = isMainScreen || visibleDestinations.any { it.direction.route == currentRoute }
    // Capabilities can arrive after the user is already on a page: sending them home beats leaving
    // a screen the navigation no longer offers.
    LaunchedEffect(capabilities, backStackEntry) {
        val route = backStackEntry?.destination?.route ?: return@LaunchedEffect
        val current = PrimaryDestination.entries.firstOrNull { it.direction.route == route }
            ?: return@LaunchedEffect
        if (!current.isVisible(capabilities)) {
            navController.navigate(MainScreenDestination.route) {
                popUpTo(MainScreenDestination.route) { saveState = false }
                launchSingleTop = true
            }
        }
    }

    var interaction by remember { mutableIntStateOf(0) }

    val onSelectDestination: (PrimaryDestination) -> Unit = { destination ->
        interaction++
        if (isMainScreen) {
            mainPagerState.animateToDestination(destination, visibleDestinations)
        } else {
            navController.popBackStack(MainScreenDestination.route, false)
            mainPagerState.animateToDestination(destination, visibleDestinations)
        }
    }

    val floatingPreferred by rememberVisualFlag("floating_navigation", false)
    val floatingShell = panorama || floatingPreferred
    val floatingHost = floatingShell && primaryPage
    val floatingSceneVisibility = if (panorama) 1f - homeVisibility else 1f
    val floatingNavigation = floatingHost && floatingSceneVisibility > 0f
    val blurEnabled by rememberVisualFlag("floating_blur", true)
    val glassEnabled by rememberVisualFlag("floating_glass", true)
    val autoHide by rememberVisualFlag("floating_auto_hide", false)
    val scrollHide by rememberVisualFlag("floating_scroll_hide", false)
    val sceneExpanded by rememberVisualFlag("scene_sidebar_expanded", true)
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(interaction, backStackEntry, floatingNavigation, autoHide, scrollHide) {
        hidden = false
        if (floatingNavigation && autoHide) { delay(3000); hidden = true }
    }
    val scrollConnection = remember(scrollHide) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (scrollHide && source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 2f) {
                    hidden = available.y < 0
                }
                return Offset.Zero
            }
        }
    }
    val backdrop = rememberLayerBackdrop()
    // The capsule is 64dp tall with a 12dp gap; screens already reserve system insets.
    val floatingNavigationHeight = 76.dp
    // YumeBox animates the space it reserves for its floating bar; snapping it would shove the page
    // up the instant we leave the home scene.
    val floatingHostInset by animateDpAsState(
        targetValue = if (floatingHost) floatingNavigationHeight else 0.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "floating_navigation_reserved_height",
    )
    // Scene chrome shares the pager's coordinate instead of starting a second animation as
    // soon as a distant target is selected. Returning from Settings keeps the bar until Home arrives.
    val reservedContentBottom = floatingHostInset * floatingSceneVisibility
    // The shell hosts the snackbar, so it has to stay clear of whatever chrome sits at the bottom:
    // the floating capsule, or the standard navigation bar. The scaffold below already adds the
    // system inset, so the standard bar only contributes the height it uses above that inset.
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var snackbarBottomPadding by remember { mutableStateOf(0.dp) }
    val safeDrawingBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    // Miuix renders overlays in the nearest Scaffold. Every page brings its own Scaffold, but those
    // sit below the floating navigation in this shell's draw order, so a dialog opened on a page was
    // drawn under the bar (and its scrim left the bar undimmed). A Scaffold at the shell root gives
    // overlays a host that is drawn above the navigation.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(bottom = snackbarBottomPadding),
            )
        },
    ) {
        // Page fades expose the shell; keep its background opaque and in sync with the app theme.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
        ) {
            val useBottomNavigation = navigationMode.usesBottomNavigation(maxWidth.value)
            val bottomBarVisible = !floatingShell && useBottomNavigation
            SideEffect {
                snackbarBottomPadding = when {
                    floatingNavigation -> reservedContentBottom
                    bottomBarVisible -> (bottomBarHeight - safeDrawingBottom).coerceAtLeast(0.dp)
                    else -> 0.dp
                }
            }
            val useCompactShell = maxWidth < CompactNavigationBreakpoint
            val railState = rememberNavigationRailState()
            val sceneRailWidth = (maxWidth * 0.25f - 28.dp).coerceIn(56.dp, 80.dp)
            val sceneProgress by animateFloatAsState(
                if (panorama && sceneExpanded && (isMainScreen || onHome)) 1f else 0f,
                tween(360, easing = FastOutSlowInEasing), label = "scene_sidebar",
            )
            val effectiveSceneProgress = sceneProgress * homeVisibility
            val homeSceneHost = remember { HomeSceneHostState() }

            LaunchedEffect(useBottomNavigation, useCompactShell, panorama) {
                if (panorama) return@LaunchedEffect
                if (useBottomNavigation || useCompactShell) railState.collapse() else railState.expand()
            }
            BackHandler(enabled = !floatingShell && !useBottomNavigation && useCompactShell && railState.isExpanded) {
                railState.collapse()
            }

            if (panorama && effectiveSceneProgress > 0.01f) {
                HomeSceneBackdrop(
                    state = wallpaperState,
                    railWidth = sceneRailWidth,
                    windowWidth = maxWidth,
                )
            }

            if (panorama && effectiveSceneProgress > 0.01f) {
                HomeSceneRail(
                    destinations = visibleDestinations,
                    currentDestination = currentDestination,
                    onSelectDestination = onSelectDestination,
                    onAppearance = { homeSceneHost.openAppearance?.invoke() },
                    modifier = Modifier.width(sceneRailWidth).fillMaxHeight().zIndex(1f).graphicsLayer {
                        alpha = effectiveSceneProgress
                        translationX = -size.width * (1f - effectiveSceneProgress)
                    })
            }
            // Keep the host in a stable slot; the scene decor sits behind the page.
            Column(Modifier.fillMaxSize().layerBackdrop(backdrop)
                .then(if (floatingNavigation) Modifier.nestedScroll(scrollConnection)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(false, PointerEventPass.Initial)
                            interaction++
                        }
                    } else Modifier)) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (!floatingShell && !useBottomNavigation && !useCompactShell) {
                        AsterNavigationRail(
                            destinations = visibleDestinations,
                            currentDestination = currentDestination,
                            state = railState,
                            onSelectDestination = onSelectDestination,
                            collapseAfterNavigation = false,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackbarHostState,
                            LocalFloatingNavigationInset provides reservedContentBottom,
                            LocalSceneProgress provides effectiveSceneProgress,
                            LocalSceneRailWidth provides sceneRailWidth,
                            LocalSceneActive provides (panorama && effectiveSceneProgress > 0.01f),
                            LocalHomeSceneHostState provides homeSceneHost,
                            LocalMainPagerState provides mainPagerState,
                            LocalPrimaryDestinations provides visibleDestinationsState,
                            LocalAsterCapabilities provides capabilities,
                        ) {
                            content(
                                Modifier.fillMaxSize()
                                    .padding(
                                        start = if (!floatingShell && !useBottomNavigation && useCompactShell) {
                                            NavigationRailDefaults.MinWidth
                                        } else {
                                            0.dp
                                        }
                                    )
                                    .then(
                                        if (!floatingShell && useBottomNavigation) Modifier.consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                        else Modifier
                                    )
                            )
                        }
                        NavigationScrim(
                            visible = !floatingShell && !useBottomNavigation && useCompactShell && railState.isExpanded,
                            onDismiss = { railState.collapse() },
                        )
                        if (!floatingShell && !useBottomNavigation && useCompactShell) {
                            AsterNavigationRail(
                                destinations = visibleDestinations,
                                currentDestination = currentDestination,
                                state = railState,
                                onSelectDestination = onSelectDestination,
                                modifier = Modifier.align(Alignment.CenterStart),
                                collapseAfterNavigation = true,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = bottomBarVisible,
                    modifier = Modifier.onSizeChanged {
                        bottomBarHeight = with(density) { it.height.toDp() }
                    },
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    AsterBottomNavigation(
                        destinations = visibleDestinations,
                        currentDestination = currentDestination,
                        onSelectDestination = onSelectDestination,
                    )
                }
            }

            AnimatedVisibility(visible = floatingHost && !hidden,
                modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                // Discard drag/indicator coordinates when the entry layout changes.
                if (floatingSceneVisibility > 0f) key(visibleDestinations) {
                    AsterFloatingNavigation(
                        destinations = visibleDestinations,
                        currentDestination = currentDestination,
                        backdrop = backdrop,
                        blurEnabled = blurEnabled && Build.VERSION.SDK_INT >= 31,
                        glassEnabled = blurEnabled && glassEnabled && Build.VERSION.SDK_INT >= 33,
                        onSelectDestination = onSelectDestination,
                        modifier = Modifier.graphicsLayer {
                            alpha = floatingSceneVisibility
                            translationY = size.height * (1f - floatingSceneVisibility)
                        }.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
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
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    onSelectDestination: (PrimaryDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination == destination
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectDestination(destination) },
                icon = destination.icon,
                label = stringResource(destination.label),
            )
        }
    }
}

@Composable
private fun AsterFloatingNavigation(
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    backdrop: Backdrop,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    onSelectDestination: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = destinations.indexOf(currentDestination).coerceAtLeast(0)
    FloatingBottomBar(
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth(),
        selectedIndex = { selected },
        onSelected = { index -> onSelectDestination(destinations[index]) },
        backdrop = backdrop, tabsCount = destinations.size,
        isBackdropBlurEnabled = blurEnabled, isLiquidGlassEnabled = glassEnabled,
    ) {
        destinations.forEachIndexed { index, destination ->
            FloatingBottomBarItem(onClick = { onSelectDestination(destination) }) {
                Icon(destination.icon, stringResource(destination.label), modifier = Modifier.size(24.dp))
                Text(stringResource(destination.label), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AsterNavigationRail(
    destinations: List<PrimaryDestination>,
    currentDestination: PrimaryDestination?,
    state: NavigationRailState,
    onSelectDestination: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
    collapseAfterNavigation: Boolean,
) {
    NavigationRail(
        modifier = modifier,
        state = state,
        minWidth = NavigationRailDefaults.MinWidth,
        expandedWidth = NavigationRailDefaults.ExpandedWidth,
        expandContentDescription = stringResource(R.string.navigation_expand),
        collapseContentDescription = stringResource(R.string.navigation_collapse),
    ) {
        destinations.forEach { destination ->
            val isCurrentDestination = currentDestination == destination

            NavigationRailItem(
                selected = isCurrentDestination,
                onClick = {
                    onSelectDestination(destination)
                    if (collapseAfterNavigation) state.collapse()
                },
                icon = destination.icon,
                label = stringResource(destination.label),
            )
        }
    }
}

/**
 * Whether [destination] is the page the user is looking at right now.
 *
 * Primary pages are kept on the back stack so their state can be restored, and Home is the anchor
 * that is never popped. "Is this route on the back stack" therefore reports Home as selected on
 * every page - and a selected navigation item swallows taps, which left Home highlighted and
 * unresponsive at the same time.
 */
@Composable
internal fun NavHostController.isCurrentPrimaryDestination(destination: PrimaryDestination): Boolean {
    val entry by currentBackStackEntryAsState()
    return entry?.destination?.route == destination.direction.route
}

internal fun navigatePrimary(
    navigator: DestinationsNavigator,
    destination: PrimaryDestination,
    isCurrentDestination: Boolean,
) {
    if (isCurrentDestination) return
    navigator.navigate(destination.direction) {
        // Keep Home as the anchor. Saving the graph itself also saves Home together with
        // the active page; restoring Home would then restore that page on top of it.
        popUpTo(HomeScreenDestination) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = destination != PrimaryDestination.Home
    }
}
