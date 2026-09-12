package me.bmax.apatch.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.ui.theme.WallpaperColorTheme

class HomeWallpaperViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HomeWallpaperStore(application)
    private val mutableUiState = MutableStateFlow(HomeWallpaperState())
    private val mutableEvents = MutableSharedFlow<HomeWallpaperEvent>(extraBufferCapacity = 1)

    /** Which theme the app is in, handed in by the theme itself. */
    private var darkTheme = false

    val uiState = mutableUiState.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { store.load() }
                    .getOrElse { HomeWallpaperState(light = failedSlot()) }
            }
            publish(loaded)
        }
    }

    /**
     * The theme follows the system or the manual switch, and the wallpaper has to follow the theme:
     * this is what swaps the picture when the app turns dark, and re-reads the colours of whichever
     * photo is now on screen.
     */
    fun setDarkTheme(isDark: Boolean) {
        if (darkTheme == isDark) {
            return
        }
        darkTheme = isDark
        publish(mutableUiState.value)
    }

    /**
     * A new wallpaper means the app colours derived from it are stale, so every state change goes
     * through here. Deriving is cheap and skips work unless the wallpaper moved on.
     */
    private fun publish(state: HomeWallpaperState) {
        val resolved = state.copy(darkTheme = darkTheme)
        mutableUiState.value = resolved
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { WallpaperColorTheme.sync(getApplication(), resolved) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (busy()) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.value = previous.copy(enabled = enabled)
        run(previous) { store.setEnabled(enabled) }
    }

    /** The switch for the dark theme's own wallpaper. */
    fun setNightEnabled(enabled: Boolean) {
        val previous = mutableUiState.value
        mutableUiState.value = previous.copy(nightEnabled = enabled)
        run(previous) { store.setNightEnabled(enabled) }
    }

    fun importImage(source: Uri, slot: HomeWallpaperSlot) {
        if (busy(slot)) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.value = previous
            .copy(
                enabled = true,
                nightEnabled = previous.nightEnabled || slot == HomeWallpaperSlot.NIGHT,
            )
            .withPhase(slot, HomeWallpaperPhase.LOADING)
        run(previous, HomeWallpaperEvent.ImportFailed) { store.importFrom(source, slot) }
    }

    fun removeImage(slot: HomeWallpaperSlot) {
        if (busy(slot)) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.value = previous.withPhase(slot, HomeWallpaperPhase.LOADING)
        run(previous) { store.remove(slot) }
    }

    fun saveCrop(crop: HomeWallpaperCrop, slot: HomeWallpaperSlot) {
        val normalized = crop.normalized()
        mutableUiState.update { it.withCrop(slot, normalized) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.saveCrop(normalized, slot) }
            }
            if (result.isFailure) {
                mutableEvents.emit(HomeWallpaperEvent.ChangeFailed)
            }
        }
    }

    /**
     * Reads the wallpaper again for a fresh colour, for when the automatic attempt found nothing.
     */
    fun regenerateColors() {
        val state = mutableUiState.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { WallpaperColorTheme.regenerate(getApplication(), state) }
            }
        }
    }

    private fun run(
        previous: HomeWallpaperState,
        failure: HomeWallpaperEvent = HomeWallpaperEvent.ChangeFailed,
        block: suspend () -> HomeWallpaperState,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { block() }
            }
            result.fold(
                onSuccess = { publish(it) },
                onFailure = {
                    mutableUiState.value = previous
                    mutableEvents.emit(failure)
                },
            )
        }
    }

    private fun busy(slot: HomeWallpaperSlot? = null): Boolean {
        val state = mutableUiState.value
        return if (slot == null) {
            state.light.phase == HomeWallpaperPhase.LOADING ||
                state.night.phase == HomeWallpaperPhase.LOADING
        } else {
            state.slot(slot).phase == HomeWallpaperPhase.LOADING
        }
    }

    private companion object {
        fun failedSlot() = HomeWallpaperSlotState(phase = HomeWallpaperPhase.ERROR)
    }
}

private fun HomeWallpaperState.withPhase(
    slot: HomeWallpaperSlot,
    phase: HomeWallpaperPhase,
): HomeWallpaperState = when (slot) {
    HomeWallpaperSlot.LIGHT -> copy(light = light.copy(phase = phase))
    HomeWallpaperSlot.NIGHT -> copy(night = night.copy(phase = phase))
}

private fun HomeWallpaperState.withCrop(
    slot: HomeWallpaperSlot,
    crop: HomeWallpaperCrop,
): HomeWallpaperState = when (slot) {
    HomeWallpaperSlot.LIGHT -> copy(light = light.copy(crop = crop))
    HomeWallpaperSlot.NIGHT -> copy(night = night.copy(crop = crop))
}
