package me.bmax.apatch.ui.shell

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
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
        if (targetIndex !in 0 until pagerState.pageCount) return
        val previousJob = navJob
        navJob = null
        previousJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        // Install the job before starting it: cancellation of an older transition must never
        // reset the selection of a newer one, even on the immediate UI dispatcher.
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val myJob = coroutineContext[Job]
            try {
                pagerState.scroll {
                    with(pagerState) { updateTargetPage(targetIndex) }
                    // Capture the position after acquiring the scroll lock, including a gesture's
                    // fractional offset. animateScrollToPage may pre-jump on long distances.
                    animatePrimaryPageScroll(
                        currentPage = pagerState.currentPage,
                        currentOffset = pagerState.currentPageOffsetFraction,
                        targetPage = targetIndex,
                        pageStride = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing,
                    )
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    selectedPage = pagerState.currentPage
                    navJob = null
                }
            }
        }
        navJob = job
        job.start()
    }

    /** Remap by identity before applying a changed list, without animating across removed pages. */
    fun updateDestinations(previous: List<PrimaryDestination>, next: List<PrimaryDestination>) {
        val destination = previous.getOrNull(selectedPage) ?: PrimaryDestination.Home
        val target = next.indexOf(destination).coerceAtLeast(0)
        val previousJob = navJob
        navJob = null // The cancelled animation must not overwrite the remapped selection.
        previousJob?.cancel()
        isNavigating = false
        pageBackStack.removeAll { it !in next }
        selectedPage = target
        pagerState.requestScrollToPage(target)
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState?> { null }
