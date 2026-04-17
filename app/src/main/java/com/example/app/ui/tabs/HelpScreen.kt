package com.example.app.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.features.FarmHelp
import com.example.app.viewmodel.FarmHelpViewModel

@Composable
fun HelpScreen(
    onChatClick: () -> Unit,
    onVideosClick: () -> Unit
) {
    var showFarmHelp by remember { mutableStateOf(false) }
    val farmHelpViewModel: FarmHelpViewModel = viewModel()
    val exploreItems = remember {
        listOf(
            ExploreItem(
                title = "Watch FREE Farming Videos",
                description = "Learn practical techniques from trusted local experts.",
                icon = Icons.Default.PlayCircle,
                onClick = onVideosClick
            ),
            ExploreItem(
                title = "AgriConnect",
                description = "Start a conversation with an extension officer.",
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = onChatClick
            ),
            ExploreItem(
                title = "Weather Forecast",
                description = "Get location-aware weather insights for your planning.",
                icon = Icons.Default.WbSunny,
                onClick = {}
            ),
            ExploreItem(
                title = "Tip of the Day",
                description = "Seasonal guidance to improve crop and livestock outcomes.",
                icon = Icons.Default.TipsAndUpdates,
                onClick = {}
            )
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "FarmHelp",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp)
                )
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "Help",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Facing Farming Challenges?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Share your questions about crop issues, livestock problems, or any farming challenges. Our expert extension officers will provide immediate solutions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "Explore More",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(exploreItems) { item ->
                        ExploreCard(item = item)
                    }
                }
                Button(
                    onClick = { showFarmHelp = !showFarmHelp },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (showFarmHelp) "Close FarmHelp" else "Ask a Question",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val ctaButtonModifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp)

                    Button(
                        onClick = onChatClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = ctaButtonModifier
                    ) {
                        Text(
                            text = "Chat with Officer",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onVideosClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = ctaButtonModifier
                    ) {
                        Text(
                            text = "Open FarmVideos",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showFarmHelp,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                FarmHelp(viewModel = farmHelpViewModel, onClose = { showFarmHelp = false })
            }
        }
    }
}

@Composable
private fun ExploreCard(item: ExploreItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = "${item.title} icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class ExploreItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)