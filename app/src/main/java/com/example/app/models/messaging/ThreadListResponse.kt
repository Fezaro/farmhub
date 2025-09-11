package com.example.app.models.messaging

data class ThreadListResponse(
    val status: String? = null,
    val threads: List<ThreadResponse>? = null
)

