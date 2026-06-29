package com.farm_tech.farmhub.ui.tabs
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.farm_tech.farmhub.models.messaging.ThreadResponse
import com.farm_tech.farmhub.repository.MessageRepository
import com.farm_tech.farmhub.session.UserSession
import com.farm_tech.farmhub.viewmodel.MessageViewModel
import com.farm_tech.farmhub.viewmodel.SendMessageUiState
import com.farm_tech.farmhub.viewmodel.ThreadsUiState
import com.farm_tech.farmhub.viewmodel.ConversationUiState

sealed class MessageContent {
    data class TextMessage(val text: String) : MessageContent()
    data class MediaMessage(val uri: Uri, val description: String? = null) : MessageContent()
}

data class Message(val sender: String, val content: MessageContent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { MessageViewModel(MessageRepository(context)) }

    // Local optimistic list for UI continuity
    val optimisticMessages = remember { mutableStateListOf<Message>() }

    val sendState by viewModel.uiState.collectAsState()
    val threadsState by viewModel.threadsState.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val selectedThreadId by viewModel.selectedThreadId.collectAsState()

    var inputText by remember { mutableStateOf("") }

    // Load threads once
    LaunchedEffect(Unit) { viewModel.loadThreads() }

    // Gallery picker
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentDescription by remember { mutableStateOf("") }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingAttachment = it }
    }

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
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Inbox", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
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
                                val senderName = if (isMine) "You" else msg.otherPartyPhone()?.let { phone ->
                                    viewModel.getThreadDisplayName(phone)
                                } ?: "Unknown"
                                Message(senderName, MessageContent.TextMessage(msg.derivedText()))
                            })
                            items(combined.reversed()) { m -> ChatBubble(m) }
                        }
                }
                ConversationUiState.Empty -> {
                    Text(
                        "No messages in this conversation yet.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConversationUiState.Idle -> {
                    Text(
                        "Select a thread to view messages",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            onSend = onSendBlock@{
                if (sendState is SendMessageUiState.Loading) return@onSendBlock
                Log.d("ChatScreen", "onSend clicked textLen=${inputText.length} attachment=${pendingAttachment!=null} selectedThread=$selectedThreadId userPhone=${UserSession.phone}")
                if (pendingAttachment != null) {
                    if (pendingAttachmentDescription.isBlank()) {
                        return@onSendBlock
                    }
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

        if (sendState is SendMessageUiState.Error) {
            val message = (sendState as SendMessageUiState.Error).message
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            TextButton(onClick = { viewModel.retryLastFailedMessage() }) {
                Text("Retry last message")
            }
        }
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
            if (threads.isEmpty()) {
                Text(
                    text = "No conversations available yet.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(threads) { thread ->
                    val threadId = thread.derivedId()
                    ThreadPreviewCard(
                        thread = thread,
                        isSelected = selectedId != null && selectedId == threadId,
                        onClick = { onSelect(threadId) }
                    )
                }
            }
        }
        ThreadsUiState.Empty -> {
            Text(
                text = "No conversations available yet.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ThreadsUiState.Idle -> { /* nothing */ }
    }
}

@Composable
private fun ThreadPreviewCard(
    thread: ThreadResponse,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val currentUserPhone = UserSession.phone
    val senderLabel = thread.otherParty(currentUserPhone) ?: "Farmer Support"
    val preview = thread.derivedLastMessage().orEmpty().ifBlank { "Tap to view conversation" }
    val timestamp = thread.derivedUpdatedAt()?.take(16)?.replace('T', ' ') ?: "Now"

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .width(240.dp)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                    )
                }
            }
        }
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
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = rememberAsyncImagePainter(pendingAttachment),
                contentDescription = "Attachment Preview",
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)),
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
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Remove Attachment",
                    tint = MaterialTheme.colorScheme.primary
                )
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach) { Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach") }
        TextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface),
            placeholder = { Text("Type a message...") },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
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
    val isUser = message.sender == "You"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                when (val content = message.content) {
                    is MessageContent.TextMessage -> Text(
                        text = content.text,
                        fontSize = 16.sp,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is MessageContent.MediaMessage -> {
                        Column {
                            Image(
                                painter = rememberAsyncImagePainter(content.uri),
                                contentDescription = "Media",
                                modifier = Modifier
                                    .size(200.dp)
                                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (!content.description.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = content.description,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
