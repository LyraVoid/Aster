package me.bmax.apatch.ui.intake

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
 * How long landing on the install page waits for the shell to have a graph. The host sets one during
 * its own composition, so in practice this is only a formality on a cold start.
 */
private const val NAVIGATION_READY_TIMEOUT_MS = 5_000L

/**
 * Acts on the file an outside app handed the manager.
 *
 * A font is imported and put to use, which is what sharing one is for. A module goes to the install
 * page behind the same question that picking one from the list asks, so an outside file is never
 * unpacked on the strength of where it came from. A kernel module is handed to kptools, which is
 * the only thing that can read one; whatever is left is answered with what the manager does take.
 *
 * Rendered above the navigation host, not inside a screen: a screen is only watching while it is
 * the one on show, so a file handed over at any other moment would sit there until the user
 * happened to come back to it. From here the dialog and the snackbar are the shell's, and they can
 * speak whatever page is showing.
 */
@Composable
internal fun ExternalFileIntake(navController: NavController) {
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

    LaunchedEffect(navController) {
        ExternalFileRequests.pending.collect { file ->
            // One file that cannot be handled must not take the intake with it: an exception here
            // would end this collection, and every file after it would be dropped in silence.
            runCatching {
                act(
                    context = context,
                    navController = navController,
                    file = file,
                    installConfirm = installConfirm,
                    loadingDialog = loadingDialog,
                    snackBarHost = snackBarHost,
                    messages = messages,
                )
            }.onFailure { Log.w(TAG, "handling ${file.name} failed", it) }
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
    navController: NavController,
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
                // Confirming takes a person, so the host is nearly always there by now; the wait
                // is for the cold start where the file arrived with the very first composition.
                val ready = withTimeoutOrNull(NAVIGATION_READY_TIMEOUT_MS) {
                    navController.currentBackStackEntryFlow.first()
                } != null
                if (ready) {
                    navController.navigate(InstallScreenDestination(file.uri, MODULE_TYPE.APM).route)
                } else {
                    Log.w(TAG, "no navigation graph for ${file.name}; dropping the request")
                }
            }
        }

        ExternalFileKind.KernelModule -> {
            val result = loadingDialog.withLoading { installKpm(file.uri) }
            say(if (result == 0) messages.kernelModuleInstalled else messages.unrecognised)
        }

        ExternalFileKind.Unknown -> say(messages.unrecognised)
    }
}
