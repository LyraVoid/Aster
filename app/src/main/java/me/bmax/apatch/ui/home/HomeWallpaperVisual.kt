package me.bmax.apatch.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Activity scoped wallpaper source. The shell draws the scene backdrop from the same state the
 * home screen uses for the hero card, so both copies always share one crop and one revision.
 */
val LocalHomeWallpaperViewModel = compositionLocalOf<HomeWallpaperViewModel?> { null }

/** How long the picture takes to swap when the theme changes the wallpaper. */
private const val SlotFadeMillis = 420

/**
 * The wallpaper the current theme calls for. When the dark theme has its own picture the two are
 * drawn on top of each other and cross faded, so each one keeps its own crop while it moves.
 */
@Composable
internal fun HomeWallpaperImage(
    state: HomeWallpaperState,
    modifier: Modifier = Modifier,
) {
    // A stored picture is drawn even while the slot is busy, so importing or replacing one does
    // not blank the scene for as long as the copy takes.
    val light = state.light
    val night = state.night.takeIf { state.nightAvailable }
    val nightShown by animateFloatAsState(
        targetValue = if (state.activeSlot == HomeWallpaperSlot.NIGHT) 1f else 0f,
        animationSpec = tween(SlotFadeMillis),
        label = "home-wallpaper-slot",
    )
    // With only one picture stored there is nothing to fade between, so it is shown outright.
    val lightAlpha = if (night?.hasImage == true) 1f - nightShown else 1f
    val nightAlpha = if (light.hasImage) nightShown else 1f

    Box(modifier) {
        if (light.hasImage) {
            HomeWallpaperSlotImage(
                slot = light,
                modifier = Modifier.fillMaxSize().alpha(lightAlpha),
            )
        }
        if (night != null && night.hasImage) {
            HomeWallpaperSlotImage(
                slot = night,
                modifier = Modifier.fillMaxSize().alpha(nightAlpha),
            )
        }
    }
}

/** A single stored wallpaper, cropped and scaled as it was chosen. */
@Composable
internal fun HomeWallpaperSlotImage(
    slot: HomeWallpaperSlotState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val path = slot.imagePath ?: return
    val file = remember(path, context) {
        HomeWallpaperFiles.resolve(context.filesDir, path)
    } ?: return
    val request = remember(file, path, slot.revision, context) {
        ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey("home-wallpaper-${slot.revision}")
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(
            horizontalBias = slot.crop.biasX,
            verticalBias = slot.crop.biasY,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = slot.crop.zoom
            scaleY = slot.crop.zoom
        },
    )
}
