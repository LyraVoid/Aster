package me.bmax.apatch.ui.kernelmodule

import org.junit.Assert.assertEquals
import org.junit.Test

class KPModuleContentStateTest {

    @Test
    fun loadErrorWinsWhenNoCachedModulesExist() {
        assertEquals(
            KPModuleContentState.ERROR,
            resolveKPModuleContentState(
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
            KPModuleContentState.LOADING,
            resolveKPModuleContentState(
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
            KPModuleContentState.EMPTY_SEARCH,
            resolveKPModuleContentState(
                isRefreshing = false,
                hasLoadError = false,
                moduleCount = 0,
                isSearching = true,
            ),
        )
    }

    @Test
    fun emptyRepositoryShowsModuleEmptyState() {
        assertEquals(
            KPModuleContentState.EMPTY,
            resolveKPModuleContentState(
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
            KPModuleContentState.CONTENT,
            resolveKPModuleContentState(
                isRefreshing = true,
                hasLoadError = true,
                moduleCount = 1,
                isSearching = false,
            ),
        )
    }
}
