package me.bmax.apatch.ui.webui

import android.content.res.Configuration

internal fun resolveWebUiUiMode(
    currentUiMode: Int,
    followSystem: Boolean,
    nightModeEnabled: Boolean,
): Int {
    if (followSystem) {
        return currentUiMode
    }

    val nightMode = if (nightModeEnabled) {
        Configuration.UI_MODE_NIGHT_YES
    } else {
        Configuration.UI_MODE_NIGHT_NO
    }
    return (currentUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
}
