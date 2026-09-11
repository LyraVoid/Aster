package me.bmax.apatch.ui.screen

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.KeyEventBlocker
import me.bmax.apatch.util.hasMetaModule
import me.bmax.apatch.util.installModule
import me.bmax.apatch.util.reboot
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class MODULE_TYPE {
    KPM, APM
}

@Composable
@Destination<RootGraph>
fun InstallScreen(navigator: DestinationsNavigator, uri: Uri, type: MODULE_TYPE) {
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var showMetaModuleWarning by rememberSaveable { mutableStateOf(false) }

    fun appendLog(line: String) {
        logContent.append(line).append("\n")
        val newText = text + line + "\n"
        text = if (newText.length > 100_000) newText.takeLast(100_000) else newText
    }

    val context = LocalContext.current
    val snackBarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val logSavedLabel = stringResource(R.string.log_saved)

    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            installModule(uri, type, onFinish = { success ->
                if (!success) return@installModule

                scope.launch {
                    showRebootAction = true
                    showMetaModuleWarning = shouldShowMetaModuleWarning(context, uri)
                }

            }, onStdout = {
                if (it.startsWith("\u001B[H\u001B[J")) { // clear command
                    text = it.substring(6)
                } else {
                    appendLog(it)
                }
            }, onStderr = {
                if (it.startsWith("\u001B[H\u001B[J")) { // clear command
                    text = it.substring(6)
                } else {
                    appendLog(it)
                }
            })
        }
    }

    Scaffold(topBar = {
        InstallTopBar(
            onBack = dropUnlessResumed {
                navigator.popBackStack()
            },
            onSave = {
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        val format = SimpleDateFormat(
                            "yyyy-MM-dd-HH-mm-ss",
                            Locale.getDefault(),
                        )
                        val date = format.format(Date())
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS,
                            ),
                            "APatch_install_${type}_log_${date}.log",
                        )
                        file.writeText(logContent.toString())
                        file.absolutePath
                    }
                    snackBarHost.showSnackbar(message = "$logSavedLabel: $path")
                }
            },
        )
    }, floatingActionButton = {
        if (showRebootAction) {
            InstallRebootAction(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        reboot()
                    }
                },
            )
        }
    }, snackbarHost = { SnackbarHost(snackBarHost) }) { innerPadding ->
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }
        InstallLog(
            text = text,
            scrollState = scrollState,
            contentPadding = innerPadding,
        )
    }

    MetaModuleWarningDialog(
        show = showMetaModuleWarning,
        onDismiss = { showMetaModuleWarning = false },
        onLearnMore = {
            uriHandler.openUri("https://apatch.dev/meta-module.html")
        },
    )
}

private suspend fun shouldShowMetaModuleWarning(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (hasMetaModule()) return@withContext false

            val moduleId = getModuleIdFromUri(context, uri) ?: return@withContext false
            val mountOldDirectory =
                SuFile.open("/data/adb/modules/$moduleId/system")
            val mountNewDirectory =
                SuFile.open("/data/adb/modules_update/$moduleId/system")
            mountOldDirectory.isDirectory || mountNewDirectory.isDirectory
        } catch (t: Throwable) {
            Log.w("ModuleInstall", "Unable to inspect installed module", t)
            false
        }
    }

@Composable
private fun InstallTopBar(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = stringResource(R.string.apm_install),
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
        },
    )
}

@Composable
private fun InstallRebootAction(onClick: () -> Unit) {
    val reboot = stringResource(R.string.reboot)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = reboot,
            style = MiuixTheme.textStyles.button,
        )
    }
}

@Composable
private fun MetaModuleWarningDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.warning_of_meta_module_title),
        summary = stringResource(R.string.warning_of_meta_module_summary),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                text = stringResource(R.string.learn_more),
                onClick = onLearnMore,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

@Composable
private fun InstallLog(
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

fun isUriAccessible(context: Context, uri: Uri): Boolean {
    if (uri == Uri.EMPTY) return false

    return try {
        context.contentResolver.openInputStream(uri)?.use {} != null
    } catch (e: Exception) {
        Log.e("ModuleInstall", "URI is inaccessible: $uri", e)
        false
    }
}


fun extractModuleId(context: Context, uri: Uri): String? {
    if (uri == Uri.EMPTY) return null

    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry?

            while (zip.nextEntry.also { entry = it } != null) {
                if (entry?.name == "module.prop") {
                    val prop = Properties()
                    prop.load(zip)
                    return prop.getProperty("id")
                }
            }
        }
    }

    return null
}

suspend fun getModuleIdFromUri(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            if (uri == Uri.EMPTY) {
                return@withContext null
            }
            if (!isUriAccessible(context, uri)) {
                return@withContext null
            }
            extractModuleId(context, uri)
        } catch (_: Exception) {
            null
        }
    }
}
