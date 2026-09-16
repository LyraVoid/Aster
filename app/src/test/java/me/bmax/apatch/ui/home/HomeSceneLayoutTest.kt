package me.bmax.apatch.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSceneLayoutTest {
    @Test
    fun tabletLandscapeUsesBothPanesEvenAboveTheOldShortWindowLimit() {
        // 1200 x 1920 screenshots, allowing for different display scaling settings.
        listOf(1.5f, 2f).forEach { density ->
            val layout = homeSceneLayout(
                width = (1920f / density - 80f).dp,
                height = (1200f / density - 48f).dp,
            )
            assertTrue("Landscape tablet at density $density", layout.across)
        }
    }

    @Test
    fun tabletPortraitLeavesRoomForGreetingAndCardsBelowTheHero() {
        listOf(1.5f, 2f).forEach { density ->
            val height = (1920f / density - 48f).dp
            val layout = homeSceneLayout((1200f / density - 80f).dp, height)
            assertFalse(layout.across)
            assertTrue("Portrait hero must not grow with the entire tablet", layout.heroHeight <= 520.dp)
            assertTrue("Cards should remain visible below the hero", height - layout.heroHeight >= 300.dp)
        }
    }

    @Test
    fun phonePortraitKeepsItsExistingProportions() {
        val layout = homeSceneLayout(320.dp, 760.dp)
        assertFalse(layout.across)
        assertEquals(478.8f, layout.heroHeight.value, 0.1f)
    }

    @Test
    fun phoneLandscapeStillUsesTwoPanes() {
        assertTrue(homeSceneLayout(680.dp, 320.dp).across)
    }

    @Test
    fun splitScreenAndRotationUseTheSpaceAvailableToThePage() {
        assertFalse(homeSceneLayout(480.dp, 900.dp).across)
        assertFalse(homeSceneLayout(700.dp, 700.dp).across)
        assertTrue(homeSceneLayout(900.dp, 700.dp).across)
        assertFalse(homeSceneLayout(700.dp, 900.dp).across)
    }
}
