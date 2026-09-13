package me.bmax.apatch.ui.screen

import me.bmax.apatch.ui.shell.LocalMainPagerState
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.OnlineModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.MissingLayerNotice
import me.bmax.apatch.ui.component.LoadingDialogHandle
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.kernelmodule.KPModuleContentState
import me.bmax.apatch.ui.kernelmodule.resolveKPModuleContentState
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.ui.viewmodel.safeKpmModuleId
import me.bmax.apatch.util.inputStream
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.writeTo
import org.ini4j.Ini
import java.io.IOException
import java.io.StringReader
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val TAG = "KernelPatchModule"

/** Installing a kernel module copies it into place, so the store and the pickers take turns. */
internal val kpmInstallMutex = Mutex()

private data class UninstallResult(
    val unloaded: Boolean,
    val removed: Boolean,
)

@Destination<RootGraph>
@Composable
fun KPModuleScreen(navigator: DestinationsNavigator) {
    val mainPagerState = LocalMainPagerState.current
    if (!LocalAsterCapabilities.current.kernelPatchReady) {
        MissingLayerNotice(
            title = stringResource(R.string.su_kernel_patch_required_title),
            description = stringResource(R.string.capability_kernel_patch_required_desc),
            onBackToHome = {
                if (mainPagerState != null) mainPagerState.animateToPage(0)
                else navigator.navigate(HomeScreenDestination)
            },
        )
        return
    }

    val viewModel = viewModel<KPModuleViewModel>()
    val modules = viewModel.moduleList
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val moduleListState = rememberLazyListState()
    val loadingDialog = rememberLoadingDialog()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val moduleLoad = stringResource(R.string.kpm_load)
    val moduleInstall = stringResource(R.string.kpm_install)
    val moduleEmbed = stringResource(R.string.kpm_embed)
    val successToastText = stringResource(R.string.kpm_load_toast_succ)
    val installSuccessToastText = stringResource(R.string.kpm_install_toast_succ)
    val failToastText = stringResource(R.string.kpm_load_toast_failed)

    // Read on every visit: a module can be installed from the store, from the picker, or removed
    // by another app, and the list is the only place that shows the result.
    LaunchedEffect(Unit) {
        viewModel.fetchModuleList()
    }

    val scope = rememberCoroutineScope()
    val selectKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val rc = loadModule(loadingDialog, uri, "")
            val toastText = if (rc == 0) successToastText else "$failToastText: $rc"
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            viewModel.fetchModuleList()
        }
    }
    val selectInstallKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val rc = kpmInstallMutex.withLock { installKpm(uri) }
            val toastText = if (rc == 0) {
                installSuccessToastText
            } else {
                "$failToastText: $rc"
            }
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            viewModel.fetchModuleList()
        }
    }
    val launchLoadPicker = {
        selectKpmLauncher.launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            },
        )
    }
    val launchInstallPicker = {
        selectInstallKpmLauncher.launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            },
        )
    }

    Scaffold(
        topBar = {
            KPModuleTopBar(
                onOpenStore = { navigator.navigate(OnlineModuleScreenDestination(MODULE_TYPE.KPM)) },
                moduleCount = modules.size,
                loadedCount = modules.count { it.loaded },
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
            Box(Modifier.padding(bottom = me.bmax.apatch.ui.shell.LocalFloatingNavigationInset.current)) {
            KPModuleFabMenu(
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                onEmbed = {
                    navigator.navigate(
                        PatchesDestination(PatchesViewModel.PatchMode.PATCH_AND_INSTALL),
                    )
                },
                onInstall = launchInstallPicker,
                onLoad = launchLoadPicker,
            )
            }
        },
    ) { innerPadding ->
        KPModuleList(
            viewModel = viewModel,
            modules = modules,
            listState = moduleListState,
            contentPadding = innerPadding,
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun KPModuleTopBar(
    onOpenStore: () -> Unit,
    moduleCount: Int,
    loadedCount: Int,
    isLoadingInitial: Boolean,
    searchText: String,
    searchExpanded: Boolean,
    onSearchTextChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    TopAppBar(
        title = stringResource(R.string.kpm),
        subtitle = if (isLoadingInitial) {
            ""
        } else {
            stringResource(R.string.kpm_module_summary, moduleCount, loadedCount)
        },
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = onOpenStore) {
                Icon(
                    imageVector = MiuixIcons.Store,
                    contentDescription = stringResource(R.string.online_module_title),
                )
            }
        },
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
private fun KPModuleFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEmbed: () -> Unit,
    onInstall: () -> Unit,
    onLoad: () -> Unit,
) {
    Box {
        FloatingActionButton(
            onClick = { onExpandedChange(true) },
            containerColor = MiuixTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = stringResource(R.string.kpm_add_kpm),
                tint = MiuixTheme.colorScheme.onPrimary,
            )
        }

        OverlayListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            ListPopupColumn {
                KPModuleMenuItem(
                    icon = MiuixIcons.Layers,
                    text = stringResource(R.string.kpm_embed),
                    onClick = {
                        onExpandedChange(false)
                        onEmbed()
                    },
                )
                KPModuleMenuItem(
                    icon = MiuixIcons.Add,
                    text = stringResource(R.string.kpm_install),
                    onClick = {
                        onExpandedChange(false)
                        onInstall()
                    },
                )
                KPModuleMenuItem(
                    icon = MiuixIcons.Import,
                    text = stringResource(R.string.kpm_load),
                    onClick = {
                        onExpandedChange(false)
                        onLoad()
                    },
                )
            }
        }
    }
}

