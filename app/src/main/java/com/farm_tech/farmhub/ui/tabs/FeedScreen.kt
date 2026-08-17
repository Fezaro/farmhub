package com.farm_tech.farmhub.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.farm_tech.farmhub.BuildConfig
import com.farm_tech.farmhub.features.AppRoutes
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.ui.components.VideoCard
import com.farm_tech.farmhub.viewmodel.MediaCategoryOption
import com.farm_tech.farmhub.viewmodel.MediaFilterSelection
import com.farm_tech.farmhub.viewmodel.MediaMenuState
import com.farm_tech.farmhub.viewmodel.MediaSubcategoryOption
import com.farm_tech.farmhub.viewmodel.MediaUiState
import com.farm_tech.farmhub.viewmodel.MediaViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val FEED_SCREEN_TAG = "FeedScreen"

@Composable
fun VideoScreen(
    navController: NavHostController,
    mediaViewModel: MediaViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val mediaState by mediaViewModel.uiState.collectAsState()
    val menuState by mediaViewModel.menuState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        mediaViewModel.loadMedia()
    }

    val selectionTitle = mediaViewModel.currentSelectionTitle()
    val selectionDescription = mediaViewModel.currentSelectionDescription()

    val displayedVideos = when (val state = mediaState) {
        is MediaUiState.Success -> state.videos.filterBySearch(searchQuery)
        else -> emptyList()
    }

    if (BuildConfig.DEBUG) {
        LaunchedEffect(mediaState, displayedVideos.size) {
            Log.d(
                FEED_SCREEN_TAG,
                "Media UI diagnostics: state=${mediaState::class.simpleName} displayed=${displayedVideos.size} hasMore=${(mediaState as? MediaUiState.Success)?.hasMore == true}"
            )
        }
    }

    val hasMore = (mediaState as? MediaUiState.Success)?.hasMore == true
    val showInlineLoader = mediaState is MediaUiState.Loading && displayedVideos.isNotEmpty()
    val showInitialLoader = mediaState is MediaUiState.Loading && displayedVideos.isEmpty()
    val errorMessage = (mediaState as? MediaUiState.Error)?.message

    val drawerContent: @Composable () -> Unit = {
        FarmVideosDrawerContent(
            menuState = menuState,
            onSelectAll = {
                mediaViewModel.selectAllVideos()
                scope.launch { drawerState.close() }
            },
            onSelectCategory = { categoryId ->
                mediaViewModel.selectCategory(categoryId)
                scope.launch { drawerState.close() }
            },
            onToggleCategory = mediaViewModel::toggleCategoryExpansion,
            onSelectSubcategory = { categoryId, subcategoryId ->
                mediaViewModel.selectSubcategory(categoryId, subcategoryId)
                scope.launch { drawerState.close() }
            }
        )
    }

    val content: @Composable () -> Unit = {
        FarmVideosScaffold(
            navController = navController,
            menuState = menuState,
            selectionTitle = selectionTitle,
            selectionDescription = selectionDescription,
            videos = displayedVideos,
            hasMore = hasMore,
            showInlineLoader = showInlineLoader,
            showInitialLoader = showInitialLoader,
            errorMessage = errorMessage,
            showMenuButton = !isLargeScreen,
            onOpenMenu = {
                if (!isLargeScreen) {
                    scope.launch { drawerState.open() }
                }
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onSelectAll = mediaViewModel::selectAllVideos,
            onSelectCategory = mediaViewModel::selectCategory,
            onSelectSubcategory = mediaViewModel::selectSubcategory,
            onClearFilters = {
                searchQuery = ""
                mediaViewModel.selectAllVideos()
            },
            onRetry = mediaViewModel::refresh,
            onLoadMore = mediaViewModel::loadNextPage
        )
    }

    if (isLargeScreen) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    drawerContent()
                }
            }
        ) {
            content()
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    drawerContent()
                }
            }
        ) {
            content()
        }
    }
}

private val primaryCategoryOrder = listOf(
    "Crop Farming",
    "Animal Farming",
    "Sector Updates",
    "Agribusiness",
    "Environment Care"
)

private data class ExplorerSubcategoryOption(
    val value: String,
    val label: String
)

private data class ExplorerCategoryOption(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val allLabel: String,
    val subcategories: List<ExplorerSubcategoryOption>
)

