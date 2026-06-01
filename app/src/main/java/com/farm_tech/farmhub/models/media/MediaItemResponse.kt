package com.farm_tech.farmhub.models.media

import com.google.gson.annotations.SerializedName

data class MediaItemResponse(
    val id: String? = null,
    val title: String? = null,
    @SerializedName("company") val channel: String? = null, // backend uses 'company'
    val type: String? = null, // if backend provides a type
    @SerializedName("thumbnail") val thumbnailUrl: String? = null, // backend field 'thumbnail'
    @SerializedName("video") val mediaUrl: String? = null, // backend field 'video'
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userId: String? = null
)

