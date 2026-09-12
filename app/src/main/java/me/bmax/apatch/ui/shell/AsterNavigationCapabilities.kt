package me.bmax.apatch.ui.shell

import androidx.compose.runtime.Immutable

/**
 * What the shell knows about the device right now. Pages are gated on usable patches rather than
 * on a probe having run, so a pending check keeps kernel pages hidden until they actually work.
 */
@Immutable
data class AsterNavigationCapabilities(
    val kernelPatchReady: Boolean,
    val androidPatchReady: Boolean,
)
