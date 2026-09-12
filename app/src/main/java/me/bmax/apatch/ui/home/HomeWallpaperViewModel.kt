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

    val uiState = mutableUiState.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { store.load() }
                    .getOrElse { HomeWallpaperState(phase = HomeWallpaperPhase.ERROR) }
            }
            publish(loaded)
        }
    }

    /**
     * A new wallpaper means the app colours derived from it are stale, so every state change goes
     * through here. Deriving is cheap and skips work unless the wallpaper revision moved.
     */
    private fun publish(state: HomeWallpaperState) {
        mutableUiState.value = state
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { WallpaperColorTheme.sync(getApplication(), state) }
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

    fun setEnabled(enabled: Boolean) {
        if (mutableUiState.value.phase == HomeWallpaperPhase.LOADING) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.update { it.copy(enabled = enabled, phase = HomeWallpaperPhase.LOADING) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.setEnabled(enabled) }
            }
            result.fold(
                onSuccess = { publish(it) },
                onFailure = {
                    mutableUiState.value = previous
                    mutableEvents.emit(HomeWallpaperEvent.ChangeFailed)
                },
            )
        }
    }

    fun importImage(source: Uri) {
        if (mutableUiState.value.phase == HomeWallpaperPhase.LOADING) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.update { it.copy(enabled = true, phase = HomeWallpaperPhase.LOADING) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.importFrom(source) }
            }
            result.fold(
                onSuccess = { publish(it) },
                onFailure = {
                    mutableUiState.value = previous
                    mutableEvents.emit(HomeWallpaperEvent.ImportFailed)
                },
            )
        }
    }

    fun saveCrop(crop: HomeWallpaperCrop) {
        val normalized = crop.normalized()
        mutableUiState.update { it.copy(crop = normalized) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.saveCrop(normalized) }
            }
            if (result.isFailure) {
                mutableEvents.emit(HomeWallpaperEvent.ChangeFailed)
            }
        }
    }

    fun removeImage() {
        if (mutableUiState.value.phase == HomeWallpaperPhase.LOADING) {
            return
        }
        val previous = mutableUiState.value
        mutableUiState.update { it.copy(phase = HomeWallpaperPhase.LOADING) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.remove() }
            }
            result.fold(
                onSuccess = { publish(it) },
                onFailure = {
                    mutableUiState.value = previous
                    mutableEvents.emit(HomeWallpaperEvent.ChangeFailed)
                },
            )
        }
    }
}
