package com.farm_tech.farmhub.models.messaging

import com.google.gson.annotations.SerializedName
import com.farm_tech.farmhub.util.PhoneNumberFormatter

// Extended to capture multiple possible backend keys.
// Whichever matches will be non-null; selection logic will pick first non-null id-like field.
data class ThreadResponse(
    val id: String? = null,
    val threadId: String? = null,
    val recipientId: String? = null,
    val participants: List<String>? = null,
    val lastMessage: String? = null,
    val last_message: String? = null,
    val updatedAt: String? = null,
    val updated_at: String? = null,
    @SerializedName("specialistName") val specialistName: String? = null,
    @SerializedName("specialistRole") val specialistRole: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("unreadCount") val unreadCount: Int? = null,
    @SerializedName("lastAttachmentUrl") val lastAttachmentUrl: String? = null
) {
    fun derivedId(): String? = id ?: threadId ?: recipientId
    fun conversationLookupId(): String? = recipientId ?: threadId ?: id
    fun derivedLastMessage(): String? = lastMessage ?: last_message
    fun derivedUpdatedAt(): String? = updatedAt ?: updated_at
    fun derivedDisplayName(currentUserPhone: String?): String {
        return specialistName?.takeIf { it.isNotBlank() }
            ?: specialistRole?.takeIf { it.isNotBlank() }
            ?: otherParty(currentUserPhone)
            ?: "Extension Officer"
    }

    // Helper to extract the other participant phone/identifier (assuming participants are phone numbers or IDs)
    fun otherParty(currentUserPhone: String?): String? {
        val others = participants?.filterNot {
            it.isNullOrBlank() || PhoneNumberFormatter.samePhone(it, currentUserPhone)
        }
        if (!others.isNullOrEmpty()) return others.first()
        // Fallbacks: recipientId might itself be the other phone
        return recipientId ?: derivedId()
    }
}
