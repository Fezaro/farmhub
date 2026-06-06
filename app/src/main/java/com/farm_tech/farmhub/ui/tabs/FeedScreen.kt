package com.farm_tech.farmhub.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.farm_tech.farmhub.features.AppRoutes
import com.farm_tech.farmhub.models.FarmVideoCategory
import com.farm_tech.farmhub.models.FarmVideoMenuCatalog
import com.farm_tech.farmhub.models.FarmVideoSelection
import com.farm_tech.farmhub.models.FarmVideoSubcategory
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.models.VideoViewModel
import com.farm_tech.farmhub.ui.components.VideoCard
import com.farm_tech.farmhub.viewmodel.MediaMenuState
import com.farm_tech.farmhub.viewmodel.MediaUiState
import com.farm_tech.farmhub.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    navController: NavHostController,
    videoViewModel: VideoViewModel = viewModel(),
    mediaViewModel: MediaViewModel = viewModel()
) {
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val mediaState by mediaViewModel.uiState.collectAsState()
    val menuState by mediaViewModel.menuState.collectAsState()

    LaunchedEffect(Unit) {
        mediaViewModel.loadMedia()
    }

    val selectionTitle = mediaViewModel.currentSelectionTitle()
    val selectionDescription = mediaViewModel.currentSelectionDescription()

    val fallbackVideos = remember(menuState) {
        mediaViewModel.filterFallbackVideos(videoViewModel.videos)
    }

    val displayedVideos = when (val state = mediaState) {
        is MediaUiState.Success -> state.videos
        else -> fallbackVideos
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
            onSelectAll = mediaViewModel::selectAllVideos,
            onSelectCategory = mediaViewModel::selectCategory,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            QuickAccessCategories(
                menuState = menuState,
                onSelectAll = onSelectAll,
                onSelectCategory = onSelectCategory
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
                    onRetry = onRetry,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun QuickAccessCategories(
    menuState: MediaMenuState,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary

    val selection = menuState.selection

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selection.categoryId == null,
                onClick = onSelectAll,
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = videosAccent,
                    selectedLabelColor = onVideosAccent
                )
            )
        }
        items(FarmVideoMenuCatalog.categories, key = { it.id }) { category ->
            FilterChip(
                selected = selection.categoryId == category.id && selection.subcategoryId == null,
                onClick = { onSelectCategory(category.id) },
                label = { Text(category.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = videosAccent,
                    selectedLabelColor = onVideosAccent
                )
            )
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
                    text = "Browse farming content the way you would on a modern video platform — by topic, sector, and practical interest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onVideosAccentContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DrawerPrimaryItem(
            title = "All FarmVideos",
            subtitle = "See the complete video feed",
            selected = menuState.selection.categoryId == null,
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
            items(FarmVideoMenuCatalog.categories, key = { it.id }) { category ->
                DrawerCategoryCard(
                    category = category,
                    selection = menuState.selection,
                    isExpanded = category.id in menuState.expandedCategoryIds,
                    onSelectCategory = { onSelectCategory(category.id) },
                    onToggleCategory = { onToggleCategory(category.id) },
                    onSelectSubcategory = { subcategoryId ->
                        onSelectSubcategory(category.id, subcategoryId)
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
            DrawerBadge(label = "A")
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
    category: FarmVideoCategory,
    selection: FarmVideoSelection,
    isExpanded: Boolean,
    onSelectCategory: () -> Unit,
    onToggleCategory: () -> Unit,
    onSelectSubcategory: (String) -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    val isSelected = selection.categoryId == category.id && selection.subcategoryId == null

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selection.categoryId == category.id) {
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
                DrawerBadge(label = category.label.take(1))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) videosAccent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (category.subcategories.isEmpty()) {
                            "Open videos in this category"
                        } else {
                            "${category.subcategories.size} sub-topics"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (category.subcategories.isNotEmpty()) {
                    IconButton(onClick = onToggleCategory) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
                            selected = selection.categoryId == category.id && selection.subcategoryId == subcategory.id,
                            onClick = { onSelectSubcategory(subcategory.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSubcategoryRow(
    subcategory: FarmVideoSubcategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val videosAccent = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) videosAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Text(
            text = subcategory.label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) videosAccent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DrawerBadge(label: String) {
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary

    Box(
        modifier = Modifier
            .size(34.dp)
            .background(videosAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = onVideosAccent,
            fontWeight = FontWeight.Bold
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
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
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
                EmptyMediaState(selectionTitle = selectionTitle, retry = onRetry)
            }
        } else {
            items(videos, key = { it.id }) { video ->
                VideoCard(video = video) {
                    navController.navigate(AppRoutes.VIDEO_DETAIL.replace("{videoId}", video.id.toString()))
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
                    OutlinedButton(onClick = onLoadMore) {
                        Text("Load more")
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
private fun EmptyMediaState(selectionTitle: String, retry: () -> Unit) {
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
            text = "No videos for $selectionTitle yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Try another category from the drawer or refresh to check for newly published content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = retry) {
            Text("Retry")
        }
    }
}