private fun canonicalSubcategories(label: String): List<String> = when (normalizeTopic(label)) {
    "crop farming" -> listOf(
        "Pests & Diseases",
        "Avocados",
        "Bananas",
        "Beans",
        "Brassicas",
        "Carrot",
        "Coffee",
        "Conservation Agriculture",
        "Green Gram",
        "Groundnut",
        "Indigenous Vegetables",
        "Maize",
        "Mango",
        "Mushroom",
        "Onion",
        "Other Topics",
        "Papaya",
        "Passion Fruit",
        "Peas",
        "Peppers",
        "Pineapple",
        "Potato",
        "Pumpkin",
        "Rice",
        "Spinach",
        "Sweet Potato",
        "Tea",
        "Tomato",
        "Watermelon",
        "Wheat",
        "Yam",
        "Zucchini / Courgette"
    )
    "animal farming" -> listOf(
        "Dairy",
        "Chicken",
        "Goats",
        "Sheep",
        "Fish",
        "Pigs",
        "Bee Keeping",
        "Rabbits",
        "Pets - Dogs & Cats",
        "Other Animals"
    )
    else -> emptyList()
}

private fun fallbackAllLabel(label: String): String = when (normalizeTopic(label)) {
    "sector updates" -> "All Sector Updates"
    "agribusiness" -> "All Agribusiness"
    "environment care" -> "All Environment Care"
    else -> "All $label"
}

private fun categoryIcon(label: String): ImageVector = when (normalizeTopic(label)) {
    "crop farming" -> Icons.Default.Spa
    "animal farming" -> Icons.Default.Pets
    "sector updates" -> Icons.Default.Campaign
    "agribusiness" -> Icons.Default.Work
    "environment care" -> Icons.Default.Public
    else -> Icons.Default.VideoLibrary
}

private fun mergeSubcategories(base: List<String>, backend: List<String>): List<String> {
    val merged = LinkedHashMap<String, String>()
    (base + backend).forEach { label ->
        val trimmed = label.trim()
        if (trimmed.isBlank()) return@forEach
        merged.putIfAbsent(normalizeTopic(trimmed), trimmed)
    }
    return merged.values.toList()
}

