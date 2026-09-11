package me.bmax.apatch.ui.module

internal fun shouldScrollToTopAfterModuleLoad(
    previousCount: Int,
    currentCount: Int,
): Boolean = previousCount >= 0 && currentCount > previousCount
