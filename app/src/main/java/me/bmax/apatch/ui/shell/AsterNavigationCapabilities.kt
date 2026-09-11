package me.bmax.apatch.ui.shell

import androidx.compose.runtime.Immutable

@Immutable
data class AsterNavigationCapabilities(
    val kernelPatchChecked: Boolean,
    val kernelPatchReady: Boolean,
    val androidPatchReady: Boolean,
)
