package me.bmax.apatch.util.ui

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.basic.SnackbarHostState

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState> {
    error("CompositionLocal LocalSnackbarController not present")
}
