package me.bmax.apatch.ui.shell

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPagerStateTest {

    private class FakePagerState(
        initialPage: Int = 0,
        override val pageCount: Int = 5,
    ) : PagerState(initialPage, 0f)

    @Test
    fun initialSelectedPageMatchesPagerStateCurrentPage() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope, Density(1f))

        assertEquals(0, state.selectedPage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun navigateBackReturnsFalseWhenAlreadyOnHomeAndBackStackEmpty() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope, Density(1f))

        val visible = listOf(PrimaryDestination.Home, PrimaryDestination.Settings)
        assertFalse(state.navigateBack(visible))
    }

    @Test
    fun animateToDestinationPushesCurrentToBackStack() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope, Density(1f))

        val visible = listOf(
            PrimaryDestination.Home,
            PrimaryDestination.KModule,
            PrimaryDestination.SuperUser,
            PrimaryDestination.AModule,
            PrimaryDestination.Settings,
        )

        // Navigate from Home to Settings
        state.animateToDestination(PrimaryDestination.Settings, visible)
        assertEquals(4, state.selectedPage)
        assertTrue(state.isNavigating)

        // Now navigate back
        val handled = state.navigateBack(visible)
        assertTrue(handled)
        assertEquals(0, state.selectedPage)
    }

    @Test
    fun withoutKernelPermissionsNoIntermediatePagesCanBeNavigatedTo() {
        val pagerState = FakePagerState(initialPage = 0, pageCount = 2)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope, Density(1f))

        val capabilities = AsterNavigationCapabilities(kernelPatchReady = false, androidPatchReady = false)
        val visible = visiblePrimaryDestinations(capabilities)

        // Only Home and Settings exist
        assertEquals(listOf(PrimaryDestination.Home, PrimaryDestination.Settings), visible)
        assertEquals(2, visible.size)

        // Attempting to navigate to KModule or AModule does nothing because they are not in visibleDestinations
        state.animateToDestination(PrimaryDestination.KModule, visible)
        assertEquals(0, state.selectedPage)

        state.animateToDestination(PrimaryDestination.AModule, visible)
        assertEquals(0, state.selectedPage)

        // Navigating to Settings goes straight to index 1 (distance 1, no intermediate pages)
        state.animateToDestination(PrimaryDestination.Settings, visible)
        assertEquals(1, state.selectedPage)
    }
}
