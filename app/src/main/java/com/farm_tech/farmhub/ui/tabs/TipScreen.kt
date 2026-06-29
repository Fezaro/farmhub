package com.farm_tech.farmhub.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farm_tech.farmhub.viewmodel.TipOfDayViewModel

@Composable
fun TipScreen() {
    val context = LocalContext.current
    val viewModel = remember { TipOfDayViewModel(context) }
    val state by viewModel.uiState.collectAsState()

    // null means "All categories"
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Tip of the Day",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Interactive category filter row
        if (state.categories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = {
                            selectedCategory = null
                            viewModel.refresh(null)
                        },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = onAccent
                        )
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = category
                            viewModel.refresh(category)
                        },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent,
                            selectedLabelColor = onAccent
                        )
                    )
                }
            }
        }

        when {
            state.isLoading -> CircularProgressIndicator()
            state.tip != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(state.tip!!.category, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(state.tip!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Text(state.tip!!.tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            else -> Text(state.error ?: "No tip available.")
        }

        Button(
            onClick = { viewModel.refresh(selectedCategory) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            Spacer(Modifier.width(4.dp))
            Text("Refresh tip")
        }
    }
}
