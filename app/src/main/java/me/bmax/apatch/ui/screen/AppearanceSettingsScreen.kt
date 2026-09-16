package me.bmax.apatch.ui.screen

import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ThemeColorScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.component.SwitchIndicator
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.settings.resolveSettingsFeatureAvailability
import me.bmax.apatch.ui.shell.AppDensity
import me.bmax.apatch.ui.shell.GlobalLayout
import me.bmax.apatch.ui.shell.GlobalLayoutDialog
import me.bmax.apatch.ui.shell.HomeLayoutDialog
import me.bmax.apatch.ui.shell.LocalAsterCapabilities
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.ui.shell.rememberAppDensity
import me.bmax.apatch.ui.shell.rememberDeviceDensity
import me.bmax.apatch.ui.shell.rememberGlobalLayout
import me.bmax.apatch.ui.shell.rememberHomeLayout
import me.bmax.apatch.ui.theme.CustomFont
import me.bmax.apatch.ui.theme.SystemDynamicColorKey
import me.bmax.apatch.ui.theme.displayName
import me.bmax.apatch.ui.theme.label
import me.bmax.apatch.ui.theme.refreshTheme
import me.bmax.apatch.ui.theme.rememberThemeColorSchemeState
import me.bmax.apatch.ui.theme.rememberWallpaperColorThemeState
import me.bmax.apatch.ui.theme.themeColorSourceOf
import me.bmax.apatch.util.LauncherIconUtils
import me.bmax.apatch.util.ui.LocalSnackbarHost
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.ZoomOut
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val globalLayout by rememberGlobalLayout()
    val appDensity by rememberAppDensity()
    val deviceDensity = rememberDeviceDensity()
    val activity = LocalActivity.current
    // The slider reads the density the app was told to use, and while it follows the device the
    // number it starts from is the device's own, so letting go of the switch alone would change
    // nothing and the drag would begin where the app already stands.
    var densityDraft by rememberSaveable(appDensity) {
        mutableIntStateOf(if (appDensity == AppDensity.FollowSystem) deviceDensity else appDensity)
    }
    val densityRange = minOf(AppDensity.Min, deviceDensity).toFloat()..
        maxOf(AppDensity.Max, deviceDensity).toFloat()
    // The slider moves in tens, which is finer than anyone can tell two sizes apart by and coarse
    // enough that the number read back is a round one.
    val densitySteps = ((densityRange.endInclusive - densityRange.start) / 10f).toInt() - 1
    val homeLayout by rememberHomeLayout()
    // Only the source of the colour is shown here; the colour itself, and how it is spread over
    // the scheme, are picked on the page this row opens.
    val wallpaperColorTheme = rememberWallpaperColorThemeState()
    val themeColorScheme = rememberThemeColorSchemeState()
    var showGlobalLayoutDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeLayoutDialog by rememberSaveable { mutableStateOf(false) }
    var nightFollowSystem by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_follow_sys", true))
    }
    var nightThemeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean("night_mode_enabled", false))
    }
    var switchIndicator by rememberSaveable { mutableStateOf(SwitchIndicator.enabled) }
    var useAltIcon by rememberSaveable {
        mutableStateOf(prefs.getBoolean(LauncherIconUtils.USE_ALT_ICON, false))
    }
    var showFontDialog by rememberSaveable { mutableStateOf(false) }
    val font = CustomFont.state
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    val fontSavedMessage = stringResource(R.string.settings_font_imported)
    val fontFailedMessage = stringResource(R.string.settings_font_import_failed)
    val fontResetMessage = stringResource(R.string.settings_font_reset_done)
    val fontResetTitle = stringResource(R.string.settings_font_reset)
    val fontResetConfirm = stringResource(R.string.settings_font_reset_confirm)

    // The picked file is read, checked and copied by CustomFont; what is left here is saying how it
    // went, which is the one thing that needs a screen.
    val pickFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val imported = loadingDialog.withLoading { CustomFont.importFont(context, uri) }
            snackBarHost.showSnackbar(
                message = if (imported) fontSavedMessage else fontFailedMessage,
                duration = SnackbarDuration.Short,
            )
        }
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

                    // How the standard Home is headed is the standard Home's own choice, so its row
                    // sits under the family it belongs to and is only there while that family is the
                    // one in use: the panorama Home draws its own scene chrome instead.
                    if (globalLayout == GlobalLayout.Standard) {
                        ArrowPreference(
                            title = stringResource(R.string.home_layout_title),
                            summary = stringResource(homeLayout.label),
                            startAction = { SettingsIcon(MiuixIcons.HorizontalSplit) },
                            onClick = { showHomeLayoutDialog = true },
                        )

                        // The status line wears the face only in the standard Home, so the switch
                        // is inside this block rather than beside the scene's own switches: while
                        // the panorama Home is the one in use there is nothing here to change.
                        val classicEmoji by me.bmax.apatch.ui.shell.rememberVisualFlag(
                            me.bmax.apatch.ui.shell.HomeClassicEmojiFlag,
                            false,
                        )
                        IndicatorSwitchPreference(
                            checked = classicEmoji,
                            onCheckedChange = {
                                me.bmax.apatch.ui.shell.setVisualFlag(
                                    me.bmax.apatch.ui.shell.HomeClassicEmojiFlag,
                                    it,
                                )
                            },
                            title = stringResource(R.string.home_classic_emoji),
                            summary = stringResource(R.string.home_classic_emoji_summary),
                            startAction = { SettingsIcon(MiuixIcons.Tune) },
                        )
                    }
                }
            }

            // How the app is marked on the desktop is a choice between its own letter and the
            // APatch mark it came from, so it stands on its own rather than inside the card about
            // Home, which is about what the app draws rather than what the system draws for it.
            item(key = "launcher_icon") {
                SettingsCard {
                    IndicatorSwitchPreference(
                        checked = useAltIcon,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean(LauncherIconUtils.USE_ALT_ICON, enabled) }
                            LauncherIconUtils.updateLauncherState(context)
                            useAltIcon = enabled
                        },
                        title = stringResource(R.string.settings_launcher_icon),
                        summary = stringResource(R.string.settings_launcher_icon_summary),
                        startAction = { SettingsIcon(MiuixIcons.GridView) },
                    )
                }
            }

            // What the app is set in is its own subject, so it gets its own card: the row says which
            // font is in use and the dialog holds the two controls that change that.
            item(key = "type") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.settings_font),
                        summary = font.title ?: stringResource(R.string.system_default),
                        startAction = { SettingsIcon(MiuixIcons.Notes) },
                        onClick = { showFontDialog = true },
                    )
                }
            }

            item(key = "night") {
                SettingsCard {
                    IndicatorSwitchPreference(
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
                        IndicatorSwitchPreference(
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

            // The switches the manager draws itself, which is every switch outside the preference
            // rows Miuix owns: the module cards, the root grants, the runtime-safety opt-in.
            item(key = "indicator") {
                SettingsCard {
                    IndicatorSwitchPreference(
                        checked = switchIndicator,
                        onCheckedChange = { enabled ->
                            SwitchIndicator.update(enabled)
                            switchIndicator = enabled
                        },
                        title = stringResource(R.string.settings_switch_indicator),
                        summary = stringResource(R.string.settings_switch_indicator_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Ok)
                        },
                    )
                }
            }

            // How large the app draws itself is the app's own business, so the two rows that decide
            // it stand together: leaving the size to the device, and the slider that says what the
            // app was given instead. Neither touches the system, and both cost the Activity in use
            // one rebuild, which is the only way a Context can be exchanged under a window.
            item(key = "size") {
                SettingsCard {
                    IndicatorSwitchPreference(
                        checked = appDensity == AppDensity.FollowSystem,
                        onCheckedChange = { follow ->
                            AppDensity.set(
                                if (follow) AppDensity.FollowSystem else deviceDensity,
                            )
                            activity?.recreate()
                        },
                        title = stringResource(R.string.system_default),
                        summary = stringResource(
                            R.string.app_density_follow_summary,
                            deviceDensity,
                        ),
                        startAction = { SettingsIcon(MiuixIcons.Reset) },
                    )

                    // The slider means nothing while the device's size is the one in use, and a row
                    // that would change nothing is left out rather than greyed, the way the rows
                    // that wait on a switch elsewhere in these settings are.
                    if (appDensity != AppDensity.FollowSystem) {
                        SliderPreference(
                            value = densityDraft.toFloat(),
                            onValueChange = { densityDraft = it.roundToInt() },
                            onValueChangeFinished = {
                                AppDensity.set(densityDraft)
                                activity?.recreate()
                            },
                            title = stringResource(R.string.app_density_title),
                            summary = stringResource(R.string.app_density_summary),
                            valueText = stringResource(R.string.app_density_value, densityDraft),
                            startAction = { SettingsIcon(MiuixIcons.ZoomOut) },
                            valueRange = densityRange,
                            steps = densitySteps,
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

        HomeLayoutDialog(
            show = showHomeLayoutDialog,
            selected = homeLayout,
            onDismissRequest = { showHomeLayoutDialog = false },
        )

        FontDialog(
            show = showFontDialog,
            onDismiss = { showFontDialog = false },
            onPickFont = {
                // Fonts are asked for as any file: plenty of pickers do not know a font when they
                // see one, and the file is checked here anyway.
                pickFontLauncher.launch("*/*")
            },
            onResetFont = {
                scope.launch {
                    val answer = confirmDialog.awaitConfirm(
                        title = fontResetTitle,
                        content = fontResetConfirm,
                    )
                    if (answer != ConfirmResult.Confirmed) return@launch
                    CustomFont.clear(context)
                    snackBarHost.showSnackbar(message = fontResetMessage, duration = SnackbarDuration.Short)
                }
            },
        )
    }
}
