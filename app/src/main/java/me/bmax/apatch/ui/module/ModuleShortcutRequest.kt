package me.bmax.apatch.ui.module

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val MODULE_SHORTCUT_SCHEME = "apatch"
internal const val MODULE_SHORTCUT_HOST_ACTION = "action"
internal const val MODULE_SHORTCUT_HOST_WEBUI = "webui"
internal const val MODULE_SHORTCUT_ID_PARAM = "id"
internal const val MODULE_SHORTCUT_TOKEN_PARAM = "token"

/** 32 bytes of randomness, spelled out as hex: as long as the shortcut's own deep link. */
internal const val MODULE_SHORTCUT_TOKEN_BYTES = 32

private const val MODULE_SHORTCUT_MAX_ID_LENGTH = 128

/** What a launcher shortcut asks for. Only ever built from a request that passed [resolveModuleShortcutRequest]. */
internal sealed interface ModuleShortcutRequest {
    val moduleId: String

    /** Run the module's `action.sh` and show its log, exactly as the card's run button does. */
    data class ExecuteAction(override val moduleId: String) : ModuleShortcutRequest

    /** Open the module's WebUI, exactly as the card's link button does. */
    data class OpenWebUi(override val moduleId: String) : ModuleShortcutRequest
}

/** Which of the two shortcuts a module can offer. */
internal enum class ModuleShortcutKind {
    /** Runs the module's `action.sh`. */
    Action,

    /** Opens the module's WebUI. */
    WebUi,
}

/**
 * Which image a shortcut of [kind] starts with. A module that ships only one of the two icons still
 * gets a picture rather than the app icon: the reader's own module is easier to recognise than the
 * manager is, and the button they long-pressed was going to use that same image.
 */
internal fun defaultShortcutIconPath(
    actionIcon: String,
    webuiIcon: String,
    kind: ModuleShortcutKind,
): String {
    val action = actionIcon.trim()
    val webui = webuiIcon.trim()
    return when (kind) {
        ModuleShortcutKind.Action -> action.ifEmpty { webui }
        ModuleShortcutKind.WebUi -> webui.ifEmpty { action }
    }
}

/**
 * A module id is one directory name under `/data/adb/modules`, and it reaches APD inside a shell
 * command (`apd module action <id>`). Ids that come from the manager's own module list can be
 * trusted; an id that arrives with an intent cannot, so it has to look like a plain id before
 * anything is handed to the root shell.
 */
internal fun isModuleShortcutId(id: String): Boolean {
    if (id.isEmpty() || id.length > MODULE_SHORTCUT_MAX_ID_LENGTH) return false
    return id.all { c ->
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-' || c == '.'
    } && id != "." && id != ".."
}

internal fun moduleShortcutToken(random: ByteArray): String =
    random.joinToString(separator = "") { "%02x".format(it) }

/**
 * Resolves the data of the intent a module shortcut was launched with.
 *
 * MainActivity is exported (it is the launcher activity), so any app on the device can hand it the
 * very same explicit intent the launcher does. The token is the whole of the difference between a
 * shortcut and a stranger: without a matching one nothing here runs an action script.
 */
internal fun resolveModuleShortcutRequest(
    scheme: String?,
    host: String?,
    moduleId: String?,
    token: String?,
    expectedToken: String,
): ModuleShortcutRequest? {
    if (scheme != MODULE_SHORTCUT_SCHEME) return null
    if (expectedToken.isEmpty() || token != expectedToken) return null
    val id = moduleId?.takeIf(::isModuleShortcutId) ?: return null
    return when (host) {
        MODULE_SHORTCUT_HOST_ACTION -> ModuleShortcutRequest.ExecuteAction(id)
        MODULE_SHORTCUT_HOST_WEBUI -> ModuleShortcutRequest.OpenWebUi(id)
        else -> null
    }
}

/**
 * The one request a shortcut handed the activity, waiting for the navigation host to be composed.
 * Held as state instead of passed down as a parameter because a shortcut can arrive before the
 * first composition and again while the activity is already on screen.
 */
internal object ModuleShortcutRequests {
    private val _pending = MutableStateFlow<ModuleShortcutRequest?>(null)

    val pending: StateFlow<ModuleShortcutRequest?> = _pending.asStateFlow()

    fun publish(request: ModuleShortcutRequest) {
        _pending.value = request
    }

    fun consume() {
        _pending.value = null
    }
}
