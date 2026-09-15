package me.bmax.apatch.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.bmax.apatch.ui.theme.LocalThemeModeState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class WarningCardTone {
    Warning,
    Neutral,
}

// How much of the error colour is mixed into the card's own surface for the warning tone. A dark
// surface swallows a tint, so it takes more of it to still read as one - the home's work card is
// mixed the same way, and for the same reason.
private const val WarningCardTintLight = 0.16f
private const val WarningCardTintDark = 0.24f

/**
 * The background a warning card is painted with.
 *
 * It is mixed out of the card's own surface instead of being taken from the palette's error
 * container. In the 2025 specification that container is a saturated red, and the colour the palette
 * pairs with it clears only about 4.5:1 against it - a rating meant for a chip or a badge, not for a
 * sentence the reader has to take in, and on a vivid palette it is the difference between a warning
 * that is read and one that is squinted at. Mixed this way the card keeps the warning's colour while
 * the words stay in the colour the theme paints text with: measured above 8:1 on a light palette and
 * above 10:1 on a dark one, where the flat container left 4.5:1.
 */
@Composable
private fun warningContainerColor(): Color = lerp(
    MiuixTheme.colorScheme.surfaceContainer,
    MiuixTheme.colorScheme.error,
    if (LocalThemeModeState.current.isDark) WarningCardTintDark else WarningCardTintLight,
)

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
        WarningCardTone.Warning -> warningContainerColor()
        WarningCardTone.Neutral -> MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (tone) {
        WarningCardTone.Warning -> MiuixTheme.colorScheme.onSurfaceContainer
        WarningCardTone.Neutral -> MiuixTheme.colorScheme.onSurface
    }
    // The icon keeps the warning's own colour so the card still reads as one at a glance, the words
    // around it being in the ordinary reading colour.
    val accentColor = when (tone) {
        WarningCardTone.Warning -> MiuixTheme.colorScheme.error
        WarningCardTone.Neutral -> contentColor
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
                    tint = accentColor,
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
                IconButton(
                    onClick = onClose,
                    minWidth = 36.dp,
                    minHeight = 36.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
