package com.example.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.models.media.MediaItemResponse
import com.example.app.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MediaUiState {
    object Idle: MediaUiState()
    object Loading: MediaUiState()
    data class Success(val items: List<MediaItemResponse>): MediaUiState()
    data class Error(val message: String): MediaUiState()
}

class MediaViewModel(
    private val repository: MediaRepository = MediaRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState

    fun loadMedia(force: Boolean = false) {
        if (_uiState.value is MediaUiState.Loading) return
        _uiState.value = MediaUiState.Loading
        viewModelScope.launch {
            try {
                val resp = repository.getMedia(force)
                if (resp.isSuccessful && resp.body()?.media != null) {
                    _uiState.value = MediaUiState.Success(resp.body()!!.media!!)
                } else {
                    _uiState.value = MediaUiState.Error("Failed: ${resp.code()} ${resp.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }
}

