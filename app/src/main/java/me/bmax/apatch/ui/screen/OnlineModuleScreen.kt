package me.bmax.apatch.ui.screen

import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RepoModuleDetailScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.MissingLayerNotice
import me.bmax.apatch.ui.repo.ModuleRepoPreferences
import me.bmax.apatch.ui.repo.ModuleSource
import me.bmax.apatch.ui.repo.OnlineModule
import me.bmax.apatch.ui.repo.RepoModule
import me.bmax.apatch.ui.repo.isSafeRepositoryUrl
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.viewmodel.OnlineModuleViewModel
import me.bmax.apatch.ui.viewmodel.RepoModuleViewModel
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.util.download
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * The module store, one store per [moduleType].
 *
 * The manager's own index feeds both of them. Manager modules can also come from a repository picked
 * out of the community cluster index, or from an address typed by hand; kernel modules have no
 * cluster to pick from, since a Magisk style repository holds Magisk style packages. Either way the
 * reader's pick is remembered per store, and nothing is installed without them: a downloaded package
 * goes to the normal installer, or, for a kernel module, through the same install the module list
 * itself uses.
 */
@Destination<RootGraph>
@Composable
fun OnlineModuleScreen(navigator: DestinationsNavigator, moduleType: MODULE_TYPE) {
    val capabilities = LocalAsterCapabilities.current
    if (!capabilities.kernelPatchReady) {
        MissingLayerNotice(
            title = stringResource(R.string.su_kernel_patch_required_title),
            description = stringResource(R.string.capability_kernel_patch_required_desc),
            onBackToHome = { navigator.navigate(HomeScreenDestination) },
        )
        return
    }
    // A kernel module is loaded by the kernel patch itself and never by the Android patch.
    val kernelModules = moduleType == MODULE_TYPE.KPM
    if (!kernelModules && !capabilities.androidPatchReady) {
        MissingLayerNotice(
            title = stringResource(R.string.apm_not_installed),
            description = stringResource(R.string.capability_android_patch_required_desc),
            onBackToHome = { navigator.navigate(HomeScreenDestination) },
        )
        return
    }

    val indexViewModel = viewModel<OnlineModuleViewModel>()
    val repoViewModel = viewModel<RepoModuleViewModel>()
    val context = LocalContext.current
    // Resource lookups go through the configuration-aware provider, not the raw context.
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val installSuccessToastText = stringResource(R.string.kpm_install_toast_succ)
    val installFailedToastText = stringResource(R.string.kpm_load_toast_failed)

    var source by rememberSaveable { mutableStateOf(ModuleRepoPreferences.source(kernelModules)) }
    var repositoryUrl by rememberSaveable {
        mutableStateOf(ModuleRepoPreferences.repositoryUrl(kernelModules))
    }
    var showSourceMenu by rememberSaveable { mutableStateOf(false) }
    var showRepositoryDialog by rememberSaveable { mutableStateOf(false) }
    var showRepositoryList by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    // The manager's index also answers for a hand typed address; the cluster only ever adds
    // repositories of Magisk style packages, which is a manager module story alone.
    val usesManagerIndex = source == ModuleSource.Official || kernelModules
    val customIndexUrl = if (source == ModuleSource.Custom) repositoryUrl else ""
    val searchQuery = if (usesManagerIndex) indexViewModel.searchQuery else repoViewModel.searchQuery
    val packageExtension = if (kernelModules) "kpm" else "zip"
    val packageMimeType = if (kernelModules) "application/octet-stream" else "application/zip"

    // Loading is driven by the selection alone, so switching back and forth never stacks fetches.
    LaunchedEffect(moduleType, source, repositoryUrl) {
        if (usesManagerIndex) {
            indexViewModel.fetchModules(moduleType, customIndexUrl)
        } else if (repositoryUrl.isNotBlank()) {
            repoViewModel.fetchModules(repositoryUrl)
        }
    }

    LaunchedEffect(showRepositoryList) {
        if (showRepositoryList && repoViewModel.repositories.isEmpty()) {
            repoViewModel.fetchRepositories()
        }
    }

    fun selectSource(target: ModuleSource, url: String) {
        if (target == source && url == repositoryUrl) return
        source = target
        repositoryUrl = url
        ModuleRepoPreferences.setSource(kernelModules, target)
        if (url.isNotEmpty()) ModuleRepoPreferences.setRepositoryUrl(kernelModules, url)
        repoViewModel.resetModules()
    }

    fun startDownload(url: String, fileName: String, description: String) {
        if (url.isBlank()) return
        Toast.makeText(context, description, Toast.LENGTH_SHORT).show()
        download(context, url, fileName, description, mimeType = packageMimeType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(
                    if (kernelModules) R.string.online_module_kpm_title else R.string.online_module_title
                ),
                subtitle = stringResource(source.label),
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSourceMenu = true }) {
                            Icon(
                                imageVector = MiuixIcons.Replace,
                                contentDescription = stringResource(R.string.online_module_source_title),
                            )
                        }
                        OverlayListPopup(
                            show = showSourceMenu,
                            alignment = PopupPositionProvider.Align.BottomEnd,
                            onDismissRequest = { showSourceMenu = false },
                        ) {
                            ListPopupColumn {
                                ModuleSource.entries
                                    .filter { !kernelModules || it != ModuleSource.Cluster }
                                    .forEach { entry ->
                                        StoreChoiceRow(
                                            title = stringResource(entry.label),
                                            summary = stringResource(entry.summary),
                                            selected = entry == source,
                                            onClick = {
                                                showSourceMenu = false
                                                when (entry) {
                                                    ModuleSource.Official ->
                                                        selectSource(entry, repositoryUrl)

                                                    ModuleSource.Cluster -> {
                                                        showRepositoryList = true
                                                        repoViewModel.fetchRepositories()
                                                    }

                                                    ModuleSource.Custom -> showRepositoryDialog = true
                                                }
                                            },
                                        )
                                    }
                            }
                        }
                    }
                },
                bottomContent = {
                    SearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { query ->
                                    if (usesManagerIndex) {
                                        indexViewModel.onSearchQueryChange(query)
                                    } else {
                                        repoViewModel.onSearchQueryChange(query)
                                    }
                                },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        content = {},
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (usesManagerIndex) {
                IndexModuleList(
                    viewModel = indexViewModel,
                    moduleType = moduleType,
                    listState = listState,
                    onDownload = { module ->
                        startDownload(
                            url = module.url,
                            fileName = "${module.name}-${module.version}.$packageExtension",
                            description = resources.getString(
                                R.string.online_module_download_start,
                                module.name,
                            ),
                        )
                    },
                )
            } else {
                RepositoryModuleList(
                    viewModel = repoViewModel,
                    listState = listState,
                    onOpen = { module ->
                        navigator.navigate(RepoModuleDetailScreenDestination(module.id))
                    },
                    onDownload = { module ->
                        module.latestRelease?.let { release ->
                            startDownload(
                                url = release.zipUrl,
                                fileName = "${module.name}-${release.version}.$packageExtension",
                                description = resources.getString(
                                    R.string.online_module_download_start,
                                    module.name,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    DownloadListener(context) { uri ->
        if (!kernelModules) {
            navigator.navigate(InstallScreenDestination(uri, MODULE_TYPE.APM))
        } else {
            scope.launch {
                val result = kpmInstallMutex.withLock { installKpm(uri) }
                val message = if (result == 0) {
                    installSuccessToastText
                } else {
                    "$installFailedToastText: $result"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showRepositoryList) {
        RepositoryListDialog(
            viewModel = repoViewModel,
            currentUrl = repositoryUrl,
            onDismiss = { showRepositoryList = false },
            onSelect = { url ->
                showRepositoryList = false
                selectSource(ModuleSource.Cluster, url)
            },
        )
    }

    if (showRepositoryDialog) {
        RepositoryUrlDialog(
            initialUrl = repositoryUrl,
            summary = stringResource(
                if (kernelModules) {
                    R.string.online_module_kpm_repo_url_summary
                } else {
                    R.string.online_module_repo_url_summary
                }
            ),
            onDismiss = { showRepositoryDialog = false },
            onConfirm = { url ->
                showRepositoryDialog = false
                selectSource(ModuleSource.Custom, url)
            },
        )
    }
}

@Composable
private fun IndexModuleList(
    viewModel: OnlineModuleViewModel,
    moduleType: MODULE_TYPE,
    listState: LazyListState,
    onDownload: (OnlineModule) -> Unit,
) {
    val modules = viewModel.modules
    when {
        modules.isNotEmpty() -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().overScrollVertical(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // No key: an index is free to repeat a url, and a duplicate key would crash the list.
            items(modules) { module ->
                OnlineModuleCard(
                    module = module,
                    moduleType = moduleType,
                    onDownload = { onDownload(module) },
                    downloadLabel = stringResource(R.string.online_module_download),
                )
            }
        }

        viewModel.isRefreshing -> APModuleLoadingState()

        viewModel.errorMessage != null -> StoreLoadErrorCard(
            onRetry = { viewModel.fetchModules(moduleType, force = true) },
        )

        else -> APModuleEmptyState(isSearching = viewModel.searchQuery.isNotBlank())
    }
}

@Composable
private fun RepositoryModuleList(
    viewModel: RepoModuleViewModel,
    listState: LazyListState,
    onOpen: (RepoModule) -> Unit,
    onDownload: (RepoModule) -> Unit,
) {
    val modules = viewModel.modules
    when {
        modules.isNotEmpty() -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().overScrollVertical(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(modules) { module ->
                RepoModuleCard(
                    module = module,
                    onOpen = { onOpen(module) },
                    onDownload = { onDownload(module) },
                    downloadLabel = stringResource(R.string.online_module_download),
                )
            }
        }

        viewModel.isRefreshing -> APModuleLoadingState()

        viewModel.errorMessage != null -> StoreLoadErrorCard(
            onRetry = {
                val url = ModuleRepoPreferences.repositoryUrl(forKernelModules = false)
                if (url.isNotBlank()) viewModel.fetchModules(url)
            },
        )

        else -> APModuleEmptyState(isSearching = viewModel.searchQuery.isNotBlank())
    }
}

@Composable
private fun StoreLoadErrorCard(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        APModuleNoticeCard(
            message = stringResource(R.string.online_module_load_error),
            actionLabel = stringResource(R.string.apm_retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun RepositoryListDialog(
    viewModel: RepoModuleViewModel,
    currentUrl: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val moduleCountLabel = stringResource(R.string.online_module_module_count)

    OverlayDialog(
        show = true,
        title = stringResource(R.string.online_module_select_repo),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (viewModel.isReposLoading && viewModel.repositories.isEmpty()) {
                APModuleLoadingState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(viewModel.repositories, key = { it.url }) { repository ->
                        StoreChoiceRow(
                            title = repository.name,
                            summary = listOfNotNull(
                                repository.description.takeIf { it.isNotBlank() },
                                repository.modulesCount
                                    .takeIf { it > 0 }
                                    ?.let { moduleCountLabel.format(it) },
                            ).joinToString(" · "),
                            selected = repository.url == currentUrl,
                            onClick = { onSelect(repository.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryUrlDialog(
    initialUrl: String,
    summary: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val inputState = rememberTextFieldState(initialUrl)
    val url = inputState.text.toString().trim()

    OverlayDialog(
        show = true,
        title = stringResource(R.string.online_module_repo_url_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                state = inputState,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.online_module_repo_url_hint),
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
                    onClick = { onConfirm(url) },
                    enabled = isSafeRepositoryUrl(url),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

private val ModuleSource.label: Int
    @StringRes get() = when (this) {
        ModuleSource.Official -> R.string.online_module_source_official
        ModuleSource.Cluster -> R.string.online_module_source_cluster
        ModuleSource.Custom -> R.string.online_module_source_custom
    }

private val ModuleSource.summary: Int
    @StringRes get() = when (this) {
        ModuleSource.Official -> R.string.online_module_source_official_summary
        ModuleSource.Cluster -> R.string.online_module_source_cluster_summary
        ModuleSource.Custom -> R.string.online_module_source_custom_summary
    }
