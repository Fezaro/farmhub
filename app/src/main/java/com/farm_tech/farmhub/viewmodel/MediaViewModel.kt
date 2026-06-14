package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.models.FarmVideoMenuCatalog
import com.farm_tech.farmhub.models.FarmVideoSelection
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.repository.MediaRepository
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MediaUiState {
    object Idle: MediaUiState()
    object Loading: MediaUiState()
    data class Success(val videos: List<VideoItem>, val hasMore: Boolean): MediaUiState()
    data object Empty: MediaUiState()
    data class Error(val message: String): MediaUiState()
}

data class MediaMenuState(
    val selection: FarmVideoSelection = FarmVideoSelection(),
    val expandedCategoryIds: Set<String> = setOf(
        FarmVideoMenuCatalog.CATEGORY_CROP,
        FarmVideoMenuCatalog.CATEGORY_ANIMAL
    )
)

class MediaViewModel(
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    private val repository = MediaRepository()

    companion object {
        private const val KEY_SELECTED_CATEGORY = "selected_category"
        private const val KEY_SELECTED_SUBCATEGORY = "selected_subcategory"
        private const val KEY_EXPANDED_CATEGORIES = "expanded_categories"
    }

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState

    private val _menuState = MutableStateFlow(
        MediaMenuState(
            selection = FarmVideoSelection(
                categoryId = savedStateHandle[KEY_SELECTED_CATEGORY],
                subcategoryId = savedStateHandle[KEY_SELECTED_SUBCATEGORY]
            ),
            expandedCategoryIds = savedStateHandle.get<ArrayList<String>>(KEY_EXPANDED_CATEGORIES)
                ?.toSet()
                ?.ifEmpty {
                    setOf(
                        FarmVideoMenuCatalog.CATEGORY_CROP,
                        FarmVideoMenuCatalog.CATEGORY_ANIMAL
                    )
                }
                ?: setOf(
                    FarmVideoMenuCatalog.CATEGORY_CROP,
                    FarmVideoMenuCatalog.CATEGORY_ANIMAL
                )
        )
    )
    val menuState: StateFlow<MediaMenuState> = _menuState

    // Keep last successful list for detail lookup
    private var lastVideos: List<VideoItem> = emptyList()
    private val remoteIdByUiId = mutableMapOf<Int, String>()
    // UI chunk size for progressive rendering (not a total feed limit).
    private val pageSize = 20
    private var currentPage = 1

    private fun currentSelection(): FarmVideoSelection = _menuState.value.selection
    private fun filteredVideos(source: List<VideoItem>): List<VideoItem> =
        FarmVideoMenuCatalog.filterVideos(source, currentSelection())

    private fun visibleSubset(source: List<VideoItem>): List<VideoItem> = source.take(currentPage * pageSize)
    private fun hasMoreInternal(source: List<VideoItem>): Boolean = currentPage * pageSize < source.size

    private fun persistMenuState(state: MediaMenuState) {
        savedStateHandle[KEY_SELECTED_CATEGORY] = state.selection.categoryId
        savedStateHandle[KEY_SELECTED_SUBCATEGORY] = state.selection.subcategoryId
        savedStateHandle[KEY_EXPANDED_CATEGORIES] = ArrayList(state.expandedCategoryIds)
    }

    private fun updateMenuState(transform: (MediaMenuState) -> MediaMenuState) {
        val updated = transform(_menuState.value)
        _menuState.value = updated
        persistMenuState(updated)
    }

    private fun emitFilteredSuccess() {
        val filtered = filteredVideos(lastVideos)
        _uiState.value = MediaUiState.Success(visibleSubset(filtered), hasMoreInternal(filtered))
    }

    fun loadMedia(force: Boolean = false) {
        if (_uiState.value is MediaUiState.Loading) return
        if (!force && lastVideos.isNotEmpty()) {
            // Re-emit current subset (useful after rotation)
            emitFilteredSuccess()
            return
        }
        _uiState.value = MediaUiState.Loading
        currentPage = 1
        viewModelScope.launch {
            when (val result = repository.getMediaFeed(force = force)) {
                is NetworkResult.Success -> {
                    val items = result.data.media.orEmpty()
                    if (items.isEmpty()) {
                        lastVideos = emptyList()
                        remoteIdByUiId.clear()
                        _uiState.value = MediaUiState.Empty
                        return@launch
                    }
                    var counter = 100000
                    remoteIdByUiId.clear()
                    lastVideos = items.map { item ->
                        val uiId = counter++
                        item.id?.let { remoteIdByUiId[uiId] = it }
                        val thumb = item.thumbnailUrl?.takeIf { it.isNotBlank() }
                        val media = item.mediaUrl?.takeIf { it.isNotBlank() }
                        VideoItem(
                            id = uiId,
                            title = item.title ?: "Untitled",
                            channel = item.channel ?: "Channel",
                            views = "",
                            time = item.createdAt ?: "",
                            thumbnail = android.R.drawable.ic_media_play,
                            thumbnailUrl = thumb,
                            mediaUrl = media,
                            description = item.description.orEmpty(),
                            tags = listOfNotNull(item.category, item.subcategory, item.resolvedMediaType())
                        )
                    }
                    emitFilteredSuccess()
                }
                is NetworkResult.Empty -> {
                    lastVideos = emptyList()
                    remoteIdByUiId.clear()
                    _uiState.value = MediaUiState.Empty
                }
                is NetworkResult.Error -> {
                    _uiState.value = MediaUiState.Error(ErrorMapper.toUserMessage(result.exception))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun refresh() { loadMedia(force = true) }

    fun loadNextPage() {
        if (_uiState.value !is MediaUiState.Success) return
        if (lastVideos.isEmpty()) return
        val filtered = filteredVideos(lastVideos)
        if (!hasMoreInternal(filtered)) return
        currentPage++
        _uiState.value = MediaUiState.Success(visibleSubset(filtered), hasMoreInternal(filtered))
    }

    fun selectAllVideos() {
        currentPage = 1
        updateMenuState {
            it.copy(selection = FarmVideoSelection())
        }
        if (lastVideos.isNotEmpty()) emitFilteredSuccess()
    }

    fun selectCategory(categoryId: String) {
        currentPage = 1
        updateMenuState {
            it.copy(selection = FarmVideoSelection(categoryId = categoryId, subcategoryId = null))
        }
        if (lastVideos.isNotEmpty()) emitFilteredSuccess()
    }

    fun selectSubcategory(categoryId: String, subcategoryId: String) {
        currentPage = 1
        updateMenuState {
            it.copy(
                selection = FarmVideoSelection(categoryId = categoryId, subcategoryId = subcategoryId),
                expandedCategoryIds = it.expandedCategoryIds + categoryId
            )
        }
        if (lastVideos.isNotEmpty()) emitFilteredSuccess()
    }

    fun toggleCategoryExpansion(categoryId: String) {
        updateMenuState {
            val expanded = if (categoryId in it.expandedCategoryIds) {
                it.expandedCategoryIds - categoryId
            } else {
                it.expandedCategoryIds + categoryId
            }
            it.copy(expandedCategoryIds = expanded)
        }
    }

    fun filterFallbackVideos(videos: List<VideoItem>): List<VideoItem> = filteredVideos(videos)

    fun currentSelectionTitle(): String = FarmVideoMenuCatalog.selectionTitle(currentSelection())

    fun currentSelectionDescription(): String = FarmVideoMenuCatalog.selectionDescription(currentSelection())

    fun getVideoById(id: Int): VideoItem? = lastVideos.firstOrNull { it.id == id }
    fun allVideos(): List<VideoItem> = lastVideos
    fun getRemoteMediaId(id: Int): String? = remoteIdByUiId[id]
}
