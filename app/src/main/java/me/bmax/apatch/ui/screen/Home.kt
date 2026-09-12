package me.bmax.apatch.ui.screen

import me.bmax.apatch.ui.home.needsRootAccess
import top.yukonga.miuix.kmp.basic.PullToRefresh

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.input.pointer.pointerInput

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.APModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KPModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.time.LocalDate
import java.time.LocalTime
import me.bmax.apatch.R
import me.bmax.apatch.ui.theme.LocalThemeModeState
import me.bmax.apatch.util.Version
import me.bmax.apatch.root.RootAccessProbeState
import me.bmax.apatch.root.RootDetailState
import me.bmax.apatch.root.RootLayerState
import me.bmax.apatch.root.RootMode
import me.bmax.apatch.root.isUsable
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
import me.bmax.apatch.ui.home.HomeWallpaperImage
import me.bmax.apatch.ui.home.LocalHomeWallpaperViewModel
import me.bmax.apatch.ui.home.HomeWallpaperMaxZoom
import me.bmax.apatch.ui.home.HomeWallpaperMinZoom
import me.bmax.apatch.ui.home.HomeWallpaperPhase
import me.bmax.apatch.ui.home.HomeWallpaperState
import me.bmax.apatch.ui.home.HomeWallpaperViewModel
import me.bmax.apatch.ui.home.androidVersion
import me.bmax.apatch.ui.home.displayName
import me.bmax.apatch.ui.shell.LocalHomeSceneHostState
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.shell.setGlobalLayout
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
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
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val viewModel: HomeViewModel = viewModel()
    // The shell paints the scene backdrop from the same instance, so it is hoisted to the activity
    // store and provided through a composition local.
    val wallpaperViewModel: HomeWallpaperViewModel =
        LocalHomeWallpaperViewModel.current ?: viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wallpaperState by wallpaperViewModel.uiState.collectAsStateWithLifecycle()
    val globalLayout by rememberGlobalLayout()
    val sceneMode = globalLayout == GlobalLayout.Panorama
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sceneHostState = LocalHomeSceneHostState.current

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

    // Panorama mode owns the scene, so an image picked earlier while the mode was off must not
    // stay in the store's disabled state.
    LaunchedEffect(sceneMode, wallpaperState.phase) {
        if (sceneMode &&
            wallpaperState.hasImage &&
            wallpaperState.phase == HomeWallpaperPhase.DISABLED
        ) {
            wallpaperViewModel.setEnabled(true)
        }
    }

    DisposableEffect(sceneHostState) {
        sceneHostState?.openAppearance = { showWallpaperSheet = true }
        onDispose { sceneHostState?.openAppearance = null }
    }

    val onInstallClick = dropUnlessResumed {
        navigator.navigate(InstallModeSelectScreenDestination)
    }
    val onApmClick = dropUnlessResumed {
        navigator.navigate(APModuleScreenDestination)
    }
    val onKpmClick = dropUnlessResumed {
        navigator.navigate(KPModuleScreenDestination)
    }
    val onUninstallClick = {
        showMore = false
        showReboot = false
        showUninstallDialog = true
    }
    val onMainCardClick = dropUnlessResumed {
        when (state.conclusion) {
            HomeConclusion.NOT_INSTALLED,
            HomeConclusion.NEED_UPDATE -> navigator.navigate(InstallModeSelectScreenDestination)

            HomeConclusion.NEED_REBOOT -> viewModel.reboot()
            HomeConclusion.CHECK_FAILED -> viewModel.refreshCapabilities()
            else -> showUninstallDialog = true
        }
    }

    Scaffold(containerColor = Color.Transparent) {
    Box(Modifier.fillMaxSize()) {
        if (sceneMode) {
            HomeScenePanel(
                state = state,
                wallpaperState = wallpaperState,
                showMore = showMore,
                onShowMoreChange = { showMore = it },
                showReboot = showReboot,
                onShowRebootChange = { showReboot = it },
                onInstallClick = {
                    showMore = false
                    onInstallClick()
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
                onApmClick = onApmClick,
                onKpmClick = onKpmClick,
                onRefresh = {
                    viewModel.refreshCapabilities()
                    Toast.makeText(context, R.string.home_refresh_requested, Toast.LENGTH_SHORT).show()
                },
                onUninstallClick = onUninstallClick,
                onInstallApatch = viewModel::installApatch,
                onUninstallApatch = viewModel::uninstallApatch,
                onDismissBackupWarning = viewModel::dismissBackupWarning,
                onUpdateClick = { showUpdateDialog = true },
                onLearnMore = { uriHandler.openUri("https://apatch.dev") },
            )
        } else {
            Scaffold(
                topBar = {
                    HomeTopBar(
                        canReboot = state.capability.rootAccess == RootAccessProbeState.AVAILABLE,
                        showMore = showMore,
                        showReboot = showReboot,
                        onShowMoreChange = { showMore = it },
                        onShowRebootChange = { showReboot = it },
                        onUninstallClick = onUninstallClick,
                        onAppearance = { showWallpaperSheet = true },
                        onInstallClick = {
                            showMore = false
                            onInstallClick()
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = me.bmax.apatch.ui.shell.LocalFloatingNavigationInset.current),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.showBackupWarning) {
                        BackupWarningCard(onDismiss = viewModel::dismissBackupWarning)
                    }

                    KStatusCard(
                        state = state,
                        onMainCardClick = onMainCardClick,
                        onApmClick = onApmClick,
                        onKpmClick = onKpmClick,
                    )

                    if (state.conclusion != HomeConclusion.NOT_INSTALLED &&
                        state.conclusion != HomeConclusion.CHECKING &&
                        state.capability.androidPatch != RootLayerState.AVAILABLE
                    ) {
                        AStatusCard(
                            state = state,
                            onInstall = viewModel::installApatch,
                            onUninstall = viewModel::uninstallApatch,
                        )
                    }

                    val availableUpdate = state.update as? HomeUpdateState.Available
                    if (availableUpdate != null) {
                        UpdateAvailableCard(
                            update = availableUpdate,
                            onClick = { showUpdateDialog = true },
                        )
                    }

                    DeviceInfoCard(state = state)

                    LearnMoreCard(onClick = { uriHandler.openUri("https://apatch.dev") })
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        HomeWallpaperSheet(
            show = showWallpaperSheet,
            state = wallpaperState,
            panoramaMode = sceneMode,
            onDismissRequest = { showWallpaperSheet = false },
            onPanoramaChange = { enabled ->
                setGlobalLayout(
                    if (enabled) GlobalLayout.Panorama else GlobalLayout.Standard
                )
            },
            onChooseImage = launchWallpaperPicker,
            onRemoveImage = wallpaperViewModel::removeImage,
            onCropChange = wallpaperViewModel::saveCrop,
        )

        if (showUninstallDialog) {
            UninstallDialog(
                show = true,
                state = state,
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
private fun HomeScenePanel(
    state: HomeUiState,
    wallpaperState: HomeWallpaperState,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    showReboot: Boolean,
    onShowRebootChange: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
    onApmClick: () -> Unit,
    onKpmClick: () -> Unit,
    onRefresh: () -> Unit,
    onUninstallClick: () -> Unit,
    onInstallApatch: () -> Unit,
    onUninstallApatch: () -> Unit,
    onDismissBackupWarning: () -> Unit,
    onUpdateClick: () -> Unit,
    onLearnMore: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val canReboot = state.capability.rootAccess == RootAccessProbeState.AVAILABLE
    val primaryAction = {
        when (state.primaryAction) {
            HomePrimaryAction.INSTALL_KERNEL_PATCH,
            HomePrimaryAction.UPDATE_KERNEL_PATCH -> onInstallClick()
            HomePrimaryAction.INSTALL_APATCH,
            HomePrimaryAction.UPDATE_APATCH -> onInstallApatch()
            HomePrimaryAction.RETRY_CHECK -> onRefresh()
            HomePrimaryAction.REBOOT -> onShowRebootChange(true)
            HomePrimaryAction.SOFT_REBOOT -> onDangerousReboot("soft")
            HomePrimaryAction.NONE -> Unit
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sceneProgress = me.bmax.apatch.ui.shell.LocalSceneProgress.current
        val sidebarExpanded by me.bmax.apatch.ui.shell.rememberVisualFlag("scene_sidebar_expanded", true)
        val toggleSidebar = { me.bmax.apatch.ui.shell.setVisualFlag("scene_sidebar_expanded", !sidebarExpanded) }
        val heroHeight = ((maxHeight - topInset) * if (maxHeight < 480.dp) 0.50f else 0.63f).coerceAtLeast(300.dp)

        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
                .clip(RoundedCornerShape(topStart = 26.dp * sceneProgress, bottomStart = 26.dp * sceneProgress))
                .background(MiuixTheme.colorScheme.background),
        ) {
            PullToRefresh(
                isRefreshing = state.conclusion == HomeConclusion.CHECKING,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                refreshTexts = listOf(stringResource(R.string.refresh_pulling), stringResource(R.string.refresh_release), stringResource(R.string.refresh_refreshing), stringResource(R.string.refresh_complete)),
            ) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .pointerInput(sidebarExpanded) {
                            detectTapGestures(onDoubleTap = { toggleSidebar() })
                        }
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                ) {
                    if (!wallpaperState.isReady) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MiuixTheme.colorScheme.primaryContainer,
                                            MiuixTheme.colorScheme.secondaryContainer,
                                        )
                                    )
                                )
                        )
                        Text(
                            text = stringResource(R.string.home_wallpaper_empty),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 28.dp),
                        )
                    }
                    HomeWallpaperImage(
                        state = wallpaperState,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    // Fade the photo into the panel colour instead of a black
                                    // scrim, so the status strip stays readable on any wallpaper.
                                    0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    0.78f to MiuixTheme.colorScheme.background.copy(alpha = 0.88f),
                                    0.90f to MiuixTheme.colorScheme.background,
                                    1f to MiuixTheme.colorScheme.background,
                                )
                            )
                    )
                    IconButton(
                        onClick = toggleSidebar,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
                        backgroundColor = Color.Transparent,
                    ) {
                        Icon(
                            painter = painterResource(if (sidebarExpanded) R.drawable.wallpaper_expand else R.drawable.wallpaper_collapse),
                            contentDescription = stringResource(if (sidebarExpanded) R.string.scene_expand else R.string.scene_restore),
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SceneRootStatus(
                            state = state,
                            onTools = { onShowMoreChange(true) },
                        )
                        SceneStatusStrip(state = state)
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 18.dp),
                ) {
                    Text(
                        text = listOfNotNull(
                            sceneGreeting(),
                            state.environment?.displayName()?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sceneQuote(),
                        style = MiuixTheme.textStyles.title3,
                    )
                    Spacer(Modifier.height(18.dp))
                    if (state.primaryAction != HomePrimaryAction.NONE) {
                        Button(
                            onClick = primaryAction,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(stringResource(state.primaryAction.labelRes()))
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.showBackupWarning) {
                        BackupWarningCard(onDismiss = onDismissBackupWarning)
                    }

                    if (state.conclusion != HomeConclusion.NOT_INSTALLED &&
                        state.conclusion != HomeConclusion.CHECKING &&
                        state.capability.androidPatch != RootLayerState.AVAILABLE
                    ) {
                        AStatusCard(
                            state = state,
                            onInstall = onInstallApatch,
                            onUninstall = onUninstallApatch,
                        )
                    }

                    val availableUpdate = state.update as? HomeUpdateState.Available
                    if (availableUpdate != null) {
                        UpdateAvailableCard(
                            update = availableUpdate,
                            onClick = onUpdateClick,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ModuleCountCard(
                            label = stringResource(R.string.apm),
                            count = state.apmCount,
                            onClick = onApmClick,
                            modifier = Modifier.weight(1f),
                        )
                        ModuleCountCard(
                            label = stringResource(R.string.kpm),
                            count = state.kpmCount,
                            onClick = onKpmClick,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    DeviceInfoCard(state = state)
                    LearnMoreCard(onClick = onLearnMore)
                    Spacer(Modifier.height(bottomInset + 24.dp))
                }
            }
            }
        }

        HomeActionsSheet(
            showMore = showMore,
            showReboot = showReboot,
            onShowMoreChange = onShowMoreChange,
            onShowRebootChange = onShowRebootChange,
            canReboot = canReboot,
            onUninstallClick = onUninstallClick,
            onInstallClick = onInstallClick,
            onFeedback = onFeedback,
            onAbout = onAbout,
            onReboot = onReboot,
            onDangerousReboot = onDangerousReboot,
        )
    }
}

@Composable
private fun SceneStatusStrip(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val managerVersion = remember { Version.getManagerVersion() }
    val installedKpatchVersion = remember(state.conclusion) {
        runCatching { Version.installedKPVString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "0" }
    }
    val kpatchValue = when (state.conclusion) {
        HomeConclusion.FULL_APATCH,
        HomeConclusion.KERNEL_PATCH_ONLY -> installedKpatchVersion
            ?: stringResource(R.string.kpatch_version, managerVersion.first)

        HomeConclusion.NEED_UPDATE -> "${Version.installedKPVString()} → ${Version.buildKPVString()}"
        HomeConclusion.NOT_INSTALLED -> stringResource(R.string.home_not_installed)
        HomeConclusion.NEED_REBOOT -> stringResource(R.string.home_ap_cando_reboot)
        HomeConclusion.CHECKING -> stringResource(R.string.home_status_checking)
        else -> stringResource(R.string.home_click_to_install)
    }

    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SceneStatusColumn(
            title = stringResource(R.string.kernel_patch),
            value = kpatchValue,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .width(1.dp)
                .height(30.dp)
                .background(MiuixTheme.colorScheme.dividerLine)
        )
        SceneStatusColumn(
            title = stringResource(R.string.android_patch),
            value = stringResource(state.capability.androidPatch.labelRes()),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SceneStatusColumn(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = title.uppercase(),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SceneRootStatus(state: HomeUiState, onTools: () -> Unit) {
    val needsAccess = state.needsRootAccess()
    val statusColor = when {
        needsAccess || state.conclusion == HomeConclusion.CHECK_FAILED -> MiuixTheme.colorScheme.error
        state.conclusion == HomeConclusion.FULL_APATCH ||
            state.conclusion == HomeConclusion.KERNEL_PATCH_ONLY -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(statusColor.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (needsAccess) MiuixIcons.Lock else state.conclusion.icon(),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = statusColor,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(when {
                    needsAccess -> R.string.home_root_access_missing
                    state.conclusion == HomeConclusion.FULL_APATCH -> R.string.home_scene_working
                    else -> state.conclusion.titleRes()
                }),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            // The two patch values below already explain the healthy state. Only exceptions
            // need a second paragraph; never substitute a detected patch for usable root.
            if (needsAccess || state.conclusion != HomeConclusion.FULL_APATCH) {
                Text(
                    text = stringResource(if (needsAccess) R.string.home_root_access_missing_summary else state.conclusion.summaryRes()),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        IconButton(onClick = onTools, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = MiuixIcons.More,
                contentDescription = stringResource(R.string.home_more),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun sceneGreeting(): String {
    val hour = LocalTime.now().hour
    val res = when (hour) {
        in 5..11 -> R.string.home_scene_greeting_morning
        in 12..17 -> R.string.home_scene_greeting_afternoon
        in 18..22 -> R.string.home_scene_greeting_evening
        else -> R.string.home_scene_greeting_night
    }
    return stringResource(res)
}

@Composable
private fun sceneQuote(): String {
    val quotes = stringArrayResource(R.array.home_scene_quotes)
    if (quotes.isEmpty()) {
        return ""
    }
    val index = LocalDate.now().dayOfYear % quotes.size
    return quotes[index]
}

@Composable
private fun HomeTopBar(
    canReboot: Boolean,
    showMore: Boolean,
    showReboot: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    onAppearance: () -> Unit,
    onUninstallClick: () -> Unit,
    onInstallClick: () -> Unit,
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

                HomeActionsSheet(
                    showMore = showMore,
                    showReboot = showReboot,
                    onShowMoreChange = onShowMoreChange,
                    onShowRebootChange = onShowRebootChange,
                    canReboot = canReboot,
                    onUninstallClick = onUninstallClick,
                    onInstallClick = onInstallClick,
                    onFeedback = onFeedback,
                    onAbout = onAbout,
                    onReboot = onReboot,
                    onDangerousReboot = onDangerousReboot,
                )
            }
        },
    )
}

@Composable
private fun HomeActionsSheet(
    showMore: Boolean,
    showReboot: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    onShowRebootChange: (Boolean) -> Unit,
    canReboot: Boolean,
    onUninstallClick: () -> Unit,
    onInstallClick: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
) {
    val dismiss = {
        onShowMoreChange(false)
        onShowRebootChange(false)
    }
    WindowBottomSheet(
        show = showMore || showReboot,
        title = stringResource(if (showReboot) R.string.reboot else R.string.home_more),
        onDismissRequest = dismiss,
        startAction = if (showReboot) {
            {
                IconButton(onClick = {
                    onShowMoreChange(true)
                    onShowRebootChange(false)
                }) {
                    Icon(MiuixIcons.Back, stringResource(R.string.home_more))
                }
            }
        } else null,
    ) {
        AnimatedContent(
            targetState = showReboot,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "home_actions",
        ) { rebootPage ->
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (rebootPage) {
                    HomeRebootOptions(onDismiss = dismiss, onReboot = onReboot, onDangerousReboot = onDangerousReboot)
                } else {
                    PopupMenuItem(
                        icon = MiuixIcons.Import,
                        text = stringResource(R.string.mode_select_page_title),
                        onClick = onInstallClick,
                    )
                    if (canReboot) {
                        PopupMenuItem(
                            icon = MiuixIcons.Reset,
                            text = stringResource(R.string.reboot),
                            onClick = { onShowRebootChange(true) },
                        )
                    }
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth().height(1.dp)
                            .background(MiuixTheme.colorScheme.dividerLine)
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
                    PopupMenuItem(
                        icon = MiuixIcons.Delete,
                        text = stringResource(R.string.home_dialog_uninstall_title),
                        onClick = onUninstallClick,
                        destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRebootOptions(
    onDismiss: () -> Unit,
    onReboot: (String) -> Unit,
    onDangerousReboot: (String) -> Unit,
) {
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot),
        onClick = {
            onDismiss()
            onReboot("")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Refresh,
        text = stringResource(R.string.reboot_soft),
        onClick = {
            onDismiss()
            onReboot("soft_reboot")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot_recovery),
        onClick = {
            onDismiss()
            onReboot("recovery")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Reset,
        text = stringResource(R.string.reboot_bootloader),
        onClick = {
            onDismiss()
            onReboot("bootloader")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Download,
        text = stringResource(R.string.reboot_download),
        onClick = {
            onDismiss()
            onDangerousReboot("download")
        },
    )
    PopupMenuItem(
        icon = MiuixIcons.Import,
        text = stringResource(R.string.reboot_edl),
        onClick = {
            onDismiss()
            onDangerousReboot("edl")
        },
    )
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val contentColor = if (!enabled) {
        MiuixTheme.colorScheme.disabledOnSurface
    } else if (destructive) {
        MiuixTheme.colorScheme.error
    } else {
        MiuixTheme.colorScheme.onSurface
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
private fun HomeWallpaperSheet(
    show: Boolean,
    state: HomeWallpaperState,
    panoramaMode: Boolean,
    onDismissRequest: () -> Unit,
    onPanoramaChange: (Boolean) -> Unit,
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
                        text = stringResource(R.string.home_panorama_switch),
                        style = MiuixTheme.textStyles.body1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_panorama_switch_summary),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = panoramaMode,
                    onCheckedChange = onPanoramaChange,
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

                state.phase == HomeWallpaperPhase.MISSING -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_missing),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                state.phase == HomeWallpaperPhase.ERROR -> {
                    Text(
                        text = stringResource(R.string.home_wallpaper_error),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }

                state.hasImage -> {
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
private fun KStatusCard(
    state: HomeUiState,
    onMainCardClick: () -> Unit,
    onApmClick: () -> Unit,
    onKpmClick: () -> Unit,
) {
    val themeMode = LocalThemeModeState.current
    val isDark = themeMode.isDark
    val isMonet = themeMode.isDynamicColor

    val isWorking = state.conclusion == HomeConclusion.FULL_APATCH ||
        state.conclusion == HomeConclusion.KERNEL_PATCH_ONLY

    val cardBg = when {
        isWorking -> when {
            isMonet -> MiuixTheme.colorScheme.primaryContainer
            isDark -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
        state.conclusion == HomeConclusion.NEED_UPDATE -> MiuixTheme.colorScheme.secondaryContainer
        state.conclusion == HomeConclusion.NEED_REBOOT -> MiuixTheme.colorScheme.errorContainer
        else -> MiuixTheme.colorScheme.secondaryContainer
    }

    val decoIconColor = when {
        isWorking -> if (isMonet) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
        } else {
            Color(0xFF36D167)
        }
        state.conclusion == HomeConclusion.NEED_UPDATE -> MiuixTheme.colorScheme.secondary
        state.conclusion == HomeConclusion.NEED_REBOOT -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.outline
    }

    val decoIcon = when {
        state.conclusion == HomeConclusion.NEED_UPDATE -> MiuixIcons.Update
        state.conclusion == HomeConclusion.NEED_REBOOT -> MiuixIcons.Reset
        state.conclusion == HomeConclusion.CHECKING -> MiuixIcons.Refresh
        else -> MiuixIcons.Help
    }

    val titleRes = when {
        isWorking -> R.string.home_working
        state.conclusion == HomeConclusion.NEED_UPDATE -> R.string.home_need_update
        state.conclusion == HomeConclusion.NEED_REBOOT -> R.string.home_ap_cando_reboot
        state.conclusion == HomeConclusion.CHECKING -> R.string.home_status_checking
        state.conclusion == HomeConclusion.CHECK_FAILED -> R.string.home_status_check_failed
        else -> R.string.home_not_installed
    }

    val managerVersion = remember { Version.getManagerVersion() }
    val subtitle = when (state.conclusion) {
        HomeConclusion.FULL_APATCH,
        HomeConclusion.KERNEL_PATCH_ONLY -> stringResource(R.string.kpatch_version, managerVersion.first)
        HomeConclusion.NEED_UPDATE -> "${Version.installedKPVString()} → ${Version.buildKPVString()}"
        HomeConclusion.NOT_INSTALLED -> stringResource(R.string.home_click_to_install)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.defaultColors(color = cardBg),
            onClick = onMainCardClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(38.dp, 45.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    if (isWorking) {
                        Icon(
                            modifier = Modifier.size(170.dp),
                            painter = painterResource(R.drawable.status_check_circle_outline),
                            tint = decoIconColor,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(170.dp),
                            imageVector = decoIcon,
                            tint = decoIconColor,
                            contentDescription = null,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (isWorking) {
                            val mode = if (state.conclusion == HomeConclusion.FULL_APATCH) "<Full>" else "<Half>"
                            "${stringResource(titleRes)} $mode"
                        } else {
                            stringResource(titleRes)
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    if (subtitle != null) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = subtitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModuleCountCard(
                label = stringResource(R.string.apm),
                count = state.apmCount,
                onClick = onApmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            ModuleCountCard(
                label = stringResource(R.string.kpm),
                count = state.kpmCount,
                onClick = onKpmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ModuleCountCard(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AStatusCard(
    state: HomeUiState,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val apLayer = state.capability.androidPatch
    val isInstalled = apLayer == RootLayerState.AVAILABLE
    val needUpdate = apLayer == RootLayerState.NEED_UPDATE
    val isBusy = apLayer == RootLayerState.BUSY

    val icon = when {
        isInstalled -> MiuixIcons.Ok
        needUpdate -> MiuixIcons.Update
        isBusy -> MiuixIcons.Refresh
        else -> MiuixIcons.Close
    }

    val titleRes = when {
        isInstalled -> R.string.home_working
        needUpdate -> R.string.home_need_update
        isBusy -> R.string.home_layer_busy
        else -> R.string.home_not_installed
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isInstalled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.android_patch),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(titleRes),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Button(
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColorsPrimary(),
                onClick = {
                    if (isInstalled) onUninstall() else onInstall()
                },
            ) {
                Text(
                    text = stringResource(
                        if (isInstalled) R.string.home_ap_cando_uninstall
                        else if (needUpdate) R.string.home_ap_cando_update
                        else R.string.home_ap_cando_install
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(state: HomeUiState) {
    val env = state.environment
    val selinuxText = when (env?.selinuxStatus) {
        HomeSelinuxStatus.ENFORCING -> stringResource(R.string.home_selinux_status_enforcing)
        HomeSelinuxStatus.PERMISSIVE -> stringResource(R.string.home_selinux_status_permissive)
        HomeSelinuxStatus.DISABLED -> stringResource(R.string.home_selinux_status_disabled)
        else -> stringResource(R.string.home_selinux_status_unknown)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.capability.details.suPath != null) {
                InfoItem(
                    title = stringResource(R.string.home_su_path),
                    content = state.capability.details.suPath,
                )
            }
            if (env != null) {
                InfoItem(
                    title = stringResource(R.string.home_device_info),
                    content = "${env.brand.replaceFirstChar { it.uppercase() }} ${env.model}",
                )
                InfoItem(
                    title = stringResource(R.string.home_kernel),
                    content = env.kernelRelease,
                )
                InfoItem(
                    title = stringResource(R.string.home_system_version),
                    content = "${env.androidRelease} (API ${env.androidApi})",
                )
                InfoItem(
                    title = stringResource(R.string.home_fingerprint),
                    content = env.fingerprint,
                )
            }
            InfoItem(
                title = stringResource(R.string.home_selinux_status),
                content = selinuxText,
            )
        }
    }
}

@Composable
private fun InfoItem(title: String, content: String) {
    Column {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = content,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
    state: HomeUiState,
    onDismiss: () -> Unit,
    onRemoveAndroidPatch: () -> Unit,
    onUninstallAll: () -> Unit,
) {
    val hasRoot = state.capability.rootAccess == RootAccessProbeState.AVAILABLE
    val canRemoveAndroidPatch = hasRoot && state.capability.androidPatch == RootLayerState.AVAILABLE
    val canUninstallAll = hasRoot && state.capability.kernelPatch.isUsable()
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
                enabled = canRemoveAndroidPatch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_dialog_uninstall_ap_only))
            }
            if (!canRemoveAndroidPatch || !canUninstallAll) {
                Text(
                    text = stringResource(
                        if (!hasRoot) R.string.home_root_access_missing_summary
                        else R.string.home_uninstall_unavailable,
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Button(
                onClick = onUninstallAll,
                enabled = canUninstallAll,
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
