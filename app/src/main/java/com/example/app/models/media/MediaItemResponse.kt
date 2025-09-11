package com.example.app.models.media

data class MediaItemResponse(
    val id: String? = null,
    val title: String? = null,
    val channel: String? = null,
    val type: String? = null, // e.g., "video", "image"
    val thumbnailUrl: String? = null,
    val mediaUrl: String? = null,
    val createdAt: String? = null
)

