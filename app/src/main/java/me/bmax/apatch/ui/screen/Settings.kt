package me.bmax.apatch.ui.screen

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppearanceSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DiagnosticsSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.GeneralSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KernelSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NavigationSettingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.shell.rememberNavigationMode
import me.bmax.apatch.ui.shell.rememberVisualFlag
import me.bmax.apatch.ui.theme.SystemDynamicColorKey
import me.bmax.apatch.ui.theme.displayName
import me.bmax.apatch.ui.theme.label
import me.bmax.apatch.ui.theme.rememberThemeColorSchemeState
import me.bmax.apatch.ui.theme.rememberWallpaperColorThemeState
import me.bmax.apatch.ui.theme.themeColorSourceOf
import me.bmax.apatch.util.Version
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale

/**
 * The way into everything that can be set: one row per subject, each saying what that subject is at
 * right now, so the page answers "what is it set to" before anything is opened.
 *
 * The rows list subjects rather than settings, so a subject that grows only makes its own page
 * longer. Which is also why the version is worn here, on the row that leads to the page about it:
 * the app names what it is before it is opened.
 */
@Destination<RootGraph>
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
    val prefs = APApplication.sharedPreferences
    val globalLayout by rememberGlobalLayout()
    val navigationMode by rememberNavigationMode()
    val floatingPreferred by rememberVisualFlag("floating_navigation", false)
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val themeColorScheme = rememberThemeColorSchemeState()
    val managerVersion = remember { Version.getManagerVersion().first }
    // An absent kernel patch answers with a zero rather than with nothing, which is not a version
    // anyone would want to read on a row, so it is turned back into the absence it means.
    val installedKernelPatch = remember {
        runCatching { Version.installedKPVString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != "0" }
    }
    val colorSource = themeColorSourceOf(
        panoramaHome = globalLayout == GlobalLayout.Panorama,
        wallpaperEnabled = wallpaperColorTheme.enabled,
        systemDynamicEnabled = prefs.getBoolean(SystemDynamicColorKey, true),
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    ).label
    val languageSummary = AppCompatDelegate.getApplicationLocales()[0]?.displayLanguage
        ?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        ?: stringResource(R.string.system_default)

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings,
                scrollBehavior = scrollBehavior,
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
            item(key = "subjects") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.settings_section_appearance),
                        summary = stringResource(colorSource) +
                            " · " + themeColorScheme.style.displayName,
                        startAction = { SettingsIcon(MiuixIcons.Layers) },
                        onClick = { navigator.navigate(AppearanceSettingsScreenDestination) },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.settings_section_navigation),
                        summary = stringResource(
                            if (globalLayout == GlobalLayout.Panorama || floatingPreferred) {
                                R.string.floating_navigation
                            } else {
                                navigationMode.label
                            }
                        ),
                        startAction = { SettingsIcon(MiuixIcons.Sidebar) },
                        onClick = { navigator.navigate(NavigationSettingsScreenDestination) },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.settings_section_kernel),
                        summary = installedKernelPatch
                            ?: stringResource(R.string.kpm_kp_not_installed),
                        startAction = { SettingsIcon(MiuixIcons.All) },
                        onClick = { navigator.navigate(KernelSettingsScreenDestination) },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.settings_section_general),
                        summary = languageSummary,
                        startAction = { SettingsIcon(MiuixIcons.Translate) },
                        onClick = { navigator.navigate(GeneralSettingsScreenDestination) },
                    )

                    ArrowPreference(
                        title = stringResource(R.string.settings_section_diagnostics),
                        startAction = { SettingsIcon(MiuixIcons.Report) },
                        onClick = { navigator.navigate(DiagnosticsSettingsScreenDestination) },
                    )
                }
            }

            item(key = "about") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.about),
                        startAction = { SettingsIcon(MiuixIcons.Info) },
                        endActions = { VersionBadge(version = managerVersion) },
                        onClick = { navigator.navigate(AboutScreenDestination) },
                    )
                }
            }
        }
    }
}
