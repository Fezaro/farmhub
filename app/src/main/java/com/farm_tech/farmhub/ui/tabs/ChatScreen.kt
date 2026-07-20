package com.farm_tech.farmhub.ui.tabs

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
import com.farm_tech.farmhub.repository.MessageRepository
import com.farm_tech.farmhub.session.UserSession
import com.farm_tech.farmhub.util.FriendlyDateTimeFormatter
import com.farm_tech.farmhub.viewmodel.ChatHeaderUi
import com.farm_tech.farmhub.viewmodel.ConversationUiState
import com.farm_tech.farmhub.viewmodel.MessageViewModel
import com.farm_tech.farmhub.viewmodel.SendMessageUiState
import com.farm_tech.farmhub.viewmodel.ThreadPreviewUi
import com.farm_tech.farmhub.viewmodel.ThreadsUiState

sealed class MessageContent {
    data class TextMessage(val text: String) : MessageContent()
    data class MediaMessage(val uri: Uri, val description: String? = null) : MessageContent()
}

data class Message(
    val sender: String,
    val content: MessageContent,
    val timestamp: String = "",
    val deliveryStatus: String? = null,
    val replyCount: Int = 0,
    val attachmentLabel: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember { MessageViewModel(MessageRepository(context)) }
    val optimisticMessages = remember { mutableStateListOf<Message>() }
    val messageListState = rememberLazyListState()

    val sendState by viewModel.uiState.collectAsState()
    val threadsState by viewModel.threadsState.collectAsState()
    val conversationState by viewModel.conversationState.collectAsState()
    val selectedThreadId by viewModel.selectedThreadId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentDescription by remember { mutableStateOf("") }

    val threadPreviews = remember(threadsState) { viewModel.threadPreviews() }
    val selectedHeader = remember(selectedThreadId, threadsState) { viewModel.selectedHeader() }

    LaunchedEffect(Unit) { viewModel.loadThreads() }

    LaunchedEffect(selectedThreadId) { optimisticMessages.clear() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingAttachment = it }
    }

    LaunchedEffect(sendState) {
        when (sendState) {
            is SendMessageUiState.Success -> {
                viewModel.resetState()
                pendingAttachment = null
                pendingAttachmentDescription = ""
            }
            is SendMessageUiState.Error -> {
                if (optimisticMessages.isNotEmpty()) {
                    val last = optimisticMessages.last()
                    if (last.sender == "You" && last.deliveryStatus == "Sending…") {
                        optimisticMessages[optimisticMessages.lastIndex] = last.copy(deliveryStatus = "Failed")
                    }
                }
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Inbox", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        ThreadsSelector(
            threadsState = threadsState,
            threads = threadPreviews,
            selectedId = selectedThreadId,
            onSelect = viewModel::selectThread,
            onRetry = { viewModel.loadThreads(force = true) }
        )

        ChatHeaderCard(header = selectedHeader)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (conversationState) {
                is ConversationUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ConversationUiState.Error -> {
                    val msg = (conversationState as ConversationUiState.Error).message
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { selectedThreadId?.let { viewModel.selectThread(it) } }) { Text("Retry") }
                    }
                }
                is ConversationUiState.Success -> {
                    val serverMessages = (conversationState as ConversationUiState.Success).messages
                    val combined = optimisticMessages + serverMessages.map { msg ->
                        val isMine = msg.isFromCurrentUser()
                        val senderName = if (isMine) "You" else selectedHeader?.name ?: "Extension Officer"
                        val mediaUri = msg.derivedImage()?.let(MediaUrlNormalizer::normalize)?.let(Uri::parse)
                        Message(
                            sender = senderName,
                            content = if (mediaUri == null) {
                                MessageContent.TextMessage(msg.derivedText())
                            } else {
                                MessageContent.MediaMessage(mediaUri, msg.derivedDescription())
                            },
                            timestamp = FriendlyDateTimeFormatter.toLocalClock(msg.derivedCreatedAt()),
                            deliveryStatus = if (isMine) msg.derivedStatus() else null,
                            replyCount = msg.derivedReplyCount(),
                            attachmentLabel = msg.derivedAttachment()?.let { "Attachment" }
                        )
                    }

                    LazyColumn(
                        state = messageListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true
                    ) {
                        items(combined.reversed()) { message ->
                            ChatBubble(message = message)
                        }
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
                        "Select a conversation to view messages.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (pendingAttachment != null) {
            AttachmentPreview(
                pendingAttachment = pendingAttachment,
                description = pendingAttachmentDescription,
                onDescriptionChange = { pendingAttachmentDescription = it },
                onRemove = {
                    pendingAttachment = null
                    pendingAttachmentDescription = ""
                }
            )
        }

        MessageInputBar(
            isSending = sendState is SendMessageUiState.Loading,
            inputText = inputText,
            onInputChange = { inputText = it },
            onAttach = { galleryLauncher.launch("image/*") },
            onSend = onSendBlock@{
                if (sendState is SendMessageUiState.Loading) return@onSendBlock
                Log.d("ChatScreen", "onSend clicked textLen=${inputText.length} attachment=${pendingAttachment != null} selectedThread=$selectedThreadId userPhone=${UserSession.phone}")
                if (pendingAttachment != null) {
                    if (pendingAttachmentDescription.isBlank()) return@onSendBlock
                    optimisticMessages.add(
                        Message(
                            sender = "You",
                            content = MessageContent.MediaMessage(pendingAttachment!!, pendingAttachmentDescription),
                            timestamp = "Now",
                            deliveryStatus = "Sending…",
                            attachmentLabel = "Attachment"
                        )
                    )
                    viewModel.sendMessage(pendingAttachmentDescription, pendingAttachment)
                    pendingAttachment = null
                    pendingAttachmentDescription = ""
                } else if (inputText.isNotBlank()) {
                    optimisticMessages.add(
                        Message(
                            sender = "You",
                            content = MessageContent.TextMessage(inputText),
                            timestamp = "Now",
                            deliveryStatus = "Sending…"
                        )
                    )
                    viewModel.sendMessage(inputText, null)
                    inputText = ""
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
            TextButton(onClick = viewModel::retryLastFailedMessage) {
                Text("Retry last message")
            }
        }
    }
}

@Composable
private fun ThreadsSelector(
    threadsState: ThreadsUiState,
    threads: List<ThreadPreviewUi>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit
) {
    when (threadsState) {
        is ThreadsUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is ThreadsUiState.Error -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unable to load conversations", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        is ThreadsUiState.Success -> {
            if (threads.isEmpty()) {
                Text(
                    text = "No conversations available yet.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = threads,
                    key = { index, thread -> "${thread.threadId}_$index" }
                ) { _, thread ->
                    ConversationPreviewRow(
                        thread = thread,
                        isSelected = selectedId == thread.threadId,
                        onClick = { onSelect(thread.threadId) }
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
        ThreadsUiState.Idle -> Unit
    }
}

@Composable
private fun ConversationPreviewRow(
    thread: ThreadPreviewUi,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarWithOnlineDot(
                avatarUrl = thread.avatarUrl,
                fallback = thread.name,
                isOnline = thread.isOnline
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(thread.role, thread.company).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = thread.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = thread.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (thread.unreadCount > 0) {
                    Badge { Text(thread.unreadCount.toString()) }
                }
            }
        }
    }
}

@Composable
private fun ChatHeaderCard(header: ChatHeaderUi?) {
    if (header == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarWithOnlineDot(
                avatarUrl = header.avatarUrl,
                fallback = header.name,
                isOnline = header.isOnline
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = header.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(header.role, header.company).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last seen ${header.lastSeenText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AvatarWithOnlineDot(
    avatarUrl: String?,
    fallback: String,
    isOnline: Boolean
) {
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(avatarUrl),
                    contentDescription = fallback,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = fallback.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .background(Color(0xFF2E7D32), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(pendingAttachment),
            contentDescription = "Attachment Preview",
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        TextField(
            value = description,
            onValueChange = onDescriptionChange,
            placeholder = { Text("Describe your attachment...") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.AttachFile, contentDescription = "Remove Attachment")
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
            modifier = Modifier.weight(1f),
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
private fun ChatBubble(message: Message) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
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
                            contentDescription = "Message image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
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

        val metadata = buildList {
            if (message.replyCount > 0) add("Replies ${message.replyCount}")
            if (!message.attachmentLabel.isNullOrBlank()) add(message.attachmentLabel)
            if (message.timestamp.isNotBlank()) add(message.timestamp)
            if (!message.deliveryStatus.isNullOrBlank()) add(deliveryStatusIcon(message.deliveryStatus))
        }.joinToString(" • ")

        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

private fun deliveryStatusIcon(status: String?): String = when (status) {
    "Sending…"  -> "⏳"
    "Delivered" -> "✓✓"
    "Read"      -> "✓✓"
    "Failed"    -> "✗ Failed"
    else        -> status ?: ""
}
