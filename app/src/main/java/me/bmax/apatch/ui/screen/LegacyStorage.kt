package me.bmax.apatch.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The storage permission this app still needs before it can write a file into the public Downloads
 * folder on this Android version.
 *
 * Below Android 10 that folder is written as a path, and the write needs WRITE_EXTERNAL_STORAGE.
 * From Android 10 on the write goes through MediaStore, which needs nothing, and the legacy flag
 * in the manifest is ignored there. Choosing a file to *read* never needed a permission at all:
 * every picker in this app is a Storage Access Framework or photo-picker one, which hands back a
 * grant for the one file that was picked.
 */
internal fun legacyStoragePermissions(sdkInt: Int): List<String> =
    if (sdkInt < Build.VERSION_CODES.Q) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    else emptyList()

/**
 * Runs an action once whatever this Android version needs before writing into Downloads has been
 * granted — asking for it at that moment rather than when a screen opens, so nothing is asked for
 * that the tap in front of it will not use.
 *
 * A composable because a permission is a request to a screen and only a screen can make it, and
 * remembered because the answer has to survive the trip out to the dialog and back.
 */
@Composable
internal fun rememberStoragePermissionForWrite(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val answer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val action = pending
        pending = null
        // A refusal is not retried: the write below reports what it could not do.
        if (granted.values.all { it }) action?.invoke()
    }
    return { action ->
        val missing = legacyStoragePermissions(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pending = action
            answer.launch(missing.toTypedArray())
        }
    }
}
