package me.bmax.apatch.util

import android.os.Parcelable
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.File
import kotlin.concurrent.thread

object PkgConfig {
    private const val TAG = "PkgConfig"

    private const val CSV_HEADER = "pkg,exclude,allow,uid,to_uid,sctx"

    @Immutable
    @Parcelize
    @Keep
    data class Config(
        var pkg: String = "", var exclude: Int = 0, var allow: Int = 0, var profile: Natives.Profile
    ) : Parcelable {
        companion object {
            fun fromLine(line: String): Config? {
                val sp = line.split(',', limit = 6)
                if (sp.size < 6) return null
                val pkg = sp[0].trim()
                val exclude = sp[1].trim().toIntOrNull()
                val allow = sp[2].trim().toIntOrNull()
                val uid = sp[3].trim().toIntOrNull()
                val toUid = sp[4].trim().toIntOrNull()
                val scontext = sp[5].trim()
                if (pkg.isEmpty() || exclude == null || allow == null ||
                    uid == null || toUid == null || scontext.isEmpty()
                ) return null
                return Config(pkg, exclude, allow, Natives.Profile(uid, toUid, scontext))
            }
        }

        fun isDefault(): Boolean {
            return allow == 0 && exclude == 0
        }

        fun toLine(): String {
            return "${pkg},${exclude},${allow},${profile.uid},${profile.toUid},${profile.scontext}"
        }
    }

    fun readConfigs(): HashMap<Int, Config> =
        readConfigs(File(APApplication.PACKAGE_CONFIG_FILE), strict = false)

    /**
     * The lines of a configuration file with the differences that do not change what a record means
     * taken out: a byte-order mark, CRLF endings, blank lines. A file that came from another manager,
     * or from a copy made elsewhere, carries them, and none of them is a reason to refuse the file —
     * which matters more than it looks, because a refusal tells the reader that nothing happened at
     * all. Kept away from the file so the rule can be checked without a device.
     */
    internal fun normaliseConfigLines(lines: List<String>): List<String> =
        lines.map { it.removePrefix("\uFEFF").trimEnd('\r') }.filter { it.isNotBlank() }

    /**
     * Whether a rewrite may be based on these lines, which is what the strict read is for: a file
     * read only in part would lose the grants of every record that was skipped.
     *
     * An empty file counts as a configuration we can read, because it has no record to lose. The
     * kernel side already treats it that way — `load_ap_package_config` accepts a file that is "not
     * found or empty" — and so must we: a device sitting on an empty file would otherwise refuse
     * every authorization the reader ever attempts, which is exactly what an overseas report of
     * "tapping the switch does nothing" turned out to be. Kept apart from the file so the rule can
     * be checked without a device.
     */
    internal fun isReadableConfig(lines: List<String>): Boolean =
        lines.isEmpty() || lines.first() == CSV_HEADER

    internal fun readConfigs(file: File, strict: Boolean): HashMap<Int, Config> {
        val configs = HashMap<Int, Config>()
        if (file.exists()) {
            val lines = normaliseConfigLines(file.readLines())
            if (strict) check(isReadableConfig(lines)) { "Invalid package configuration header" }
            lines.filter { it != CSV_HEADER }.forEach {
                Log.d(TAG, it)
                val p = Config.fromLine(it)
                if (p == null) {
                    check(!strict) { "Invalid package configuration record" }
                    Log.w(TAG, "Skip malformed package_config line: $it")
                } else if (!p.isDefault()) {
                    configs[p.profile.uid] = p
                }
            }
        }
        return configs
    }

    private fun writeConfigs(configs: HashMap<Int, Config>) {
        val content = buildString {
            appendLine(CSV_HEADER)
            configs.values.filterNot { it.isDefault() }.forEach { appendLine(it.toLine()) }
        }
        PackageConfigStorage.writeAtomically(File(APApplication.PACKAGE_CONFIG_FILE), content)
    }

    /**
     * Applies one configuration change, and says whether it landed.
     *
     * The write runs on its own thread, so a caller cannot see the outcome directly: [onResult] is
     * called on that thread with the throwable when the change did not land, and with null when it
     * did. Refusing to rewrite a file we cannot read in full stays deliberate — a partial parse
     * would drop the grants of every record it could not read — but the refusal must not be quiet,
     * because a quiet refusal looks exactly like a tap that never registered.
     */
    fun changeConfig(config: Config, apply: () -> Unit = {}, onResult: (Throwable?) -> Unit = {}) {
        thread {
            synchronized(PkgConfig.javaClass) {
                try {
                    // This used to sit outside the try with its result discarded, so a failure to
                    // escalate escaped the thread unheard and the write below failed for want of it.
                    check(Natives.su()) { "Root access is not available" }
                    PackageConfigStorage.withLock(File(APApplication.PACKAGE_CONFIG_FILE)) {
                        val configs = readConfigs(File(APApplication.PACKAGE_CONFIG_FILE), strict = true)
                        val uid = config.profile.uid
                        // Root App should not be excluded.
                        if (config.allow == 1) config.exclude = 0
                        if (config.isDefault()) {
                            configs.remove(uid)
                        } else {
                            configs[uid] = config
                        }
                        writeConfigs(configs)
                        // Keep the native change inside the same transaction so APD
                        // cannot restore an older profile between persistence and apply.
                        apply()
                    }
                    onResult(null)
                } catch (error: Exception) {
                    Log.e(TAG, "Cannot complete package configuration update", error)
                    onResult(error)
                }
            }
        }
    }
}
