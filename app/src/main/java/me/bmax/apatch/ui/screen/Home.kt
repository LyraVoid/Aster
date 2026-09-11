package me.bmax.apatch.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.RootDetailState
import me.bmax.apatch.root.RootLayerState
import me.bmax.apatch.root.RootMode
import me.bmax.apatch.ui.home.HomeConclusion
import me.bmax.apatch.ui.home.HomeDeviceDensity
import me.bmax.apatch.ui.home.HomeDeviceEnvironment
import me.bmax.apatch.ui.home.HomeEvent
import me.bmax.apatch.ui.home.HomePrimaryAction
import me.bmax.apatch.ui.home.HomeSelinuxStatus
import me.bmax.apatch.ui.home.HomeUiState
import me.bmax.apatch.ui.home.HomeUpdateState
import me.bmax.apatch.ui.home.HomeViewModel
import me.bmax.apatch.ui.home.HomeWallpaperCrop
import me.bmax.apatch.ui.home.HomeWallpaperEvent
import me.bmax.apatch.ui.home.HomeWallpaperMaxZoom
import me.bmax.apatch.ui.home.HomeWallpaperMinZoom
import me.bmax.apatch.ui.home.HomeWallpaperPhase
import me.bmax.apatch.ui.home.HomeWallpaperState
import me.bmax.apatch.ui.home.HomeWallpaperViewModel
import me.bmax.apatch.ui.home.androidVersion
import me.bmax.apatch.ui.home.displayName
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import java.io.File

