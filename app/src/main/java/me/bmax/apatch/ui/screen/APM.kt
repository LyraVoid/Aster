package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ExecuteAPMActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.WebUIActivity
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.module.APModuleContentState
import me.bmax.apatch.ui.module.MetaModuleWarning
import me.bmax.apatch.ui.module.resolveAPModuleContentState
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.util.download
import me.bmax.apatch.util.isJailbreakMode
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.undoRemoveModule
import me.bmax.apatch.util.uninstallModule
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Destination<RootGraph>
@Composable
fun APModuleScreen(navigator: DestinationsNavigator) {
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    if (state != APApplication.State.ANDROIDPATCH_INSTALLED && state != APApplication.State.ANDROIDPATCH_NEED_UPDATE) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.apm_not_installed),
                style = MiuixTheme.textStyles.body2,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val viewModel = viewModel<APModuleViewModel>()
    val modules = viewModel.moduleList
    val scrollBehavior = MiuixScrollBehavior()
    val moduleListState = rememberLazyListState()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (modules.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchModuleList()
        }
    }

    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.fetchModuleList()
    }
    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        Log.i("ModuleScreen", "select zip result: $uri")
        navigator.navigate(InstallScreenDestination(uri, MODULE_TYPE.APM))
        viewModel.markNeedRefresh()
    }
    val launchZipPicker = {
        selectZipLauncher.launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
            },
        )
    }

    Scaffold(
        topBar = {
            APModuleTopBar(
                moduleCount = modules.size,
                enabledCount = modules.count { it.enabled },
                isLoadingInitial = viewModel.isRefreshing && modules.isEmpty(),
                searchText = viewModel.search,
                searchExpanded = searchExpanded,
                onSearchTextChange = { viewModel.search = it },
                onSearchExpandedChange = { expanded ->
                    searchExpanded = expanded
                    if (!expanded) {
                        viewModel.search = ""
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!viewModel.isMagiskPresent) {
                FloatingActionButton(
                    onClick = launchZipPicker,
                    containerColor = MiuixTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.apm_install),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackBarHost) },
    ) { innerPadding ->
        if (viewModel.isMagiskPresent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                APModuleNoticeCard(
                    message = stringResource(R.string.apm_magisk_conflict),
                    icon = MiuixIcons.Info,
                )
            }
        } else {
            APModuleList(
                viewModel = viewModel,
                modules = modules,
                listState = moduleListState,
                contentPadding = innerPadding,
                scrollBehavior = scrollBehavior,
                snackBarHost = snackBarHost,
                onInstallModule = {
                    navigator.navigate(InstallScreenDestination(it, MODULE_TYPE.APM))
                },
                onOpenWebUi = { id, name ->
                    webUILauncher.launch(
                        Intent(context, WebUIActivity::class.java)
                            .setData("apatch://webui/$id".toUri())
                            .putExtra("id", id)
                            .putExtra("name", name),
                    )
                },
                onOpenAction = { id ->
                    navigator.navigate(ExecuteAPMActionScreenDestination(id))
                    viewModel.markNeedRefresh()
                },
            )
        }
    }
}

