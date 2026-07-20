package com.farm_tech.farmhub.models.media

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

@JsonAdapter(MediaCategoryResponse.Adapter::class)
data class MediaCategoryResponse(
    @SerializedName(value = "id", alternate = ["_id", "categoryId", "category_id"]) val id: String? = null,
    @SerializedName(value = "name", alternate = ["label", "title", "value", "category"]) val name: String? = null,
    @SerializedName(value = "slug", alternate = ["code"]) val slug: String? = null
) {
    fun resolvedLabel(): String? = firstNonBlank(name, slug, id)

    class Adapter : JsonDeserializer<MediaCategoryResponse> {
        override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): MediaCategoryResponse {
            if (json == null || json.isJsonNull) return MediaCategoryResponse()
            if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
                return MediaCategoryResponse(name = json.asString)
            }

            val obj = json.asJsonObjectOrNull() ?: return MediaCategoryResponse()
            return MediaCategoryResponse(
                id = obj.stringOrNull("id", "_id", "categoryId", "category_id"),
                name = obj.stringOrNull("name", "label", "title", "value", "category"),
                slug = obj.stringOrNull("slug", "code")
            )
        }
    }
}

@JsonAdapter(MediaSubcategoryResponse.Adapter::class)
data class MediaSubcategoryResponse(
    @SerializedName(value = "id", alternate = ["_id", "subcategoryId", "subcategory_id"]) val id: String? = null,
    @SerializedName(value = "name", alternate = ["label", "title", "value", "subcategory"]) val name: String? = null,
    @SerializedName(value = "slug", alternate = ["code"]) val slug: String? = null
) {
    fun resolvedLabel(): String? = firstNonBlank(name, slug, id)

    class Adapter : JsonDeserializer<MediaSubcategoryResponse> {
        override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): MediaSubcategoryResponse {
            if (json == null || json.isJsonNull) return MediaSubcategoryResponse()
            if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
                return MediaSubcategoryResponse(name = json.asString)
            }

            val obj = json.asJsonObjectOrNull() ?: return MediaSubcategoryResponse()
            return MediaSubcategoryResponse(
                id = obj.stringOrNull("id", "_id", "subcategoryId", "subcategory_id"),
                name = obj.stringOrNull("name", "label", "title", "value", "subcategory"),
                slug = obj.stringOrNull("slug", "code")
            )
        }
    }
}

data class MediaItemResponse(
    @SerializedName(value = "id", alternate = ["_id"]) val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName(value = "company", alternate = ["uploader", "channel"]) val company: String? = null,
    @SerializedName(value = "categoryId", alternate = ["category_id"]) val categoryId: String? = null,
    val category: MediaCategoryResponse? = null,
    @SerializedName(value = "subcategoryId", alternate = ["subcategory_id"]) val subcategoryId: String? = null,
    val subcategory: MediaSubcategoryResponse? = null,
    @SerializedName(value = "author", alternate = ["videoAuthor"]) val author: String? = null,
    @SerializedName(value = "thumbnailUrl", alternate = ["thumbnail", "imageUrl"]) val thumbnailUrl: String? = null,
    @SerializedName(value = "duration", alternate = ["videoDuration"]) val duration: String? = null,
    @SerializedName(value = "uploadedAt", alternate = ["uploaded_at"]) val uploadedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("mediaType") val mediaType: String? = null,
    @SerializedName(value = "mimeType", alternate = ["mime_type", "contentType", "content_type"]) val mimeType: String? = null,
    @SerializedName("video") val video: String? = null,
    @SerializedName("streamUrl") val streamUrl: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("videoUrl") val videoUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileUrl") val fileUrl: String? = null,
    @SerializedName("playbackUrl") val playbackUrl: String? = null
) {
    fun resolvedThumbnailUrl(): String? = thumbnailUrl

    fun resolvedMediaUrl(): String? = firstNonBlank(playbackUrl, streamUrl, videoUrl, mediaUrl, fileUrl, video, url)

    fun resolvedMediaType(): String? = firstNonBlank(mimeType, mediaType, type)

    fun resolvedUploadedAt(): String? = uploadedAt ?: createdAt

    fun resolvedCategoryLabel(): String? = firstNonBlank(category?.resolvedLabel(), categoryId)

    fun resolvedSubcategoryLabel(): String? = firstNonBlank(subcategory?.resolvedLabel(), subcategoryId)
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? = try {
    if (isJsonObject) asJsonObject else null
} catch (_: Exception) {
    null
}

private fun JsonObject.stringOrNull(vararg keys: String): String? {
    keys.forEach { key ->
        val element = get(key) ?: return@forEach
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return@forEach
        val value = element.asJsonPrimitive.asString.trim()
        if (value.isNotBlank()) return value
    }
    return null
}

private fun firstNonBlank(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
