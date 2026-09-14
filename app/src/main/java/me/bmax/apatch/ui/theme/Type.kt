package me.bmax.apatch.ui.theme

import androidx.compose.ui.text.font.FontFamily
import top.yukonga.miuix.kmp.theme.TextStyles

/**
 * The same text styles with every one of them drawn in [family].
 *
 * Miuix builds its styles out of the platform's own typefaces, so a font of the reader's own has to
 * be put on each style by hand. A style Miuix adds later keeps the platform's font until it is
 * named here, which is the safe way round: the one thing a missing entry costs is that style.
 */
internal fun TextStyles.withFontFamily(family: FontFamily): TextStyles = copy(
    main = main.copy(fontFamily = family),
    paragraph = paragraph.copy(fontFamily = family),
    body1 = body1.copy(fontFamily = family),
    body2 = body2.copy(fontFamily = family),
    button = button.copy(fontFamily = family),
    footnote1 = footnote1.copy(fontFamily = family),
    footnote2 = footnote2.copy(fontFamily = family),
    headline1 = headline1.copy(fontFamily = family),
    headline2 = headline2.copy(fontFamily = family),
    subtitle = subtitle.copy(fontFamily = family),
    title1 = title1.copy(fontFamily = family),
    title2 = title2.copy(fontFamily = family),
    title3 = title3.copy(fontFamily = family),
    title4 = title4.copy(fontFamily = family),
)
