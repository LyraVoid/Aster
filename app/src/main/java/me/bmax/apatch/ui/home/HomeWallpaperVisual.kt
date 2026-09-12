package me.bmax.apatch.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
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

@Composable
internal fun HomeWallpaperImage(
    state: HomeWallpaperState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val path = state.imagePath ?: return
    val file = remember(path, context) {
        HomeWallpaperFiles.resolve(context.filesDir, path)
    } ?: return
    val request = remember(file, path, state.revision, context) {
        ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey("home-wallpaper-${state.revision}")
            .crossfade(true)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(
            horizontalBias = state.crop.biasX,
            verticalBias = state.crop.biasY,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = state.crop.zoom
            scaleY = state.crop.zoom
        },
    )
}
