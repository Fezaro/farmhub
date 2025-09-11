package com.example.app.models.messaging

data class MessagesResponse(
    val status: String? = null,
    val messages: List<MessageItemResponse>? = null
)

