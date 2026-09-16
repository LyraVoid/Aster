package me.bmax.apatch.ui.screen

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.component.MarkdownContent
import me.bmax.apatch.ui.home.HomeUpdateState
import me.bmax.apatch.ui.home.HomeViewModel
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale

/**
 * The settings that belong to no other subject: the language the app speaks and the updates it
 * looks for. What the app is, rather than what it does, is on the About page this one leads to
 * through the page that lists the subjects.
 */
@Destination<RootGraph>
@Composable
fun GeneralSettingsScreen(navigator: DestinationsNavigator) {
    val prefs = APApplication.sharedPreferences
    val uriHandler = LocalUriHandler.current
    val updateModel: HomeViewModel = viewModel()
    val updateState by updateModel.uiState.collectAsStateWithLifecycle()
    var showVersionCheck by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showHomeTitleDialog by rememberSaveable { mutableStateOf(false) }
    var checkUpdate by rememberSaveable {
        mutableStateOf(prefs.getBoolean("check_update", true))
    }
    var confirmInstall by rememberSaveable {
        mutableStateOf(apApp.getModuleInstallConfirmState())
    }
    var stayOnActionPage by rememberSaveable {
        mutableStateOf(apApp.getStayOnActionPageState())
    }
    var homeTitleKey by rememberSaveable {
        mutableStateOf(prefs.getString(APApplication.HOME_TITLE, "aster") ?: "aster")
    }
    val homeTitles = stringArrayResource(R.array.home_titles)
    val homeTitleValues = stringArrayResource(R.array.home_titles_values)
    val homeTitleLabel = remember(homeTitleKey, homeTitles) {
        homeTitles.elementAtOrNull(homeTitleValues.indexOf(homeTitleKey)) ?: "Aster"
    }
    val languageSummary = rememberApplicationLanguageLabel()

    if (showVersionCheck) {
        val update = updateState.update
        OverlayDialog(
            show = true,
            title = stringResource(
                when (update) {
                    HomeUpdateState.UpToDate -> R.string.home_update_current
                    HomeUpdateState.Failed -> R.string.home_update_failed
                    is HomeUpdateState.Available ->
                        R.string.home_update_available_title

                    else -> R.string.home_update_checking
                }
            ),
            onDismissRequest = { showVersionCheck = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (update is HomeUpdateState.Available) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // The notes are written in Markdown, the way the release page shows them.
                        // Handed to a plain Text they arrived as a wall of asterisks with the
                        // headings and the lists flattened into it; drawn as Markdown they keep the
                        // shape they were written in. The summary stands in only for a release
                        // published without notes at all.
                        if (update.changelog.isBlank()) {
                            Text(
                                text = stringResource(R.string.home_update_available_summary),
                                style = MiuixTheme.textStyles.body2,
                            )
                        } else {
                            MarkdownContent(content = update.changelog)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { showVersionCheck = false },
                        modifier = Modifier.weight(1f),
                    )
                    if (update is HomeUpdateState.Available) {
                        Button(
                            onClick = { uriHandler.openUri(update.downloadUrl) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.apm_update))
                        }
                    }
                }
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = R.string.settings_section_general,
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
            item(key = "updates") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.home_update_check),
                        startAction = { SettingsIcon(MiuixIcons.Update) },
                        onClick = {
                            showVersionCheck = true
                            updateModel.checkForUpdates(force = true)
                        },
                    )
                    IndicatorSwitchPreference(
                        checked = checkUpdate,
                        onCheckedChange = { enabled ->
                            prefs.edit { putBoolean("check_update", enabled) }
                            checkUpdate = enabled
                        },
                        title = stringResource(R.string.settings_check_update),
                        summary = stringResource(R.string.settings_check_update_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Timer)
                        },
                    )
                }
            }

            item(key = "language") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.settings_app_language),
                        summary = languageSummary,
                        startAction = {
                            SettingsIcon(MiuixIcons.Translate)
                        },
                        onClick = { showLanguageDialog = true },
                    )
                }
            }

            item(key = "home_title") {
                SettingsCard {
                    ArrowPreference(
                        title = stringResource(R.string.settings_home_title),
                        summary = homeTitleLabel,
                        startAction = { SettingsIcon(MiuixIcons.Edit) },
                        onClick = { showHomeTitleDialog = true },
                    )
                }
            }

            item(key = "module_install") {
                SettingsCard {
                    IndicatorSwitchPreference(
                        checked = confirmInstall,
                        onCheckedChange = { enabled ->
                            apApp.updateModuleInstallConfirmState(enabled)
                            confirmInstall = enabled
                        },
                        title = stringResource(R.string.settings_install_confirm),
                        summary = stringResource(R.string.settings_install_confirm_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Info)
                        },
                    )

                    // The other half of the same subject: what happens around a module's action,
                    // before it is unpacked and after it has run.
                    IndicatorSwitchPreference(
                        checked = stayOnActionPage,
                        onCheckedChange = { enabled ->
                            apApp.updateStayOnActionPageState(enabled)
                            stayOnActionPage = enabled
                        },
                        title = stringResource(R.string.settings_stay_on_action_page),
                        summary = stringResource(R.string.settings_stay_on_action_page_summary),
                        startAction = {
                            SettingsIcon(MiuixIcons.Pin)
                        },
                    )
                }
            }
        }

        LanguageDialog(
            show = showLanguageDialog,
            onDismiss = { showLanguageDialog = false },
        )

        HomeTitleDialog(
            show = showHomeTitleDialog,
            onDismiss = { showHomeTitleDialog = false },
            onSelected = { homeTitleKey = it },
        )
    }
}
