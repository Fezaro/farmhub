package com.farm_tech.farmhub.models.media

import com.google.gson.annotations.SerializedName

data class MediaItemResponse(
    val id: String? = null,
    val title: String? = null,
    @SerializedName("company") val channel: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("mediaType") val mediaType: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("video") val video: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    val description: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userId: String? = null
) {
    fun resolvedThumbnailUrl(): String? = thumbnailUrl ?: thumbnail ?: imageUrl

    fun resolvedMediaUrl(): String? = mediaUrl ?: video

    fun resolvedMediaType(): String? = mediaType ?: type
}

