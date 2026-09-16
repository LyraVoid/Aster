package me.bmax.apatch.ui.screen

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import androidx.core.content.edit
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.IndicatorSwitchPreference
import me.bmax.apatch.ui.theme.CustomFont
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        SettingsCard(content = content)
    }
}

/**
 * A card of preference rows with no name above it. A page that is cut by subject is already named
 * by its own bar, so naming the card again would only say what the bar just said.
 */
@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
    ) {
        content()
    }
}

/**
 * The bar every settings page wears: the subject it holds, the way back to the page that lists the
 * subjects, and the scroll that bar answers to.
 *
 * The name of the subject is written large and belongs to the page rather than to the bar: it
 * leaves with the content when the page is scrolled, and the bar keeps only the small name once the
 * large one is gone. Passing no navigator leaves the bar without a way back, which is what the page
 * that lists the subjects wants.
 */
@Composable
internal fun SettingsTopBar(
    @StringRes title: Int,
    scrollBehavior: ScrollBehavior,
    navigator: DestinationsNavigator? = null,
) {
    TopAppBar(
        title = stringResource(title),
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (navigator != null) {
                IconButton(onClick = { navigator.popBackStack() }) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        },
    )
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Spacer(Modifier.width(12.dp))
}

/**
 * The manager version, worn as a pill on the end of the row that leads to the page about it. It
 * says the same thing the page does before it is opened, so the row does not have to spell the
 * version out in its summary.
 */
@Composable
internal fun VersionBadge(version: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = VersionBadgeAlpha))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = version,
            style = MiuixTheme.textStyles.footnote1,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

private const val VersionBadgeAlpha = 0.12f

internal data class APColor(
    val name: String,
    @param:StringRes val nameId: Int,
)

internal fun colorsList(): List<APColor> = listOf(
    APColor("amber", R.string.amber_theme),
    APColor("blue_grey", R.string.blue_grey_theme),
    APColor("blue", R.string.blue_theme),
    APColor("brown", R.string.brown_theme),
    APColor("cyan", R.string.cyan_theme),
    APColor("deep_orange", R.string.deep_orange_theme),
    APColor("deep_purple", R.string.deep_purple_theme),
    APColor("green", R.string.green_theme),
    APColor("indigo", R.string.indigo_theme),
    APColor("light_blue", R.string.light_blue_theme),
    APColor("light_green", R.string.light_green_theme),
    APColor("lime", R.string.lime_theme),
    APColor("orange", R.string.orange_theme),
    APColor("pink", R.string.pink_theme),
    APColor("purple", R.string.purple_theme),
    APColor("red", R.string.red_theme),
    APColor("sakura", R.string.sakura_theme),
    APColor("teal", R.string.teal_theme),
    APColor("yellow", R.string.yellow_theme),
)

@Composable
internal fun colorNameToString(colorName: String): Int =
    colorsList().firstOrNull { it.name == colorName }?.nameId ?: R.string.blue_theme

/**
 * The name the language row gives the language in force.
 *
 * Taken from the same list the dialog offers, not from the locale's own display name: a display
 * name is resolved *in* the language being displayed, so a themed locale comes back as a bare tag
 * ("Mgl") or as a plain "中文" that says nothing about which of the six is on.
 */
