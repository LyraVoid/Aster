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

class HomeWallpaperViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HomeWallpaperStore(application)
    private val mutableUiState = MutableStateFlow(HomeWallpaperState())
    private val mutableEvents = MutableSharedFlow<HomeWallpaperEvent>(extraBufferCapacity = 1)

    val uiState = mutableUiState.asStateFlow()
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            mutableUiState.value = withContext(Dispatchers.IO) {
                runCatching { store.load() }
                    .getOrElse { HomeWallpaperState(phase = HomeWallpaperPhase.ERROR) }
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
                onSuccess = { mutableUiState.value = it },
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
                onSuccess = { mutableUiState.value = it },
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
                onSuccess = { mutableUiState.value = it },
                onFailure = {
                    mutableUiState.value = previous
                    mutableEvents.emit(HomeWallpaperEvent.ChangeFailed)
                },
            )
        }
    }
}
