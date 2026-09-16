package me.bmax.apatch.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.KeyEventBlocker
import me.bmax.apatch.util.Downloads
import me.bmax.apatch.util.runAPModuleAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@Destination<RootGraph>
fun ExecuteAPMActionScreen(navigator: DestinationsNavigator, moduleId: String) {
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    val context = LocalContext.current
    val snackBarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val logSavedLabel = stringResource(R.string.log_saved)
    val logSaveFailedLabel = stringResource(R.string.log_save_failed)
    // Saving the log is the only thing this screen does that reaches outside the app, so the
    // storage permission below Android 10 is asked for at that tap and not before.
    val withStoragePermission = rememberStoragePermissionForWrite()

    fun appendLog(line: String) {
        logContent.append(line).append("\n")
        val newText = text + line + "\n"
        text = if (newText.length > 100_000) newText.takeLast(100_000) else newText
    }

    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        val success = withContext(Dispatchers.IO) {
            runAPModuleAction(
                moduleId,
                onStdout = {
                    if (it.startsWith("\u001B[H\u001B[J")) { // clear command
                        text = it.substring(6)
                    } else {
                        appendLog(it)
                    }
                },
                onStderr = {
                    appendLog(it)
                }
            )
        }
        if (shouldLeaveActionPage(success = success, stayOnPage = apApp.getStayOnActionPageState())) {
            navigator.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            ExecuteAPMActionTopBar(
                onBack = dropUnlessResumed {
                    navigator.popBackStack()
                },
                onSave = {
                    withStoragePermission {
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                val format = SimpleDateFormat(
                                    "yyyy-MM-dd-HH-mm-ss",
                                    Locale.getDefault(),
                                )
                                val date = format.format(Date())
                                Downloads.write(
                                    context = context,
                                    displayName = "Aster_apm_action_log_${date}.log",
                                    mimeType = "text/plain",
                                ) { stream -> stream.write(logContent.toString().toByteArray()) }
                            }
                            snackBarHost.showSnackbar(
                                message = if (saved != null) "$logSavedLabel: $saved"
                                else logSaveFailedLabel,
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost) }
    ) { innerPadding ->
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }
        ExecuteAPMActionLog(
            text = text,
            scrollState = scrollState,
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun ExecuteAPMActionTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = stringResource(R.string.apm_action),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = MiuixIcons.FileDownloads,
                    contentDescription = stringResource(R.string.save_log),
                )
            }
        }
    )
}

/**
 * Whether the log has said all it has to say.
 *
 * A failed action always keeps its output on screen, because that output is the only account of
 * what went wrong. A successful one leaves only when the reader has asked to be taken back.
 */
internal fun shouldLeaveActionPage(success: Boolean, stayOnPage: Boolean): Boolean =
    success && !stayOnPage

@Composable
private fun ExecuteAPMActionLog(
    text: String,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(scrollState),
    ) {
        LaunchedEffect(text) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        Text(
            modifier = Modifier.padding(12.dp),
            text = text,
            style = MiuixTheme.textStyles.body2.copy(
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}
