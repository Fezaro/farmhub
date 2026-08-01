package com.farm_tech.farmhub.models.messaging

import com.google.gson.annotations.SerializedName

data class ThreadListResponse(
    val status: String? = null,
    val threads: List<ThreadResponse>? = null,
    val conversations: List<ConversationResponse>? = null
)

/** The current messaging API is conversation-first; [threads] remains a legacy fallback. */
data class ConversationResponse(
    val id: String? = null,
    val subject: String? = null,
    val state: String? = null,
    val farmerId: String? = null,
    val specialistId: String? = null,
    val updatedAt: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int? = null,
    val participants: List<ConversationParticipant>? = null,
    val latestMessage: MessageItemResponse? = null
) {
    fun toThreadResponse(currentUserId: String?): ThreadResponse {
        val fallbackRecipient = when {
            specialistId != null && specialistId != currentUserId -> specialistId
            farmerId != null && farmerId != currentUserId -> farmerId
            else -> null
        }
        val otherParticipant = if (currentUserId.isNullOrBlank()) {
            participants.orEmpty().firstOrNull { it.id == fallbackRecipient }
                ?: participants.orEmpty().firstOrNull()
        } else {
            participants.orEmpty().firstOrNull { it.id != currentUserId }
        }
        val latestAttachment = latestMessage?.attachments?.firstOrNull()?.url
            ?: latestMessage?.attachmentUrl

        return ThreadResponse(
            id = id,
            threadId = id,
            recipientId = otherParticipant?.id ?: fallbackRecipient,
            recipientPhone = otherParticipant?.phone,
            participants = participants?.mapNotNull { it.phone ?: it.id },
            lastMessage = latestMessage?.derivedText() ?: subject,
            updatedAt = lastMessageAt ?: updatedAt,
            name = otherParticipant?.names,
            role = otherParticipant?.role,
            unreadCount = unreadCount,
            lastAttachmentUrl = latestAttachment
        )
    }
}

data class ConversationParticipant(
    val id: String? = null,
    @SerializedName(value = "names", alternate = ["name"]) val names: String? = null,
    val phone: String? = null,
    val role: String? = null
)


