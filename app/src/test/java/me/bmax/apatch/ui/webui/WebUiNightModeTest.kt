package me.bmax.apatch.ui.webui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class WebUiNightModeTest {

    private val unrelatedUiModeBits =
        Configuration.UI_MODE_TYPE_TELEVISION or (1 shl 20)

    @Test
    fun followSystemPreservesUiModeExactly() {
        val currentUiMode = unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_UNDEFINED

        assertEquals(
            currentUiMode,
            resolveWebUiUiMode(
                currentUiMode = currentUiMode,
                followSystem = true,
                nightModeEnabled = true,
            ),
        )
    }

    @Test
    fun manualDarkModeSetsNightModeAndPreservesOtherBits() {
        val currentUiMode = unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_NO

        assertEquals(
            unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_YES,
            resolveWebUiUiMode(
                currentUiMode = currentUiMode,
                followSystem = false,
                nightModeEnabled = true,
            ),
        )
    }

    @Test
    fun manualLightModeClearsNightModeAndPreservesOtherBits() {
        val currentUiMode = unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_YES

        assertEquals(
            unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_NO,
            resolveWebUiUiMode(
                currentUiMode = currentUiMode,
                followSystem = false,
                nightModeEnabled = false,
            ),
        )
    }

    @Test
    fun manualModeReplacesUndefinedNightMode() {
        val currentUiMode = unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_UNDEFINED

        assertEquals(
            unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_YES,
            resolveWebUiUiMode(
                currentUiMode = currentUiMode,
                followSystem = false,
                nightModeEnabled = true,
            ),
        )
        assertEquals(
            unrelatedUiModeBits or Configuration.UI_MODE_NIGHT_NO,
            resolveWebUiUiMode(
                currentUiMode = currentUiMode,
                followSystem = false,
                nightModeEnabled = false,
            ),
        )
    }
}
