package com.farm_tech.farmhub.models.messaging

import com.farm_tech.farmhub.util.PhoneNumberFormatter
import com.google.gson.annotations.SerializedName

data class ThreadResponse(
    @SerializedName(value = "id", alternate = ["_id"]) val id: String? = null,
    @SerializedName(value = "threadId", alternate = ["thread_id", "conversationId", "conversation_id"]) val threadId: String? = null,
    @SerializedName(value = "recipientId", alternate = ["recipient_id"]) val recipientId: String? = null,
    @SerializedName(value = "recipientPhone", alternate = ["recipient_phone", "phone"]) val recipientPhone: String? = null,
    val participants: List<String>? = null,
    @SerializedName(value = "lastMessage", alternate = ["last_message"]) val lastMessage: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"]) val updatedAt: String? = null,
    @SerializedName(value = "name", alternate = ["participantName", "specialistName", "displayName"]) val name: String? = null,
    @SerializedName(value = "role", alternate = ["participantRole", "specialistRole"]) val role: String? = null,
    @SerializedName(value = "company", alternate = ["organization", "uploaderCompany", "institution"]) val company: String? = null,
    @SerializedName(value = "avatarUrl", alternate = ["avatar", "profileImage"]) val avatarUrl: String? = null,
    @SerializedName(value = "unreadCount", alternate = ["unread_count"]) val unreadCount: Int? = null,
    @SerializedName(value = "lastSeen", alternate = ["last_seen"]) val lastSeen: String? = null,
    @SerializedName(value = "isOnline", alternate = ["online"]) val isOnline: Boolean? = null,
    @SerializedName(value = "lastAttachmentUrl", alternate = ["last_attachment_url"]) val lastAttachmentUrl: String? = null
) {
    fun derivedId(): String? = id ?: threadId ?: recipientId
    fun conversationLookupId(): String? = threadId ?: id ?: recipientId
    fun derivedLastMessage(): String = lastMessage.orEmpty()
    fun derivedUpdatedAt(): String? = updatedAt
    fun derivedRole(): String = role?.trim().orEmpty().ifBlank { "Extension Officer" }
    fun derivedCompany(): String? = company?.trim()?.takeIf { it.isNotBlank() }
    fun derivedLastSeen(): String? = lastSeen ?: updatedAt
    fun derivedRecipientPhone(currentUserPhone: String?): String? =
        recipientPhone?.trim()?.takeIf { it.isNotBlank() }
            ?: participants?.firstOrNull { !PhoneNumberFormatter.samePhone(it, currentUserPhone) }

    fun derivedDisplayName(currentUserPhone: String?): String {
        return name?.takeIf { it.isNotBlank() }
            ?: otherParty(currentUserPhone)
            ?: "Extension Officer"
    }

    fun otherParty(currentUserPhone: String?): String? {
        val others = participants?.filterNot {
            it.isNullOrBlank() || PhoneNumberFormatter.samePhone(it, currentUserPhone)
        }
        if (!others.isNullOrEmpty()) return others.first()
        return recipientId ?: derivedId()
    }
}
