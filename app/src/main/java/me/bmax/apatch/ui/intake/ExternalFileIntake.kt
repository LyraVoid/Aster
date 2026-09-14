package me.bmax.apatch.ui.intake

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.LoadingDialogHandle
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.install.ModuleInstallConfirm
import me.bmax.apatch.ui.install.rememberModuleInstallConfirm
import me.bmax.apatch.ui.screen.MODULE_TYPE
import me.bmax.apatch.ui.screen.installKpm
import me.bmax.apatch.ui.theme.CustomFont
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val TAG = "ExternalFileIntake"

/**
 * Acts on the file an outside app handed the manager.
 *
 * A font is imported and put to use, which is what sharing one is for. A module goes to the install
 * page behind the same question that picking one from the list asks, so an outside file is never
 * unpacked on the strength of where it came from. A kernel module is handed to kptools, which is
 * the only thing that can read one; whatever is left is answered with what the manager does take.
 *
 * Rendered by the start destination: it is inside the shell, so the dialogs and the snackbar are
 * the shell's, and the navigation host has a graph by the time a destination composes.
 */
@Composable
internal fun ExternalFileIntake(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val installConfirm = rememberModuleInstallConfirm()
    val messages = ExternalFileMessages(
        unrecognised = stringResource(R.string.external_file_unrecognised),
        fontSaved = stringResource(R.string.settings_font_imported),
        fontFailed = stringResource(R.string.settings_font_import_failed),
        kernelModuleInstalled = stringResource(R.string.kpm_install_toast_succ),
    )

    LaunchedEffect(Unit) {
        ExternalFileRequests.pending.collect { file ->
            if (file == null) return@collect
            // Taken before it is acted on: what follows can navigate or show a dialog, and the file
            // is this app's either way.
            ExternalFileRequests.consume()
            // One file that cannot be handled must not take the intake with it: an exception here
            // would end this collection, and every file after it would be dropped in silence.
            runCatching { act(context, navigator, file, installConfirm, loadingDialog, snackBarHost, messages) }
                .onFailure { Log.w(TAG, "handling ${file.name} failed", it) }
        }
    }
}

private class ExternalFileMessages(
    val unrecognised: String,
    val fontSaved: String,
    val fontFailed: String,
    val kernelModuleInstalled: String,
)

private suspend fun act(
    context: Context,
    navigator: DestinationsNavigator,
    file: ExternalFile,
    installConfirm: ModuleInstallConfirm,
    loadingDialog: LoadingDialogHandle,
    snackBarHost: SnackbarHostState,
    messages: ExternalFileMessages,
) {
    suspend fun say(message: String) {
        snackBarHost.showSnackbar(message = message, duration = SnackbarDuration.Long)
    }

    when (file.kind) {
        ExternalFileKind.Font -> {
            val imported = loadingDialog.withLoading { CustomFont.importFont(context, file.uri) }
            say(if (imported) messages.fontSaved else messages.fontFailed)
        }

        ExternalFileKind.Module -> {
            if (installConfirm.ask(file.uri)) {
                navigator.navigate(InstallScreenDestination(file.uri, MODULE_TYPE.APM))
            }
        }

        ExternalFileKind.KernelModule -> {
            val result = loadingDialog.withLoading { installKpm(file.uri) }
            say(if (result == 0) messages.kernelModuleInstalled else messages.unrecognised)
        }

        ExternalFileKind.Unknown -> say(messages.unrecognised)
    }
}