@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val viewModel: HomeViewModel = viewModel()
    val wallpaperViewModel: HomeWallpaperViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaperState by wallpaperViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showMore by rememberSaveable { mutableStateOf(false) }
    var showReboot by rememberSaveable { mutableStateOf(false) }
    var showWallpaperSheet by rememberSaveable { mutableStateOf(false) }
    var showAdvancedDetails by rememberSaveable { mutableStateOf(false) }
    var showUninstallDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDangerousReboot by rememberSaveable { mutableStateOf<String?>(null) }

    val jailbreakFailedMessage = stringResource(R.string.settings_jailbreak_failed)
    val jailbreakTriggeredMessage = stringResource(R.string.jailbreak_triggered)
    val wallpaperImportFailedMessage = stringResource(R.string.home_wallpaper_import_failed)
    val wallpaperChangeFailedMessage = stringResource(R.string.home_wallpaper_change_failed)
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            wallpaperViewModel.importImage(uri)
        }
    }
    val launchWallpaperPicker = {
        wallpaperPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.JailbreakResult -> Toast.makeText(
                    context,
                    if (event.success) jailbreakTriggeredMessage else jailbreakFailedMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(wallpaperViewModel) {
        wallpaperViewModel.events.collect { event ->
            val message = when (event) {
                HomeWallpaperEvent.ImportFailed -> wallpaperImportFailedMessage
                HomeWallpaperEvent.ChangeFailed -> wallpaperChangeFailedMessage
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val onInstallClick = dropUnlessResumed {
        navigator.navigate(InstallModeSelectScreenDestination)
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                canReboot = state.capability.rootAccess == RootAccessProbeState.AVAILABLE,
                update = state.update,
                showMore = showMore,
                showReboot = showReboot,
                onShowMoreChange = { showMore = it },
                onShowRebootChange = { showReboot = it },
                onAppearance = { showWallpaperSheet = true },
                onInstallClick = {
                    showMore = false
                    onInstallClick()
                },
                onCheckUpdates = {
                    showMore = false
                    viewModel.checkForUpdates(force = true)
                },
                onFeedback = {
                    showMore = false
                    uriHandler.openUri("https://github.com/bmax121/APatch/issues/new/choose")
                },
                onAbout = {
                    showMore = false
                    navigator.navigate(AboutScreenDestination)
                },
                onReboot = { reason ->
                    showReboot = false
                    viewModel.reboot(reason)
                },
                onDangerousReboot = { reason ->
                    showReboot = false
                    pendingDangerousReboot = reason
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            if (wallpaperState.enabled && wallpaperState.isReady) {
                HomeWallpaperEnvironment(
                    state = wallpaperState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.34f)
                        .align(Alignment.TopCenter),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeStatusCard(
                    state = state,
                    onPrimaryAction = dropUnlessResumed {
                        when (state.primaryAction) {
                            HomePrimaryAction.NONE -> Unit
                            HomePrimaryAction.RETRY_CHECK -> viewModel.refreshCapabilities()
                            HomePrimaryAction.INSTALL_KERNEL_PATCH,
                            HomePrimaryAction.UPDATE_KERNEL_PATCH,
                            -> navigator.navigate(InstallModeSelectScreenDestination)

                            HomePrimaryAction.INSTALL_APATCH,
                            HomePrimaryAction.UPDATE_APATCH,
                            -> viewModel.installApatch()

                            HomePrimaryAction.REBOOT -> viewModel.reboot()
                            HomePrimaryAction.SOFT_REBOOT -> viewModel.softReboot()
                        }
                    },
                    onJailbreak = viewModel::triggerJailbreak,
                )

                state.environment?.let { environment ->
                    DeviceIdentityCard(
                        environment = environment,
                        density = state.deviceDensity,
                    )
                }

                if (state.showBackupWarning) {
                    BackupWarningCard(onDismiss = viewModel::dismissBackupWarning)
                }

                val availableUpdate = state.update as? HomeUpdateState.Available
                if (availableUpdate != null) {
                    UpdateAvailableCard(
                        update = availableUpdate,
                        onClick = { showUpdateDialog = true },
                    )
                }

                RuntimeStackCard(state = state)

                AdvancedDetailsCard(
                    state = state,
                    expanded = showAdvancedDetails,
                    onExpandedChange = { showAdvancedDetails = it },
                    onCheckUpdates = { viewModel.checkForUpdates(force = true) },
                    onRemoveAndroidPatch = viewModel::uninstallApatch,
                    onUninstallAll = { showUninstallDialog = true },
                )

                LearnMoreCard(onClick = { uriHandler.openUri("https://apatch.dev") })
                Spacer(Modifier.height(12.dp))
            }

            HomeWallpaperSheet(
                show = showWallpaperSheet,
                state = wallpaperState,
                onDismissRequest = { showWallpaperSheet = false },
                onEnabledChange = wallpaperViewModel::setEnabled,
                onChooseImage = launchWallpaperPicker,
                onRemoveImage = wallpaperViewModel::removeImage,
                onCropChange = wallpaperViewModel::saveCrop,
            )

            if (showUninstallDialog) {
                UninstallDialog(
                    show = true,
                    onDismiss = { showUninstallDialog = false },
                    onRemoveAndroidPatch = {
                        showUninstallDialog = false
                        viewModel.uninstallApatch()
                    },
                    onUninstallAll = {
                        showUninstallDialog = false
                        viewModel.uninstallApatch()
                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                    },
                )
            }

            pendingDangerousReboot?.let { reason ->
                val download = reason == "download"
                RebootConfirmationDialog(
                    show = true,
                    download = download,
                    onDismiss = { pendingDangerousReboot = null },
                    onConfirm = {
                        pendingDangerousReboot = null
                        viewModel.reboot(reason)
                    },
                )
            }

            val update = state.update as? HomeUpdateState.Available
            if (showUpdateDialog && update != null) {
                UpdateDialog(
                    show = true,
                    update = update,
                    onDismiss = { showUpdateDialog = false },
                    onOpen = {
                        showUpdateDialog = false
                        uriHandler.openUri(update.downloadUrl)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    canReboot: Boolean,
    update: HomeUpdateState,
    showMore: Boolean,
    showReboot: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    onAppearance: () -> Unit,
    onInstallClick: () -> Unit,
    onCheckUpdates: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
) {
    SmallTopAppBar(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = onAppearance) {
                Icon(
                    imageVector = MiuixIcons.Photos,
                    contentDescription = stringResource(R.string.home_appearance),
                )
            }

            Box {
                IconButton(onClick = { onShowMoreChange(true) }) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = stringResource(R.string.home_more),
                    )
                }

                OverlayListPopup(
                    show = showMore,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { onShowMoreChange(false) },
                ) {
                    ListPopupColumn {
                        PopupMenuItem(
                            icon = MiuixIcons.Import,
                            text = stringResource(R.string.mode_select_page_title),
                            onClick = onInstallClick,
                        )
                        if (canReboot) {
                            PopupMenuItem(
                                icon = MiuixIcons.Reset,
                                text = stringResource(R.string.reboot),
                                onClick = {
                                    onShowMoreChange(false)
                                    onShowRebootChange(true)
                                },
                            )
                        }
                        PopupMenuItem(
                            icon = MiuixIcons.Update,
                            text = when (update) {
                                HomeUpdateState.Checking -> stringResource(R.string.home_update_checking)
                                else -> stringResource(R.string.home_update_check)
                            },
                            enabled = update !is HomeUpdateState.Checking,
                            onClick = onCheckUpdates,
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Help,
                            text = stringResource(R.string.home_more_menu_feedback_or_suggestion),
                            onClick = onFeedback,
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Info,
                            text = stringResource(R.string.home_more_menu_about),
                            onClick = onAbout,
                        )
                    }
                }

                OverlayListPopup(
                    show = showReboot,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { onShowRebootChange(false) },
                ) {
                    ListPopupColumn {
                        PopupMenuItem(
                            icon = MiuixIcons.Reset,
                            text = stringResource(R.string.reboot),
                            onClick = {
                                onShowRebootChange(false)
                                onReboot("")
                            },
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Refresh,
                            text = stringResource(R.string.reboot_soft),
                            onClick = {
                                onShowRebootChange(false)
                                onReboot("soft_reboot")
                            },
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Reset,
                            text = stringResource(R.string.reboot_recovery),
                            onClick = {
                                onShowRebootChange(false)
                                onReboot("recovery")
                            },
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Reset,
                            text = stringResource(R.string.reboot_bootloader),
                            onClick = {
                                onShowRebootChange(false)
                                onReboot("bootloader")
                            },
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Download,
                            text = stringResource(R.string.reboot_download),
                            onClick = {
                                onShowRebootChange(false)
                                onDangerousReboot("download")
                            },
                        )
                        PopupMenuItem(
                            icon = MiuixIcons.Import,
                            text = stringResource(R.string.reboot_edl),
                            onClick = {
                                onShowRebootChange(false)
                                onDangerousReboot("edl")
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, color = contentColor)
    }
}

@Composable
private fun HomeWallpaperEnvironment(
    state: HomeWallpaperState,
    modifier: Modifier = Modifier,
) {
    val background = MiuixTheme.colorScheme.background
    Box(
        modifier = modifier
            .clipToBounds()
            .background(background),
    ) {
        HomeWallpaperImage(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.58f to Color.Transparent,
                        0.82f to background.copy(alpha = 0.92f),
                        1f to background,
                    )
                )
        )
    }
}

@Composable
private fun HomeWallpaperImage(
    state: HomeWallpaperState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val path = state.imagePath ?: return
    val request = remember(path, state.revision, context) {
        ImageRequest.Builder(context)
            .data(File(path))
            .memoryCacheKey("home-wallpaper-${state.revision}")
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(
            horizontalBias = state.crop.biasX,
            verticalBias = state.crop.biasY,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = state.crop.zoom
            scaleY = state.crop.zoom
        },
    )
}

@Composable
private fun HomeWallpaperSheet(
    show: Boolean,
    state: HomeWallpaperState,
    onDismissRequest: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onCropChange: (HomeWallpaperCrop) -> Unit,
) {
    val busy = state.phase == HomeWallpaperPhase.LOADING
    var zoom by remember(show, state.imagePath, state.revision, state.crop) {
        mutableFloatStateOf(state.crop.zoom)
    }
    var biasX by remember(show, state.imagePath, state.revision, state.crop) {
        mutableFloatStateOf(state.crop.biasX)
    }
    var biasY by remember(show, state.imagePath, state.revision, state.crop) {
        mutableFloatStateOf(state.crop.biasY)
    }
    val draftCrop = HomeWallpaperCrop(
        zoom = zoom,
        biasX = biasX,
        biasY = biasY,
    )

    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.home_appearance),
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_wallpaper_enable),
                        style = MiuixTheme.textStyles.body1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_wallpaper_enable_summary),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !busy,
                )
            }

            when {
                busy -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_loading),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                state.enabled && state.phase == HomeWallpaperPhase.MISSING -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_missing),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                state.enabled && state.phase == HomeWallpaperPhase.ERROR -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_error),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                state.isReady -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clipToBounds(),
                        ) {
                            HomeWallpaperImage(
                                state = state.copy(crop = draftCrop),
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.72f to Color.Transparent,
                                            1f to MiuixTheme.colorScheme.background,
                                        )
                                    )
                            )
                        }
                    }

                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_scale),
                        value = zoom,
                        valueRange = HomeWallpaperMinZoom..HomeWallpaperMaxZoom,
                        enabled = !busy,
                        onValueChange = { zoom = it },
                        onValueChangeFinished = { onCropChange(draftCrop) },
                    )
                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_horizontal),
                        value = biasX,
                        valueRange = -1f..1f,
                        enabled = !busy,
                        onValueChange = { biasX = it },
                        onValueChangeFinished = { onCropChange(draftCrop) },
                    )
                    WallpaperSlider(
                        label = stringResource(R.string.home_wallpaper_vertical),
                        value = biasY,
                        valueRange = -1f..1f,
                        enabled = !busy,
                        onValueChange = { biasY = it },
                        onValueChangeFinished = { onCropChange(draftCrop) },
                    )
                }

                state.hasImage -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_saved),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onChooseImage,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                ) {
                    Text(
                        text = stringResource(
                            if (state.hasImage) {
                                R.string.home_wallpaper_change
                            } else {
                                R.string.home_wallpaper_choose
                            }
                        ),
                        style = MiuixTheme.textStyles.button,
                    )
                }
                if (state.hasImage) {
                    TextButton(
                        text = stringResource(R.string.home_wallpaper_remove),
                        onClick = onRemoveImage,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun HomeStatusCard(
    state: HomeUiState,
    onPrimaryAction: () -> Unit,
    onJailbreak: () -> Unit,
) {
    val colors = homeStatusColors(state.conclusion)
    val showJailbreak = state.conclusion == HomeConclusion.NOT_INSTALLED &&
        state.environment?.selinuxStatus == HomeSelinuxStatus.PERMISSIVE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
        insideMargin = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = state.conclusion.icon(),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(state.conclusion.titleRes()),
                    style = MiuixTheme.textStyles.title3,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(state.conclusion.summaryRes()),
                    style = MiuixTheme.textStyles.body2,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                )
            }
        }

        if (state.primaryAction != HomePrimaryAction.NONE) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = stringResource(state.primaryAction.labelRes()),
                    style = MiuixTheme.textStyles.button,
                )
            }
        }

        if (showJailbreak) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = stringResource(R.string.jailbreak),
                onClick = onJailbreak,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeviceIdentityCard(
    environment: HomeDeviceEnvironment,
    density: HomeDeviceDensity,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Home,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.home_device_info),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = environment.displayName(),
            style = MiuixTheme.textStyles.title4,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = environment.androidVersion(),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = environment.kernelRelease,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        if (density == HomeDeviceDensity.DIAGNOSTIC) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CompactValue(
                    label = stringResource(R.string.home_kmi),
                    value = environment.kmi ?: stringResource(R.string.home_layer_unknown),
                    modifier = Modifier.weight(1f),
                )
                CompactValue(
                    label = stringResource(R.string.home_abi),
                    value = environment.primaryAbi.ifBlank {
                        stringResource(R.string.home_layer_unknown)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun BackupWarningCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.patch_warnning),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body2,
            )
            IconButton(
                onClick = onDismiss,
                minWidth = 36.dp,
                minHeight = 36.dp,
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.home_close),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    update: HomeUpdateState.Available,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
        ),
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Update,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.home_update_available_title,
                        update.versionCode,
                    ),
                    style = MiuixTheme.textStyles.subtitle,
                )
                Text(
                    text = stringResource(R.string.home_update_available_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                )
            }
        }
    }
}

