package me.bmax.apatch.ui.screen

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
        ) {
            content()
        }
    }
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

internal fun suPathChecked(path: String): Boolean =
    path.startsWith("/") && path.trim().length > 1

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
