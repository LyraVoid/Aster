package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.ui.shell.NavigationMode
import me.bmax.apatch.ui.shell.PrimaryDestination
import me.bmax.apatch.ui.shell.rememberNavigationEntryPreferences
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.shell.rememberNavigationMode
import me.bmax.apatch.ui.shell.rememberVisualFlag
import me.bmax.apatch.ui.shell.setNavigationMode
import me.bmax.apatch.ui.shell.setVisualFlag
import me.bmax.apatch.ui.theme.label
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * How the shell carries the reader between pages: whether the bar floats over them, which side it
 * takes in the classic shell, and how the floating bar behaves while the page underneath moves.
 *
 * The floating bar exists in the panoramic home whether or not it was asked for, so the switches
 * that describe it are only offered where they can be answered.
 */
@Destination<RootGraph>
@Composable
fun NavigationSettingsScreen(navigator: DestinationsNavigator) {
    val globalLayout by rememberGlobalLayout()
    val navigationMode by rememberNavigationMode()
    val entryPreferences = rememberNavigationEntryPreferences()
    val floatingPreferred by rememberVisualFlag("floating_navigation", false)
    val floatingBlur by rememberVisualFlag("floating_blur", true)
    val floatingGlass by rememberVisualFlag("floating_glass", true)
    val floatingAutoHide by rememberVisualFlag("floating_auto_hide", false)
    val floatingScrollHide by rememberVisualFlag("floating_scroll_hide", false)
    var showNavigationModeDialog by rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings_section_navigation,
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
            item(key = "entries") {
                SettingsCard {
                    listOf(
                        PrimaryDestination.KModule to "show_nav_kpm",
                        PrimaryDestination.SuperUser to "show_nav_superuser",
                        PrimaryDestination.AModule to "show_nav_apm",
                    ).forEach { (destination, key) ->
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.navigation_show_entry, stringResource(destination.label)),
                            summary = stringResource(R.string.navigation_show_entry_summary),
                            checked = entryPreferences.shows(destination),
                            onCheckedChange = { setVisualFlag(key, it) },
                            startAction = { SettingsIcon(destination.icon) },
                        )
                    }
                }
            }

            item(key = "shell") {
                SettingsCard {
                    IndicatorSwitchPreference(
                        title = stringResource(R.string.floating_navigation),
                        summary = stringResource(
                            if (globalLayout == GlobalLayout.Panorama) {
                                R.string.floating_navigation_forced
                            } else {
                                R.string.floating_navigation_summary
                            }
                        ),
                        checked = globalLayout == GlobalLayout.Panorama || floatingPreferred,
                        enabled = globalLayout == GlobalLayout.Standard,
                        onCheckedChange = { setVisualFlag("floating_navigation", it) },
                        startAction = { SettingsIcon(MiuixIcons.Sidebar) },
                    )

                    // The classic shell is the one with a choice of sides, so the row belongs with
                    // it rather than with the floating bar.
                    if (globalLayout == GlobalLayout.Standard && !floatingPreferred) {
                        ArrowPreference(
                            title = stringResource(R.string.navigation_mode_title),
                            summary = stringResource(navigationMode.label),
                            startAction = { SettingsIcon(MiuixIcons.More) },
                            onClick = { showNavigationModeDialog = true },
                        )
                    }
                }
            }

            if (globalLayout == GlobalLayout.Panorama || floatingPreferred) {
                item(key = "floating") {
                    SettingsCard {
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.floating_blur),
                            summary = stringResource(R.string.floating_blur_summary),
                            checked = floatingBlur,
                            onCheckedChange = { setVisualFlag("floating_blur", it) },
                            startAction = { SettingsIcon(MiuixIcons.Filter) },
                        )
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.floating_glass),
                            summary = stringResource(R.string.floating_glass_summary),
                            checked = floatingGlass,
                            enabled = floatingBlur && android.os.Build.VERSION.SDK_INT >= 33,
                            onCheckedChange = { setVisualFlag("floating_glass", it) },
                            startAction = { SettingsIcon(MiuixIcons.CloudFill) },
                        )
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.floating_auto_hide),
                            summary = stringResource(R.string.floating_auto_hide_summary),
                            checked = floatingAutoHide,
                            onCheckedChange = { setVisualFlag("floating_auto_hide", it) },
                            startAction = { SettingsIcon(MiuixIcons.Hide) },
                        )
                        IndicatorSwitchPreference(
                            title = stringResource(R.string.floating_scroll_hide),
                            summary = stringResource(R.string.floating_scroll_hide_summary),
                            checked = floatingScrollHide,
                            onCheckedChange = { setVisualFlag("floating_scroll_hide", it) },
                            startAction = { SettingsIcon(MiuixIcons.ExpandMore) },
                        )
                    }
                }
            }
        }

        OverlayDialog(
            show = showNavigationModeDialog,
            title = stringResource(R.string.navigation_mode_title),
            onDismissRequest = { showNavigationModeDialog = false },
        ) {
            NavigationMode.entries.forEach { mode ->
                RadioButtonPreference(
                    title = stringResource(mode.label),
                    summary = if (mode == NavigationMode.Auto) {
                        stringResource(R.string.navigation_mode_auto_summary)
                    } else {
                        null
                    },
                    selected = navigationMode == mode,
                    onClick = {
                        showNavigationModeDialog = false
                        setNavigationMode(mode)
                    },
                )
            }
        }
    }
}
