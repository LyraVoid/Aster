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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
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
    val onHome = backStackEntry?.destination?.route == HomeScreenDestination.route
    // Panorama mode owns its chrome: the wallpaper scene on Home, floating navigation everywhere
    // else. Standard mode keeps the classic shell untouched.
    val sceneActive = panorama && onHome
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
    val primaryPage = PrimaryDestination.entries.any { it.direction.route == backStackEntry?.destination?.route }
    val visibleDestinations = visiblePrimaryDestinations(capabilities)
    // Capabilities can arrive after the user is already on a page: sending them home beats leaving
    // a screen the navigation no longer offers.
    LaunchedEffect(visibleDestinations, backStackEntry) {
        val route = backStackEntry?.destination?.route ?: return@LaunchedEffect
        val current = PrimaryDestination.entries.firstOrNull { it.direction.route == route }
            ?: return@LaunchedEffect
        if (current !in visibleDestinations) {
            navController.navigate(PrimaryDestination.Home.direction) {
                popUpTo(HomeScreenDestination) { saveState = false }
                launchSingleTop = true
            }
        }
    }
    val floatingPreferred by rememberVisualFlag("floating_navigation", false)
    val floatingShell = panorama || floatingPreferred
    val floatingNavigation = floatingShell && primaryPage && !sceneActive
    val blurEnabled by rememberVisualFlag("floating_blur", true)
    val glassEnabled by rememberVisualFlag("floating_glass", true)
    val autoHide by rememberVisualFlag("floating_auto_hide", false)
    val scrollHide by rememberVisualFlag("floating_scroll_hide", false)
    val sceneExpanded by rememberVisualFlag("scene_sidebar_expanded", true)
    var interaction by remember { mutableIntStateOf(0) }
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
    val reservedContentBottom by animateDpAsState(
        targetValue = if (floatingNavigation) floatingNavigationHeight else 0.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "floating_navigation_reserved_height",
    )
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
            val density = LocalDensity.current
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
                if (sceneActive && sceneExpanded) 1f else 0f,
                tween(360, easing = FastOutSlowInEasing), label = "scene_sidebar",
            )
            val homeSceneHost = remember { HomeSceneHostState() }

            LaunchedEffect(useBottomNavigation, useCompactShell, panorama) {
                if (panorama) return@LaunchedEffect
                if (useBottomNavigation || useCompactShell) railState.collapse() else railState.expand()
            }
            BackHandler(enabled = !floatingShell && !useBottomNavigation && useCompactShell && railState.isExpanded) {
                railState.collapse()
            }

            if (sceneActive) {
                HomeSceneBackdrop(
                    state = wallpaperState,
                    railWidth = sceneRailWidth,
                    windowWidth = maxWidth,
                )
            }

            if (sceneActive && sceneProgress > 0.01f) {
                HomeSceneRail(navController, capabilities,
                    onAppearance = { homeSceneHost.openAppearance?.invoke() },
                    modifier = Modifier.width(sceneRailWidth).fillMaxHeight().zIndex(1f).graphicsLayer {
                        alpha = sceneProgress
                        translationX = -size.width * (1f - sceneProgress)
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
                        AsterNavigationRail(navController, capabilities, railState, collapseAfterNavigation = false)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackbarHostState,
                            LocalFloatingNavigationInset provides reservedContentBottom,
                            LocalSceneProgress provides sceneProgress,
                            LocalHomeSceneHostState provides homeSceneHost,
                            LocalAsterCapabilities provides capabilities,
                        ) {
                            content(
                                Modifier.fillMaxSize()
                                    .padding(
                                        start = if (sceneActive) sceneRailWidth * sceneProgress else if (!floatingShell && !useBottomNavigation && useCompactShell) {
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
                                navController, capabilities, railState,
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
                    AsterBottomNavigation(navController, visibleDestinations)
                }
            }

            AnimatedVisibility(visible = floatingNavigation && !hidden,
                modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                AsterFloatingNavigation(
                    navController = navController,
                    destinations = visibleDestinations,
                    backdrop = backdrop,
                    blurEnabled = blurEnabled && Build.VERSION.SDK_INT >= 31,
                    glassEnabled = blurEnabled && glassEnabled && Build.VERSION.SDK_INT >= 33,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(horizontal = 12.dp, vertical = 12.dp),
                )
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
    destinations: List<PrimaryDestination>,
) {
    val navigator = navController.rememberDestinationsNavigator()
    NavigationBar(
        modifier = Modifier.background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        destinations.forEach { destination ->
            val selected = navController.isCurrentPrimaryDestination(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { navigatePrimary(navigator, destination, selected) },
                icon = destination.icon,
                label = stringResource(destination.label),
            )
        }
    }
}

@Composable
private fun AsterFloatingNavigation(
    navController: NavHostController,
    destinations: List<PrimaryDestination>,
    backdrop: Backdrop,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator = navController.rememberDestinationsNavigator()
    val entry by navController.currentBackStackEntryAsState()
    val selected = destinations.indexOfFirst { it.direction.route == entry?.destination?.route }
        .coerceAtLeast(0)
    val select: (Int) -> Unit = { index ->
        navigatePrimary(navigator, destinations[index], selected == index)
    }
    FloatingBottomBar(
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth(),
        selectedIndex = { selected }, onSelected = select,
        backdrop = backdrop, tabsCount = destinations.size,
        isBackdropBlurEnabled = blurEnabled, isLiquidGlassEnabled = glassEnabled,
    ) {
        destinations.forEachIndexed { index, destination ->
            FloatingBottomBarItem(onClick = { select(index) }) {
                Icon(destination.icon, stringResource(destination.label), modifier = Modifier.size(24.dp))
                Text(stringResource(destination.label), fontSize = 10.sp, maxLines = 1)
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
        visiblePrimaryDestinations(capabilities).forEach { destination ->
            val isCurrentDestination = navController.isCurrentPrimaryDestination(destination)

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
