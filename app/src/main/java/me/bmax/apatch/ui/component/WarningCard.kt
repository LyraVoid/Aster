package me.bmax.apatch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class WarningCardTone {
    Warning,
    Neutral,
}

@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    tone: WarningCardTone = WarningCardTone.Warning,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val containerColor = when (tone) {
        WarningCardTone.Warning -> MiuixTheme.colorScheme.errorContainer
        WarningCardTone.Neutral -> MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (tone) {
        WarningCardTone.Warning -> MiuixTheme.colorScheme.onErrorContainer
        WarningCardTone.Neutral -> MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
            } else {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (onClose != null) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    tint = contentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose),
                )
            }
        }
    }
}
