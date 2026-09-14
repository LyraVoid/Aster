package me.bmax.apatch.ui.install

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmDialogHandle
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.util.getFileNameFromUri

/**
 * The one question an install asks before it is allowed to start.
 *
 * It is asked where the install is started, not on the install page: that page exists to unpack
 * the file, so sending the reader there first would put the question behind a screen that is
 * already busy doing the thing being asked about.
 */
@Stable
internal class ModuleInstallConfirm internal constructor(
    private val context: Context,
    private val dialog: ConfirmDialogHandle,
    private val title: String,
    private val content: String,
    private val confirmLabel: String,
    private val dismissLabel: String,
) {
    /**
     * True when the install may start.
     *
     * The switch is read here rather than remembered when the screen was composed, so turning the
     * question off applies to the next install instead of the next launch.
     */
    suspend fun ask(uri: Uri): Boolean {
        if (!apApp.getModuleInstallConfirmState()) return true
        val name = withContext(Dispatchers.IO) {
            // A provider that cannot answer, or answers without a display name, must not turn the
            // question into nothing: the dialog names the last path segment instead.
            runCatching { getFileNameFromUri(context, uri) }.getOrNull()
                ?: uri.lastPathSegment
                ?: uri.toString()
        }
        val answer = dialog.awaitConfirm(
            title = title,
            content = content.format(name),
            confirm = confirmLabel,
            dismiss = dismissLabel,
        )
        return answer == ConfirmResult.Confirmed
    }
}

@Composable
internal fun rememberModuleInstallConfirm(): ModuleInstallConfirm {
    val context = LocalContext.current
    val dialog = rememberConfirmDialog()
    val title = stringResource(R.string.apm_install_confirm_title)
    val content = stringResource(R.string.apm_install_confirm_content)
    val confirmLabel = stringResource(R.string.apm_install)
    val dismissLabel = stringResource(android.R.string.cancel)
    return remember(context, dialog, title, content, confirmLabel, dismissLabel) {
        ModuleInstallConfirm(context, dialog, title, content, confirmLabel, dismissLabel)
    }
}