@Composable
private fun RuntimeStackCard(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(18.dp),
    ) {
        Text(
            text = stringResource(R.string.home_runtime_stack),
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        RuntimeLayerRow(
            icon = MiuixIcons.Layers,
            title = stringResource(R.string.kernel_patch),
            state = state.capability.kernelPatch,
        )
        Spacer(Modifier.height(14.dp))
        RuntimeLayerRow(
            icon = MiuixIcons.GridView,
            title = stringResource(R.string.android_patch),
            state = state.capability.androidPatch,
        )
    }
}

@Composable
private fun RuntimeLayerRow(
    icon: ImageVector,
    title: String,
    state: RootLayerState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = when (state) {
                RootLayerState.AVAILABLE -> MiuixTheme.colorScheme.primary
                RootLayerState.NEED_UPDATE,
                RootLayerState.NEED_REBOOT,
                -> MiuixTheme.colorScheme.secondaryVariant

                RootLayerState.ERROR,
                RootLayerState.BLOCKED,
                -> MiuixTheme.colorScheme.error

                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            text = stringResource(state.labelRes()),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AdvancedDetailsCard(
    state: HomeUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onRemoveAndroidPatch: () -> Unit,
    onUninstallAll: () -> Unit,
) {
    val details = state.capability.details
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_advanced_details),
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
            )
            Icon(
                imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DetailRow(
                    label = stringResource(R.string.home_root_mode),
                    value = stringResource(state.rootModeLabelRes()),
                )
                DetailRow(
                    label = stringResource(R.string.home_selinux_status),
                    value = stringResource(
                        state.environment?.selinuxStatus?.labelRes()
                            ?: R.string.home_selinux_status_unknown
                    ),
                )
                DetailRow(
                    label = stringResource(R.string.home_su_path),
                    value = when (details.suPathState) {
                        RootDetailState.AVAILABLE -> details.suPath
                            ?: stringResource(R.string.home_layer_unknown)

                        RootDetailState.ERROR -> stringResource(R.string.home_layer_error)
                        RootDetailState.UNAVAILABLE -> stringResource(R.string.home_layer_unavailable)
                        RootDetailState.UNKNOWN -> stringResource(R.string.home_layer_unknown)
                    },
                )
                if (details.androidPatchVersionState != RootDetailState.UNKNOWN) {
                    DetailRow(
                        label = stringResource(R.string.home_apatch_version),
                        value = when (details.androidPatchVersionState) {
                            RootDetailState.AVAILABLE -> details.androidPatchVersion?.toString()
                                ?: stringResource(R.string.home_layer_unknown)

                            RootDetailState.ERROR -> stringResource(R.string.home_layer_error)
                            RootDetailState.UNAVAILABLE ->
                                stringResource(R.string.home_layer_unavailable)

                            RootDetailState.UNKNOWN -> stringResource(R.string.home_layer_unknown)
                        },
                    )
                }
                state.environment?.let { environment ->
                    DetailRow(
                        label = stringResource(R.string.home_manager_version),
                        value = "${environment.managerVersionName} (${environment.managerVersionCode})",
                    )
                    DetailRow(
                        label = stringResource(R.string.home_fingerprint),
                        value = environment.fingerprint,
                    )
                }

                UpdateCheckRow(
                    update = state.update,
                    onClick = onCheckUpdates,
                )

                if (state.canShowSecurityActions()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_security_actions),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (state.canRemoveAndroidPatch()) {
                        TextButton(
                            text = stringResource(R.string.home_remove_android_patch),
                            onClick = onRemoveAndroidPatch,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    }
                    TextButton(
                        text = stringResource(R.string.home_dialog_uninstall_all),
                        onClick = onUninstallAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            textColor = MiuixTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckRow(
    update: HomeUpdateState,
    onClick: () -> Unit,
) {
    val label = when (update) {
        HomeUpdateState.Checking -> stringResource(R.string.home_update_checking)
        HomeUpdateState.Failed -> stringResource(R.string.home_update_failed)
        HomeUpdateState.UpToDate -> stringResource(R.string.home_update_up_to_date)
        HomeUpdateState.Disabled -> stringResource(R.string.home_update_check)
        HomeUpdateState.Idle,
        is HomeUpdateState.Available,
        -> stringResource(R.string.home_update_check)
    }
    TextButton(
        text = label,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = update !is HomeUpdateState.Checking,
        colors = ButtonDefaults.textButtonColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun LearnMoreCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(18.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Help,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_learn_apatch),
                    style = MiuixTheme.textStyles.body1,
                )
                Text(
                    text = stringResource(R.string.home_click_to_learn_apatch),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun UninstallDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRemoveAndroidPatch: () -> Unit,
    onUninstallAll: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.home_dialog_uninstall_title),
        summary = stringResource(R.string.home_dialog_uninstall_message),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onRemoveAndroidPatch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_dialog_uninstall_ap_only))
            }
            Button(
                onClick = onUninstallAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.home_dialog_uninstall_all))
            }
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RebootConfirmationDialog(
    show: Boolean,
    download: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(
            if (download) R.string.reboot_download else R.string.reboot_edl
        ),
        summary = stringResource(
            if (download) R.string.reboot_download_confirm else R.string.reboot_edl_confirm
        ),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.reboot))
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    show: Boolean,
    update: HomeUpdateState.Available,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.home_update_available_title, update.versionCode),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = update.changelog.ifBlank {
                        stringResource(R.string.home_update_available_summary)
                    },
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.apm_update))
                }
            }
        }
    }
}

