package me.bmax.apatch.ui.shell

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PrimaryPageMotionTest {
    private class TestFrameClock : MonotonicFrameClock {
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            time += 16_666_667L
            return onFrame(time)
        }
    }

    private class RecordingScroll(start: Float) : ScrollScope {
        var position = start
        val frames = mutableListOf(start)
        override fun scrollBy(pixels: Float): Float {
            position += pixels
            frames += position
            return pixels
        }
    }

    @Test
    fun everyPairOfPagesMovesContinuouslyInBothDirections() {
        for (count in 2..5) for (start in 0 until count) for (target in 0 until count) {
            if (start == target) continue
            val scroll = RecordingScroll(start * 1200f)
            runBlocking(TestFrameClock()) {
                scroll.animatePrimaryPageScroll(start, 0f, target, 1200)
            }
            assertEquals(target * 1200f, scroll.position, 0.01f)
            assertTrue(scroll.frames.size > 10)
            scroll.frames.zipWithNext().forEach { (before, after) ->
                assertTrue("A frame must not jump over a page", abs(after - before) < 1200f * 0.35f)
                assertTrue("Motion must follow the target direction", (after - before) * (target - start) >= 0f)
            }
        }
    }

    @Test
    fun interruptedGestureContinuesFromItsActualFractionalPosition() {
        listOf(-0.4f, 0.4f).forEach { offset ->
            val scroll = RecordingScroll((2 + offset) * 1240f)
            runBlocking(TestFrameClock()) {
                scroll.animatePrimaryPageScroll(2, offset, 0, 1240)
            }
            assertEquals(0f, scroll.position, 0.01f)
            assertEquals(scroll.frames[0], scroll.frames[1], 0.01f)
        }
    }

    @Test
    fun cancelledNavigationDoesNotSnapToItsOldTarget() = runBlocking(TestFrameClock()) {
        val scroll = RecordingScroll(4800f)
        val job = launch { scroll.animatePrimaryPageScroll(4, 0f, 0, 1200) }
        while (scroll.frames.size < 6) yield()
        job.cancelAndJoin()
        val stopped = scroll.position
        assertTrue(stopped > 0f && stopped < 4800f)
        repeat(3) { yield() }
        assertEquals(stopped, scroll.position, 0f)
    }

    @Test
    fun panoramaChromeOnlyChangesWhenHomeActuallyEntersTheViewport() {
        assertEquals(0f, homePageVisibility(4, 0f), 0f)
        assertEquals(0f, homePageVisibility(2, -0.4f), 0f)
        assertEquals(0f, homePageVisibility(1, 0f), 0f)
        assertEquals(0.25f, homePageVisibility(1, -0.25f), 0f)
        assertEquals(0.75f, homePageVisibility(0, 0.25f), 0f)
        assertEquals(1f, homePageVisibility(0, 0f), 0f)
    }
}
