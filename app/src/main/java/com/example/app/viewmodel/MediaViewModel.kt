package com.example.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.R
import com.example.app.models.VideoItem
import com.example.app.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MediaUiState {
    object Idle: MediaUiState()
    object Loading: MediaUiState()
    data class Success(val videos: List<VideoItem>, val hasMore: Boolean): MediaUiState()
    data class Error(val message: String): MediaUiState()
}

class MediaViewModel(
    private val repository: MediaRepository = MediaRepository()
): ViewModel() {

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState

    // Keep last successful list for detail lookup
    private var lastVideos: List<VideoItem> = emptyList()
    private val pageSize = 10
    private var currentPage = 1

    private fun visibleSubset(): List<VideoItem> = lastVideos.take(currentPage * pageSize)
    private fun hasMoreInternal(): Boolean = currentPage * pageSize < lastVideos.size

    fun loadMedia(force: Boolean = false) {
        if (_uiState.value is MediaUiState.Loading) return
        if (!force && lastVideos.isNotEmpty()) {
            // Re-emit current subset (useful after rotation)
            _uiState.value = MediaUiState.Success(visibleSubset(), hasMoreInternal())
            return
        }
        _uiState.value = MediaUiState.Loading
        currentPage = 1
        viewModelScope.launch {
            try {
                val resp = repository.getMediaFeed()
                if (resp.isSuccessful && resp.body()?.media != null) {
                    var counter = 100000 // offset to avoid clashing with static sample IDs
                    lastVideos = resp.body()!!.media!!.filterNotNull().map { item ->
                        val thumb = item.thumbnailUrl?.takeIf { it.isNotBlank() }
                        val media = item.mediaUrl?.takeIf { it.isNotBlank() }
                        VideoItem(
                            id = counter++,
                            title = item.title ?: "Untitled",
                            channel = item.channel ?: "Channel",
                            views = "", // backend does not provide; placeholder
                            time = item.createdAt ?: "",
                            thumbnail = R.drawable.ic_launcher_background,
                            thumbnailUrl = thumb,
                            mediaUrl = media
                        )
                    }
                    _uiState.value = MediaUiState.Success(visibleSubset(), hasMoreInternal())
                } else {
                    _uiState.value = MediaUiState.Error("Media error: ${resp.code()} ${resp.message()}")
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Exception: ${e.message}")
                _uiState.value = MediaUiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }

    fun refresh() { loadMedia(force = true) }

    fun loadNextPage() {
        if (lastVideos.isEmpty()) return
        if (!hasMoreInternal()) return
        currentPage++
        _uiState.value = MediaUiState.Success(visibleSubset(), hasMoreInternal())
    }

    fun getVideoById(id: Int): VideoItem? = lastVideos.firstOrNull { it.id == id }
    fun allVideos(): List<VideoItem> = lastVideos
}