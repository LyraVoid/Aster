package me.bmax.apatch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Destination<RootGraph>
@Composable
fun RuntimeSafetyScreen(navigator: DestinationsNavigator) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<String?>(null) }
    val source = rememberTextFieldState()
    suspend fun refresh() { status = withContext(Dispatchers.IO) { RuntimeSafetyClient.status() } }
    fun execute(vararg args: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { RuntimeSafetyClient.run(*args) }
                if (args.firstOrNull() == "logs") logs = result.takeLast(16000)
                error = null
                refresh()
            } catch (failure: Exception) {
                error = failure.message
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
    val state = status?.optJSONObject("state")
    val phase = state?.optString("phase") ?: "off"
    val safe = status?.optBoolean("safe_mode", true) ?: true
    val canPreview = status != null && !safe && !busy && phase in listOf("off", "preview")
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(topBar = {
        SettingsTopBar(title = R.string.runtime_safety_title, scrollBehavior = scrollBehavior, navigator = navigator)
    }) { innerPadding ->
        LazyColumn(
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
                            phase == "active" -> R.string.runtime_safety_active
                            phase == "restoring" -> R.string.runtime_safety_restoring
                            else -> R.string.runtime_safety_off
                        }))
                        state?.optString("error")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
                        status?.optString("safety_error")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) }
                        error?.let { Text(it) }
                    }
                }
            }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.runtime_safety_hide_description))
                        Button(onClick = { execute("preview", "hide") }, enabled = canPreview) {
                            Text(stringResource(R.string.runtime_safety_hide_preview))
                        }
                        Text(stringResource(R.string.runtime_safety_umount_description))
                        TextField(state = source, modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.runtime_safety_module_path), lineLimits = TextFieldLineLimits.SingleLine)
                        Button(onClick = { execute("preview", "umount", "--source", source.text.toString().trim()) },
                            enabled = canPreview && source.text.isNotBlank()) {
                            Text(stringResource(R.string.runtime_safety_umount_preview))
                        }
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
                        Button(onClick = { execute("logs") }, enabled = !busy) { Text(stringResource(R.string.runtime_safety_logs)) }
                        logs?.let { Text(it.ifBlank { stringResource(R.string.runtime_safety_logs_empty) }) }
                    }
                }
            }
        }
    }
}