private fun buildExplorerCategories(source: List<MediaCategoryOption>): List<ExplorerCategoryOption> {
    val backendByNormalized = source.associateBy { normalizeTopic(it.value) }

    return primaryCategoryOrder.map { canonicalLabel ->
        val backendCategory = backendByNormalized[normalizeTopic(canonicalLabel)]
        val backendSubcategoryLabels = backendCategory
            ?.subcategories
            ?.map { it.label.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val mergedLabels = if (normalizeTopic(canonicalLabel) in setOf("crop farming", "animal farming")) {
            mergeSubcategories(
                base = canonicalSubcategories(canonicalLabel),
                backend = backendSubcategoryLabels
            )
        } else {
            mergeSubcategories(base = emptyList(), backend = backendSubcategoryLabels)
        }

        val allLabel = fallbackAllLabel(canonicalLabel)
        val subcategories = mergedLabels.map { label ->
            ExplorerSubcategoryOption(value = label, label = label)
        }

        ExplorerCategoryOption(
            value = backendCategory?.value ?: canonicalLabel,
            label = canonicalLabel,
            icon = categoryIcon(canonicalLabel),
            allLabel = allLabel,
            subcategories = subcategories
        )
    }
}

private fun normalizeTopic(value: String?): String = value?.trim().orEmpty().lowercase()

private fun List<VideoItem>.filterBySearch(query: String): List<VideoItem> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return this
    val needle = trimmed.lowercase()
    return filter { video ->
        buildString {
            append(video.title)
            append(' ')
            append(video.channel)
            append(' ')
            append(video.description)
            append(' ')
            append(video.category)
            append(' ')
            append(video.subcategory)
            append(' ')
            append(video.tags.joinToString(" "))
        }.lowercase().contains(needle)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
private fun FarmVideosScaffold(
    navController: NavHostController,
    menuState: MediaMenuState,
    selectionTitle: String,
    selectionDescription: String,
    videos: List<VideoItem>,
    hasMore: Boolean,
    showInlineLoader: Boolean,
    showInitialLoader: Boolean,
    errorMessage: String?,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectSubcategory: (String, String) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary
    val isRefreshing = showInitialLoader || showInlineLoader
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRetry
    )
    val listState = rememberLazyListState()
    val categoryKey = menuState.selection.category ?: "ALL"
    var previousCategoryKey by rememberSaveable { mutableStateOf(categoryKey) }
    var savedCategoryPositions by rememberSaveable { mutableStateOf<Map<String, List<Int>>>(emptyMap()) }

    LaunchedEffect(categoryKey) {
        if (categoryKey != previousCategoryKey) {
            val currentPosition = listOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            savedCategoryPositions = savedCategoryPositions + (previousCategoryKey to currentPosition)
            val restored = savedCategoryPositions[categoryKey]
            if (restored != null && restored.size == 2) {
                listState.scrollToItem(restored[0], restored[1])
            } else {
                listState.scrollToItem(0)
            }
            previousCategoryKey = categoryKey
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FarmVideos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectionTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = onVideosAccent.copy(alpha = 0.88f)
                        )
                    }
                },
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Open FarmVideos menu")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = videosAccent,
                    titleContentColor = onVideosAccent,
                    navigationIconContentColor = onVideosAccent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                QuickAccessCategories(
                    menuState = menuState,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectAll = onSelectAll,
                    onSelectCategory = onSelectCategory,
                    onSelectSubcategory = onSelectSubcategory
                )

                if (showInlineLoader) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    showInitialLoader -> LoadingState()
                    else -> VideoResults(
                        navController = navController,
                        selectionTitle = selectionTitle,
                        selectionDescription = selectionDescription,
                        videos = videos,
                        errorMessage = errorMessage,
                        hasMore = hasMore,
                        listState = listState,
                        onViewAll = onClearFilters,
                        onRetry = onRetry,
                        onLoadMore = onLoadMore
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun QuickAccessCategories(
    menuState: MediaMenuState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectSubcategory: (String, String) -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary
    val selection = menuState.selection
    val explorerCategories = remember(menuState.categories) { buildExplorerCategories(menuState.categories) }
    val categoriesByNormalized = remember(explorerCategories) {
        explorerCategories.associateBy { normalizeTopic(it.value) }
    }
    val chipListState = rememberLazyListState()
    val selectedCategory = selection.category
    val selectedCategoryOption = categoriesByNormalized[normalizeTopic(selectedCategory)]

    LaunchedEffect(selection.category, explorerCategories.size) {
        val targetIndex = if (selection.category == null) {
            0
        } else {
            explorerCategories.indexOfFirst {
                normalizeTopic(it.value) == normalizeTopic(selection.category)
            }.let { index -> if (index >= 0) index + 1 else 0 }
        }
        chipListState.animateScrollToItem(targetIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search videos") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            }
        )

        LazyRow(
            state = chipListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val selected = selection.category == null
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.04f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f),
                    label = "all_chip_scale"
                )
                FilterChip(
                    selected = selected,
                    onClick = onSelectAll,
                    label = { Text("All") },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = videosAccent,
                        selectedLabelColor = onVideosAccent
                    )
                )
            }
            items(explorerCategories, key = { it.value }) { category ->
                val selected = normalizeTopic(selection.category) == normalizeTopic(category.value) && selection.subcategory == null
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.04f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f),
                    label = "category_chip_${category.value}"
                )
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategory(category.value) },
                    label = { Text(category.label) },
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = videosAccent,
                        selectedLabelColor = onVideosAccent
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = selectedCategoryOption != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val subcategories = selectedCategoryOption?.subcategories.orEmpty()
            val selectedSubcategory = selection.subcategory
            // Keep the selected video's result list on screen. A wrapping
            // grid with crop subcategories can consume the entire viewport,
            // making a successful filter look as though it did nothing.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val allSelected = selectedSubcategory.isNullOrBlank()
                    val allScale by animateFloatAsState(
                        targetValue = if (allSelected) 1.03f else 1f,
                        animationSpec = spring(dampingRatio = 0.75f),
                        label = "subcategory_all_scale"
                    )
                    FilterChip(
                        selected = allSelected,
                        onClick = { onSelectCategory(selectedCategoryOption?.value ?: return@FilterChip) },
                        label = { Text(selectedCategoryOption?.allLabel ?: "All Topic") },
                        modifier = Modifier.scale(allScale),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = videosAccent,
                            selectedLabelColor = onVideosAccent
                        )
                    )
                }

                items(subcategories, key = { it.value }) { subcategory ->
                    val selected = normalizeTopic(subcategory.value) == normalizeTopic(selectedSubcategory)
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1f,
                        animationSpec = spring(dampingRatio = 0.75f),
                        label = "subcategory_chip_${subcategory.value}"
                    )
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val parent = selectedCategoryOption?.value ?: return@FilterChip
                            onSelectSubcategory(parent, subcategory.value)
                        },
                        label = { Text(subcategory.label) },
                        modifier = Modifier.scale(scale),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = videosAccent,
                            selectedLabelColor = onVideosAccent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FarmVideosDrawerContent(
    menuState: MediaMenuState,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onSelectSubcategory: (String, String) -> Unit
) {
    val videosAccentContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onVideosAccentContainer = MaterialTheme.colorScheme.onTertiaryContainer
    val explorerCategories = remember(menuState.categories) { buildExplorerCategories(menuState.categories) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = videosAccentContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "FarmVideos",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onVideosAccentContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Browse videos by category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onVideosAccentContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DrawerPrimaryItem(
            title = "All FarmVideos",
            subtitle = "Displays every available video.",
            selected = menuState.selection.category == null,
            onClick = onSelectAll
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(explorerCategories, key = { it.value }) { category ->
                DrawerCategoryCard(
                    category = category,
                    selection = menuState.selection,
                    isExpanded = category.value in menuState.expandedCategoryIds,
                    onSelectCategory = { onSelectCategory(category.value) },
                    onToggleCategory = { onToggleCategory(category.value) },
                    onSelectSubcategory = { subcategoryId ->
                        onSelectSubcategory(category.value, subcategoryId)
                    }
                )
            }
        }
    }
}

