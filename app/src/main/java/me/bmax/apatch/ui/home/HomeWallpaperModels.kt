package me.bmax.apatch.ui.home

const val HomeWallpaperMinZoom = 1f
const val HomeWallpaperMaxZoom = 3f

data class HomeWallpaperCrop(
    val zoom: Float = HomeWallpaperMinZoom,
    val biasX: Float = 0f,
    val biasY: Float = 0f,
) {
    fun normalized(): HomeWallpaperCrop = HomeWallpaperCrop(
        zoom = zoom.finiteIn(HomeWallpaperMinZoom, HomeWallpaperMaxZoom),
        biasX = biasX.finiteIn(-1f, 1f),
        biasY = biasY.finiteIn(-1f, 1f),
    )

    companion object {
        val Default = HomeWallpaperCrop()
    }
}

enum class HomeWallpaperPhase {
    DISABLED,
    LOADING,
    READY,
    MISSING,
    ERROR,
}

data class HomeWallpaperState(
    val enabled: Boolean = false,
    val phase: HomeWallpaperPhase = HomeWallpaperPhase.DISABLED,
    val imagePath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val revision: Long = 0L,
    val crop: HomeWallpaperCrop = HomeWallpaperCrop.Default,
) {
    val hasImage: Boolean
        get() = !imagePath.isNullOrBlank()

    val isReady: Boolean
        get() = phase == HomeWallpaperPhase.READY && hasImage
}

sealed interface HomeWallpaperEvent {
    data object ImportFailed : HomeWallpaperEvent

    data object ChangeFailed : HomeWallpaperEvent
}

private fun Float.finiteIn(min: Float, max: Float): Float =
    takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
