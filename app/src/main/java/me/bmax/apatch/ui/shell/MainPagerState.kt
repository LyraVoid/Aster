package me.bmax.apatch.ui.shell

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Stable
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val density: Density,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    private val pageBackStack = ArrayDeque<PrimaryDestination>()

    fun animateToDestination(
        target: PrimaryDestination,
        visibleDestinations: List<PrimaryDestination>,
    ) {
        val targetIndex = visibleDestinations.indexOf(target)
        if (targetIndex < 0) return
        val currentDest = visibleDestinations.getOrNull(selectedPage)
        if (targetIndex == selectedPage) return
        if (currentDest != null) {
            pageBackStack.addLast(currentDest)
        }
        doAnimateToPage(targetIndex)
    }

    fun animateToPage(targetIndex: Int, currentDest: PrimaryDestination? = null) {
        if (targetIndex == selectedPage) return
        if (currentDest != null) {
            pageBackStack.addLast(currentDest)
        }
        doAnimateToPage(targetIndex)
    }

    fun navigateBack(visibleDestinations: List<PrimaryDestination>): Boolean {
        while (pageBackStack.isNotEmpty()) {
            val prev = pageBackStack.removeLast()
            val targetIndex = visibleDestinations.indexOf(prev)
            if (targetIndex >= 0 && targetIndex != selectedPage) {
                doAnimateToPage(targetIndex)
                return true
            }
        }
        if (selectedPage != 0) {
            doAnimateToPage(0)
            return true
        }
        return false
    }

    private fun doAnimateToPage(targetIndex: Int) {
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
        val duration = 100 * distance + 100

        navJob = coroutineScope.launch {
            val myJob = coroutineContext[Job]
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = tween(easing = EaseInOut, durationMillis = duration)
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState?> { null }
