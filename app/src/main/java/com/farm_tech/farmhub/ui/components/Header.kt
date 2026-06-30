package com.farm_tech.farmhub.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.farm_tech.farmhub.ui.theme.GreenColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    modifier: Modifier = Modifier,
    containerColor: Color = GreenColor,
    contentColor: Color = Color.White,
    title: String = "FarmHub",
    onChatClick: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor,
            titleContentColor = contentColor,
            actionIconContentColor = contentColor
        ),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        actions = {
            ActionButtons(
                onChatClick = onChatClick
            )
        }
    )
}

@Composable
private fun ActionButtons(
    onChatClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onChatClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "Chat"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppHeaderPreview() {
    MaterialTheme {
        Surface {
            AppHeader(
                onChatClick = {},
            )
        }
    }
}

