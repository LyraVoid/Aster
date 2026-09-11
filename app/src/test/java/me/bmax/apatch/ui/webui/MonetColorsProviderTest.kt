package me.bmax.apatch.ui.webui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MonetColorsProviderTest {

    @Test
    fun cssContainsEveryCompatibilityVariableExactlyOnce() {
        val css = MonetColorsProvider.buildMonetColorsCss(lightColorScheme())

        WebUiCssColorKeys.forEach { key ->
            assertEquals(
                "WebUI CSS variable --$key must be emitted exactly once",
                1,
                Regex("(?m)^  --${Regex.escape(key)}: ").findAll(css).count(),
            )
        }
        assertEquals(WebUiCssColorKeys.size, WebUiCssColorKeys.distinct().size)
    }

    @Test
    fun cssColorsUseValidRgbOrRgbaHexValues() {
        val css = MonetColorsProvider.buildMonetColorsCss(lightColorScheme())
        val values = Regex("(?m)^  --[^:]+: (#[0-9a-f]{6}(?:[0-9a-f]{2})?);$")
            .findAll(css)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(WebUiCssColorKeys.size, values.size)
        assertTrue(values.all { it.startsWith("#") })
    }
}
