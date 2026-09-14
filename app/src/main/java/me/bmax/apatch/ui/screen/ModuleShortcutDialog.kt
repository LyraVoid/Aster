package me.bmax.apatch.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.module.ModuleShortcutKind
import me.bmax.apatch.ui.module.defaultShortcutIconPath
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.ModuleShortcut
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Turns a module's run or WebUI button into something the reader can keep on the home screen.
 *
 * Long-pressing either button opens this already knowing which of the two was pressed, so the
 * common case asks nothing; a module that offers both may still switch, because that is the one
 * choice the button could not make for itself.
 */
@Composable
internal fun ModuleShortcutDialog(
    module: APModuleViewModel.ModuleInfo,
    initialKind: ModuleShortcutKind,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasAction = module.hasActionScript && !module.remove
    val hasWebUi = module.hasWebUi && !module.remove

    var kind by remember(module.id, initialKind) { mutableStateOf(initialKind) }
    val defaultIcon = defaultShortcutIconPath(module.actionIcon, module.webuiIcon, kind)
    var iconUri by remember(module.id, kind) { mutableStateOf(defaultIcon) }
    var hasShortcut by remember(module.id, kind) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val label = rememberTextFieldState(module.name)

    LaunchedEffect(module.id, kind) {
        hasShortcut = withContext(Dispatchers.IO) { ModuleShortcut.has(context, kind, module.id) }
    }

    // What the launcher will draw, drawn here first: a module image that cannot be read must show
    // up as the built-in glyph rather than as an empty box the reader only notices on the home
    // screen.
    val preview by produceState<ImageBitmap?>(initialValue = null, iconUri) {
        value = ModuleShortcut.loadIcon(context, iconUri)?.asImageBitmap()
    }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) iconUri = uri.toString()
    }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.apm_shortcut_title),
        summary = module.name,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasAction && hasWebUi) {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.apm_shortcut_action),
                        stringResource(R.string.apm_shortcut_webui),
                    ),
                    selectedTabIndex = if (kind == ModuleShortcutKind.Action) 0 else 1,
                    onTabSelected = { index ->
                        kind = if (index == 0) ModuleShortcutKind.Action else ModuleShortcutKind.WebUi
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = preview
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (kind == ModuleShortcutKind.Action) MiuixIcons.Play else MiuixIcons.Link,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MiuixTheme.colorScheme.onSecondaryVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.apm_shortcut_name_label),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        state = label,
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = stringResource(R.string.apm_shortcut_icon_pick),
                    onClick = { pickIcon.launch("image/*") },
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = iconUri != defaultIcon,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = { iconUri = defaultIcon },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Undo,
                            contentDescription = stringResource(R.string.apm_shortcut_icon_reset),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (hasShortcut) {
                TextButton(
                    text = stringResource(R.string.apm_shortcut_delete),
                    onClick = {
                        ModuleShortcut.delete(context, kind, module.id)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

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
                        creating = true
                        val name = label.text.toString().trim().ifBlank { module.name }
                        scope.launch {
                            val outcome = ModuleShortcut.create(context, kind, module.id, name, module.name, iconUri)
                            ModuleShortcut.report(context, outcome)
                            onDismiss()
                        }
                    },
                    enabled = !creating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        stringResource(
                            if (hasShortcut) R.string.apm_shortcut_update else R.string.apm_shortcut_create,
                        ),
                    )
                }
            }
        }
    }
}
