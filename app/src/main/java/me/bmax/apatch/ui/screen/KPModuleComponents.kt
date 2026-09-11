package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.bmax.apatch.R
import me.bmax.apatch.ui.kernelmodule.KPModuleStatus
import me.bmax.apatch.ui.kernelmodule.resolveKPModuleStatuses
import me.bmax.apatch.ui.viewmodel.KPModel
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
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun KPModuleMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
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
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
internal fun KPModuleCard(
    module: KPModel.KPMInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onControl: () -> Unit,
    onRemove: () -> Unit,
) {
    val decoration = TextDecoration.None
    val statuses = resolveKPModuleStatuses(
        loadSource = module.loadSource,
        loaded = module.loaded,
        installed = module.installed,
        disabled = module.disabled,
    )
    val metadata = listOf(module.version, module.author)
        .filter(String::isNotBlank)
        .joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = module.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = decoration,
                )

                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (module.args.isNotBlank()) {
                    Text(
                        text = "${stringResource(R.string.kpm_args)}: ${module.args}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (statuses.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        statuses.forEach { status ->
                            KPModuleStatusBadge(status)
                        }
                    }
                }
            }

            if (module.installed && module.loadSource != "embedded") {
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textDecoration = decoration,
            )
        }

        if (module.loaded || (module.installed && module.loadSource != "embedded")) {
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
                if (module.loaded) {
                    KPModuleActionButton(
                        icon = MiuixIcons.Tune,
                        contentDescription = stringResource(R.string.kpm_control),
                        onClick = onControl,
                    )
                }

                Spacer(Modifier.weight(1f))

                if (module.installed && module.loadSource != "embedded") {
                    KPModuleActionButton(
                        icon = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.kpm_unload),
                        containerColor = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        onClick = onRemove,
                    )
                } else if (module.loaded) {
                    KPModuleActionButton(
                        icon = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.kpm_unload),
                        containerColor = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        onClick = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun KPModuleStatusBadge(status: KPModuleStatus) {
    val label = when (status) {
        KPModuleStatus.EMBEDDED -> stringResource(R.string.kpm_embedded)
        KPModuleStatus.LOADED -> stringResource(R.string.kpm_loaded)
        KPModuleStatus.INSTALLED -> stringResource(R.string.kpm_installed)
        KPModuleStatus.DISABLED -> stringResource(R.string.kpm_disabled)
    }
    val containerColor = when (status) {
        KPModuleStatus.EMBEDDED -> MiuixTheme.colorScheme.tertiaryContainer
        KPModuleStatus.LOADED -> MiuixTheme.colorScheme.primaryContainer
        KPModuleStatus.INSTALLED -> MiuixTheme.colorScheme.secondaryVariant
        KPModuleStatus.DISABLED -> MiuixTheme.colorScheme.errorContainer
    }
    val contentColor = when (status) {
        KPModuleStatus.EMBEDDED -> MiuixTheme.colorScheme.onTertiaryContainer
        KPModuleStatus.LOADED -> MiuixTheme.colorScheme.onPrimaryContainer
        KPModuleStatus.INSTALLED -> MiuixTheme.colorScheme.onSecondaryVariant
        KPModuleStatus.DISABLED -> MiuixTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun KPModuleActionButton(
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
internal fun KPModuleLoadingState() {
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
internal fun KPModuleEmptyState(isSearching: Boolean) {
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
                    R.string.kpm_no_search_results
                } else {
                    R.string.kpm_apm_empty
                },
            ),
            modifier = Modifier.padding(top = 12.dp),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun KPModuleLoadErrorCard(onRetry: () -> Unit) {
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
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.kpm_load_failed),
                    style = MiuixTheme.textStyles.body2,
                )
                TextButton(
                    text = stringResource(R.string.kpm_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 6.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
