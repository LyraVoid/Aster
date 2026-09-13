package me.bmax.apatch.ui.shell

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollScope
import kotlin.math.abs
import kotlin.math.roundToInt

/** Scroll the full measured distance; there is deliberately no snap to an intermediate page. */
internal suspend fun ScrollScope.animatePrimaryPageScroll(
    currentPage: Int,
    currentOffset: Float,
    targetPage: Int,
    pageStride: Int,
) {
    if (pageStride <= 0) return
    val distanceInPages = targetPage - currentPage - currentOffset
    val distance = distanceInPages * pageStride
    val duration = (100f * abs(distanceInPages).coerceAtLeast(2f) + 100f).roundToInt()
    var previous = 0f
    animate(0f, distance, animationSpec = tween(durationMillis = duration, easing = EaseInOut)) { value, _ ->
        previous += scrollBy(value - previous)
    }
}

/** Home is anchored at index zero, regardless of which optional entries are hidden. */
internal fun homePageVisibility(currentPage: Int, currentOffset: Float): Float =
    (1f - abs(currentPage + currentOffset)).coerceIn(0f, 1f)