@Composable
private fun APModuleTopBar(
    moduleCount: Int,
    enabledCount: Int,
    isLoadingInitial: Boolean,
    searchText: String,
    searchExpanded: Boolean,
    onSearchTextChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    TopAppBar(
        title = stringResource(R.string.apm),
        subtitle = if (isLoadingInitial) {
            ""
        } else {
            stringResource(R.string.apm_module_summary, moduleCount, enabledCount)
        },
        scrollBehavior = scrollBehavior,
        bottomContent = {
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                inputField = {
                    InputField(
                        query = searchText,
                        onQueryChange = onSearchTextChange,
                        onSearch = { onSearchExpandedChange(false) },
                        expanded = searchExpanded,
                        onExpandedChange = onSearchExpandedChange,
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = onSearchExpandedChange,
                content = {},
            )
        },
    )
}

@Composable
private fun APModuleList(
    viewModel: APModuleViewModel,
    modules: List<APModuleViewModel.ModuleInfo>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    snackBarHost: SnackbarHostState,
    onInstallModule: (Uri) -> Unit,
    onOpenWebUi: (id: String, name: String) -> Unit,
    onOpenAction: (id: String) -> Unit,
) {
    val failedEnable = stringResource(R.string.apm_failed_to_enable)
    val failedDisable = stringResource(R.string.apm_failed_to_disable)
    val failedUninstall = stringResource(R.string.apm_uninstall_failed)
    val failedUndoUninstall = stringResource(R.string.apm_module_undo_uninstall_failed)
    val successUninstall = stringResource(R.string.apm_uninstall_success)
    val successUndoUninstall = stringResource(R.string.apm_module_undo_uninstall_success)
    val rebootMessage = stringResource(R.string.reboot)
    val rebootToApply = stringResource(R.string.apm_reboot_to_apply)
    val moduleTitle = stringResource(R.string.apm)
    val uninstall = stringResource(R.string.apm_remove)
    val cancel = stringResource(android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(R.string.apm_uninstall_confirm)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.apm_update)
    val changelogText = stringResource(R.string.apm_changelog)
    val downloadingText = stringResource(R.string.apm_downloading)
    val startDownloadingText = stringResource(R.string.apm_start_downloading)
    val metaModuleWarningText = when (viewModel.metaModuleWarning) {
        MetaModuleWarning.NOT_INSTALLED -> stringResource(R.string.no_meta_module_installed)
        MetaModuleWarning.PENDING_REMOVAL -> stringResource(R.string.meta_module_removed)
        MetaModuleWarning.DISABLED -> stringResource(R.string.meta_module_disabled)
        null -> null
    }
    val contentState = resolveAPModuleContentState(
        isRefreshing = viewModel.isRefreshing,
        hasLoadError = viewModel.hasLoadError,
        moduleCount = modules.size,
        isSearching = viewModel.search.isNotBlank(),
    )

    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    var warningDismissed by rememberSaveable { mutableStateOf(false) }

    suspend fun onModuleUpdate(
        module: APModuleViewModel.ModuleInfo,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String,
    ) {
        val changelog = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (Patterns.WEB_URL.matcher(changelogUrl).matches()) {
                        apApp.okhttpClient.newCall(
                            Request.Builder().url(changelogUrl).build(),
                        ).execute().use { it.body.string() }
                    } else {
                        changelogUrl
                    }
                }.getOrDefault("")
            }
        }

        if (changelog.isNotEmpty()) {
            val confirmResult = confirmDialog.awaitConfirm(
                changelogText,
                content = changelog,
                markdown = true,
                confirm = updateText,
            )
            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                startDownloadingText.format(module.name),
                Toast.LENGTH_SHORT,
            ).show()
        }

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                downloadUrl,
                fileName,
                downloading,
                onDownloaded = onInstallModule,
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    suspend fun onModuleUninstall(module: APModuleViewModel.ModuleInfo) {
        val formatter = if (module.metamodule) {
            metaModuleUninstallConfirm
        } else {
            moduleUninstallConfirm
        }
        val confirmResult = confirmDialog.awaitConfirm(
            moduleTitle,
            content = formatter.format(module.name),
            confirm = uninstall,
            dismiss = cancel,
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val success = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                uninstallModule(module.id)
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
        val message = if (success) {
            successUninstall.format(module.name)
        } else {
            failedUninstall.format(module.name)
        }
        val result = snackBarHost.showSnackbar(
            message = message,
            actionLabel = if (success) rebootMessage else null,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
        }
    }

    suspend fun onUndoModuleUninstall(module: APModuleViewModel.ModuleInfo) {
        val success = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                undoRemoveModule(module.id)
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
        val message = if (success) {
            successUndoUninstall.format(module.name)
        } else {
            failedUndoUninstall.format(module.name)
        }
        val result = snackBarHost.showSnackbar(
            message = message,
            actionLabel = if (success) rebootMessage else null,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
        }
    }

    PullToRefresh(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::fetchModuleList,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
        refreshTexts = listOf(
            stringResource(R.string.refresh_pulling),
            stringResource(R.string.refresh_release),
            stringResource(R.string.refresh_refreshing),
            stringResource(R.string.refresh_complete),
        ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
                start = 4.dp,
                end = 4.dp,
            ),
        ) {
            if (metaModuleWarningText != null && !warningDismissed) {
                item(key = "meta-module-warning") {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        MetaModuleWarningCard(
                            text = metaModuleWarningText,
                            onClosed = { warningDismissed = true },
                        )
                    }
                }
            }

            if (viewModel.hasLoadError && modules.isNotEmpty()) {
                item(key = "load-error") {
                    APModuleLoadErrorCard(onRetry = viewModel::fetchModuleList)
                }
            }

            when (contentState) {
                APModuleContentState.LOADING -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            APModuleLoadingState()
                        }
                    }
                }

                APModuleContentState.ERROR -> {
                    item(key = "error") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            APModuleLoadErrorCard(onRetry = viewModel::fetchModuleList)
                        }
                    }
                }

                APModuleContentState.EMPTY,
                APModuleContentState.EMPTY_SEARCH,
                -> {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            APModuleEmptyState(isSearching = contentState == APModuleContentState.EMPTY_SEARCH)
                        }
                    }
                }

                APModuleContentState.CONTENT -> {
                    items(modules, key = { it.id }) { module ->
                        var isChecked by rememberSaveable(
                            module.id,
                            module.enabled,
                            module.remove,
                        ) {
                            mutableStateOf(module.enabled)
                        }
                        val scope = rememberCoroutineScope()

                        APModuleCard(
                            module = module,
                            checked = isChecked,
                            updateAvailable = module.updateInfo != null,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val success = loadingDialog.withLoading {
                                        withContext(Dispatchers.IO) {
                                            toggleModule(module.id, checked)
                                        }
                                    }
                                    if (success) {
                                        isChecked = checked
                                        viewModel.fetchModuleList()

                                        if (!withContext(Dispatchers.IO) { isJailbreakMode() }) {
                                            val result = snackBarHost.showSnackbar(
                                                message = rebootToApply,
                                                actionLabel = rebootMessage,
                                                duration = SnackbarDuration.Long,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                reboot()
                                            }
                                        }
                                    } else {
                                        val message = if (isChecked) failedDisable else failedEnable
                                        snackBarHost.showSnackbar(message.format(module.name))
                                    }
                                }
                            },
                            onOpen = {
                                onOpenWebUi(module.id, module.name)
                            },
                            onUpdate = {
                                module.updateInfo?.let { updateInfo ->
                                    scope.launch {
                                        onModuleUpdate(
                                            module,
                                            updateInfo.changelog,
                                            updateInfo.zipUrl,
                                            "${module.name}-${updateInfo.version}.zip",
                                        )
                                    }
                                }
                            },
                            onAction = {
                                onOpenAction(module.id)
                            },
                            onUninstall = {
                                scope.launch { onModuleUninstall(module) }
                            },
                            onUndoUninstall = {
                                scope.launch { onUndoModuleUninstall(module) }
                            },
                        )
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        DownloadListener(context, onInstallModule)
    }
}
