package me.bmax.apatch.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.MainActivity
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_HOST_ACTION
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_HOST_WEBUI
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_ICON_MAX_SIDE
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_ID_PARAM
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_SCHEME
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_TOKEN_BYTES
import me.bmax.apatch.ui.module.MODULE_SHORTCUT_TOKEN_PARAM
import me.bmax.apatch.ui.module.ModuleShortcutKind
import me.bmax.apatch.ui.module.isModuleIconPath
import me.bmax.apatch.ui.module.loadModuleBitmap
import me.bmax.apatch.ui.module.moduleIconSampleSize
import me.bmax.apatch.ui.module.moduleShortcutToken
import me.bmax.apatch.ui.module.squareModuleIcon
import java.security.SecureRandom
import java.util.Locale

/**
 * The two launcher shortcuts a module can offer: one that runs its action script, one that opens
 * its WebUI. Both belong to the manager rather than to the module, because both come back through
 * the manager before anything happens: the shortcut only carries a deep link that MainActivity
 * accepts when it matches the token this install generated.
 */
internal object ModuleShortcut {
    private const val TAG = "ModuleShortcut"
    private const val TOKEN_KEY = "module_shortcut_token"
    private const val MODULE_NAME_EXTRA = "module_name"
    private const val COLOR_OS_PERMISSION_URI = "content://settings/secure/launcher_shortcut_permission_settings"

    // MIUI and HyperOS gate pinning behind an appop of their own; other platforms either allow it
    // or have no way to ask.
    private const val MIUI_SHORTCUT_OP = 10017

    /**
     * The token this install signs its own shortcut links with. Random, per install, and never
     * handed to anything else: MainActivity is the launcher activity and therefore exported, so
     * without it any app could replay the intent a shortcut carries and have the root shell run a
     * module's action script.
     */
    internal fun token(context: Context): String {
        val prefs = APApplication.sharedPreferences
        prefs.getString(TOKEN_KEY, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val fresh = moduleShortcutToken(ByteArray(MODULE_SHORTCUT_TOKEN_BYTES).also { SecureRandom().nextBytes(it) })
        prefs.edit().putString(TOKEN_KEY, fresh).apply()
        return fresh
    }

    internal fun shortcutId(kind: ModuleShortcutKind, moduleId: String): String = when (kind) {
        ModuleShortcutKind.Action -> "module_action_$moduleId"
        ModuleShortcutKind.WebUi -> "module_webui_$moduleId"
    }

    internal fun has(context: Context, kind: ModuleShortcutKind, moduleId: String): Boolean =
        hasPinnedShortcut(context, shortcutId(kind, moduleId))

    /**
     * Builds the intent the launcher stores for a shortcut. [moduleName] travels along because the
     * WebUI shell names its task after the module, not after whatever the shortcut was labelled.
     */
    internal fun buildIntent(
        context: Context,
        kind: ModuleShortcutKind,
        moduleId: String,
        moduleName: String,
    ): Intent {
        val host = when (kind) {
            ModuleShortcutKind.Action -> MODULE_SHORTCUT_HOST_ACTION
            ModuleShortcutKind.WebUi -> MODULE_SHORTCUT_HOST_WEBUI
        }
        val data = Uri.Builder()
            .scheme(MODULE_SHORTCUT_SCHEME)
            .authority(host)
            .appendQueryParameter(MODULE_SHORTCUT_ID_PARAM, moduleId)
            .appendQueryParameter(MODULE_SHORTCUT_TOKEN_PARAM, token(context))
            .build()
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setData(data)
            putExtra(MODULE_NAME_EXTRA, moduleName)
            // Bring the manager up instead of stacking another copy of it, and drop whatever the
            // WebUI shell left above it: a shortcut tap is a fresh request, not a resume.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    internal fun moduleNameOf(intent: Intent): String =
        intent.getStringExtra(MODULE_NAME_EXTRA)?.takeIf { it.isNotBlank() } ?: ""

    /**
     * Pins a shortcut, or refreshes one that is already pinned, and answers what happened instead
     * of saying it: the caller closes its dialog first and then calls [report].
     */
    internal suspend fun create(
        context: Context,
        kind: ModuleShortcutKind,
        moduleId: String,
        label: String,
        moduleName: String,
        iconUri: String?,
    ): Outcome {
        val id = shortcutId(kind, moduleId)
        val alreadyPinned = hasPinnedShortcut(context, id)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIntent(buildIntent(context, kind, moduleId, moduleName))
            .setIcon(iconFor(context, iconUri))
            .build()

        // Pushing it as a dynamic shortcut is what updates a shortcut the user pinned earlier, and
        // it is also the only entry a launcher that cannot pin anything would ever show.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
            .onFailure { Log.w(TAG, "pushDynamicShortcut failed for $id", it) }

        if (alreadyPinned) return Outcome.Updated
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return Outcome.Unsupported
        if (desktopShortcutPermission(context) == ShortcutPermission.Denied) return Outcome.PermissionRequired

        val pinned = runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
            .onFailure { Log.w(TAG, "requestPinShortcut failed for $id", it) }
            .getOrDefault(false)
        return if (pinned) Outcome.Pinned else Outcome.PermissionRequired
    }

    /** Forgets both shortcuts of a module, which is what uninstalling it has to do. */
    internal fun delete(context: Context, moduleId: String) {
        forget(context, ModuleShortcutKind.entries.map { shortcutId(it, moduleId) })
    }

    /** Forgets one shortcut, which is what the dialog's own delete row does. */
    internal fun delete(context: Context, kind: ModuleShortcutKind, moduleId: String) {
        forget(context, listOf(shortcutId(kind, moduleId)))
    }

    private fun forget(context: Context, ids: List<String>) {
        runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, ids) }
            .onFailure { Log.w(TAG, "removeDynamicShortcuts failed for $ids", it) }
        runCatching { ShortcutManagerCompat.disableShortcuts(context, ids, "") }
            .onFailure { Log.w(TAG, "disableShortcuts failed for $ids", it) }
    }

