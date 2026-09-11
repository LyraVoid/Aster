package me.bmax.apatch.ui.webui

internal object WebUiThemeInjector {
    const val StyleElementId = "aster-webui-theme-colors"

    fun buildScript(css: String): String {
        val cssLiteral = css.toJavaScriptStringLiteral()
        return """
            (function() {
                const id = "$StyleElementId";
                let style = document.getElementById(id);
                if (!style) {
                    style = document.createElement("style");
                    style.id = id;
                    (document.head || document.documentElement).appendChild(style);
                }
                style.textContent = $cssLiteral;
            })();
        """.trimIndent()
    }
}

private fun String.toJavaScriptStringLiteral(): String = buildString {
    append('"')
    this@toJavaScriptStringLiteral.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}
