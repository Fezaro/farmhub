package com.example.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.app.features.AppRoutes
import com.example.app.models.VideoViewModel
import com.example.app.models.videoFilters
import com.example.app.ui.components.FilterRow
import com.example.app.ui.components.VideoCard
import com.example.app.viewmodel.MediaUiState
import com.example.app.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

// Entry point screen deciding layout based on width
@Composable
fun VideoScreen(navController: NavHostController) {
    val configuration = LocalConfiguration.current
    val isLargeScreen = configuration.screenWidthDp >= 600
    if (isLargeScreen) {
        PermanentDrawerScreen(navController)
    } else {
        ModalDrawerScreen(navController)
    }
}

@Preview
@Composable
fun VideoScreenPreview() {
    val nav = rememberNavController()
    VideoScreen(navController = nav)
}

// Modal drawer layout (phones)
@Composable
fun ModalDrawerScreen(navController: NavHostController) {
    var selectedFilter by remember { mutableStateOf("All") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                selectedFilter = selectedFilter,
                onFilterSelected = {
                    selectedFilter = it
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBarFilters(
                selectedFilter = selectedFilter,
                onMenu = { scope.launch { drawerState.open() } },
                onFilterChange = { selectedFilter = it }
            )
            Spacer(Modifier.height(12.dp))
            VideoFeed(selectedFilter = selectedFilter, navController = navController)
        }
    }
}

@Preview
@Composable
fun ModalDrawerScreenPreview() {
    ModalDrawerScreen(rememberNavController())
}

// Permanent drawer layout (tablets / large screens)
@Composable
fun PermanentDrawerScreen(navController: NavHostController) {
    var selectedFilter by remember { mutableStateOf("All") }
    PermanentNavigationDrawer(
        drawerContent = {
            DrawerContent(selectedFilter = selectedFilter, onFilterSelected = { selectedFilter = it })
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBarFilters(
                selectedFilter = selectedFilter,
                onMenu = {},
                onFilterChange = { selectedFilter = it }
            )
            Spacer(Modifier.height(12.dp))
            VideoFeed(selectedFilter = selectedFilter, navController = navController)
        }
    }
}

@Preview
@Composable
fun PermanentDrawerScreenPreview() {
    PermanentDrawerScreen(rememberNavController())
}

// Shared top bar with filter chips row
@Composable
private fun TopBarFilters(
    selectedFilter: String,
    onMenu: () -> Unit,
    onFilterChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
        Spacer(Modifier.width(8.dp))
        FilterRow(selectedFilter = selectedFilter, filters = videoFilters, onFilterSelected = onFilterChange)
    }
}

// Drawer content listing filters/categories
@Composable
fun DrawerContent(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("Categories", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        videoFilters.forEach { filter ->
            Text(
                text = filter,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilterSelected(filter) }
                    .padding(vertical = 12.dp),
                color = if (filter == selectedFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview
@Composable
fun DrawerContentPreview() {
    DrawerContent(selectedFilter = "All", onFilterSelected = {})
}

// Main feed list combining remote media (if available) with static fallback
@Composable
fun VideoFeed(
    selectedFilter: String,
    navController: NavHostController,
    videoViewModel: VideoViewModel = viewModel(),
    mediaViewModel: MediaViewModel = viewModel()
) {
    val mediaState = mediaViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { mediaViewModel.loadMedia() }

    val staticVideos = videoViewModel.videos
    val (remoteVideos, hasMore) = when (val state = mediaState.value) {
        is MediaUiState.Success -> state.videos to state.hasMore
        else -> emptyList<com.example.app.models.VideoItem>() to false
    }
    val baseList = remoteVideos.ifEmpty { staticVideos }
    val filtered = if (selectedFilter == "All") baseList else baseList.filter {
        it.title.contains(selectedFilter, ignoreCase = true) || it.channel.contains(selectedFilter, ignoreCase = true)
    }

    val isRefreshing = mediaState.value is MediaUiState.Loading && baseList.isNotEmpty()

    // Loading initial empty state
    if (mediaState.value is MediaUiState.Loading && baseList.isEmpty()) {
        LoadingState(); return
    }
    // Error state with fallback
    if (mediaState.value is MediaUiState.Error && baseList.isEmpty()) {
        ErrorWithFallback(emptyList(), navController, retry = { mediaViewModel.refresh() }); return
    }

    Box(Modifier.fillMaxSize()) {
        // Simple top progress indicator while refreshing (non-blocking)
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            item {
                // Manual refresh button row
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Videos", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { mediaViewModel.refresh() }, enabled = !isRefreshing) {
                        Text(if (isRefreshing) "Refreshing..." else "Refresh")
                    }
                }
            }
            items(filtered.size) { index ->
                val video = filtered[index]
                VideoCard(video = video) {
                    navController.navigate(AppRoutes.VIDEO_DETAIL.replace("{videoId}", video.id.toString()))
                }
                // Trigger pagination when reaching near end
                if (index == filtered.lastIndex - 2 && hasMore && mediaState.value is MediaUiState.Success) {
                    LaunchedEffect("page_$index") { mediaViewModel.loadNextPage() }
                }
            }
            if (hasMore) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Loading more...")
                    }
                }
            }
            if (mediaState.value is MediaUiState.Error && baseList.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Load error", color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { mediaViewModel.refresh() }) { Text("Retry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Loading media...")
    }
}

@Composable
private fun ErrorWithFallback(videos: List<com.example.app.models.VideoItem>, navController: NavHostController, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Failed to load media", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = retry) { Text("Retry") }
        Spacer(Modifier.height(24.dp))
        VideoList(videos, navController)
    }
}

@Composable
private fun VideoList(videos: List<com.example.app.models.VideoItem>, navController: NavHostController) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        items(videos) { video ->
            VideoCard(video = video) {
                navController.navigate(AppRoutes.VIDEO_DETAIL.replace("{videoId}", video.id.toString()))
            }
        }
    }
}

@Preview
@Composable
fun VideoFeedPreview() {
    VideoList(videos = emptyList(), navController = rememberNavController())
}