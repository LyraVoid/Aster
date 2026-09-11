package me.bmax.apatch.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.WarningCard
import me.bmax.apatch.ui.component.WarningCardTone
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.isJailbreakMode
import me.bmax.apatch.util.reboot
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "Patches"
private const val LEGACY_STORAGE_REQUEST_CODE = 1001

@Destination<RootGraph>
@Composable
fun Patches(mode: PatchesViewModel.PatchMode) {
    var jailbreakBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        jailbreakBlocked = withContext(Dispatchers.IO) { isJailbreakMode() }
    }

    if (jailbreakBlocked) {
        Scaffold(topBar = { PatchesTopBar() }) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(12.dp),
            ) {
                WarningCard(
                    message = stringResource(R.string.jailbreak_no_patch),
                    tone = WarningCardTone.Neutral,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var needKey by rememberSaveable { mutableStateOf(false) }

    val viewModel = viewModel<PatchesViewModel>()
    LaunchedEffect(mode) {
        viewModel.prepare(mode)
    }
    LaunchedEffect(context) {
        requestLegacyStoragePermissions(context)
    }

    Scaffold(topBar = {
        PatchesTopBar()
    }, floatingActionButton = {
        if (viewModel.needReboot) {
            PatchRebootAction(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        reboot()
                    }
                },
            )
        }
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PatchModeCard(mode)
            PatchErrorCard(viewModel.error)
            KernelPatchImageCard(viewModel.kpimgInfo)

            // Consume a boot image chosen on the install-mode screen exactly once.
            LaunchedEffect(selectedBootImage) {
                val bootImage = selectedBootImage
                if (
                    mode == PatchesViewModel.PatchMode.PATCH_ONLY &&
                    bootImage != null &&
                    viewModel.kimgInfo.banner.isEmpty()
                ) {
                    viewModel.copyAndParseBootimg(bootImage)
                    if (!viewModel.running && viewModel.kimgInfo.banner.isEmpty()) {
                        selectedBootImage = null
                    }
                }
            }

            if (
                mode == PatchesViewModel.PatchMode.PATCH_ONLY &&
                viewModel.kimgInfo.banner.isEmpty()
            ) {
                SelectFileButton(
                    text = stringResource(R.string.patch_select_bootimg_btn),
                    icon = MiuixIcons.File,
                    onSelected = { data, uri ->
                        Log.d(TAG, "select boot.img, data: $data, uri: $uri")
                        viewModel.copyAndParseBootimg(uri)
                    },
                )
            }

            if (viewModel.bootSlot.isNotEmpty() || viewModel.bootDev.isNotEmpty()) {
                BootImageCard(
                    slot = viewModel.bootSlot,
                    boot = viewModel.bootDev,
                )
            }

            if (viewModel.kimgInfo.banner.isNotEmpty()) {
                KernelImageCard(viewModel.kimgInfo)
            }

            if (
                mode != PatchesViewModel.PatchMode.UNPATCH &&
                viewModel.kimgInfo.banner.isNotEmpty()
            ) {
                PatchSuperKeyToggleCard(
                    checked = needKey,
                    onCheckedChange = { needKey = it },
                )

                AnimatedVisibility(
                    visible = needKey,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        SetSuperKeyView(viewModel)
                    }
                }
            }

            if (
                mode == PatchesViewModel.PatchMode.PATCH_AND_INSTALL ||
                mode == PatchesViewModel.PatchMode.INSTALL_TO_NEXT_SLOT
            ) {
                viewModel.existedExtras.forEach { extra ->
                    ExtraItem(
                        extra = extra,
                        existed = true,
                        onDelete = {
                            viewModel.existedExtras.remove(extra)
                        },
                    )
                }
            }

            if (mode != PatchesViewModel.PatchMode.UNPATCH) {
                viewModel.newExtras.forEach { extra ->
                    ExtraItem(
                        extra = extra,
                        existed = false,
                        onDelete = {
                            val index = viewModel.newExtras.indexOf(extra)
                            viewModel.newExtras.remove(extra)
                            if (index in viewModel.newExtrasFileName.indices) {
                                viewModel.newExtrasFileName.removeAt(index)
                            }
                        },
                    )
                }
            }

            if (
                !viewModel.patching &&
                !viewModel.patchdone &&
                mode != PatchesViewModel.PatchMode.UNPATCH
            ) {
                SelectFileButton(
                    text = stringResource(R.string.patch_embed_kpm_btn),
                    icon = MiuixIcons.Add,
                    onSelected = { data, uri ->
                        Log.d(TAG, "select kpm, data: $data, uri: $uri")
                        viewModel.embedKPM(uri)
                    },
                )
            }

            if (!viewModel.patching && !viewModel.patchdone) {
                if (mode != PatchesViewModel.PatchMode.UNPATCH) {
                    val isKeyReady = !needKey || viewModel.superkey.isNotEmpty()
                    if (isKeyReady) {
                        StartButton(
                            text = stringResource(R.string.patch_start_patch_btn),
                            icon = MiuixIcons.Play,
                            onClick = { viewModel.doPatch(mode, needKey) },
                        )
                    }
                }

                if (
                    mode == PatchesViewModel.PatchMode.UNPATCH &&
                    viewModel.kimgInfo.banner.isNotEmpty()
                ) {
                    StartButton(
                        text = stringResource(R.string.patch_start_unpatch_btn),
                        icon = MiuixIcons.Undo,
                        onClick = viewModel::doUnpatch,
                    )
                }
            }

            if (viewModel.patching || viewModel.patchdone) {
                PatchLogCard(
                    text = viewModel.patchLog,
                    scrollState = scrollState,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (viewModel.running) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

internal fun legacyStoragePermissions(sdkInt: Int): List<String> = buildList {
    if (sdkInt <= Build.VERSION_CODES.Q) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (sdkInt <= Build.VERSION_CODES.S_V2) {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun requestLegacyStoragePermissions(context: android.content.Context) {
    val permissionsToRequest = legacyStoragePermissions(Build.VERSION.SDK_INT).filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    if (permissionsToRequest.isEmpty()) return

    (context as? Activity)?.let { activity ->
        ActivityCompat.requestPermissions(
            activity,
            permissionsToRequest.toTypedArray(),
            LEGACY_STORAGE_REQUEST_CODE,
        )
    }
}

@Composable
private fun PatchRebootAction(onClick: () -> Unit) {
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
private fun StartButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
            )
        }
    }
}

@Composable
private fun SelectFileButton(
    text: String,
    icon: ImageVector,
    onSelected: (data: Intent, uri: Uri) -> Unit,
) {
    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult
        onSelected(data, uri)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Button(
            onClick = {
                selectFileLauncher.launch(
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                    },
                )
            },
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
            )
        }
    }
}

