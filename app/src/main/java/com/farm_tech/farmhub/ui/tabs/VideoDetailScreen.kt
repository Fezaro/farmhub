package com.farm_tech.farmhub.ui.tabs

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farm_tech.farmhub.BuildConfig
import com.farm_tech.farmhub.models.VideoViewModel
import com.farm_tech.farmhub.ui.components.VideoCard
import com.farm_tech.farmhub.ui.components.VideoPlayer
import com.farm_tech.farmhub.viewmodel.MediaUiState
import com.farm_tech.farmhub.viewmodel.MediaViewModel

private const val VIDEO_DETAIL_TAG = "VideoDetailScreen"

private fun String?.isPlayableVideoUrl(): Boolean {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return false
    if (!value.startsWith("https://", ignoreCase = true)) return false
    return value.endsWith(".mp4", ignoreCase = true) ||
        value.endsWith(".m3u8", ignoreCase = true) ||
        value.endsWith(".mpd", ignoreCase = true) ||
        value.contains(".mp4?", ignoreCase = true) ||
        value.contains(".m3u8?", ignoreCase = true) ||
        value.contains(".mpd?", ignoreCase = true)
}

@Composable
fun VideoDetailScreen(
    videoId: Int,
    onVideoClick: (Int) -> Unit,
    videoViewModel: VideoViewModel = viewModel(),
    mediaViewModel: MediaViewModel = viewModel()
) {
    var commentsExpanded by remember { mutableStateOf(false) }
    var descriptionExpanded by remember(videoId) { mutableStateOf(false) }
    var descriptionHasOverflow by remember(videoId) { mutableStateOf(false) }
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
    val relatedRemoteVideos = mediaViewModel.allVideos().filter { it.id != videoId }.take(10)
    val relatedVideos = if (relatedRemoteVideos.isNotEmpty()) {
        relatedRemoteVideos
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

    if (BuildConfig.DEBUG) {
        LaunchedEffect(video.id, video.mediaUrl, video.thumbnailUrl, video.streamUrl, video.videoUrl, video.playbackUrl, video.fileUrl, video.rawUrl, video.mimeType) {
            Log.d(
                VIDEO_DETAIL_TAG,
                "SELECTED_VIDEO id=${video.id} title=${video.title} thumbnailUrl=${video.thumbnailUrl} streamUrl=${video.streamUrl} videoUrl=${video.videoUrl} playbackUrl=${video.playbackUrl} fileUrl=${video.fileUrl} url=${video.rawUrl} mediaUrl=${video.mediaUrl} mimeType=${video.mimeType} actualPlaybackUrl=${video.mediaUrl} actualPlaybackValid=${video.mediaUrl.isPlayableVideoUrl()}"
            )
        }
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
                    VideoPlayer(
                        url = video.mediaUrl,
                        thumbnailUrl = video.thumbnailUrl,
                        title = video.title,
                        mimeType = video.mimeType,
                        modifier = Modifier.fillMaxSize()
                    )
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
                            text = video.company.ifBlank { video.channel }.firstOrNull()?.toString() ?: "?",
                            color = onVideosAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Company Name",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = video.company.ifBlank { video.channel },
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOf(
                        video.category,
                        video.subcategory,
                        video.uploadedAt.ifBlank { video.time },
                        video.duration,
                        video.author
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "Category" to video.category,
                    "Subcategory" to video.subcategory,
                    "Upload Date" to video.uploadedAt.ifBlank { video.time },
                    "Duration" to video.duration,
                    "Video Author" to video.author
                ).forEach { (label, value) ->
                    if (value.isNotBlank()) {
                        Text(
                            text = "$label: $value",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp)
        }

        val descriptionText = video.description.trim()
        if (descriptionText.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Description",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = descriptionText,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.bodyMedium,
                        onTextLayout = { layoutResult ->
                            if (!descriptionExpanded) {
                                descriptionHasOverflow = layoutResult.hasVisualOverflow
                            }
                        }
                    )
                    if (descriptionHasOverflow || descriptionExpanded) {
                        TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                            Text(if (descriptionExpanded) "Read Less" else "Read More")
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp)
            }
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
