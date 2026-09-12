package me.bmax.apatch.ui.home

const val HomeWallpaperMinZoom = 1f
const val HomeWallpaperMaxZoom = 3f

/**
 * Home keeps up to two wallpapers: one shown in the light theme and one for the dark theme.
 */
enum class HomeWallpaperSlot {
    LIGHT,
    NIGHT,
}

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

/** One stored wallpaper: where it is, how big it is, and how it is cropped. */
data class HomeWallpaperSlotState(
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

/**
 * Both stored wallpapers, plus the two facts that decide which one is on screen: whether the dark
 * theme asked for its own image, and whether the app is dark right now.
 *
 * Everything that draws or reads a wallpaper reads it through here rather than choosing a slot
 * itself, so the hero, the scene behind the rail, the appearance sheet and the colours derived from
 * the photo always agree on which photo they are talking about.
 */
data class HomeWallpaperState(
    val enabled: Boolean = false,
    val light: HomeWallpaperSlotState = HomeWallpaperSlotState(),
    val night: HomeWallpaperSlotState = HomeWallpaperSlotState(),
    /**
     * The switch for the whole two wallpaper feature. Off means one wallpaper for both themes, which
     * is exactly what the app did before the dark wallpaper existed.
     */
    val nightEnabled: Boolean = false,
    /** The dark theme choice the app has already made, handed in by the theme. */
    val darkTheme: Boolean = false,
) {
    /**
     * The dark wallpaper counts only once it is chosen *and* readable, so a deleted or broken file
     * falls back to the light image instead of leaving an empty scene.
     */
    val nightAvailable: Boolean
        get() = nightEnabled && night.isReady

    val activeSlot: HomeWallpaperSlot
        get() = if (nightAvailable && darkTheme) HomeWallpaperSlot.NIGHT else HomeWallpaperSlot.LIGHT

    fun slot(slot: HomeWallpaperSlot): HomeWallpaperSlotState = when (slot) {
        HomeWallpaperSlot.LIGHT -> light
        HomeWallpaperSlot.NIGHT -> night
    }

    /** The wallpaper the current theme shows. */
    val visible: HomeWallpaperSlotState
        get() = slot(activeSlot)

    val phase: HomeWallpaperPhase
        get() = visible.phase

    val imagePath: String?
        get() = visible.imagePath

    val imageWidth: Int
        get() = visible.imageWidth

    val imageHeight: Int
        get() = visible.imageHeight

    val revision: Long
        get() = visible.revision

    val crop: HomeWallpaperCrop
        get() = visible.crop

    val hasImage: Boolean
        get() = visible.hasImage

    val isReady: Boolean
        get() = visible.isReady
}

sealed interface HomeWallpaperEvent {
    data object ImportFailed : HomeWallpaperEvent

    data object ChangeFailed : HomeWallpaperEvent
}

private fun Float.finiteIn(min: Float, max: Float): Float =
    takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
