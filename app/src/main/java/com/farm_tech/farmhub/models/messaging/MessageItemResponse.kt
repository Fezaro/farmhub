package com.farm_tech.farmhub.models.messaging

import com.farm_tech.farmhub.session.UserSession
import com.farm_tech.farmhub.util.PhoneNumberFormatter
import com.google.gson.annotations.SerializedName

data class MessageItemResponse(
    @SerializedName(value = "id", alternate = ["messageId", "_id"]) val id: String? = null,
    @SerializedName(value = "senderId", alternate = ["sender_id", "from"]) val senderId: String? = null,
    @SerializedName(value = "to", alternate = ["recipientId", "recipient_id"]) val to: String? = null,
    @SerializedName(value = "text", alternate = ["message", "content"]) val text: String? = null,
    @SerializedName(value = "description", alternate = ["caption"]) val description: String? = null,
    @SerializedName(value = "image", alternate = ["imageUrl", "mediaUrl"]) val image: String? = null,
    @SerializedName(value = "attachmentUrl", alternate = ["attachment_url"]) val attachmentUrl: String? = null,
    @SerializedName(value = "attachments", alternate = ["files"]) val attachments: List<MessageAttachmentResponse>? = null,
    @SerializedName(value = "replyCount", alternate = ["replies"]) val replyCount: Int? = null,
    @SerializedName(value = "status", alternate = ["deliveryStatus"]) val status: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at", "time"]) val createdAt: String? = null
) {
    fun derivedId(): String? = id
    fun derivedText(): String = text.orEmpty()
    fun derivedAttachment(): String? = attachmentUrl ?: attachments?.firstOrNull()?.url
    fun derivedImage(): String? = image ?: derivedAttachment()
    fun derivedDescription(): String = description.orEmpty().ifBlank { derivedText() }
    fun derivedReplyCount(): Int = replyCount ?: 0
    fun derivedStatus(): String = status?.trim().orEmpty().ifBlank { "Delivered" }
    fun derivedCreatedAt(): String? = createdAt

    fun isFromCurrentUser(): Boolean {
        val currentUserId = UserSession.userId
        val currentPhone = UserSession.phone
        val senderToken = senderId
        return senderToken != null && (
            senderToken == currentUserId || PhoneNumberFormatter.samePhone(senderToken, currentPhone)
        )
    }

    fun otherPartyPhone(): String? {
        val currentUserId = UserSession.userId
        val currentPhone = UserSession.phone
        return if (senderId != null && (
                senderId == currentUserId || PhoneNumberFormatter.samePhone(senderId, currentPhone)
            )
        ) {
            to
        } else {
            senderId
        }
    }
}

data class MessageAttachmentResponse(
    val id: String? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val filename: String? = null
)
