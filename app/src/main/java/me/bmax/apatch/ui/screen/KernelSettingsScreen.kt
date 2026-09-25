package me.bmax.apatch.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import com.ramcosta.composedestinations.generated.destinations.RuntimeSafetyScreenDestination
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.settings.resolveSettingsFeatureAvailability
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.util.getKernelVersionCode
import me.bmax.apatch.util.getSELinuxMode
import me.bmax.apatch.util.isGkiKernel
import me.bmax.apatch.util.isMagicMountEnabled
import me.bmax.apatch.util.isGlobalNamespaceEnabled
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.setMagicMountEnabled
import me.bmax.apatch.util.setSELinuxMode
import me.bmax.apatch.util.setGlobalNamespaceEnabled
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

private data class KernelRuntimeInfo(
    val versionCode: Int?,
    val isGki: Boolean,
)

/**
 * The switches the kernel patch answers to. They change how root itself behaves rather than how the
 * app looks, so they are read apart from the rest, and each is only offered where the layer it
 * needs is really there.
 */
@Destination<RootGraph>
@Composable
fun KernelSettingsScreen(navigator: DestinationsNavigator) {
    val capabilities = LocalAsterCapabilities.current
    val kPatchReady = capabilities.kernelPatchReady
    val aPatchReady = capabilities.androidPatchReady
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = APApplication.sharedPreferences

    var magicMountEnabled by remember { mutableStateOf(false) }
    var magicMountLoaded by remember { mutableStateOf(false) }
    var magicMountSaving by remember { mutableStateOf(false) }
    var globalNamespaceEnabled by remember { mutableStateOf(false) }
    var namespaceLoaded by remember { mutableStateOf(false) }
    var kernelRuntime by remember { mutableStateOf<KernelRuntimeInfo?>(null) }
    var sucompatEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("sucompat_enabled", false))
    }
    var selinuxHideEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("selinux_hide_enabled", false))
    }
    var selinuxMode by rememberSaveable { mutableStateOf("Unknown") }
    var showSelinuxModeDialog by rememberSaveable { mutableStateOf(false) }
    var showResetSuPathDialog by rememberSaveable { mutableStateOf(false) }
    var showSelinuxHideWarning by rememberSaveable { mutableStateOf(false) }

    // Only the patch side of the availability is asked for here; the night theme is answered on
    // the appearance page, and the switches below are not gated on it.
    val availability = resolveSettingsFeatureAvailability(
        kPatchReady = kPatchReady,
        aPatchReady = aPatchReady,
        nightFollowSystem = false,
    )

    LaunchedEffect(kPatchReady, aPatchReady) {
        magicMountLoaded = false
        if (kPatchReady && aPatchReady) {
            val result = withContext(Dispatchers.IO) { runCatching { isMagicMountEnabled() } }
            result.onSuccess {
                magicMountEnabled = it
                magicMountLoaded = true
            }
        }
    }

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

    LaunchedEffect(kPatchReady, aPatchReady) {
        selinuxMode = if (kPatchReady && aPatchReady) {
            withContext(Dispatchers.IO) { getSELinuxMode() }
        } else {
            "Unknown"
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

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings_section_kernel,
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
            item(key = "patch") {
                SettingsCard {
                    if (kPatchReady && aPatchReady) {
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.settings_magic_mount),
                            summary = stringResource(R.string.settings_magic_mount_summary),
                            checked = magicMountEnabled,
                            enabled = magicMountLoaded && !magicMountSaving,
                            startAction = { SettingsIcon(MiuixIcons.Layers) },
                            onCheckedChange = { enabled ->
                                magicMountSaving = true
                                scope.launch {
                                    try {
                                        val success = withContext(Dispatchers.IO) {
                                            runCatching { setMagicMountEnabled(enabled) }.getOrDefault(false)
                                        }
                                        if (success) magicMountEnabled = enabled
                                        Toast.makeText(
                                            context,
                                            if (success) R.string.apm_reboot_to_apply else R.string.failure,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        magicMountSaving = false
                                    }
                                }
                            },
                        )
                    }

                    if (kPatchReady && aPatchReady) {
                        ArrowPreference(
                            title = stringResource(R.string.runtime_safety_title),
                            summary = stringResource(R.string.runtime_safety_summary),
                            startAction = { SettingsIcon(MiuixIcons.Lock) },
                            onClick = { navigator.navigate(RuntimeSafetyScreenDestination) },
                        )
                    }

                    if (availability.globalNamespace) {
                        IndicatorSwitchPreference(
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
                                SettingsIcon(MiuixIcons.All)
                            },
                            enabled = namespaceLoaded,
                        )
                    }

                    if (availability.sucompat) {
                        IndicatorSwitchPreference(
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
                        IndicatorSwitchPreference(
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

                    if (availability.selinuxMode) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_selinux_mode),
                            summary = stringResource(
                                R.string.settings_selinux_current_mode,
                                when (selinuxMode) {
                                    "Enforcing" ->
                                        stringResource(R.string.settings_selinux_mode_enforcing)
                                    "Permissive" ->
                                        stringResource(R.string.settings_selinux_mode_permissive)
                                    else ->
                                        stringResource(R.string.home_selinux_status_unknown)
                                },
                            ),
                            startAction = { SettingsIcon(MiuixIcons.Lock) },
                            onClick = { showSelinuxModeDialog = true },
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

        SelinuxModeDialog(
            show = showSelinuxModeDialog,
            currentMode = selinuxMode,
            onDismiss = { showSelinuxModeDialog = false },
            onApply = { enforcing ->
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        setSELinuxMode(enforcing)
                    }
                    if (success) {
                        selinuxMode = if (enforcing) "Enforcing" else "Permissive"
                    }
                    Toast.makeText(
                        context,
                        if (success) R.string.success else R.string.failure,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}
