package com.farm_tech.farmhub

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import com.farm_tech.farmhub.ui.tabs.ChatBubble
import com.farm_tech.farmhub.ui.tabs.Message
import com.farm_tech.farmhub.ui.tabs.MessageContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import com.farm_tech.farmhub.ui.components.ChatImageViewer

class ChatImageViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullImageCanBeClosedWithoutLeavingChat() {
        val open = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (open.value) ChatImageViewer(
                    imageUri = "android.resource://android/drawable/ic_menu_gallery",
                    onClose = { open.value = false }
                )
            }
        }
        compose.onNodeWithContentDescription("Close image").assertExists().performClick()
        compose.onNodeWithContentDescription("Close image").assertDoesNotExist()
    }

    @Test fun incomingAndPreviouslySentImagesOpenAndReturnToChat() {
        val opened = mutableStateOf<String?>(null)
        val uri = Uri.parse("android.resource://android/drawable/ic_menu_gallery")
        compose.setContent {
            MaterialTheme {
                Column {
                    listOf("Dashboard", "You").forEach { sender ->
                        ChatBubble(Message(sender, MessageContent.MediaMessage(uri, "Saved message"))) {
                            opened.value = it.toString()
                        }
                    }
                }
                opened.value?.let { ChatImageViewer(it) { opened.value = null } }
            }
        }
        for (index in 0..1) {
            compose.onAllNodesWithContentDescription("Message image")[index].performClick()
            compose.onNodeWithContentDescription("Full-screen message image").assertExists()
                .performTouchInput { doubleClick() }
            compose.onNodeWithContentDescription("Close image").performClick()
            compose.onAllNodesWithContentDescription("Message image")[index].assertExists()
        }
    }
}
