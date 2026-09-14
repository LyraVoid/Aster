package me.bmax.apatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.ui.shell.LocalSceneActive
import me.bmax.apatch.ui.intake.ExternalFileIntake
import me.bmax.apatch.ui.shell.LocalSceneProgress
import me.bmax.apatch.ui.shell.LocalSceneRailWidth
import me.bmax.apatch.ui.shell.LocalMainPagerState
import me.bmax.apatch.ui.shell.PrimaryDestination
import me.bmax.apatch.ui.shell.LocalPrimaryDestinations
import me.bmax.apatch.ui.shell.primaryPageKey

@Destination<RootGraph>(start = true)
@Composable
fun MainScreen(navigator: DestinationsNavigator) {
    ExternalFileIntake(navigator)

    val sceneActive = LocalSceneActive.current
    val sceneRailWidth = LocalSceneRailWidth.current
    val sceneProgress = LocalSceneProgress.current
    val mainPagerState = LocalMainPagerState.current ?: return
    val destinationsState = LocalPrimaryDestinations.current
    val visibleDestinations = destinationsState.value

    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val canBack = mainPagerState.selectedPage > 0
    BackHandler(enabled = canBack) {
        mainPagerState.navigateBack(visibleDestinations)
    }

    HorizontalPager(
        state = mainPagerState.pagerState,
        // Read the same snapshot state as pageCount, not a list captured by an older composition.
        key = { primaryPageKey(destinationsState.value, it) },
        beyondViewportPageCount = (visibleDestinations.size - 1).coerceAtLeast(0),
        userScrollEnabled = true,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (destinationsState.value.getOrNull(page)) {
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
