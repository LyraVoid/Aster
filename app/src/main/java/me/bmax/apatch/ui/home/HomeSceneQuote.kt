package me.bmax.apatch.ui.home

/**
 * The line the panorama scene says under the greeting.
 *
 * The reader may write their own, one per line; when they have not, the set the app ships with
 * stands in. Either way a single line is shown at a time and the rest wait their turn, so several
 * lines read as a rotation rather than as a list to get through.
 *
 * Kept apart from the screen and the preference so the rule can be checked without a device.
 */
internal object HomeSceneQuote {

    /**
     * The reader's lines, without the blanks and stray spaces a text field collects. A line that is
     * only spaces is a line they did not mean to write.
     */
    fun parse(custom: String?): List<String> =
        custom?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            ?: emptyList()

    /**
     * The line for [dayOfYear].
     *
     * The day is what turns it over. A line that changed while the reader was looking at the screen
     * they open most would be a distraction, and so would one that changed on every recomposition.
     */
    fun resolve(custom: String?, builtIn: List<String>, dayOfYear: Int): String {
        val lines = parse(custom).ifEmpty { builtIn }
        if (lines.isEmpty()) return ""
        // dayOfYear is positive in practice; the second modulo keeps a negative one in range
        // rather than throwing on an index.
        return lines[((dayOfYear % lines.size) + lines.size) % lines.size]
    }
}
