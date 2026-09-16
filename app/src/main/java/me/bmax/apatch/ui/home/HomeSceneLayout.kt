package me.bmax.apatch.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class HomeSceneLayout(
    val across: Boolean,
    val heroHeight: Dp,
)

/** Uses the space remaining after the scene rail and system bars, including in split screen. */
internal fun homeSceneLayout(width: Dp, height: Dp): HomeSceneLayout = HomeSceneLayout(
    // Tall landscape tablets need the same two panes as landscape phones.
    across = width > height,
    // Keep the status and following cards within reach on tall tablets. An unbounded fraction
    // of the window height turns a landscape wallpaper into a narrow, heavily cropped banner.
    heroHeight = (height * if (height < 480.dp) 0.50f else 0.63f)
        .coerceIn(300.dp, 520.dp),
)
