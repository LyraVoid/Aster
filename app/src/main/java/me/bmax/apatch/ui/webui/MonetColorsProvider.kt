package me.bmax.apatch.ui.webui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.Colors
import java.util.concurrent.atomic.AtomicReference

/**
 * @author rifsxd
 * @date 2025/6/2.
 */
object MonetColorsProvider {

    private val colorsCss: AtomicReference<String?> = AtomicReference(null)

    fun getColorsCss(): String {
        return colorsCss.get() ?: ""
    }

    @Composable
    fun UpdateCss() {
        val colorScheme = MiuixTheme.colorScheme

        LaunchedEffect(colorScheme) {
            // Generate CSS only when colorScheme changes.
            colorsCss.set(buildMonetColorsCss(colorScheme))
        }
    }

    internal fun buildMonetColorsCss(colorScheme: Colors): String = buildString {
        append(":root {\n")
        WebUiCssColorKeys.forEach { key ->
            append("  --$key: ${colorScheme.cssValue(key)};\n")
        }
        append("}\n")
    }
}

internal val WebUiCssColorKeys = listOf(
    "primary",
    "onPrimary",
    "primaryContainer",
    "onPrimaryContainer",
    "inversePrimary",
    "secondary",
    "onSecondary",
    "secondaryContainer",
    "onSecondaryContainer",
    "tertiary",
    "onTertiary",
    "tertiaryContainer",
    "onTertiaryContainer",
    "background",
    "onBackground",
    "surface",
    "tonalSurface",
    "onSurface",
    "surfaceVariant",
    "onSurfaceVariant",
    "surfaceTint",
    "inverseSurface",
    "inverseOnSurface",
    "error",
    "onError",
    "errorContainer",
    "onErrorContainer",
    "outline",
    "outlineVariant",
    "scrim",
    "surfaceBright",
    "surfaceDim",
    "surfaceContainer",
    "surfaceContainerHigh",
    "surfaceContainerHighest",
    "surfaceContainerLow",
    "surfaceContainerLowest",
    "filledTonalButtonContentColor",
    "filledTonalButtonContainerColor",
    "filledTonalButtonDisabledContentColor",
    "filledTonalButtonDisabledContainerColor",
    "filledCardContentColor",
    "filledCardContainerColor",
    "filledCardDisabledContentColor",
    "filledCardDisabledContainerColor",
)

private fun Colors.cssValue(key: String): String = when (key) {
    "primary" -> primary
    "onPrimary" -> onPrimary
    "primaryContainer" -> primaryContainer
    "onPrimaryContainer" -> onPrimaryContainer
    "inversePrimary" -> primaryVariant
    "secondary" -> secondary
    "onSecondary" -> onSecondary
    "secondaryContainer" -> secondaryContainer
    "onSecondaryContainer" -> onSecondaryContainer
    "tertiary" -> tertiaryContainerVariant
    "onTertiary" -> tertiaryContainer
    "tertiaryContainer" -> tertiaryContainer
    "onTertiaryContainer" -> onTertiaryContainer
    "background" -> background
    "onBackground" -> onBackground
    "surface" -> surface
    "tonalSurface" -> surfaceContainer
    "onSurface" -> onSurface
    "surfaceVariant" -> surfaceVariant
    "onSurfaceVariant" -> onSurfaceVariantSummary
    "surfaceTint" -> primary
    "inverseSurface" -> disabledOnSurface
    "inverseOnSurface" -> surfaceContainer
    "error" -> error
    "onError" -> onError
    "errorContainer" -> errorContainer
    "onErrorContainer" -> onErrorContainer
    "outline" -> outline
    "outlineVariant" -> dividerLine
    "scrim" -> windowDimming
    "surfaceBright" -> surface
    "surfaceDim" -> background
    "surfaceContainer" -> surfaceContainer
    "surfaceContainerHigh" -> surfaceContainerHigh
    "surfaceContainerHighest" -> surfaceContainerHighest
    "surfaceContainerLow" -> surfaceContainer
    "surfaceContainerLowest" -> background
    "filledTonalButtonContentColor" -> onPrimaryContainer
    "filledTonalButtonContainerColor" -> secondaryContainer
    "filledTonalButtonDisabledContentColor" -> onSurfaceVariantSummary
    "filledTonalButtonDisabledContainerColor" -> surfaceVariant
    "filledCardContentColor" -> onPrimaryContainer
    "filledCardContainerColor" -> primaryContainer
    "filledCardDisabledContentColor" -> onSurfaceVariantSummary
    "filledCardDisabledContainerColor" -> surfaceVariant
    else -> error("Unsupported WebUI CSS color key: $key")
}.toCssValue()

private fun Color.toCssValue(): String {
    fun Float.toHex(): String {
        return (this * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    }
    return if (alpha == 1f) {
        "#${red.toHex()}${green.toHex()}${blue.toHex()}"
    } else {
        "#${red.toHex()}${green.toHex()}${blue.toHex()}${alpha.toHex()}"
    }
}
