package me.bmax.apatch.ui.home

internal data class HomeWallpaperFileInfo(
    val width: Int,
    val height: Int,
)

internal object HomeWallpaperStateMapper {
    fun disabled(
        imagePath: String?,
        revision: Long,
        crop: HomeWallpaperCrop,
    ): HomeWallpaperState = HomeWallpaperState(
        enabled = false,
        phase = HomeWallpaperPhase.DISABLED,
        imagePath = imagePath?.takeIf { it.isNotBlank() },
        revision = revision,
        crop = crop.normalized(),
    )

    fun resolveEnabled(
        imagePath: String?,
        revision: Long,
        crop: HomeWallpaperCrop,
        fileExists: Boolean,
        fileInfo: HomeWallpaperFileInfo?,
    ): HomeWallpaperState {
        val normalizedPath = imagePath?.takeIf { it.isNotBlank() }
        val phase = when {
            normalizedPath == null -> HomeWallpaperPhase.MISSING
            !fileExists -> HomeWallpaperPhase.MISSING
            fileInfo == null || fileInfo.width <= 0 || fileInfo.height <= 0 ->
                HomeWallpaperPhase.ERROR

            else -> HomeWallpaperPhase.READY
        }
        return HomeWallpaperState(
            enabled = true,
            phase = phase,
            imagePath = normalizedPath,
            imageWidth = fileInfo?.width?.takeIf { phase == HomeWallpaperPhase.READY } ?: 0,
            imageHeight = fileInfo?.height?.takeIf { phase == HomeWallpaperPhase.READY } ?: 0,
            revision = revision,
            crop = crop.normalized(),
        )
    }
}
