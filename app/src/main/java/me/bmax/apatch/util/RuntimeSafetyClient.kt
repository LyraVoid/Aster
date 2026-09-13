package me.bmax.apatch.util

import me.bmax.apatch.APApplication
import org.json.JSONObject

internal object RuntimeSafetyClient {
    internal fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun run(vararg args: String): String {
        val out = ArrayList<String>()
        val err = ArrayList<String>()
        val command = "${APApplication.APD_PATH} runtime-safety " + args.joinToString(" ", transform = ::quote)
        val result = getRootShell().newJob().add(command).to(out, err).exec()
        check(result.isSuccess) { err.joinToString("\n").ifBlank { "Operation failed. Update AndroidPatch from Home if this command is unavailable." } }
        return out.joinToString("\n")
    }

    fun status(): JSONObject = JSONObject(run("status"))
}
