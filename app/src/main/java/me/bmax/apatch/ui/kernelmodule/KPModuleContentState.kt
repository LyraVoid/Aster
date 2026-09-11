package me.bmax.apatch.ui.kernelmodule

enum class KPModuleContentState {
    LOADING,
    ERROR,
    EMPTY,
    EMPTY_SEARCH,
    CONTENT,
}

fun resolveKPModuleContentState(
    isRefreshing: Boolean,
    hasLoadError: Boolean,
    moduleCount: Int,
    isSearching: Boolean,
): KPModuleContentState = when {
    hasLoadError && moduleCount == 0 -> KPModuleContentState.ERROR
    isRefreshing && moduleCount == 0 -> KPModuleContentState.LOADING
    moduleCount > 0 -> KPModuleContentState.CONTENT
    isSearching -> KPModuleContentState.EMPTY_SEARCH
    else -> KPModuleContentState.EMPTY
}