@Composable
private fun PatchModeCard(mode: PatchesViewModel.PatchMode) {
    InfoCard {
        Text(
            text = stringResource(mode.sId),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PatchErrorCard(error: String) {
    if (error.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.patch_item_error),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = error,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun KernelPatchImageCard(kpImgInfo: KPModel.KPImgInfo) {
    if (kpImgInfo.version.isEmpty()) return

    InfoCard {
        InfoTitle(stringResource(R.string.patch_item_kpimg))
        InfoLine(
            label = stringResource(R.string.patch_item_kpimg_version),
            value = Version.uInt2String(kpImgInfo.version.substring(2).toUInt(16)),
        )
        InfoLine(
            label = stringResource(R.string.patch_item_kpimg_comile_time),
            value = kpImgInfo.compileTime,
        )
        InfoLine(
            label = stringResource(R.string.patch_item_kpimg_config),
            value = kpImgInfo.config,
        )
    }
}

@Composable
private fun BootImageCard(slot: String, boot: String) {
    InfoCard {
        InfoTitle(stringResource(R.string.patch_item_bootimg))
        if (slot.isNotEmpty()) {
            InfoLine(
                label = stringResource(R.string.patch_item_bootimg_slot),
                value = slot,
            )
        }
        InfoLine(
            label = stringResource(R.string.patch_item_bootimg_dev),
            value = boot,
        )
    }
}

@Composable
private fun KernelImageCard(kImgInfo: KPModel.KImgInfo) {
    InfoCard {
        InfoTitle(stringResource(R.string.patch_item_kernel))
        Text(
            text = kImgInfo.banner,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun PatchSuperKeyToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = infoCardColors(),
    ) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = stringResource(R.string.patch_custom_superkey),
            summary = stringResource(R.string.patch_custom_superkey_summary),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            },
        )
    }
}

@Composable
private fun SetSuperKeyView(viewModel: PatchesViewModel) {
    var superKey by remember { mutableStateOf(viewModel.superkey) }
    var showWarning by remember {
        mutableStateOf(!viewModel.checkSuperKeyValidation(superKey))
    }
    var keyVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        colors = infoCardColors(),
    ) {
        Text(
            text = stringResource(R.string.patch_item_skey),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
        )
        if (showWarning) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.patch_item_set_skey_label),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = superKey,
            onValueChange = { value ->
                superKey = value
                if (viewModel.checkSuperKeyValidation(value)) {
                    viewModel.superkey = value
                    showWarning = false
                } else {
                    viewModel.superkey = ""
                    showWarning = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.patch_set_superkey),
            singleLine = true,
            visualTransformation = if (keyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) MiuixIcons.Hide else MiuixIcons.Show,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            },
        )
    }
}