@Composable
private fun homeStatusColors(conclusion: HomeConclusion): CardColors = when (conclusion) {
    HomeConclusion.FULL_APATCH -> CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.primaryContainer,
        contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
    )

    HomeConclusion.CHECK_FAILED -> CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.errorContainer,
        contentColor = MiuixTheme.colorScheme.onErrorContainer,
    )

    HomeConclusion.NEED_UPDATE,
    HomeConclusion.NEED_REBOOT,
    -> CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.secondaryContainer,
        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
    )

    else -> CardDefaults.defaultColors()
}

private fun HomeConclusion.icon(): ImageVector = when (this) {
    HomeConclusion.CHECKING -> MiuixIcons.Refresh
    HomeConclusion.CHECK_FAILED -> MiuixIcons.Info
    HomeConclusion.NOT_INSTALLED -> MiuixIcons.Lock
    HomeConclusion.KERNEL_PATCH_ONLY -> MiuixIcons.Layers
    HomeConclusion.FULL_APATCH -> MiuixIcons.Ok
    HomeConclusion.NEED_UPDATE -> MiuixIcons.Update
    HomeConclusion.NEED_REBOOT -> MiuixIcons.Reset
    HomeConclusion.BUSY -> MiuixIcons.Refresh
    HomeConclusion.JAILBREAK -> MiuixIcons.Unlock
    HomeConclusion.UNKNOWN -> MiuixIcons.Help
}

