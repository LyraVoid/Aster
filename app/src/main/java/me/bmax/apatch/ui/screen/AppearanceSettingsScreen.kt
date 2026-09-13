package me.bmax.apatch.ui.screen

import android.os.Build
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
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ThemeColorScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.settings.resolveSettingsFeatureAvailability
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.GlobalLayoutDialog
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.theme.SystemDynamicColorKey
import me.bmax.apatch.ui.theme.displayName
import me.bmax.apatch.ui.theme.label
import me.bmax.apatch.ui.theme.refreshTheme
import me.bmax.apatch.ui.theme.rememberThemeColorSchemeState
import me.bmax.apatch.ui.theme.rememberWallpaperColorThemeState
import me.bmax.apatch.ui.theme.themeColorSourceOf
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * What the app looks like: which family Home is drawn in, the palette it is painted with, and the
 * night theme both of those are seen through.
 *
 * The colour itself is not chosen here. The row says where the colour comes from and opens the page
 * that holds the colour, so this page stays a list of subjects rather than a page of controls.
 */
@Destination<RootGraph>
@Composable
fun AppearanceSettingsScreen(navigator: DestinationsNavigator) {
    val capabilities = LocalAsterCapabilities.current
    val prefs = APApplication.sharedPreferences
    val globalLayout by rememberGlobalLayout()
    // Only the source of the colour is shown here; the colour itself, and how it is spread over
    // the scheme, are picked on the page this row opens.
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val themeColorScheme = rememberThemeColorSchemeState()
    var showGlobalLayoutDialog by rememberSaveable { mutableStateOf(false) }
    var nightFollowSystem by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_follow_sys", true))
    }
    var nightThemeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_enabled", false))
    }

    val availability = resolveSettingsFeatureAvailability(
        kPatchReady = capabilities.kernelPatchReady,
        aPatchReady = capabilities.androidPatchReady,
        nightFollowSystem = nightFollowSystem,
    )
    // The wallpaper is only a source while the panoramic home is the one in use, so the row says
    // whichever source is really in charge after that is taken into account.
    val colorSource = themeColorSourceOf(
        panoramaHome = globalLayout == GlobalLayout.Panorama,
        wallpaperEnabled = wallpaperColorTheme.enabled,
        systemDynamicEnabled = prefs.getBoolean(SystemDynamicColorKey, true),
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    ).label

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings_section_appearance,
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
            item(key = "look") {
                SettingsCard {
                    // Where the colour comes from, how it is spread over the scheme and which spec
                    // it is built to are one decision, so they are read on one page. The row says
                    // the two that describe the palette; the spec is left to the page, which is
                    // also where it is said when the one picked has no form of its own.
                    ArrowPreference(
                        title = stringResource(R.string.theme_color_title),
                        summary = stringResource(colorSource) +
                            " · " + themeColorScheme.style.displayName,
                        startAction = {
                            SettingsIcon(MiuixIcons.Layers)
                        },
                        onClick = { navigator.navigate(ThemeColorScreenDestination) },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.global_layout_title),
                        summary = stringResource(globalLayout.label),
                        startAction = { SettingsIcon(MiuixIcons.ScreenMirroring) },
                        onClick = { showGlobalLayoutDialog = true },
                    )
                }
            }

            item(key = "night") {
                SettingsCard {
                    SwitchPreference(
                        checked = nightFollowSystem,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("night_mode_follow_sys", enabled) }
                            nightFollowSystem = enabled
                            refreshTheme.value = true
                        },
                        title = stringResource(R.string.settings_night_mode_follow_sys),
                        summary = stringResource(R.string.settings_night_mode_follow_sys_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Refresh)
                        },
                    )

                    if (availability.nightTheme) {
                        SwitchPreference(
                            checked = nightThemeEnabled,
                            onCheckedChange = { enabled ->
                                prefs.edit { putBoolean("night_mode_enabled", enabled) }
                                nightThemeEnabled = enabled
                                refreshTheme.value = true
                            },
                            title = stringResource(R.string.settings_night_theme_enabled),
                            startAction = {
                                SettingsIcon(MiuixIcons.Theme)
                            },
                        )
                    }
                }
            }
        }

        GlobalLayoutDialog(
            show = showGlobalLayoutDialog,
            selected = globalLayout,
            onDismissRequest = { showGlobalLayoutDialog = false },
        )
    }
}
