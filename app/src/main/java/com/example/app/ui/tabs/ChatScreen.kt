package com.example.app.ui.tabs

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.app.models.messaging.MessageItemResponse
import com.example.app.models.messaging.ThreadResponse
import com.example.app.repository.MessageRepository
import com.example.app.session.UserSession
import com.example.app.viewmodel.MessageViewModel
import com.example.app.viewmodel.SendMessageUiState
import com.example.app.viewmodel.ThreadsUiState
import com.example.app.viewmodel.ConversationUiState

sealed class MessageContent {
    data class TextMessage(val text: String) : MessageContent()
    data class MediaMessage(val uri: Uri, val description: String? = null) : MessageContent()
}

data class Message(val sender: String, val content: MessageContent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val viewModel = remember { MessageViewModel(MessageRepository(context)) }

    // Local optimistic list for UI continuity
    val optimisticMessages = remember { mutableStateListOf<Message>() }

    val sendState by viewModel.uiState.collectAsState()
    val threadsState by viewModel.threadsState.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val selectedThreadId by viewModel.selectedThreadId.collectAsState()

    // Load threads once
    LaunchedEffect(Unit) { viewModel.loadThreads() }

    // Gallery picker
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentDescription by remember { mutableStateOf("") }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingAttachment = it }
    }

    var inputText by remember { mutableStateOf("") }

    // Observe send result
    LaunchedEffect(sendState) {
        when (sendState) {
            is SendMessageUiState.Success -> {
                viewModel.resetState()
                pendingAttachment = null
                pendingAttachmentDescription = ""
            }
            is SendMessageUiState.Error -> {
                optimisticMessages.add(Message("Bot", MessageContent.TextMessage("Error sending message")))
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        TopAppBar(
            title = { Text("FarmHub Chat", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF388E3C),
                titleContentColor = Color.White
            )
        )

        // Threads selector row
        ThreadsSelector(
            threadsState = threadsState,
            selectedId = selectedThreadId,
            onSelect = { viewModel.selectThread(it) },
            onRetry = { viewModel.loadThreads(force = true) }
        )

        // Conversation / messages
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (conversationState) {
                is ConversationUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ConversationUiState.Error -> {
                    val msg = (conversationState as ConversationUiState.Error).message
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, color = Color.Red, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { selectedThreadId?.let { viewModel.selectThread(it) } }) { Text("Retry") }
                    }
                }
                is ConversationUiState.Success -> {
                    val serverMessages = (conversationState as ConversationUiState.Success).messages
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true
                    ) {
                        val combined = (optimisticMessages + serverMessages.map { msg ->
                            val isMine = msg.isFromCurrentUser()
                            Message(if (isMine) "User" else "Bot", MessageContent.TextMessage(msg.derivedText()))
                        })
                        items(combined.reversed()) { m -> ChatBubble(m) }
                    }
                }
                ConversationUiState.Idle -> {
                    Text("Select a thread to view messages", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                }
            }
        }

        // Pending attachment
        if (pendingAttachment != null) {
            AttachmentPreview(
                pendingAttachment = pendingAttachment,
                description = pendingAttachmentDescription,
                onDescriptionChange = { pendingAttachmentDescription = it },
                onRemove = { pendingAttachment = null; pendingAttachmentDescription = "" }
            )
        }

        // Input bar
        MessageInputBar(
            isSending = sendState is SendMessageUiState.Loading,
            inputText = inputText,
            onInputChange = { inputText = it },
            onAttach = { galleryLauncher.launch("image/*") },
            onSend = {
                Log.d("ChatScreen", "onSend clicked textLen=${inputText.length} attachment=${pendingAttachment!=null} selectedThread=$selectedThreadId userPhone=${UserSession.phone}")
                if (pendingAttachment != null) {
                    optimisticMessages.add(Message("User", MessageContent.MediaMessage(pendingAttachment!!, pendingAttachmentDescription)))
                    viewModel.sendMessage(pendingAttachmentDescription, pendingAttachment)
                    pendingAttachment = null
                    pendingAttachmentDescription = ""
                } else if (inputText.isNotBlank()) {
                    optimisticMessages.add(Message("User", MessageContent.TextMessage(inputText)))
                    viewModel.sendMessage(inputText, null)
                    inputText = ""
                } else {
                    Log.d("ChatScreen", "Nothing to send (empty text & no attachment)")
                }
            }
        )
    }
}

@Composable
private fun ThreadsSelector(
    threadsState: ThreadsUiState,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit
) {
    when (threadsState) {
        is ThreadsUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is ThreadsUiState.Error -> {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Threads error", color = Color.Red)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        is ThreadsUiState.Success -> {
            val threads = threadsState.threads
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                val selectedLabel = threads.find { it.derivedId() == selectedId }?.derivedLastMessage() ?: "Select Conversation"
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedLabel)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    threads.forEach { t ->
                        val label = t.derivedLastMessage() ?: t.derivedId() ?: "Thread"
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            expanded = false
                            onSelect(t.derivedId())
                        })
                    }
                    DropdownMenuItem(text = { Text("Clear Selection") }, onClick = { expanded = false; onSelect(null) })
                }
            }
        }
        ThreadsUiState.Idle -> { /* nothing */ }
    }
}

@Composable
private fun AttachmentPreview(
    pendingAttachment: Uri?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    if (pendingAttachment == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberAsyncImagePainter(pendingAttachment),
                contentDescription = "Attachment Preview",
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.LightGray, shape = RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextField(
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = { Text("Describe your attachment...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.AttachFile, contentDescription = "Remove Attachment", tint = Color(0xFF388E3C))
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    isSending: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach) { Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach") }
        TextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).background(Color.White),
            placeholder = { Text("Type a message...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            maxLines = 4
        )
        IconButton(enabled = !isSending, onClick = onSend) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isUser = message.sender == "User"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (isUser) Color(0xFFDCF8C6) else Color(0xFFEFEFEF),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            when (val content = message.content) {
                is MessageContent.TextMessage -> Text(text = content.text, fontSize = 16.sp, color = Color.Black)
                is MessageContent.MediaMessage -> {
                    Column {
                        Image(
                            painter = rememberAsyncImagePainter(content.uri),
                            contentDescription = "Media",
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.LightGray, shape = RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (!content.description.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(text = content.description, fontSize = 14.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}