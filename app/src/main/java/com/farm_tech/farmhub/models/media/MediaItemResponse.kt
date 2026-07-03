package com.farm_tech.farmhub.models.media

import com.google.gson.annotations.SerializedName

data class MediaItemResponse(
    @SerializedName(value = "id", alternate = ["_id"]) val id: String? = null,
    val title: String? = null,
    @SerializedName(value = "company", alternate = ["uploader", "channel"]) val company: String? = null,
    @SerializedName(value = "category", alternate = ["mediaCategory"]) val category: String? = null,
    @SerializedName(value = "subcategory", alternate = ["subCategory", "mediaSubcategory"]) val subcategory: String? = null,
    @SerializedName(value = "author", alternate = ["videoAuthor"]) val author: String? = null,
    val description: String? = null,
    @SerializedName(value = "thumbnail", alternate = ["thumbnailUrl", "imageUrl"]) val thumbnail: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName(value = "duration", alternate = ["videoDuration"]) val duration: String? = null,
    @SerializedName(value = "uploadedAt", alternate = ["uploaded_at", "createdAt", "created_at"]) val uploadedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("mediaType") val mediaType: String? = null,
    @SerializedName("video") val video: String? = null,
    @SerializedName("streamUrl") val streamUrl: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null
) {
    fun resolvedThumbnailUrl(): String? = thumbnailUrl ?: thumbnail ?: imageUrl

    fun resolvedMediaUrl(): String? = mediaUrl ?: streamUrl ?: video

    fun resolvedMediaType(): String? = mediaType ?: type

    fun resolvedUploadedAt(): String? = uploadedAt ?: createdAt
}
