package me.bmax.apatch.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.settings.resolveSettingsFeatureAvailability
import me.bmax.apatch.ui.theme.refreshTheme
import me.bmax.apatch.util.getBugreportFile
import me.bmax.apatch.util.getKernelVersionCode
import me.bmax.apatch.util.isGkiKernel
import me.bmax.apatch.util.isGlobalNamespaceEnabled
import me.bmax.apatch.util.outputStream
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.setGlobalNamespaceEnabled
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class KernelRuntimeInfo(
    val versionCode: Int?,
    val isGki: Boolean,
)

@Destination<RootGraph>
@Composable
fun SettingScreen() {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady =
        state == APApplication.State.ANDROIDPATCH_INSTALLING ||
            state == APApplication.State.ANDROIDPATCH_INSTALLED ||
            state == APApplication.State.ANDROIDPATCH_NEED_UPDATE

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = APApplication.sharedPreferences
    val snackBarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()

    var globalNamespaceEnabled by remember { mutableStateOf(false) }
    var namespaceLoaded by remember { mutableStateOf(false) }
    var kernelRuntime by remember { mutableStateOf<KernelRuntimeInfo?>(null) }

    var sucompatEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("sucompat_enabled", false))
    }
    var selinuxHideEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("selinux_hide_enabled", false))
    }
    var webDebuggingEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("enable_web_debugging", false))
    }
    var checkUpdate by rememberSaveable {
        mutableStateOf(prefs.getBoolean("check_update", true))
    }
    var nightFollowSystem by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_follow_sys", true))
    }
    var nightThemeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_enabled", false))
    }
    var useSystemDynamicColor by rememberSaveable {
        mutableStateOf(prefs.getBoolean("use_system_color_theme", true))
    }
    var customColor by rememberSaveable {
        mutableStateOf(prefs.getString("custom_color", "blue") ?: "blue")
    }

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showResetSuPathDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeChooseDialog by rememberSaveable { mutableStateOf(false) }
    var showLogBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showSelinuxHideWarning by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(kPatchReady, aPatchReady) {
        namespaceLoaded = false
        if (kPatchReady && aPatchReady) {
            globalNamespaceEnabled = withContext(Dispatchers.IO) {
                isGlobalNamespaceEnabled()
            }
            namespaceLoaded = true
        }
    }

    LaunchedEffect(kPatchReady, aPatchReady) {
        kernelRuntime = if (kPatchReady && aPatchReady) {
            withContext(Dispatchers.IO) {
                KernelRuntimeInfo(
                    versionCode = getKernelVersionCode(),
                    isGki = isGkiKernel(),
                )
            }
        } else {
            null
        }
    }

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val availability = resolveSettingsFeatureAvailability(
        kPatchReady = kPatchReady,
        aPatchReady = aPatchReady,
        dynamicColorSupported = dynamicColorSupported,
        nightFollowSystem = nightFollowSystem,
        useSystemDynamicColor = useSystemDynamicColor,
    )
    val languageSummary = AppCompatDelegate.getApplicationLocales()[0]?.displayLanguage
        ?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        ?: stringResource(R.string.system_default)

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

    fun applySelinuxHide(enabled: Boolean) {
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                val command = if (enabled) {
                    "touch ${APApplication.SELINUX_HIDE_FILE}"
                } else {
                    "rm -f ${APApplication.SELINUX_HIDE_FILE}"
                }
                val result = rootShellForResult(command)
                Log.d("SelinuxHideToggle", "$command result: ${result.code}")
                result.isSuccess
            }
            if (success) {
                prefs.edit { putBoolean("selinux_hide_enabled", enabled) }
                selinuxHideEnabled = enabled
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = stringResource(R.string.settings))
        },
        snackbarHost = { SnackbarHost(snackBarHost) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            if (
                availability.globalNamespace ||
                availability.sucompat ||
                availability.selinuxHide ||
                availability.webViewDebugging ||
                availability.resetSuPath
            ) {
                item(key = "root") {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_section_root),
                    ) {
                        if (availability.globalNamespace) {
                            SwitchPreference(
                                checked = globalNamespaceEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            setGlobalNamespaceEnabled(
                                                if (enabled) "1" else "0",
                                            )
                                        }
                                        globalNamespaceEnabled = enabled
                                    }
                                },
                                title = stringResource(R.string.settings_global_namespace_mode),
                                summary = stringResource(R.string.settings_global_namespace_mode_summary),
                                startAction = {
                                    SettingsIcon(MiuixIcons.Settings)
                                },
                                enabled = namespaceLoaded,
                            )
                        }

                        if (availability.sucompat) {
                            SwitchPreference(
                                checked = sucompatEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            if (enabled) {
                                                rootShellForResult(
                                                    "touch ${APApplication.SUCOMPAT_FILE}",
                                                )
                                                Natives.controlFeature("sucompat_extra", true)
                                            } else {
                                                rootShellForResult(
                                                    "rm -f ${APApplication.SUCOMPAT_FILE}",
                                                )
                                                Natives.controlFeature("sucompat_extra", false)
                                            }
                                        }
                                        Log.d(
                                            "SucompatToggle",
                                            "sucompat_extra ${if (enabled) "enable" else "disable"} result: $result",
                                        )
                                        if (result == 0L) {
                                            prefs.edit { putBoolean("sucompat_enabled", enabled) }
                                            sucompatEnabled = enabled
                                        }
                                    }
                                },
                                title = stringResource(R.string.settings_sucompat),
                                summary = stringResource(R.string.settings_sucompat_summary),
                                startAction = {
                                    SettingsIcon(MiuixIcons.Layers)
                                },
                            )
                        }

                        if (availability.selinuxHide) {
                            val kernelVersion = kernelRuntime?.versionCode
                            val isGki = kernelRuntime?.isGki ?: false
                            SwitchPreference(
                                checked = selinuxHideEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        applySelinuxHide(false)
                                    } else if ((kernelVersion ?: 0) < 510 || !isGki) {
                                        showSelinuxHideWarning = true
                                    } else {
                                        applySelinuxHide(true)
                                    }
                                },
                                title = stringResource(R.string.settings_selinux_hide),
                                summary = stringResource(R.string.settings_selinux_hide_summary),
                                startAction = {
                                    SettingsIcon(MiuixIcons.Lock)
                                },
                                enabled = kernelRuntime != null &&
                                    (kernelRuntime?.versionCode ?: 0) >= 419,
                            )
                        }

                        if (availability.webViewDebugging) {
                            SwitchPreference(
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
                                    SettingsIcon(MiuixIcons.Edit)
                                },
                            )
                        }

                        if (availability.resetSuPath) {
                            ArrowPreference(
                                title = stringResource(R.string.setting_reset_su_path),
                                startAction = {
                                    SettingsIcon(MiuixIcons.Edit)
                                },
                                onClick = { showResetSuPathDialog = true },
                            )
                        }
                    }
                }
            }

            item(key = "appearance") {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_appearance),
                ) {
                    SwitchPreference(
                        checked = nightFollowSystem,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("night_mode_follow_sys", enabled) }
                            nightFollowSystem = enabled
                            refreshTheme.value = true
                        },
                        title = stringResource(R.string.settings_night_mode_follow_sys),
                        summary = stringResource(R.string.settings_night_mode_follow_sys_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Theme)
                        },
                    )

                    if (availability.nightTheme) {
                        SwitchPreference(
                            checked = nightThemeEnabled,
                            onCheckedChange = { enabled ->
                                prefs.edit { putBoolean("night_mode_enabled", enabled) }
                                nightThemeEnabled = enabled
                                refreshTheme.value = true
                            },
                            title = stringResource(R.string.settings_night_theme_enabled),
                            startAction = {
                                SettingsIcon(MiuixIcons.Theme)
                            },
                        )
                    }

                    if (dynamicColorSupported) {
                        SwitchPreference(
                            checked = useSystemDynamicColor,
                            onCheckedChange = { enabled ->
                                prefs.edit {
                                    putBoolean("use_system_color_theme", enabled)
                                }
                                useSystemDynamicColor = enabled
                                refreshTheme.value = true
                            },
                            title = stringResource(R.string.settings_use_system_color_theme),
                            summary = stringResource(R.string.settings_use_system_color_theme_summary),
                            startAction = {
                                SettingsIcon(MiuixIcons.Theme)
                            },
                        )
                    }

                    if (availability.customColor) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_custom_color_theme),
                            summary = stringResource(colorNameToString(customColor)),
                            startAction = {
                                SettingsIcon(MiuixIcons.Theme)
                            },
                            onClick = { showThemeChooseDialog = true },
                        )
                    }
                }
            }

            item(key = "general") {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_general),
                ) {
                    SwitchPreference(
                        checked = checkUpdate,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("check_update", enabled) }
                            checkUpdate = enabled
                        },
                        title = stringResource(R.string.settings_check_update),
                        summary = stringResource(R.string.settings_check_update_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Update)
                        },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.settings_app_language),
                        summary = languageSummary,
                        startAction = {
                            SettingsIcon(MiuixIcons.Translate)
                        },
                        onClick = { showLanguageDialog = true },
                    )
                }
            }

            item(key = "diagnostics") {
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_diagnostics),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.send_log),
                        startAction = {
                            SettingsIcon(MiuixIcons.Report)
                        },
                        onClick = { showLogBottomSheet = true },
                    )
                }
            }
        }

        ThemeChooseDialog(
            show = showThemeChooseDialog,
            selectedColor = customColor,
            onDismiss = { showThemeChooseDialog = false },
            onSelect = { color ->
                prefs.edit { putString("custom_color", color) }
                customColor = color
                refreshTheme.value = true
                showThemeChooseDialog = false
            },
        )

        LanguageDialog(
            show = showLanguageDialog,
            onDismiss = { showLanguageDialog = false },
        )

        ResetSUPathDialog(
            show = showResetSuPathDialog,
            onDismiss = { showResetSuPathDialog = false },
            onApply = { path ->
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        val result = Natives.resetSuPath(path)
                        rootShellForResult(
                            "echo $path > ${APApplication.SU_PATH_FILE}",
                        )
                        result
                    }
                    Toast.makeText(
                        context,
                        if (success) R.string.success else R.string.failure,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )

        SelinuxHideWarningDialog(
            show = showSelinuxHideWarning,
            kernelVersion = kernelRuntime?.versionCode,
            isGki = kernelRuntime?.isGki ?: false,
            onDismiss = { showSelinuxHideWarning = false },
            onConfirm = {
                showSelinuxHideWarning = false
                applySelinuxHide(true)
            },
        )

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