@Composable
private fun KPModuleList(
    viewModel: KPModuleViewModel,
    modules: List<KPModel.KPMInfo>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
) {
    val moduleTitle = stringResource(R.string.kpm)
    val unloadConfirm = stringResource(R.string.kpm_unload_confirm)
    val removeConfirm = stringResource(R.string.kpm_remove_confirm)
    val uninstallSuccess = stringResource(R.string.kpm_uninstall_success)
    val uninstallFailed = stringResource(R.string.kpm_uninstall_failed)
    val unload = stringResource(R.string.kpm_unload)
    val cancel = stringResource(android.R.string.cancel)
    val controlSuccess = stringResource(R.string.kpm_control_ok)
    val controlFailed = stringResource(R.string.kpm_control_failed)
    val controlMessage = stringResource(R.string.kpm_control_outMsg)
    val contentState = resolveKPModuleContentState(
        isRefreshing = viewModel.isRefreshing,
        hasLoadError = viewModel.hasLoadError,
        moduleCount = modules.size,
        isSearching = viewModel.search.isNotBlank(),
    )

    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val loadingDialog = rememberLoadingDialog()
    val dialogScope = rememberCoroutineScope()
    var controlTarget by remember { mutableStateOf<KPModel.KPMInfo?>(null) }

    suspend fun onModuleControl(module: KPModel.KPMInfo, param: String) {
        val controlResult = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                Natives.kernelPatchModuleControl(module.name, param)
            }
        }

        val prefix = if (controlResult.rc >= 0) controlSuccess else controlFailed
        Toast.makeText(
            context,
            "$prefix\n$controlMessage: ${controlResult.outMsg}",
            Toast.LENGTH_SHORT,
        ).show()
    }

    controlTarget?.let { target ->
        KPMControlDialog(
            show = true,
            onDismiss = { controlTarget = null },
            onConfirm = { param ->
                controlTarget = null
                dialogScope.launch {
                    onModuleControl(target, param)
                }
            },
        )
    }

    suspend fun onModuleUninstall(module: KPModel.KPMInfo) {
        val confirmResult = confirmDialog.awaitConfirm(
            moduleTitle,
            content = if (module.installed) {
                removeConfirm.format(module.name)
            } else {
                unloadConfirm.format(module.name)
            },
            confirm = unload,
            dismiss = cancel,
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val result = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                val unloaded = module.loadSource.isBlank() ||
                    Natives.unloadKernelPatchModule(module.name) == 0L
                val removed = if (module.installed) {
                    val id = safeKpmModuleId(module.moduleId.ifBlank { module.name })
                    val dir = "${APApplication.KPMS_DIR}$id"
                    rootShellForResult("rm -rf '$dir' && test ! -e '$dir'").isSuccess
                } else {
                    true
                }
                UninstallResult(unloaded, removed)
            }
        }

        if (result.removed) {
            viewModel.fetchModuleList()
        }

        val message = if (result.unloaded && result.removed) {
            uninstallSuccess.format(module.name)
        } else {
            uninstallFailed.format(module.name)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                bottom = contentPadding.calculateBottomPadding() + me.bmax.apatch.ui.shell.LocalFloatingNavigationInset.current + 96.dp,
                start = 4.dp,
                end = 4.dp,
            ),
        ) {
            if (viewModel.hasLoadError && modules.isNotEmpty()) {
                item(key = "load-error") {
                    KPModuleLoadErrorCard(onRetry = viewModel::fetchModuleList)
                }
            }

            when (contentState) {
                KPModuleContentState.LOADING -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KPModuleLoadingState()
                        }
                    }
                }

                KPModuleContentState.ERROR -> {
                    item(key = "error") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KPModuleLoadErrorCard(onRetry = viewModel::fetchModuleList)
                        }
                    }
                }

                KPModuleContentState.EMPTY,
                KPModuleContentState.EMPTY_SEARCH,
                -> {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KPModuleEmptyState(
                                isSearching = contentState == KPModuleContentState.EMPTY_SEARCH,
                            )
                        }
                    }
                }

                KPModuleContentState.CONTENT -> {
                    items(
                        items = modules,
                        key = { "${it.moduleId}:${it.name}" },
                    ) { module ->
                        val scope = rememberCoroutineScope()
                        KPModuleCard(
                            module = module,
                            checked = !module.disabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val id = safeKpmModuleId(
                                            module.moduleId.ifBlank { module.name },
                                        )
                                        if (enabled) {
                                            rootShellForResult(
                                                "rm -f '${APApplication.KPMS_DIR}$id/disable'",
                                            )
                                        } else {
                                            rootShellForResult(
                                                "touch '${APApplication.KPMS_DIR}$id/disable'",
                                            )
                                        }
                                    }
                                    viewModel.updateModuleDisabled(module.moduleId, !enabled)
                                    viewModel.fetchModuleList()
                                }
                            },
                            onControl = { controlTarget = module },
                            onRemove = {
                                scope.launch { onModuleUninstall(module) }
                            },
                        )
                    }
                }
            }
        }
    }
}

