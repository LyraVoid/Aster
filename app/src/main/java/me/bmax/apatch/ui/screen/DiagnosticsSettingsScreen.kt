package me.bmax.apatch.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.settings.resolveSettingsFeatureAvailability
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.outputStream
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What one reaches for when something is wrong: the log, and the switch that lets the WebUI be read
 * while it is being fixed.
 *
 * It is kept apart from the rest because nothing here changes how the app works; everything here
 * only describes it.
 */
@Destination<RootGraph>
@Composable
fun DiagnosticsSettingsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = APApplication.sharedPreferences
    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val capabilities = LocalAsterCapabilities.current
    var showLogBottomSheet by rememberSaveable { mutableStateOf(false) }
    var webDebuggingEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("enable_web_debugging", false))
    }

    // Nothing on this page is gated on the night theme, which is answered on the appearance page.
    val availability = resolveSettingsFeatureAvailability(
        kPatchReady = capabilities.kernelPatchReady,
        aPatchReady = capabilities.androidPatchReady,
        nightFollowSystem = false,
    )

    val logSavedMessage = stringResource(R.string.log_saved)
    val saveLog = stringResource(R.string.save_log)
    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            loadingDialog.show()
            try {
                withContext(Dispatchers.IO) {
                    uri.outputStream().use { output ->
                        getBugreportFile(context).inputStream().use {
                            it.copyTo(output)
                        }
                    }
                }
                snackBarHost.showSnackbar(message = logSavedMessage)
            } finally {
                loadingDialog.hide()
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings_section_diagnostics,
                scrollBehavior = scrollBehavior,
                navigator = navigator,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() +
                    LocalFloatingNavigationInset.current + 32.dp,
            ),
        ) {
            item(key = "logs") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.send_log),
                        startAction = {
                            SettingsIcon(MiuixIcons.Report)
                        },
                        onClick = { showLogBottomSheet = true },
                    )

                    // Reading the WebUI is a thing one does when something is wrong, which is what
                    // this page is for, rather than a switch the kernel answers to.
                    if (availability.webViewDebugging) {
                        IndicatorSwitchPreference(
                            checked = webDebuggingEnabled,
                            onCheckedChange = { enabled ->
                                prefs.edit {
                                    putBoolean("enable_web_debugging", enabled)
                                }
                                webDebuggingEnabled = enabled
                            },
                            title = stringResource(R.string.enable_web_debugging),
                            summary = stringResource(R.string.enable_web_debugging_summary),
                            startAction = {
                                SettingsIcon(MiuixIcons.Search)
                            },
                        )
                    }
                }
            }
        }

        SettingsLogSheet(
            show = showLogBottomSheet,
            onDismiss = { showLogBottomSheet = false },
            onSave = {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                val current = LocalDateTime.now().format(formatter)
                exportBugreportLauncher.launch("APatch_bugreport_${current}.tar.gz")
                showLogBottomSheet = false
            },
            onShare = {
                scope.launch {
                    val bugreport = loadingDialog.withLoading {
                        withContext(Dispatchers.IO) {
                            getBugreportFile(context)
                        }
                    }
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        bugreport,
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, uri)
                        setDataAndType(uri, "application/gzip")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, saveLog),
                    )
                    showLogBottomSheet = false
                }
            },
        )
    }
}
