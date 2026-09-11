package me.bmax.apatch.ui.module

import org.junit.Assert.assertEquals
import org.junit.Test

class APModuleContentStateTest {

    @Test
    fun loadErrorWinsWhenNoCachedModulesExist() {
        assertEquals(
            APModuleContentState.ERROR,
            resolveAPModuleContentState(
                isRefreshing = true,
                hasLoadError = true,
                moduleCount = 0,
                isSearching = false,
            ),
        )
    }

    @Test
    fun initialRefreshShowsLoading() {
        assertEquals(
            APModuleContentState.LOADING,
            resolveAPModuleContentState(
                isRefreshing = true,
                hasLoadError = false,
                moduleCount = 0,
                isSearching = false,
            ),
        )
    }

    @Test
    fun searchMissShowsSearchSpecificEmptyState() {
        assertEquals(
            APModuleContentState.EMPTY_SEARCH,
            resolveAPModuleContentState(
                isRefreshing = false,
                hasLoadError = false,
                moduleCount = 0,
                isSearching = true,
            ),
        )
    }

    @Test
    fun emptyRepositoryShowsInstallEmptyState() {
        assertEquals(
            APModuleContentState.EMPTY,
            resolveAPModuleContentState(
                isRefreshing = false,
                hasLoadError = false,
                moduleCount = 0,
                isSearching = false,
            ),
        )
    }

    @Test
    fun cachedModulesRemainVisibleDuringRefreshOrError() {
        assertEquals(
            APModuleContentState.CONTENT,
            resolveAPModuleContentState(
                isRefreshing = true,
                hasLoadError = true,
                moduleCount = 1,
                isSearching = false,
            ),
        )
    }
}
