package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bmax.apatch.R
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun APModuleCard(
    module: APModuleViewModel.ModuleInfo,
    checked: Boolean,
    updateAvailable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
    onAction: () -> Unit,
    onUninstall: () -> Unit,
    onUndoUninstall: () -> Unit,
) {
    val decoration = if (module.remove) {
        TextDecoration.LineThrough
    } else {
        TextDecoration.None
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = if (module.hasWebUi) onOpen else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (module.remove) 0.62f else 1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = module.name,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = decoration,
                    )

                    if (module.metamodule) {
                        Spacer(Modifier.width(8.dp))
                        APModuleBadge(text = "META")
                    }
                }

                Text(
                    text = "${module.version} · ${module.author}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = decoration,
                )

                if (module.update || module.remove) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (module.update) {
                            APModuleBadge(
                                text = stringResource(R.string.apm_update),
                                containerColor = MiuixTheme.colorScheme.primaryContainer,
                                contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (module.remove) {
                            APModuleBadge(
                                text = stringResource(R.string.apm_remove),
                                containerColor = MiuixTheme.colorScheme.errorContainer,
                                contentColor = MiuixTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = if (module.update) null else onCheckedChange,
                enabled = !module.update,
            )
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(if (module.remove) 0.62f else 1f),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textDecoration = decoration,
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            color = MiuixTheme.colorScheme.dividerLine,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (updateAvailable) {
                APModuleIconAction(
                    icon = MiuixIcons.Download,
                    contentDescription = stringResource(R.string.apm_update),
                    containerColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                    onClick = onUpdate,
                )
            }

            if (module.hasActionScript) {
                APModuleIconAction(
                    icon = MiuixIcons.Play,
                    contentDescription = stringResource(R.string.apm_action),
                    onClick = onAction,
                )
            }

            if (module.hasWebUi) {
                APModuleIconAction(
                    icon = MiuixIcons.Link,
                    contentDescription = stringResource(R.string.apm_webui_open),
                    onClick = onOpen,
                )
            }

            Spacer(Modifier.weight(1f))

            if (module.remove) {
                APModuleIconAction(
                    icon = MiuixIcons.Undo,
                    contentDescription = stringResource(R.string.apm_undo),
                    onClick = onUndoUninstall,
                )
            } else {
                APModuleIconAction(
                    icon = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.apm_remove),
                    containerColor = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    onClick = onUninstall,
                )
            }
        }
    }
}

@Composable
private fun APModuleBadge(
    text: String,
    containerColor: Color = MiuixTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MiuixTheme.colorScheme.onTertiaryContainer,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun APModuleIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = MiuixTheme.colorScheme.secondaryVariant,
    contentColor: Color = MiuixTheme.colorScheme.onSecondaryVariant,
) {
    IconButton(
        onClick = onClick,
        backgroundColor = containerColor,
        minWidth = 40.dp,
        minHeight = 40.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = contentColor,
        )
    }
}

@Composable
internal fun MetaModuleWarningCard(
    text: String,
    onClosed: () -> Unit,
) {
    APModuleNoticeCard(
        message = text,
        icon = MiuixIcons.Info,
        onDismiss = onClosed,
    )
}

@Composable
internal fun APModuleNoticeCard(
    message: String,
    icon: ImageVector = MiuixIcons.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = message,
                    modifier = Modifier.padding(top = if (title.isNullOrBlank()) 0.dp else 4.dp),
                    style = MiuixTheme.textStyles.body2,
                )
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    TextButton(
                        text = actionLabel,
                        onClick = onAction,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
internal fun APModuleLoadingState() {
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
internal fun APModuleEmptyState(
    isSearching: Boolean,
    onInstall: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isSearching) MiuixIcons.Search else MiuixIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(
                if (isSearching) {
                    R.string.apm_no_search_results
                } else {
                    R.string.apm_empty
                },
            ),
            modifier = Modifier.padding(top = 12.dp),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (!isSearching && onInstall != null) {
            Button(
                onClick = onInstall,
                modifier = Modifier.padding(top = 16.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.apm_install),
                    style = MiuixTheme.textStyles.button,
                )
            }
        }
    }
}

@Composable
internal fun APModuleLoadErrorCard(
    onRetry: () -> Unit,
) {
    APModuleNoticeCard(
        message = stringResource(R.string.apm_load_failed),
        actionLabel = stringResource(R.string.apm_retry),
        onAction = onRetry,
    )
}
