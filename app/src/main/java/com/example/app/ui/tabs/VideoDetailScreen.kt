package com.example.app.ui.tabs

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.models.VideoViewModel
import com.example.app.ui.components.VideoCard
import com.example.app.ui.components.VideoPlayer
import com.example.app.viewmodel.MediaUiState
import com.example.app.viewmodel.MediaViewModel

@Composable
fun VideoDetailScreen(
    videoId: Int,
    onVideoClick: (Int) -> Unit,
    videoViewModel: VideoViewModel = viewModel(),
    mediaViewModel: MediaViewModel = viewModel()
) {
    var commentsExpanded by remember { mutableStateOf(false) }
    val videosAccent = MaterialTheme.colorScheme.tertiary
    val onVideosAccent = MaterialTheme.colorScheme.onTertiary

    val mediaState = mediaViewModel.uiState.collectAsState()

    // Ensure media loaded so remote id can be found
    LaunchedEffect(Unit) {
        if (mediaState.value is MediaUiState.Idle) {
            mediaViewModel.loadMedia()
        }
    }

    // Try remote first, then fallback to static
    val remoteVideo = mediaViewModel.getVideoById(videoId)
    val video = remoteVideo ?: videoViewModel.getVideoById(videoId)
    val relatedVideos = if (remoteVideo != null) {
        // Use other remote videos (simple exclusion)
        mediaViewModel.allVideos().filter { it.id != videoId }.take(10)
    } else {
        videoViewModel.getRelatedVideos(videoId)
    }
    val comments = videoViewModel.comments // still using static comments

    if (video == null) {
        // Show loading if remote list still loading
        if (mediaState.value is MediaUiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading video...")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Video not found", fontSize = 18.sp) }
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val mediaUrl = video.mediaUrl
                    if (!mediaUrl.isNullOrBlank()) {
                        VideoPlayer(url = mediaUrl, modifier = Modifier.fillMaxSize())
                    } else if (video.thumbnailUrl != null) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video Placeholder",
                            modifier = Modifier.size(64.dp),
                            tint = videosAccent
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(videosAccent, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = video.channel.firstOrNull()?.toString() ?: "?",
                            color = onVideosAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = video.channel,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(video.views.takeIf { it.isNotBlank() }, video.time.takeIf { it.isNotBlank() }).joinToString(" • "),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            HorizontalDivider(thickness = 1.dp)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { commentsExpanded = !commentsExpanded }
                    ) {
                        Text(
                            "Comments (${comments.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (commentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (commentsExpanded) "Collapse" else "Expand"
                        )
                    }
                    if (commentsExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        comments.forEach { comment ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(comment.user, fontWeight = FontWeight.Bold)
                                Text(comment.text, fontSize = 15.sp)
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp)
        }

        item {
            Text(
                "Related Videos",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(relatedVideos) { videoItem ->
            VideoCard(
                video = videoItem,
                onClick = { onVideoClick(videoItem.id) }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}