    /** Shows the hint that fits this device, and opens the screen that can fix it when we know which one that is. */
    internal fun report(context: Context, outcome: Outcome) {
        val message = when (outcome) {
            Outcome.Pinned -> R.string.apm_shortcut_created
            Outcome.Updated -> R.string.apm_shortcut_updated
            Outcome.Unsupported -> R.string.apm_shortcut_not_supported
            Outcome.PermissionRequired -> if (isColorOs()) {
                R.string.apm_shortcut_permission_tip_coloros
            } else {
                R.string.apm_shortcut_permission_tip_default
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        // Only walk the reader to the settings page when we know the switch is off. On a platform
        // we cannot read, the pin attempt may have failed for a reason that page cannot fix.
        if (outcome == Outcome.PermissionRequired && desktopShortcutPermission(context) == ShortcutPermission.Denied) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { Log.w(TAG, "opening app details failed", it) }
        }
    }

    /**
     * Loads the image a shortcut should carry: an APD-validated path inside the module, or whatever
     * the reader picked when the shortcut was created. Blocking root or provider I/O.
     */
    internal suspend fun loadIcon(context: Context, iconUri: String?): Bitmap? = withContext(Dispatchers.IO) {
        val uri = iconUri?.trim().orEmpty()
        if (uri.isEmpty()) return@withContext null
        if (isModuleIconPath(uri)) return@withContext loadModuleBitmap(uri, MODULE_SHORTCUT_ICON_MAX_SIDE)
        // A picked image is not a module icon, but the same bounds, formats and sampling apply:
        // nothing here should decode a 20000x20000 file just because the reader chose it.
        runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outMimeType !in SUPPORTED_ICON_MIME_TYPES) return@use null
                val sample = moduleIconSampleSize(bounds.outWidth, bounds.outHeight, MODULE_SHORTCUT_ICON_MAX_SIDE)
                    ?: return@use null
                context.contentResolver.openInputStream(uri.toUri())?.use { second ->
                    BitmapFactory.decodeStream(
                        second,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )?.let { squareModuleIcon(it, MODULE_SHORTCUT_ICON_MAX_SIDE) }
                }
            }
        }.getOrNull()
    }

    private val SUPPORTED_ICON_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

    private suspend fun iconFor(context: Context, iconUri: String?): IconCompat =
        loadIcon(context, iconUri)?.let(IconCompat::createWithBitmap)
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)

    private fun hasPinnedShortcut(context: Context, id: String): Boolean = runCatching {
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == id && it.isEnabled }
    }.getOrDefault(false)

    private enum class ShortcutPermission { Granted, Denied, Unknown }

    private fun desktopShortcutPermission(context: Context): ShortcutPermission = when {
        isColorOs() -> colorOsPermission(context)
        isMiui() -> miuiPermission(context)
        else -> ShortcutPermission.Unknown
    }

    /** ColorOS keeps the switch in a secure setting, one `<package>, <0|1>` row per app. */
    private fun colorOsPermission(context: Context): ShortcutPermission = runCatching {
        val cursor = context.contentResolver.query(COLOR_OS_PERMISSION_URI.toUri(), null, null, null, null)
            ?: return@runCatching ShortcutPermission.Unknown
        cursor.use {
            val column = it.getColumnIndex("value")
            if (column < 0) return@use ShortcutPermission.Unknown
            while (it.moveToNext()) {
                val row = it.getString(column) ?: continue
                if (row.contains("${context.packageName}, 0")) return@use ShortcutPermission.Denied
                if (row.contains("${context.packageName}, 1")) return@use ShortcutPermission.Granted
            }
            ShortcutPermission.Unknown
        }
    }.getOrDefault(ShortcutPermission.Unknown)

    private fun miuiPermission(context: Context): ShortcutPermission = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return@runCatching ShortcutPermission.Unknown
        @Suppress("DiscouragedPrivateApi")
        val check = AppOpsManager::class.java.getDeclaredMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        when (check.invoke(appOps, MIUI_SHORTCUT_OP, context.applicationInfo.uid, context.packageName)?.toString()) {
            "0" -> ShortcutPermission.Granted
            "1" -> ShortcutPermission.Denied
            else -> ShortcutPermission.Unknown
        }
    }.getOrDefault(ShortcutPermission.Unknown)

    private fun isColorOs(): Boolean = brandOf().let { brand ->
        listOf("oppo", "oneplus", "realme", "oplus").any { it in brand }
    }

    private fun isMiui(): Boolean = brandOf().let { brand ->
        listOf("xiaomi", "redmi", "poco").any { it in brand }
    }

    private fun brandOf(): String =
        "${Build.BRAND} ${Build.MANUFACTURER}".lowercase(Locale.ROOT)

    internal enum class Outcome {
        /** The shortcut is on the home screen now. */
        Pinned,

        /** The user already had this shortcut; its name or icon was refreshed in place. */
        Updated,

        /** This launcher cannot pin shortcuts at all. */
        Unsupported,

        /** The launcher refused, in the way these platforms do: the desktop-shortcut switch is off. */
        PermissionRequired,
    }
}
