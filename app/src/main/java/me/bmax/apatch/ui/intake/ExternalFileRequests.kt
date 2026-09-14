package me.bmax.apatch.ui.intake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one file an outside app handed the manager, waiting for a screen that can act on it.
 *
 * The activity is the only thing that sees the intent, and what a file is takes a little reading,
 * so it is read there and handed on as a decision. The screen that acts on it is the one inside the
 * shell, which has the dialogs and the snackbar that saying what happened needs.
 */
internal object ExternalFileRequests {
    private val _pending = MutableStateFlow<ExternalFile?>(null)

    val pending: StateFlow<ExternalFile?> = _pending.asStateFlow()

    fun publish(file: ExternalFile) {
        _pending.value = file
    }

    fun consume() {
        _pending.value = null
    }
}
