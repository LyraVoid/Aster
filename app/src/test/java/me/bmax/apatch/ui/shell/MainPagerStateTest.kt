package me.bmax.apatch.ui.shell

import androidx.compose.foundation.pager.PagerState
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
        initialOffset: Float = 0f,
    ) : PagerState(initialPage, initialOffset)

    @Test
    fun initialSelectedPageMatchesPagerStateCurrentPage() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope)

        assertEquals(0, state.selectedPage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun navigateBackReturnsFalseWhenAlreadyOnHomeAndBackStackEmpty() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope)

        val visible = listOf(PrimaryDestination.Home, PrimaryDestination.Settings)
        assertFalse(state.navigateBack(visible))
    }

    @Test
    fun animateToDestinationPushesCurrentToBackStack() {
        val pagerState = FakePagerState(initialPage = 0)
        val testScope = CoroutineScope(Job())
        val state = MainPagerState(pagerState, testScope)

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
        val state = MainPagerState(pagerState, testScope)

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
    @Test
    fun hidingEntriesBeforeSettingsKeepsSettingsSelected() {
        val pager = FakePagerState(initialPage = 4)
        val state = MainPagerState(pager, CoroutineScope(Job()))
        val next = listOf(PrimaryDestination.Home, PrimaryDestination.Settings)

        state.updateDestinations(PrimaryDestination.entries, next)

        assertEquals(1, state.selectedPage)
        assertEquals(1, pager.currentPage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun hidingCurrentEntryReturnsToHomeWithoutAnimatingThroughOtherPages() {
        val pager = FakePagerState(initialPage = 2)
        val state = MainPagerState(pager, CoroutineScope(Job()))
        val next = PrimaryDestination.entries.filter { it != PrimaryDestination.SuperUser }

        state.updateDestinations(PrimaryDestination.entries, next)

        assertEquals(0, state.selectedPage)
        assertEquals(0, pager.currentPage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun restoringEntriesKeepsSettingsAndHomeAtTheirCorrectPositions() {
        val previous = listOf(PrimaryDestination.Home, PrimaryDestination.Settings)
        listOf(0 to 0, 1 to 4).forEach { (initial, expected) ->
            val pager = FakePagerState(initialPage = initial)
            val state = MainPagerState(pager, CoroutineScope(Job()))
            state.updateDestinations(previous, PrimaryDestination.entries)
            assertEquals(expected, state.selectedPage)
            assertEquals(expected, pager.currentPage)
            assertFalse(state.isNavigating)
        }
    }

    @Test
    fun changingEntriesDuringSwipeClearsOldPageOffset() {
        val pager = FakePagerState(initialPage = 2, initialOffset = 0.35f)
        val state = MainPagerState(pager, CoroutineScope(Job()))
        val next = PrimaryDestination.entries.filter { it != PrimaryDestination.KModule }

        state.updateDestinations(PrimaryDestination.entries, next)

        assertEquals(PrimaryDestination.SuperUser, next[state.selectedPage])
        assertEquals(1, pager.currentPage)
        // Panorama derives its scene progress from this coordinate; an old fraction would
        // leave the home scene partially visible after the list was remapped.
        assertEquals(0f, pager.currentPageOffsetFraction, 0f)
    }

}