@Composable
private fun ExtraItem(
    extra: KPModel.IExtraInfo,
    existed: Boolean,
    onDelete: () -> Unit,
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    if (showConfigDialog && extra is KPModel.KPMInfo) {
        ExtraConfigDialog(
            kpmInfo = extra,
            onDismiss = { showConfigDialog = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        colors = infoCardColors(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (existed) {
                        R.string.patch_item_existed_extra_kpm
                    } else {
                        R.string.patch_item_new_extra_kpm
                    },
                ) + " " + extra.type.toString().uppercase(),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
            )
            if (extra.type == KPModel.ExtraType.KPM) {
                IconButton(onClick = { showConfigDialog = true }) {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = stringResource(R.string.kpm_control),
                        tint = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.apm_remove),
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (extra.type == KPModel.ExtraType.KPM) {
            val kpmInfo = extra as KPModel.KPMInfo
            Spacer(Modifier.height(4.dp))
            ExtraInfoLine(R.string.patch_item_extra_name, kpmInfo.name)
            ExtraInfoLine(R.string.patch_item_extra_version, kpmInfo.version)
            ExtraInfoLine(R.string.patch_item_extra_kpm_license, kpmInfo.license)
            ExtraInfoLine(R.string.patch_item_extra_author, kpmInfo.author)
            ExtraInfoLine(
                label = R.string.patch_item_extra_kpm_desciption,
                value = kpmInfo.description,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun ExtraConfigDialog(
    kpmInfo: KPModel.KPMInfo,
    onDismiss: () -> Unit,
) {
    var event by remember(kpmInfo) { mutableStateOf(kpmInfo.event) }
    var args by remember(kpmInfo) { mutableStateOf(kpmInfo.args) }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.kpm_control_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = event,
                onValueChange = {
                    event = it
                    kpmInfo.event = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.patch_item_extra_event),
                singleLine = true,
            )
            TextField(
                value = args,
                onValueChange = {
                    args = it
                    kpmInfo.args = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.patch_item_extra_args),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun ExtraInfoLine(
    label: Int,
    value: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (value.isBlank()) return

    Text(
        text = "${stringResource(label)} $value",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSecondaryContainer,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PatchLogCard(
    text: String,
    scrollState: ScrollState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
    }
    LaunchedEffect(text) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        colors = infoCardColors(),
    ) {
        content()
    }
}

@Composable
private fun InfoTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text(
        text = "$label $value",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun infoCardColors() = CardDefaults.defaultColors(
    color = MiuixTheme.colorScheme.secondaryContainer,
    contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun PatchesTopBar() {
    TopAppBar(title = stringResource(R.string.patch_config_title))
}
