package com.farm_tech.farmhub.models.messaging

import com.farm_tech.farmhub.session.UserSession

// Expanded to handle possible alternative field names from backend.
data class MessageItemResponse(
    val id: String? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val sender_id: String? = null,
    val from: String? = null,
    val to: String? = null,
    val text: String? = null,
    val message: String? = null,
    val content: String? = null,
    val attachmentUrl: String? = null,
    val attachment_url: String? = null,
    val createdAt: String? = null,
    val created_at: String? = null
) {
    fun derivedId(): String? = id ?: messageId
    fun derivedText(): String = text ?: message ?: content ?: ""
    fun derivedAttachment(): String? = attachmentUrl ?: attachment_url
    fun derivedCreatedAt(): String? = createdAt ?: created_at
    fun isFromCurrentUser(): Boolean {
        val currentUserId = UserSession.userId
        val currentPhone = UserSession.phone
        val senderToken = senderId ?: sender_id ?: from
        return senderToken != null && (senderToken == currentUserId || senderToken == currentPhone)
    }
    fun otherPartyPhone(): String? {
        val currentUserId = UserSession.userId
        val currentPhone = UserSession.phone
        val senderToken = senderId ?: sender_id ?: from
        val recipientToken = to
        // If sender is me, return recipient, else sender
        return if (senderToken != null && (senderToken == currentUserId || senderToken == currentPhone)) {
            recipientToken
        } else senderToken
    }
}