suspend fun loadModule(loadingDialog: LoadingDialogHandle, uri: Uri, args: String): Int {
    val rc = loadingDialog.withLoading {
        withContext(Dispatchers.IO) {
            run {
                val kpmDir: ExtendedFile =
                    FileSystemManager.getLocal().getFile(apApp.cacheDir.path, "kpm")
                kpmDir.deleteRecursively()
                kpmDir.mkdirs()
                val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
                val kpm = kpmDir.getChildFile("$rand.kpm")
                Log.d(TAG, "save tmp kpm: ${kpm.path}")
                var loadResult = -1
                try {
                    uri.inputStream().buffered().writeTo(kpm)
                    loadResult = Natives.loadKernelPatchModule(kpm.path, args).toInt()
                } catch (e: IOException) {
                    Log.e(TAG, "Copy kpm error: $e")
                }
                Log.d(TAG, "load ${kpm.path} rc: $loadResult")
                loadResult
            }
        }
    }
    return rc
}

/** Install a KPM from an app-local temporary file; it takes effect after reboot. */
suspend fun installKpm(uri: Uri): Int = withContext(Dispatchers.IO) {
    val tempDir: ExtendedFile =
        FileSystemManager.getLocal().getFile(apApp.cacheDir.path, "kpm-install")
    tempDir.deleteRecursively()
    tempDir.mkdirs()
    val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
    val temp = tempDir.getChildFile("$rand.kpm")
    try {
        Log.d(TAG, "save temporary KPM: ${temp.path}")
        uri.inputStream().buffered().writeTo(temp)
        val infoResult = rootShellForResult(
            "${APApplication.APATCH_FOLDER}bin/kptools -l -M '${temp.path}'",
        )
        if (!infoResult.isSuccess) return@withContext -2
        val section = Ini(StringReader(infoResult.out.joinToString("\n")))["kpm"]
            ?: return@withContext -3
        val name = section["name"]?.trim().orEmpty()
        if (name.isEmpty()) return@withContext -4
        val id = safeKpmModuleId(name)
        val dir = "${APApplication.KPMS_DIR}$id"
        val destination = "$dir/$id.kpm"
        val result = rootShellForResult(
            "mkdir -p '$dir' && cp -f '${temp.path}' '$destination'",
        )
        if (!result.isSuccess) return@withContext -5

        // Installed KPMs are loaded by the boot-time loader. Do not load them in
        // the current session; installation takes effect after reboot.
        Log.i(TAG, "install KPM $name to $destination; reboot required")
        0
    } catch (e: Exception) {
        Log.e(TAG, "install KPM failed", e)
        -1
    } finally {
        tempDir.deleteRecursively()
    }
}

@Composable
fun KPMControlDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val controlParam = rememberTextFieldState()

    OverlayDialog(
        show = show,
        title = stringResource(R.string.kpm_control_dialog_title),
        summary = stringResource(R.string.kpm_control_dialog_content),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = controlParam,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.kpm_control_paramters),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
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
                    onClick = {
                        val param = controlParam.text.toString()
                        onDismiss()
                        onConfirm(param)
                    },
                    enabled = controlParam.text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

