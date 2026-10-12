package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.models.filterBySearch
import com.farm_tech.farmhub.models.media.MediaItemResponse
import com.farm_tech.farmhub.models.media.MediaTaxonomy
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.repository.MediaRepository
import com.farm_tech.farmhub.util.FriendlyDateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MediaUiState {
    object Idle : MediaUiState()
    object Loading : MediaUiState()
    data class Success(val videos: List<VideoItem>, val hasMore: Boolean, val notice: String? = null, val refreshing: Boolean = false) : MediaUiState()
    data object Empty : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}

data class MediaFilterSelection(val category: String? = null, val subcategory: String? = null)
data class MediaSubcategoryOption(val value: String, val label: String)
data class MediaCategoryOption(val value: String, val label: String, val subcategories: List<MediaSubcategoryOption> = emptyList())
data class MediaMenuState(
    val selection: MediaFilterSelection = MediaFilterSelection(),
    val categories: List<MediaCategoryOption> = emptyList(),
    val expandedCategoryIds: Set<String> = emptySet()
)

class MediaViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val repository = MediaRepository()
    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState
    private val _menuState = MutableStateFlow(MediaMenuState(selection = MediaFilterSelection(
        savedStateHandle["selected_category"], savedStateHandle["selected_subcategory"]
    )))
    val menuState: StateFlow<MediaMenuState> = _menuState
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("media_search_query").orEmpty())
    val searchQuery: StateFlow<String> = _searchQuery
    private var lastVideos: List<VideoItem> = emptyList()
    private val remoteIdByUiId = mutableMapOf<Int, String>()
    private var currentPage = 1
    private val pageSize = 20 // Rendering chunk only; all backend pages are loaded.
    private var loading = false
    private var notice: String? = null

    private fun normalize(value: String?) = value?.trim().orEmpty().lowercase()

    private fun filteredVideos(source: List<VideoItem>): List<VideoItem> {
        val selection = _menuState.value.selection
        val category = _menuState.value.categories.firstOrNull { it.value == selection.category }
        val subcategory = category?.subcategories?.firstOrNull { it.value == selection.subcategory }
        val categoryVideos = if (category == null) source else source.filter { video ->
            val categoryMatches = if (video.categoryId.isNotBlank()) video.categoryId == category.value else normalize(video.category) == normalize(category.label)
            val subcategoryMatches = subcategory == null || if (video.subcategoryId.isNotBlank()) video.subcategoryId == subcategory.value else normalize(video.subcategory) == normalize(subcategory.label)
            categoryMatches && subcategoryMatches
        }
        return categoryVideos.filterBySearch(_searchQuery.value)
    }

    private fun updateMenuState(transform: (MediaMenuState) -> MediaMenuState) {
        val next = transform(_menuState.value)
        _menuState.value = next
        savedStateHandle["selected_category"] = next.selection.category
        savedStateHandle["selected_subcategory"] = next.selection.subcategory
    }

    private fun emitFilteredSuccess() {
        val filtered = filteredVideos(lastVideos)
        _uiState.value = MediaUiState.Success(filtered.take(currentPage * pageSize), currentPage * pageSize < filtered.size, notice, loading)
    }

    private fun applyTaxonomy(taxonomy: MediaTaxonomy) {
        val categories = taxonomy.categories.map { category ->
            MediaCategoryOption(category.id, category.category, category.subcategories.map { label ->
                MediaSubcategoryOption(category.subcategoryIds.getValue(label), label)
            })
        }
        updateMenuState { current ->
            // Accept old saved labels once, then persist canonical backend IDs.
            val category = categories.firstOrNull { it.value == current.selection.category || normalize(it.label) == normalize(current.selection.category) }
            val subcategory = category?.subcategories?.firstOrNull { it.value == current.selection.subcategory || normalize(it.label) == normalize(current.selection.subcategory) }
            current.copy(categories = categories, selection = MediaFilterSelection(category?.value, subcategory?.value),
                expandedCategoryIds = current.expandedCategoryIds.intersect(categories.map { it.value }.toSet()))
        }
    }

    fun loadMedia(force: Boolean = false) {
        if (loading) return
        if (!force && lastVideos.isNotEmpty()) { emitFilteredSuccess(); return }
        loading = true
        notice = null
        if (lastVideos.isEmpty()) _uiState.value = MediaUiState.Loading else emitFilteredSuccess()
        viewModelScope.launch {
            try {
                val taxonomyNotice = when (val result = repository.getMediaTaxonomy(force)) {
                    is NetworkResult.Success -> {
                        applyTaxonomy(result.data)
                        if (result.data.stale) "Showing saved categories. Pull down to retry." else null
                    }
                    else -> "Categories could not be refreshed. All videos remain available."
                }
                when (val result = repository.getMediaFeed(force)) {
                    is NetworkResult.Success -> {
                        remoteIdByUiId.clear()
                        lastVideos = result.data.media.orEmpty().mapIndexed { index, item ->
                            val id = 100000 + index
                            item.id?.let { remoteIdByUiId[id] = it }
                            mapVideo(id, item)
                        }
                        notice = listOfNotNull(taxonomyNotice, if (result.data.cached) "Showing saved videos. Pull down to retry." else null).joinToString(" ").ifBlank { null }
                        loading = false
                        emitFilteredSuccess()
                    }
                    is NetworkResult.Error -> {
                        loading = false
                        val message = ErrorMapper.toMediaUserMessage(result.exception)
                        if (lastVideos.isNotEmpty() && result.exception !is com.farm_tech.farmhub.network.ApiException.Unauthorized && result.exception !is com.farm_tech.farmhub.network.ApiException.Forbidden) {
                            notice = message + " Showing previously loaded videos."
                            emitFilteredSuccess()
                        } else {
                            lastVideos = emptyList()
                            remoteIdByUiId.clear()
                            _uiState.value = MediaUiState.Error(message)
                        }
                    }
                    else -> {
                        loading = false
                        lastVideos = emptyList()
                        notice = taxonomyNotice
                        emitFilteredSuccess()
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun mapVideo(id: Int, item: MediaItemResponse): VideoItem {
        val category = item.resolvedCategoryLabel().orEmpty()
        val subcategory = item.resolvedSubcategoryLabel().orEmpty()
        val uploaded = item.resolvedUploadedAt()
        return VideoItem(
            id = id, title = item.title ?: "Untitled", channel = item.company?.ifBlank { "Unknown Company" } ?: "Unknown Company",
            views = "", time = FriendlyDateTimeFormatter.toRelativeOrDateTime(uploaded),
            thumbnail = android.R.drawable.ic_media_play, thumbnailUrl = item.resolvedThumbnailUrl(), mediaUrl = item.resolvedMediaUrl(),
            description = item.description.orEmpty(), tags = listOf(category, subcategory), company = item.company.orEmpty(),
            category = category, subcategory = subcategory, author = item.author.orEmpty(), duration = item.duration.orEmpty(),
            uploadedAt = FriendlyDateTimeFormatter.toDateTime(uploaded), streamUrl = item.streamUrl, videoUrl = item.videoUrl,
            playbackUrl = item.playbackUrl, fileUrl = item.fileUrl, rawUrl = item.url, mimeType = item.resolvedMediaType(),
            categoryId = item.categoryId ?: item.category?.id.orEmpty(), subcategoryId = item.subcategoryId ?: item.subcategory?.id.orEmpty()
        )
    }

    fun refresh() { currentPage = 1; loadMedia(force = true) }
    fun setSearchQuery(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        savedStateHandle["media_search_query"] = query
        currentPage = 1
        if (_uiState.value is MediaUiState.Success) emitFilteredSuccess()
    }
    fun loadNextPage() {
        if (loading || currentPage * pageSize >= filteredVideos(lastVideos).size) return
        currentPage++
        emitFilteredSuccess()
    }
    fun selectAllVideos() {
        currentPage = 1
        updateMenuState { it.copy(selection = MediaFilterSelection()) }
        if (_uiState.value is MediaUiState.Success) emitFilteredSuccess()
    }
    fun selectCategory(categoryId: String) {
        currentPage = 1
        val category = _menuState.value.categories.firstOrNull { it.value == categoryId } ?: return
        updateMenuState { it.copy(selection = MediaFilterSelection(category.value)) }
        if (_uiState.value is MediaUiState.Success) emitFilteredSuccess()
    }
    fun selectSubcategory(categoryId: String, subcategoryId: String) {
        val category = _menuState.value.categories.firstOrNull { it.value == categoryId } ?: return
        val subcategory = category.subcategories.firstOrNull { it.value == subcategoryId } ?: return
        currentPage = 1
        updateMenuState { it.copy(selection = MediaFilterSelection(category.value, subcategory.value), expandedCategoryIds = it.expandedCategoryIds + category.value) }
        if (_uiState.value is MediaUiState.Success) emitFilteredSuccess()
    }
    fun toggleCategoryExpansion(categoryId: String) {
        updateMenuState { it.copy(expandedCategoryIds = if (categoryId in it.expandedCategoryIds) it.expandedCategoryIds - categoryId else it.expandedCategoryIds + categoryId) }
    }
    fun filterFallbackVideos(videos: List<VideoItem>) = filteredVideos(videos)
    fun currentSelectionTitle(): String {
        val selection = _menuState.value.selection
        val category = _menuState.value.categories.firstOrNull { it.value == selection.category }
        return category?.subcategories?.firstOrNull { it.value == selection.subcategory }?.label ?: category?.label ?: "All FarmVideos"
    }
    fun currentSelectionDescription() = if (_menuState.value.selection.category == null) "Browse all available farming videos." else "Videos in ${currentSelectionTitle()}."
    fun getVideoById(id: Int): VideoItem? = lastVideos.firstOrNull { it.id == id }
    fun allVideos(): List<VideoItem> = lastVideos
    fun getRemoteMediaId(id: Int): String? = remoteIdByUiId[id]
}
