package me.bmax.apatch.ui.shell

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the shell knows about the device right now. Pages are gated on usable patches rather than
 * on a probe having run, so a pending check keeps kernel pages hidden until they actually work.
 */
@Immutable
data class AsterNavigationCapabilities(
    val kernelPatchReady: Boolean = false,
    val androidPatchReady: Boolean = false,
)

/**
 * The same two answers the shell gates navigation with, exposed to pages that have to gate their
 * own entries. Reading them here keeps a single answer to "is this device patched" instead of each
 * screen consulting its own source and disagreeing with the navigation.
 *
 * Defaults are unavailable so a page composed outside the shell (a preview, a test) shows its
 * "not available" state instead of pretending the device is patched.
 */
val LocalAsterCapabilities = staticCompositionLocalOf { AsterNavigationCapabilities() }
