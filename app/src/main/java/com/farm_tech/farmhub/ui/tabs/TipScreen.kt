package com.farm_tech.farmhub.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

        if (state.categories.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = { },
                label = { Text(state.categories.first()) }
            )
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

        Button(onClick = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            Spacer(Modifier.height(0.dp))
            Text("Refresh tip")
        }
    }
}