@Composable
private fun DrawerPrimaryItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) videosAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerBadge(icon = Icons.Default.VideoLibrary, contentDescription = "All FarmVideos")
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerCategoryCard(
    category: ExplorerCategoryOption,
    selection: MediaFilterSelection,
    isExpanded: Boolean,
    onSelectCategory: () -> Unit,
    onToggleCategory: () -> Unit,
    onSelectSubcategory: (String) -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    val isSelected = selection.category == category.value && selection.subcategory == null

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selection.category == category.value) {
                videosAccent.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectCategory)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawerBadge(icon = category.icon, contentDescription = category.label)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) videosAccent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (category.subcategories.isEmpty()) "Browse category" else "${category.subcategories.size} topics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (category.subcategories.isNotEmpty()) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.8f),
                        label = "drawer_chevron_${category.value}"
                    )
                    IconButton(onClick = onToggleCategory) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            modifier = Modifier.rotate(rotation),
                            contentDescription = if (isExpanded) "Collapse ${category.label}" else "Expand ${category.label}"
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && category.subcategories.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    category.subcategories.forEach { subcategory ->
                        DrawerSubcategoryRow(
                            subcategory = subcategory,
                            selected = selection.category == category.value && selection.subcategory == subcategory.value,
                            onClick = { onSelectSubcategory(subcategory.value) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSubcategoryRow(
    subcategory: ExplorerSubcategoryOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) videosAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                tint = if (selected) videosAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = subcategory.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) videosAccent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DrawerBadge(icon: ImageVector, contentDescription: String) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary

    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                shape = RoundedCornerShape(10.dp)
                clip = true
            }
            .background(videosAccent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = onVideosAccent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun VideoResults(
    navController: NavHostController,
    selectionTitle: String,
    selectionDescription: String,
    videos: List<VideoItem>,
    errorMessage: String?,
    hasMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onViewAll: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    LaunchedEffect(listState, hasMore, videos.size) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
            .map { nearEnd -> nearEnd && hasMore }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadMore()
            }
    }

    AnimatedContent(
        targetState = videos.map { it.id },
        transitionSpec = {
            fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
        },
        label = "videos_content"
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            item {
                SelectionSummaryCard(
                    selectionTitle = selectionTitle,
                    selectionDescription = selectionDescription,
                    onRetry = onRetry
                )
            }

            if (errorMessage != null) {
                item {
                    ErrorBanner(message = errorMessage, onRetry = onRetry)
                }
            }

            if (videos.isEmpty()) {
                item {
                    EmptyMediaState(onViewAll = onViewAll)
                }
            } else {
                items(videos, key = { it.id }) { video ->
                    Box(modifier = Modifier.animateItem()) {
                        VideoCard(video = video) {
                            navController.navigate(AppRoutes.VIDEO_DETAIL.replace("{videoId}", video.id.toString()))
                        }
                    }
                }
            }

            if (hasMore) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading more videos...")
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionSummaryCard(
    selectionTitle: String,
    selectionDescription: String,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = selectionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectionDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text("Refresh feed")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading FarmVideos...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(4) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaState(onViewAll: () -> Unit) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = "No videos",
            modifier = Modifier.size(64.dp),
            tint = videosAccent
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "No videos found for this topic.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Try another category, subcategory, or search query.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onViewAll) {
            Text("View All Videos")
        }
    }
}
