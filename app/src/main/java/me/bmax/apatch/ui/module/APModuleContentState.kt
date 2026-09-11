package me.bmax.apatch.ui.module

enum class APModuleContentState {
    LOADING,
    ERROR,
    EMPTY,
    EMPTY_SEARCH,
    CONTENT,
}

fun resolveAPModuleContentState(
    isRefreshing: Boolean,
    hasLoadError: Boolean,
    moduleCount: Int,
    isSearching: Boolean,
): APModuleContentState = when {
    hasLoadError && moduleCount == 0 -> APModuleContentState.ERROR
    isRefreshing && moduleCount == 0 -> APModuleContentState.LOADING
    moduleCount > 0 -> APModuleContentState.CONTENT
    isSearching -> APModuleContentState.EMPTY_SEARCH
    else -> APModuleContentState.EMPTY
}
