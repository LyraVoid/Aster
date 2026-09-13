package me.bmax.apatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.shell.LocalSceneActive
import me.bmax.apatch.ui.shell.LocalSceneProgress
import me.bmax.apatch.ui.shell.LocalSceneRailWidth
import me.bmax.apatch.ui.shell.LocalMainPagerState
import me.bmax.apatch.ui.shell.PrimaryDestination
import me.bmax.apatch.ui.shell.visiblePrimaryDestinations

@Destination<RootGraph>(start = true)
@Composable
fun MainScreen(navigator: DestinationsNavigator) {
    val sceneActive = LocalSceneActive.current
    val sceneRailWidth = LocalSceneRailWidth.current
    val sceneProgress = LocalSceneProgress.current
    val mainPagerState = LocalMainPagerState.current ?: return
    val capabilities = LocalAsterCapabilities.current
    val visibleDestinations = remember(capabilities) { visiblePrimaryDestinations(capabilities) }

    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    var currentDestination by remember { mutableStateOf(PrimaryDestination.Home) }

    LaunchedEffect(mainPagerState.selectedPage, visibleDestinations) {
        visibleDestinations.getOrNull(mainPagerState.selectedPage)?.let {
            currentDestination = it
        }
    }

    LaunchedEffect(visibleDestinations) {
        val targetIndex = visibleDestinations.indexOf(currentDestination)
        if (targetIndex >= 0 && targetIndex != mainPagerState.pagerState.currentPage) {
            mainPagerState.pagerState.scrollToPage(targetIndex)
            mainPagerState.syncPage()
        } else if (targetIndex < 0) {
            mainPagerState.pagerState.scrollToPage(0)
            mainPagerState.syncPage()
        }
    }

    val canBack = mainPagerState.selectedPage > 0
    BackHandler(enabled = canBack) {
        mainPagerState.navigateBack(visibleDestinations)
    }

    HorizontalPager(
        state = mainPagerState.pagerState,
        beyondViewportPageCount = (visibleDestinations.size - 1).coerceAtLeast(0),
        userScrollEnabled = true,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (visibleDestinations.getOrNull(page)) {
            PrimaryDestination.Home -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = if (sceneActive) sceneRailWidth * sceneProgress else 0.dp)
                ) {
                    HomeScreen(navigator)
                }
            }
            PrimaryDestination.KModule -> KPModuleScreen(navigator)
            PrimaryDestination.SuperUser -> SuperUserScreen(navigator)
            PrimaryDestination.AModule -> APModuleScreen(navigator)
            PrimaryDestination.Settings -> SettingScreen(navigator)
            null -> {}
        }
    }
}