@StringRes
private fun HomeConclusion.titleRes(): Int = when (this) {
    HomeConclusion.CHECKING -> R.string.home_status_checking
    HomeConclusion.CHECK_FAILED -> R.string.home_status_check_failed
    HomeConclusion.NOT_INSTALLED -> R.string.home_status_not_installed
    HomeConclusion.KERNEL_PATCH_ONLY -> R.string.home_status_kpatch_only
    HomeConclusion.FULL_APATCH -> R.string.home_status_full
    HomeConclusion.NEED_UPDATE -> R.string.home_status_need_update
    HomeConclusion.NEED_REBOOT -> R.string.home_status_need_reboot
    HomeConclusion.BUSY -> R.string.home_status_busy
    HomeConclusion.JAILBREAK -> R.string.home_status_jailbreak
    HomeConclusion.UNKNOWN -> R.string.home_status_unknown
}

@StringRes
private fun HomeConclusion.summaryRes(): Int = when (this) {
    HomeConclusion.CHECKING -> R.string.home_status_checking_summary
    HomeConclusion.CHECK_FAILED -> R.string.home_status_check_failed_summary
    HomeConclusion.NOT_INSTALLED -> R.string.home_status_not_installed_summary
    HomeConclusion.KERNEL_PATCH_ONLY -> R.string.home_status_kpatch_only_summary
    HomeConclusion.FULL_APATCH -> R.string.home_status_full_summary
    HomeConclusion.NEED_UPDATE -> R.string.home_status_need_update_summary
    HomeConclusion.NEED_REBOOT -> R.string.home_status_need_reboot_summary
    HomeConclusion.BUSY -> R.string.home_status_busy_summary
    HomeConclusion.JAILBREAK -> R.string.home_status_jailbreak_summary
    HomeConclusion.UNKNOWN -> R.string.home_status_unknown_summary
}

