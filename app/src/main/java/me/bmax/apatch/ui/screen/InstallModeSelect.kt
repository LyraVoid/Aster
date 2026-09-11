package me.bmax.apatch.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.WarningCard
import me.bmax.apatch.ui.component.WarningCardTone
import me.bmax.apatch.ui.install.InstallMethodType
import me.bmax.apatch.ui.install.resolveInstallModeState
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.isABDevice
import me.bmax.apatch.util.isJailbreakMode
import me.bmax.apatch.util.rootAvailable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Hand-off channel from this screen to the Patches screen; a plain var would not
// notify the LaunchedEffect consuming it there.
var selectedBootImage by mutableStateOf<Uri?>(null)

private data class InstallEnvironment(
    val rootAvailable: Boolean,
    val isAbDevice: Boolean,
    val jailbreakBlocked: Boolean,
)

@Destination<RootGraph>
@Composable
fun InstallModeSelectScreen(navigator: DestinationsNavigator) {
    var environment by remember { mutableStateOf<InstallEnvironment?>(null) }
    var selectedMethod by remember { mutableStateOf<InstallMethodType?>(null) }
    var showInactiveSlotConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        environment = withContext(Dispatchers.IO) {
            InstallEnvironment(
                rootAvailable = rootAvailable(),
                isAbDevice = isABDevice(),
                jailbreakBlocked = isJailbreakMode(),
            )
        }
    }

    val modeState = environment?.let {
        resolveInstallModeState(
            rootAvailable = it.rootAvailable,
            isAbDevice = it.isAbDevice,
            jailbreakBlocked = it.jailbreakBlocked,
        )
    }
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        selectedMethod = InstallMethodType.SelectFile
        selectedBootImage = uri
        navigator.navigate(
            PatchesDestination(PatchesViewModel.PatchMode.PATCH_ONLY),
        )
    }

    val onSelect: (InstallMethodType) -> Unit = { method ->
        selectedMethod = method
        when (method) {
            InstallMethodType.SelectFile -> {
                selectedBootImage = null
                selectImageLauncher.launch(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/octet-stream"
                    },
                )
            }

            InstallMethodType.DirectInstall -> {
                navigator.navigate(
                    PatchesDestination(PatchesViewModel.PatchMode.PATCH_AND_INSTALL),
                )
            }

            InstallMethodType.InactiveSlot -> {
                showInactiveSlotConfirm = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.mode_select_page_title),
                navigationIcon = {
                    IconButton(
                        onClick = dropUnlessResumed { navigator.popBackStack() },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (modeState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                if (modeState.showJailbreakWarning) {
                    WarningCard(
                        message = stringResource(R.string.jailbreak_no_patch),
                        tone = WarningCardTone.Neutral,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (modeState.showRootWarning) {
                    WarningCard(
                        message = stringResource(R.string.home_install_unknown_summary),
                        tone = WarningCardTone.Neutral,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                if (modeState.methods.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        insideMargin = PaddingValues(vertical = 4.dp),
                    ) {
                        modeState.methods.forEach { method ->
                            RadioButtonPreference(
                                title = stringResource(method.labelRes()),
                                selected = selectedMethod == method,
                                onClick = { onSelect(method) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInactiveSlotConfirm) {
        Dialog(
            onDismissRequest = { showInactiveSlotConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    insideMargin = PaddingValues(20.dp),
                ) {
                    Text(
                        text = stringResource(android.R.string.dialog_alert_title),
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.mode_select_page_install_inactive_slot_warning),
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = { showInactiveSlotConfirm = false },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                showInactiveSlotConfirm = false
                                navigator.navigate(
                                    PatchesDestination(
                                        PatchesViewModel.PatchMode.INSTALL_TO_NEXT_SLOT,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun InstallMethodType.labelRes(): Int = when (this) {
    InstallMethodType.SelectFile -> R.string.mode_select_page_select_file
    InstallMethodType.DirectInstall -> R.string.mode_select_page_patch_and_install
    InstallMethodType.InactiveSlot -> R.string.mode_select_page_install_inactive_slot
}
