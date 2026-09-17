package me.bmax.apatch.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
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
import me.bmax.apatch.ui.component.IndicatorSwitch
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.shell.LocalMainPagerState
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.CapabilityNotice
import me.bmax.apatch.ui.superuser.SuperUserItem
import me.bmax.apatch.ui.superuser.SuperUserSort
import me.bmax.apatch.ui.superuser.SuperUserUiState
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Destination<RootGraph>
@Composable
fun SuperUserScreen(navigator: DestinationsNavigator) {
    val mainPagerState = LocalMainPagerState.current
    val viewModel = viewModel<SuperUserViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.ensureAppListLoaded()
    }

    // A refused write leaves the row showing whatever is on disk, so this is the only thing that
    // separates "the change was saved" from "the tap did nothing at all". It carries the reason
    // with it, which is what a report of a tap doing nothing needs to be actionable.
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // The batch the reader has asked for and not yet run. It is a dialog rather than an immediate
    // act for the reason a single switch is not: granting root to a pageful of apps at once is not
    // something to do on a mis-tap.
    var pendingBatch by remember { mutableStateOf<SuperUserViewModel.BatchAction?>(null) }

    // Back leaves the picking before it leaves the page, which is what a mode is for: the way out
    // of it is the first thing the gesture everyone tries does.
    BackHandler(enabled = viewModel.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (viewModel.isSelectionMode) {
                SuperUserSelectionTopBar(
                    selectedCount = viewModel.selectedCount,
                    onClose = viewModel::exitSelectionMode,
                    onAction = { pendingBatch = it },
                    scrollBehavior = scrollBehavior,
                )
            } else {
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
                    onEnterSelection = viewModel::enterSelectionMode,
                    scrollBehavior = scrollBehavior,
                )
            }
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
                            onAction = {
                                if (mainPagerState != null) mainPagerState.animateToPage(0)
                                else navigator.navigate(HomeScreenDestination)
                            },
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
                                selectionMode = viewModel.isSelectionMode,
                                isSelected = viewModel.isUidSelected(item.uid),
                                onToggleSelection = { viewModel.toggleSelection(item.uid) },
                                onLongPress = {
                                    viewModel.enterSelectionMode()
                                    viewModel.toggleSelection(item.uid)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // The last thing the reader sees before the lot changes at once, and the only place the count
    // of what is about to change is said out loud.
    pendingBatch?.let { batch ->
        OverlayDialog(
            show = true,
            title = stringResource(batch.confirmTitleRes(), viewModel.selectedCount),
            onDismissRequest = { pendingBatch = null },
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { pendingBatch = null },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val uids = viewModel.selectedUids()
                            pendingBatch = null
                            // Leaving the mode first puts the page back as the reader found it; the
                            // write reports itself through the message flow either way.
                            viewModel.exitSelectionMode()
                            viewModel.applyBatch(batch, uids)
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

/** What each batch asks before it runs, with the count filled in by the caller. */
private fun SuperUserViewModel.BatchAction.confirmTitleRes(): Int = when (this) {
    SuperUserViewModel.BatchAction.GRANT_ROOT -> R.string.su_multi_select_confirm_grant
    SuperUserViewModel.BatchAction.NORMAL -> R.string.su_multi_select_confirm_normal
    SuperUserViewModel.BatchAction.EXCLUDE -> R.string.su_multi_select_confirm_exclude
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
    onEnterSelection: () -> Unit,
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
            // Picking apps sits beside the menu rather than inside it: it is a way of using the
            // page, not one of the things the page can do.
            IconButton(onClick = onEnterSelection) {
                Icon(
                    imageVector = MiuixIcons.All,
                    contentDescription = stringResource(R.string.su_multi_select_enter),
                )
            }
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

/** Batch actions have visible labels so their meaning does not depend on recognizing an icon. */
@Composable
private fun SuperUserSelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onAction: (SuperUserViewModel.BatchAction) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    TopAppBar(
        title = stringResource(R.string.su_multi_select_count, selectedCount),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                )
            }
        },
        bottomContent = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuperUserBatchButton(
                    icon = MiuixIcons.Ok,
                    label = stringResource(R.string.su_multi_select_grant_label),
                    enabled = selectedCount > 0,
                    onClick = { onAction(SuperUserViewModel.BatchAction.GRANT_ROOT) },
                    modifier = Modifier.weight(1f),
                )
                SuperUserBatchButton(
                    icon = MiuixIcons.Reset,
                    label = stringResource(R.string.su_multi_select_normal_label),
                    enabled = selectedCount > 0,
                    onClick = { onAction(SuperUserViewModel.BatchAction.NORMAL) },
                    modifier = Modifier.weight(1f),
                )
                SuperUserBatchButton(
                    icon = MiuixIcons.Blocklist,
                    label = stringResource(R.string.su_multi_select_exclude_label),
                    enabled = selectedCount > 0,
                    onClick = { onAction(SuperUserViewModel.BatchAction.EXCLUDE) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun SuperUserBatchButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            Text(text = label, fontSize = 13.sp, textAlign = TextAlign.Center, color = color)
        }
    }
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
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var showExcludeSetting by rememberSaveable(item.packageName, item.uid) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = false,
        // The card is the target of a tap, not the label inside it: every app opens wherever it is
        // touched, and the press is answered by the card itself rather than by a highlight across
        // its middle. Granting root stays on the switch alone.
        onClick = {
            // While the page is picking apps, the row picks instead of opening: the switch is gone
            // and a tap anywhere on the row is a tap on the whole row.
            if (selectionMode) {
                onToggleSelection()
            } else {
                showExcludeSetting = !showExcludeSetting
            }
        },
        // A long press is the way into picking: it starts the mode and picks the row that was held,
        // so the reader does not have to find the button first and then find the row again.
        onLongPress = {
            if (selectionMode) onToggleSelection() else onLongPress()
        },
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
                        .padding(horizontal = 12.dp),
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

                if (selectionMode) {
                    // A check where the switch was, saying "picked" instead of "granted", so a row
                    // does not have to move for the page to change what it means. The unselected
                    // side holds the same space, or the rows would jump as they were picked.
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = stringResource(R.string.su_multi_select_selected),
                                modifier = Modifier.size(24.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f),
                            )
                        }
                    }
                } else {
                    IndicatorSwitch(
                        checked = item.isAllowed,
                        onCheckedChange = onToggleRoot,
                    )
                }
            }

            // Exclusion reaches an app that holds root too: turning it on takes the root away and
            // leaves the app excluded, and the row says so straight away. While the page is picking
            // apps the row is a checkbox and nothing else, so this stays out of the way.
            AnimatedVisibility(visible = showExcludeSetting && !selectionMode) {
                IndicatorSwitchPreference(
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