@Composable
internal fun rememberApplicationLanguageLabel(): String {
    val languages = stringArrayResource(R.array.languages)
    val values = stringArrayResource(R.array.languages_values)
    val tag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
    return remember(tag, languages, values) {
        if (tag == null) {
            languages.firstOrNull().orEmpty()
        } else {
            values.indexOfFirst { it.equals(tag, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.let { languages.getOrNull(it) }
                ?: tag
        }
    }
}

@Composable
internal fun LanguageDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val languages = stringArrayResource(R.array.languages)
    val languageValues = stringArrayResource(R.array.languages_values)
    val selectedTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()

    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_app_language),
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            itemsIndexed(
                items = languages,
                key = { index, _ -> index },
            ) { index, language ->
                val selected = if (index == 0) {
                    selectedTag == null
                } else {
                    languageValues[index].equals(selectedTag, ignoreCase = true)
                }
                RadioButtonPreference(
                    title = language,
                    selected = selected,
                    onClick = {
                        if (index == 0) {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.getEmptyLocaleList(),
                            )
                        } else {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(languageValues[index]),
                            )
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * The brand the standard home calls itself. The names are products rather than prose, so they are
 * kept beside their stable keys and never translated; the panorama home keeps its own title.
 */
@Composable
internal fun HomeTitleDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelected: (key: String) -> Unit = {},
) {
    val titles = stringArrayResource(R.array.home_titles)
    val values = stringArrayResource(R.array.home_titles_values)
    var selected by remember { mutableStateOf(APApplication.sharedPreferences.getString(APApplication.HOME_TITLE, "aster") ?: "aster") }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_home_title),
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            itemsIndexed(
                items = titles,
                key = { index, _ -> values[index] },
            ) { index, title ->
                RadioButtonPreference(
                    title = title,
                    selected = values[index] == selected,
                    onClick = {
                        APApplication.sharedPreferences.edit { putString(APApplication.HOME_TITLE, values[index]) }
                        selected = values[index]
                        onSelected(values[index])
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * The lines the panorama Home says under the greeting, one per line.
 *
 * An empty field is an answer rather than a mistake: it puts the scene back on the set the app
 * ships with. That is why there is no separate reset to find — clearing what is written is the way
 * back. Several lines take turns by the day, the same way the built-in set does.
 */
@Composable
internal fun HomeSceneQuoteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit = {},
) {
    val controlParam = rememberTextFieldState()

    LaunchedEffect(show) {
        if (show) {
            val stored = APApplication.sharedPreferences
                .getString(APApplication.HOME_SCENE_QUOTE, null)
                .orEmpty()
            controlParam.edit { replace(0, length, stored) }
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.home_scene_quote_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = controlParam,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.home_scene_quote_hint),
                lineLimits = TextFieldLineLimits.MultiLine(
                    minHeightInLines = 3,
                    maxHeightInLines = 6,
                ),
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
                        val lines = controlParam.text.toString()
                        onDismiss()
                        onApply(lines)
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

internal fun suPathChecked(path: String): Boolean =
    path.startsWith("/") && path.trim().length > 1
/**
 * The font the app is drawn in: whether it is the reader's own, which file it came from, and the two
 * ways to change either.
 *
 * The file picker and the confirmation for putting the font back belong to the appearance page,
 * which is where the picker, the confirmation and the snackbar that reports them already are.
 */
@Composable
internal fun FontDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onPickFont: () -> Unit,
    onResetFont: () -> Unit,
) {
    val context = LocalContext.current
    val font = CustomFont.state

    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_font),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A font cannot be turned on before there is one, so the switch says that by being
            // unusable until a file has been picked, rather than failing when it is tapped.
            IndicatorSwitchPreference(
                checked = font.enabled,
                onCheckedChange = { CustomFont.setEnabled(context, it) },
                title = stringResource(R.string.settings_font_custom),
                summary = font.title ?: stringResource(R.string.settings_font_custom_summary),
                enabled = font.picked,
            )
            TextButton(
                text = stringResource(R.string.settings_font_pick),
                onClick = onPickFont,
                modifier = Modifier.fillMaxWidth(),
            )
            if (font.picked) {
                TextButton(
                    text = stringResource(R.string.settings_font_reset),
                    onClick = onResetFont,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun ResetSUPathDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val controlParam = rememberTextFieldState()

    LaunchedEffect(show) {
        if (show) {
            val path = withContext(Dispatchers.IO) {
                Natives.suPath()
            }
            controlParam.edit {
                replace(0, length, path)
            }
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.setting_reset_su_path),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                state = controlParam,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_reset_su_new_path),
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
                        val path = controlParam.text.toString()
                        onDismiss()
                        onApply(path)
                    },
                    enabled = suPathChecked(controlParam.text.toString()),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
internal fun SelinuxHideWarningDialog(
    show: Boolean,
    kernelVersion: Int?,
    isGki: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_selinux_hide_warning_title),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if ((kernelVersion ?: 0) < 510) {
                Text(
                    text = stringResource(R.string.settings_selinux_hide_warning_below_5_10),
                    style = MiuixTheme.textStyles.body2,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (!isGki) {
                Text(
                    text = stringResource(R.string.settings_selinux_hide_warning_non_gki),
                    style = MiuixTheme.textStyles.body2,
                )
            }
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
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
internal fun SettingsLogSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.send_log),
        onDismissRequest = onDismiss,
    ) {
        ArrowPreference(
            title = stringResource(R.string.save_log),
            startAction = {
                SettingsIcon(MiuixIcons.FileDownloads)
            },
            onClick = onSave,
        )
        ArrowPreference(
            title = stringResource(R.string.send_log),
            startAction = {
                SettingsIcon(MiuixIcons.Share)
            },
            onClick = onShare,
        )
    }
}
