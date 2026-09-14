package me.bmax.apatch.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.bmax.apatch.apApp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchColors
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Whether the manager's own switches put a status icon in their thumb.
 *
 * Held as state rather than read from the preferences by every switch, because that is what makes
 * the appearance row take effect on the screens behind it at once instead of on the next launch.
 */
object SwitchIndicator {
    var enabled by mutableStateOf(apApp.getSwitchIndicatorState())
        private set

    fun update(value: Boolean) {
        enabled = value
        apApp.updateSwitchIndicatorState(value)
    }
}

// Miuix draws its switch out of these numbers and exposes none of them, so they are repeated here:
// a 49x28 dp track holding a 20 dp thumb that starts 4 dp in when off and 25 dp in when on, with
// the thumb centered on the track's height. The indicator rides the same spring Miuix moves the
// thumb with, so the two arrive together rather than one after the other.
private val TrackHeight = 28.dp
private val ThumbSize = 20.dp
private val ThumbOff = 4.dp
private val ThumbOn = 25.dp
private val IndicatorSize = 12.dp

/**
 * A Miuix [Switch] with the status indicator the appearance page offers.
 *
 * Miuix's switch has no slot for thumb content, so the switch itself is still drawn by Miuix and
 * the indicator is laid over its thumb. Read the geometry above before changing any of it.
 */
@Composable
fun IndicatorSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.switchColors(),
    enabled: Boolean = true,
) {
    Box(modifier = modifier) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = colors,
            enabled = enabled,
        )
        if (SwitchIndicator.enabled) {
            val thumbLeft by animateDpAsState(
                targetValue = if (checked) ThumbOn else ThumbOff,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 987f),
            )
            // The icon is drawn in the colour of the track it sits on, which is what keeps it
            // legible on both thumbs without a second colour to maintain.
            val tint = when {
                checked && enabled -> MiuixTheme.colorScheme.primary
                checked -> MiuixTheme.colorScheme.disabledPrimary
                enabled -> MiuixTheme.colorScheme.secondary
                else -> MiuixTheme.colorScheme.disabledSecondary
            }
            Icon(
                imageVector = if (checked) MiuixIcons.Ok else MiuixIcons.Close,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = thumbLeft + (ThumbSize - IndicatorSize) / 2,
                        y = (TrackHeight - IndicatorSize) / 2,
                    )
                    .size(IndicatorSize),
                tint = tint,
            )
        }
    }
}

/**
 * Miuix's `SwitchPreference` with [IndicatorSwitch] where Miuix puts its own switch.
 *
 * Miuix's row renders its switch with no slot for one, so the row is repeated here in the shape
 * Miuix draws it. Everything except the switch itself, down to the end-action row and the
 * [Role.Switch] the row is announced with, is Miuix's own arrangement.
 */
@Composable
@NonRestartableComposable
fun IndicatorSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    switchColors: SwitchColors = SwitchDefaults.switchColors(),
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
            ) {
                endActions()
            }
            IndicatorSwitch(
                checked = checked,
                onCheckedChange = currentOnCheckedChange,
                enabled = enabled,
                colors = switchColors,
            )
        },
        bottomAction = bottomAction,
        onClick = {
            currentOnCheckedChange.takeIf { enabled }?.invoke(!checked)
        },
        role = Role.Switch,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}
