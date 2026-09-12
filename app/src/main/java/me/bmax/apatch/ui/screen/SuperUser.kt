package me.bmax.apatch.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.CapabilityNotice
import me.bmax.apatch.ui.superuser.SuperUserItem
import me.bmax.apatch.ui.superuser.SuperUserSort
import me.bmax.apatch.ui.superuser.SuperUserUiState
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
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
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Destination<RootGraph>
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    val viewModel = viewModel<SuperUserViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(Unit) {
        viewModel.ensureAppListLoaded()
    }

    Scaffold(
        topBar = {
            SuperUserTopBar(
                uiState = uiState,
                searchExpanded = searchExpanded,
                onSearchExpandedChange = { expanded ->
                    searchExpanded = expanded
                    if (!expanded) {
                        viewModel.updateSearch("")
                    }
                },
                onSearchChange = viewModel::updateSearch,
                onRefresh = viewModel::refresh,
                onToggleSystemApps = viewModel::toggleSystemApps,
                onSortChange = viewModel::updateSort,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        PullToRefresh(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            contentPadding = innerPadding,
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
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + me.bmax.apatch.ui.shell.LocalFloatingNavigationInset.current + 24.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!uiState.isKernelPatchReady) {
                    item(key = "capability-notice") {
                        CapabilityNotice(
                            title = stringResource(R.string.su_kernel_patch_required_title),
                            description = stringResource(R.string.su_kernel_patch_required_desc),
                            actionLabel = stringResource(R.string.su_back_to_home),
                            onAction = { navigator.navigate(HomeScreenDestination) },
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    item(key = "error") {
                        ErrorNotice(message)
                    }
                }

                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        item(key = "loading") {
                            LoadingState()
                        }
                    }

                    uiState.errorMessage != null && uiState.items.isEmpty() -> Unit

                    uiState.items.isEmpty() -> {
                        item(key = "empty") {
                            EmptyState(isSearching = uiState.searchQuery.isNotBlank())
                        }
                    }

                    else -> {
                        items(
                            items = uiState.items,
                            key = { "${it.packageName}:${it.uid}" },
                        ) { item ->
                            SuperUserAppItem(
                                item = item,
                                onToggleRoot = { granted ->
                                    viewModel.setRootGranted(item, granted)
                                },
                                onToggleExcluded = { excluded ->
                                    viewModel.setExcluded(item, excluded)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperUserTopBar(
    uiState: SuperUserUiState,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleSystemApps: () -> Unit,
    onSortChange: (SuperUserSort) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val subtitle = if (
        (uiState.isLoading && uiState.totalAppCount == 0) ||
        (uiState.errorMessage != null && uiState.totalAppCount == 0)
    ) {
        ""
    } else {
        stringResource(R.string.su_summary_granted, uiState.allowedUidCount)
    }

    TopAppBar(
        title = stringResource(R.string.su_title),
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = stringResource(R.string.su_more_actions),
                    )
                }

                OverlayListPopup(
                    show = showMenu,
                    alignment = PopupPositionProvider.Align.BottomEnd,
                    onDismissRequest = { showMenu = false },
                ) {
                    ListPopupColumn {
                        SuperUserMenuItem(
                            icon = MiuixIcons.Refresh,
                            text = stringResource(R.string.su_refresh),
                            onClick = {
                                showMenu = false
                                onRefresh()
                            },
                        )
                        SuperUserMenuItem(
                            icon = MiuixIcons.GridView,
                            text = if (uiState.showSystemApps) {
                                stringResource(R.string.su_hide_system_apps)
                            } else {
                                stringResource(R.string.su_show_system_apps)
                            },
                            onClick = {
                                showMenu = false
                                onToggleSystemApps()
                            },
                        )
                        SuperUserSort.entries.forEach { sort ->
                            SuperUserMenuItem(
                                icon = MiuixIcons.Sort,
                                text = stringResource(sort.labelRes()),
                                selected = uiState.sortBy == sort,
                                onClick = {
                                    showMenu = false
                                    onSortChange(sort)
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
                        query = uiState.searchQuery,
                        onQueryChange = onSearchChange,
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
private fun SuperUserMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
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
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = contentColor,
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuperUserAppItem(
    item: SuperUserItem,
    onToggleRoot: (Boolean) -> Unit,
    onToggleExcluded: (Boolean) -> Unit,
) {
    var showExcludeSetting by rememberSaveable(item.packageName, item.uid) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.packageInfo)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.label,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(48.dp),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clickable(enabled = !item.isAllowed) {
                            showExcludeSetting = !showExcludeSetting
                        },
                ) {
                    Text(
                        text = item.label,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = item.packageName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )

                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (item.isExcluded) {
                            StatusBadge(
                                label = stringResource(R.string.su_pkg_excluded_label),
                            )
                        }
                        if (item.isAllowed) {
                            StatusBadge(label = "UID ${item.profileUid}")
                            StatusBadge(label = "toUid ${item.profileToUid}")
                            StatusBadge(
                                label = item.profileScontext.ifBlank {
                                    stringResource(R.string.su_selinux_via_hook)
                                },
                            )
                        }
                    }
                }

                Switch(
                    checked = item.isAllowed,
                    onCheckedChange = onToggleRoot,
                )
            }

            AnimatedVisibility(visible = showExcludeSetting && !item.isAllowed) {
                SwitchPreference(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    title = stringResource(R.string.su_pkg_excluded_setting_title),
                    summary = stringResource(R.string.su_pkg_excluded_setting_summary),
                    checked = item.isExcluded,
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    onCheckedChange = onToggleExcluded,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp, end = 4.dp)
            .background(
                color = MiuixTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp),
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            color = MiuixTheme.colorScheme.onTertiaryContainer,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ErrorNotice(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MiuixIcons.GridView,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(
                if (isSearching) {
                    R.string.su_no_search_results
                } else {
                    R.string.su_no_apps
                },
            ),
            modifier = Modifier.padding(top = 12.dp),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun SuperUserSort.labelRes(): Int = when (this) {
    SuperUserSort.NAME -> R.string.su_sort_name
    SuperUserSort.PACKAGE_NAME -> R.string.su_sort_package
    SuperUserSort.INSTALL_TIME -> R.string.su_sort_install_time
}
