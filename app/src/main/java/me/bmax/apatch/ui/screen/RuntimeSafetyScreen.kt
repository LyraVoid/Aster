package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.R
import me.bmax.apatch.ui.shell.LocalFloatingNavigationInset
import me.bmax.apatch.util.RuntimeSafetyClient
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.ui.platform.LocalContext

@Destination<RootGraph>
@Composable
fun RuntimeSafetyScreen(navigator: DestinationsNavigator) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var legacyNoChanges by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val source = rememberTextFieldState()
    suspend fun refresh() { status = withContext(Dispatchers.IO) { RuntimeSafetyClient.status() } }
    fun execute(vararg args: String) {
        if (busy) return
        busy = true
        legacyNoChanges = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { RuntimeSafetyClient.run(*args) }
                if (args.firstOrNull() == "logs") logs = result.takeLast(16000)
                error = null
                refresh()
            } catch (failure: Exception) {
                legacyNoChanges = args.firstOrNull() == "preview" && args.getOrNull(1) == "hide" && failure.message?.contains("No changes required") == true
                error = if (legacyNoChanges) null else failure.message
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (!busy) {
                try { refresh() } catch (failure: Exception) { error = failure.message; status = null }
            }
            delay(2000)
        }
    }
    // The field is filled once from the saved configuration; polling must never
    // overwrite what the user is typing.
    LaunchedEffect(status) {
        val saved = status?.optJSONObject("config")?.optString("umount_paths").orEmpty()
        if (!prefilled && status != null) {
            source.edit { replace(0, length, saved) }
            prefilled = true
        }
    }
    val state = status?.optJSONObject("state")
    val phase = state?.optString("phase") ?: "off"
    val safe = status?.optBoolean("safe_mode", true) ?: true
    val config = status?.optJSONObject("config")
    val hideAuto = config?.optBoolean("hide_auto") ?: false
    val umountAuto = config?.optBoolean("umount_auto") ?: false
    val probe = config?.optBoolean("boot_probe") ?: false
    val auto = status?.optJSONObject("auto")
    val autoEnabled = status != null && !busy && !safe
    val canPreview = status != null && !safe && !busy && phase in listOf("off", "preview")
    val savedPaths = config?.optString("umount_paths").orEmpty()
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
    val listState = rememberLazyListState()
    LaunchedEffect(phase) {
        if (phase in listOf("preview", "trial", "recovery_failed")) listState.animateScrollToItem(1)
    }
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(topBar = {
        SettingsTopBar(title = R.string.runtime_safety_title, scrollBehavior = scrollBehavior, navigator = navigator)
    }) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + LocalFloatingNavigationInset.current + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.runtime_safety_description))
                        Text(stringResource(when {
                            status == null -> R.string.runtime_safety_unavailable
                            phase == "recovery_failed" -> R.string.runtime_safety_recovery_failed
                            safe -> R.string.runtime_safety_safe_mode
                            phase == "preview" -> R.string.runtime_safety_preview
                            phase in listOf("queued", "applying") -> R.string.runtime_safety_applying
                            phase == "trial" -> R.string.runtime_safety_trial
                            phase == "active" && state?.optBoolean("auto") == true -> R.string.runtime_safety_active_auto
                            phase == "active" -> R.string.runtime_safety_active
                            phase == "restoring" -> R.string.runtime_safety_restoring
                            else -> R.string.runtime_safety_off
                        }))
                        state?.optString("error")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
                        status?.optString("safety_error")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
                        auto?.optString("last_error")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                            Text(stringResource(R.string.runtime_safety_auto_record, it))
                        }
                        when (state?.optString("notice")) {
                            "already_matches" -> Text(stringResource(R.string.runtime_safety_already_matches))
                            "properties_unavailable" -> Text(stringResource(R.string.runtime_safety_properties_unavailable))
                        }
                        if (legacyNoChanges) Text(stringResource(R.string.runtime_safety_legacy_no_changes))
                        error?.let { Text(it) }
                    }
                }
            }
            if (state != null && phase !in listOf("off")) {
                item {
                    SettingsCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val changes = state.optJSONArray("changes")
                            if (changes != null) for (i in 0 until changes.length()) {
                                val change = changes.getJSONObject(i)
                                Text(if (change.optString("type") == "Property") {
                                    "${change.optString("key")}: ${change.optString("before")} → ${change.optString("after")}"
                                } else {
                                    "${change.optString("source")} → ${change.optString("target")}"
                                })
                            }
                            if (phase == "preview") Button(onClick = { execute("apply", state.getString("token")) }, enabled = !busy && !safe) {
                                Text(stringResource(R.string.runtime_safety_start_trial))
                            }
                            if (phase == "trial") Button(onClick = { execute("confirm", state.getString("token")) }, enabled = !busy && !safe) {
                                Text(stringResource(R.string.runtime_safety_keep))
                            }
                            Button(onClick = { execute("restore") }, enabled = !busy) {
                                Text(stringResource(R.string.runtime_safety_restore))
                            }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIcon(MiuixIcons.Lock)
                            Text(stringResource(R.string.runtime_safety_hide_title), fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(R.string.runtime_safety_hide_description))
                        Text(stringResource(R.string.runtime_safety_hide_steps))
                        SettingsSwitchRow(
                            title = stringResource(R.string.runtime_safety_hide_auto),
                            checked = hideAuto,
                            enabled = autoEnabled || hideAuto,
                            onCheckedChange = { execute("configure", "--hide-auto", if (it) "on" else "off") },
                        )
                        Text(stringResource(R.string.runtime_safety_auto_hint))
                        Button(onClick = { execute("preview", "hide") }, enabled = canPreview) {
                            Text(stringResource(R.string.runtime_safety_hide_preview))
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIcon(MiuixIcons.Layers)
                            Text(stringResource(R.string.runtime_safety_umount_title), fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(R.string.runtime_safety_umount_description))
                        TextField(state = source, modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.runtime_safety_module_path), lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 8))
                        Text(stringResource(R.string.runtime_safety_module_examples))
                        SettingsSwitchRow(
                            title = stringResource(R.string.runtime_safety_umount_auto),
                            checked = umountAuto,
                            enabled = autoEnabled || umountAuto,
                            onCheckedChange = {
                                if (it && source.text.isBlank()) {
                                    error = context.getString(R.string.runtime_safety_need_paths)
                                } else {
                                    execute("configure", "--source", source.text.toString(), "--umount-auto", if (it) "on" else "off")
                                }
                            },
                        )
                        Text(stringResource(R.string.runtime_safety_auto_hint))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { execute("configure", "--source", source.text.toString()) }, enabled = !busy) {
                                Text(stringResource(R.string.runtime_safety_save_paths))
                            }
                            Text(stringResource(R.string.runtime_safety_paths_saved, savedPaths))
                        }
                        Button(onClick = { execute("preview", "umount", "--source", source.text.toString()) },
                            enabled = canPreview && source.text.isNotBlank()) {
                            Text(stringResource(R.string.runtime_safety_umount_preview))
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIcon(MiuixIcons.Refresh)
                            Text(stringResource(R.string.runtime_safety_auto_title), fontWeight = FontWeight.SemiBold)
                        }
                        val enabled = buildList {
                            if (hideAuto) add(stringResource(R.string.runtime_safety_hide_title))
                            if (umountAuto) add(stringResource(R.string.runtime_safety_umount_title))
                        }
                        Text(if (enabled.isEmpty()) stringResource(R.string.runtime_safety_auto_off)
                            else stringResource(R.string.runtime_safety_auto_running, enabled.joinToString(", ")))
                        Text(stringResource(R.string.runtime_safety_auto_notice))
                        auto?.optString("last_result")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                            Text(stringResource(R.string.runtime_safety_auto_record, it))
                        }
                        auto?.optString("interruption")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                            Text(stringResource(R.string.runtime_safety_auto_interrupted))
                            Text(stringResource(R.string.runtime_safety_auto_record, it))
                        }
                        SettingsSwitchRow(
                            title = stringResource(R.string.runtime_safety_probe),
                            checked = probe,
                            enabled = status != null && !busy,
                            onCheckedChange = { execute("configure", "--probe", if (it) "on" else "off") },
                        )
                        Text(stringResource(R.string.runtime_safety_probe_description))
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { execute("logs") }, enabled = !busy) { Text(stringResource(R.string.runtime_safety_logs)) }
                        logs?.let { Text(it.ifBlank { stringResource(R.string.runtime_safety_logs_empty) }) }
                    }
                }
            }
        }
    }
}

/** One label plus a switch, used for the persisted automatic options. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
