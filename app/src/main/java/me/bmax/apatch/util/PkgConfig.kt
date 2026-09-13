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

    fun readConfigs(): HashMap<Int, Config> = readConfigs(strict = false)

    private fun readConfigs(strict: Boolean): HashMap<Int, Config> {
        val configs = HashMap<Int, Config>()
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (file.exists()) {
            val lines = file.readLines()
            if (strict) check(lines.firstOrNull() == CSV_HEADER) { "Invalid package configuration header" }
            lines.filter { it.isNotBlank() && it != CSV_HEADER }.forEach {
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

    fun changeConfig(config: Config, apply: () -> Unit = {}) {
        thread {
            synchronized(PkgConfig.javaClass) {
                Natives.su()
                try {
                    PackageConfigStorage.withLock(File(APApplication.PACKAGE_CONFIG_FILE)) {
                        val configs = readConfigs(strict = true)
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
                } catch (error: Exception) {
                    Log.e(TAG, "Cannot complete package configuration update", error)
                }
            }
        }
    }
}
