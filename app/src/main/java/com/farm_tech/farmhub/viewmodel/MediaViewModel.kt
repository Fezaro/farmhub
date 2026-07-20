package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.farm_tech.farmhub.BuildConfig
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.repository.MediaRepository
import com.farm_tech.farmhub.util.FriendlyDateTimeFormatter
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

data class MediaFilterSelection(
    val category: String? = null,
    val subcategory: String? = null
)

data class MediaSubcategoryOption(
    val value: String,
    val label: String
)

data class MediaCategoryOption(
    val value: String,
    val label: String,
    val subcategories: List<MediaSubcategoryOption> = emptyList()
)

data class MediaMenuState(
    val selection: MediaFilterSelection = MediaFilterSelection(),
    val categories: List<MediaCategoryOption> = emptyList(),
    val expandedCategoryIds: Set<String> = emptySet()
)

class MediaViewModel(
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    companion object {
        private const val TAG = "MediaViewModel"
        private const val KEY_SELECTED_CATEGORY = "selected_category"
        private const val KEY_SELECTED_SUBCATEGORY = "selected_subcategory"
        private const val KEY_EXPANDED_CATEGORIES = "expanded_categories"
    }

    private val repository = MediaRepository()

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Idle)
    val uiState: StateFlow<MediaUiState> = _uiState

    private val _menuState = MutableStateFlow(
        MediaMenuState(
            selection = MediaFilterSelection(
                category = savedStateHandle[KEY_SELECTED_CATEGORY],
                subcategory = savedStateHandle[KEY_SELECTED_SUBCATEGORY]
            ),
            expandedCategoryIds = savedStateHandle.get<ArrayList<String>>(KEY_EXPANDED_CATEGORIES)
                ?.toSet()
                ?: emptySet()
        )
    )
    val menuState: StateFlow<MediaMenuState> = _menuState

    // Keep last successful list for detail lookup
    private var lastVideos: List<VideoItem> = emptyList()
    private val remoteIdByUiId = mutableMapOf<Int, String>()
    // UI chunk size for progressive rendering (not a total feed limit).
    private val pageSize = 20
    private var currentPage = 1

    private fun normalize(value: String?): String = value?.trim().orEmpty().lowercase()

    private fun filteredVideos(source: List<VideoItem>): List<VideoItem> {
        val selection = _menuState.value.selection
        if (selection.category.isNullOrBlank()) return source
        val selectedCategory = normalize(selection.category)
        val selectedSubcategory = normalize(selection.subcategory)
        return source.filter { video ->
            val categoryMatches = normalize(video.category) == selectedCategory
            val subcategoryMatches = selectedSubcategory.isBlank() || normalize(video.subcategory) == selectedSubcategory
            categoryMatches && subcategoryMatches
        }
    }

    private fun visibleSubset(source: List<VideoItem>): List<VideoItem> = source.take(currentPage * pageSize)
    private fun hasMoreInternal(source: List<VideoItem>): Boolean = currentPage * pageSize < source.size

    private fun persistMenuState(state: MediaMenuState) {
        savedStateHandle[KEY_SELECTED_CATEGORY] = state.selection.category
        savedStateHandle[KEY_SELECTED_SUBCATEGORY] = state.selection.subcategory
        savedStateHandle[KEY_EXPANDED_CATEGORIES] = ArrayList(state.expandedCategoryIds)
    }

    private fun updateMenuState(transform: (MediaMenuState) -> MediaMenuState) {
        val updated = transform(_menuState.value)
        _menuState.value = updated
        persistMenuState(updated)
    }

    private fun emitFilteredSuccess() {
        val filtered = filteredVideos(lastVideos)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Media VM filter diagnostics: total=${lastVideos.size} filtered=${filtered.size} page=$currentPage")
        }
        _uiState.value = MediaUiState.Success(visibleSubset(filtered), hasMoreInternal(filtered))
    }

    private fun rebuildCategories(source: List<VideoItem>) {
        val categories = linkedMapOf<String, LinkedHashSet<String>>()
        source.forEach { video ->
            val category = video.category.trim()
            if (category.isBlank()) return@forEach
            val subcategories = categories.getOrPut(category) { LinkedHashSet() }
            val subcategory = video.subcategory.trim()
            if (subcategory.isNotBlank()) {
                subcategories.add(subcategory)
            }
        }

        updateMenuState { current ->
            val normalizedAvailableCategories = categories.keys.map { normalize(it) }.toSet()
            val currentCategory = current.selection.category
            val currentSubcategory = current.selection.subcategory
            val categoryStillValid = currentCategory?.let { normalize(it) in normalizedAvailableCategories } == true

            val selectedCategoryLabel = categories.keys.firstOrNull { normalize(it) == normalize(currentCategory) }
            val normalizedSubcategories = selectedCategoryLabel?.let { key ->
                categories[key].orEmpty().map { normalize(it) }.toSet()
            }.orEmpty()
            val subcategoryStillValid = currentSubcategory?.let { normalize(it) in normalizedSubcategories } == true

            val nextSelection = when {
                !categoryStillValid -> MediaFilterSelection()
                !subcategoryStillValid -> MediaFilterSelection(category = selectedCategoryLabel)
                else -> MediaFilterSelection(category = selectedCategoryLabel, subcategory = currentSubcategory)
            }

            val nextCategories = categories.entries.map { (category, subcategories) ->
                MediaCategoryOption(
                    value = category,
                    label = category,
                    subcategories = subcategories.map { MediaSubcategoryOption(value = it, label = it) }
                )
            }

            val nextExpanded = current.expandedCategoryIds.filterTo(mutableSetOf()) { expanded ->
                normalizedAvailableCategories.contains(normalize(expanded))
            }
            if (nextExpanded.isEmpty() && nextCategories.isNotEmpty()) {
                nextExpanded.add(nextCategories.first().value)
            }

            current.copy(
                selection = nextSelection,
                categories = nextCategories,
                expandedCategoryIds = nextExpanded
            )
        }
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
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Media VM repository diagnostics: received=${items.size} status=${result.data.status} message=${result.data.message}")
                    }
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
                        val thumb = item.resolvedThumbnailUrl()?.takeIf { it.isNotBlank() }
                        val media = item.resolvedMediaUrl()?.takeIf { it.isNotBlank() }
                        val companyName = item.company?.trim().orEmpty()
                        val uploadedAt = item.resolvedUploadedAt()
                        val category = item.resolvedCategoryLabel().orEmpty()
                        val subcategory = item.resolvedSubcategoryLabel().orEmpty()
                        VideoItem(
                            id = uiId,
                            title = item.title ?: "Untitled",
                            channel = companyName.ifBlank { "Unknown Company" },
                            views = "",
                            time = FriendlyDateTimeFormatter.toRelativeOrDateTime(uploadedAt),
                            thumbnail = android.R.drawable.ic_media_play,
                            thumbnailUrl = thumb,
                            mediaUrl = media,
                            description = item.description.orEmpty(),
                            tags = listOfNotNull(category.takeIf { it.isNotBlank() }, subcategory.takeIf { it.isNotBlank() }, item.resolvedMediaType()),
                            company = companyName,
                            category = category,
                            subcategory = subcategory,
                            author = item.author.orEmpty(),
                            duration = item.duration.orEmpty(),
                            uploadedAt = FriendlyDateTimeFormatter.toDateTime(uploadedAt),
                            streamUrl = item.streamUrl,
                            videoUrl = item.videoUrl,
                            playbackUrl = item.playbackUrl,
                            fileUrl = item.fileUrl,
                            rawUrl = item.url,
                            mimeType = item.resolvedMediaType()
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Media VM mapping diagnostics: mapped=${lastVideos.size}")
                    }
                    rebuildCategories(lastVideos)
                    emitFilteredSuccess()
                }
                is NetworkResult.Empty -> {
                    lastVideos = emptyList()
                    remoteIdByUiId.clear()
                    updateMenuState { it.copy(categories = emptyList(), expandedCategoryIds = emptySet()) }
                    _uiState.value = MediaUiState.Empty
                }
                is NetworkResult.Error -> {
                    _uiState.value = MediaUiState.Error(ErrorMapper.toMediaUserMessage(result.exception))
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
            it.copy(selection = MediaFilterSelection())
        }
        if (lastVideos.isNotEmpty()) emitFilteredSuccess()
    }

    fun selectCategory(categoryId: String) {
        currentPage = 1
        updateMenuState {
            val selected = it.categories.firstOrNull { category -> normalize(category.value) == normalize(categoryId) }?.value
            it.copy(selection = MediaFilterSelection(category = selected))
        }
        if (lastVideos.isNotEmpty()) emitFilteredSuccess()
    }

    fun selectSubcategory(categoryId: String, subcategoryId: String) {
        currentPage = 1
        updateMenuState {
            val selectedCategory = it.categories.firstOrNull { category -> normalize(category.value) == normalize(categoryId) }?.value
            val selectedSubcategory = it.categories
                .firstOrNull { category -> normalize(category.value) == normalize(categoryId) }
                ?.subcategories
                ?.firstOrNull { subcategory -> normalize(subcategory.value) == normalize(subcategoryId) }
                ?.value
            it.copy(
                selection = MediaFilterSelection(category = selectedCategory, subcategory = selectedSubcategory),
                expandedCategoryIds = it.expandedCategoryIds + (selectedCategory ?: categoryId)
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

    fun currentSelectionTitle(): String {
        val selection = _menuState.value.selection
        return selection.subcategory ?: selection.category ?: "All FarmVideos"
    }

    fun currentSelectionDescription(): String {
        val selection = _menuState.value.selection
        return when {
            !selection.subcategory.isNullOrBlank() && !selection.category.isNullOrBlank() ->
                "Showing backend media tagged under ${selection.category} / ${selection.subcategory}."
            !selection.category.isNullOrBlank() ->
                "Showing backend media tagged under ${selection.category}."
            else -> "Showing all videos returned by the backend media feed."
        }
    }

    fun getVideoById(id: Int): VideoItem? = lastVideos.firstOrNull { it.id == id }
    fun allVideos(): List<VideoItem> = lastVideos
    fun getRemoteMediaId(id: Int): String? = remoteIdByUiId[id]
}