@StringRes
private fun HomePrimaryAction.labelRes(): Int = when (this) {
    HomePrimaryAction.NONE -> R.string.home_advanced_details
    HomePrimaryAction.RETRY_CHECK -> R.string.home_action_retry
    HomePrimaryAction.INSTALL_KERNEL_PATCH -> R.string.home_ap_cando_install
    HomePrimaryAction.UPDATE_KERNEL_PATCH -> R.string.home_ap_cando_update
    HomePrimaryAction.INSTALL_APATCH -> R.string.home_ap_cando_install
    HomePrimaryAction.UPDATE_APATCH -> R.string.home_ap_cando_update
    HomePrimaryAction.REBOOT -> R.string.home_ap_cando_reboot
    HomePrimaryAction.SOFT_REBOOT -> R.string.reboot_soft
}

@StringRes
private fun RootLayerState.labelRes(): Int = when (this) {
    RootLayerState.UNKNOWN -> R.string.home_layer_unknown
    RootLayerState.CHECKING -> R.string.home_layer_checking
    RootLayerState.UNAVAILABLE -> R.string.home_layer_unavailable
    RootLayerState.AVAILABLE -> R.string.home_layer_ready
    RootLayerState.NEED_UPDATE -> R.string.home_layer_need_update
    RootLayerState.NEED_REBOOT -> R.string.home_layer_need_reboot
    RootLayerState.BUSY -> R.string.home_layer_busy
    RootLayerState.ERROR -> R.string.home_layer_error
    RootLayerState.BLOCKED -> R.string.home_layer_blocked
}

