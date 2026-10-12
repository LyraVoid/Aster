package me.bmax.apatch.ui.intake

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The files an outside app handed the manager, waiting for the intake to take them.
 *
 * A queue rather than one slot: handing the same file over twice is two things that happened, and
 * each is answered on its own. The activity is the only thing that sees the intent, and what a file
 * is takes a little reading, so it is read there and handed on as a decision. The screen that acts
 * on it has the dialogs and the snackbar that saying what happened needs.
 */
internal object ExternalFileRequests {
    private val requests = Channel<ExternalFile>(Channel.BUFFERED)

    val pending: Flow<ExternalFile> = requests.receiveAsFlow()

    fun publish(file: ExternalFile) {
        requests.trySend(file)
    }
}
