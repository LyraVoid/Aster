package me.bmax.apatch.ui.webui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUiThemeInjectorTest {

    @Test
    fun scriptCreatesOrUpdatesDedicatedStyleElement() {
        val script = WebUiThemeInjector.buildScript(":root { --primary: #123456; }\n")

        assertTrue(script.contains("document.getElementById(id)"))
        assertTrue(script.contains("document.createElement(\"style\")"))
        assertTrue(script.contains(WebUiThemeInjector.StyleElementId))
        assertTrue(script.contains("style.textContent ="))
    }

    @Test
    fun scriptEscapesThemeCssAsAJavaScriptStringLiteral() {
        val script = WebUiThemeInjector.buildScript(":root {\n  --label: \"quoted\";\n}")

        assertTrue(script.contains("\\n"))
        assertTrue(script.contains("\\\"quoted\\\""))
        assertFalse(script.contains("style.textContent = \":root {\n"))
    }
}