@StringRes
private fun HomeSelinuxStatus.labelRes(): Int = when (this) {
    HomeSelinuxStatus.UNKNOWN -> R.string.home_selinux_status_unknown
    HomeSelinuxStatus.ENFORCING -> R.string.home_selinux_status_enforcing
    HomeSelinuxStatus.PERMISSIVE -> R.string.home_selinux_status_permissive
    HomeSelinuxStatus.DISABLED -> R.string.home_selinux_status_disabled
}

private fun HomeUiState.rootModeLabelRes(): Int = when {
    environment?.jailbreakActive == true || capability.mode == RootMode.JAILBREAK ->
        R.string.home_mode_jailbreak

    capability.mode == RootMode.NONE -> R.string.home_mode_none
    capability.mode == RootMode.KERNEL_PATCH_ONLY -> R.string.home_mode_kpatch_only
    capability.mode == RootMode.FULL_APATCH -> R.string.home_mode_full
    else -> R.string.home_mode_unknown
}

private fun HomeUiState.canRemoveAndroidPatch(): Boolean = when (capability.androidPatch) {
    RootLayerState.AVAILABLE,
    RootLayerState.NEED_UPDATE,
    -> true

    else -> false
}

private fun HomeUiState.canShowSecurityActions(): Boolean = when (capability.kernelPatch) {
    RootLayerState.AVAILABLE,
    RootLayerState.NEED_UPDATE,
    RootLayerState.NEED_REBOOT,
    -> true

    else -> false
}
