package com.farm_tech.farmhub.models.media

import com.google.gson.annotations.SerializedName

data class MediaCategoriesResponse(
    @SerializedName("data") val data: MediaCategoriesPayload? = null
)

data class MediaCategoriesPayload(
    @SerializedName("categories") val categories: List<MediaCategoryRecord>? = null
)

data class MediaCategoryRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("active") val active: Boolean? = null,
    @SerializedName("subcategories") val subcategories: List<MediaSubcategoryRecord>? = null
)

data class MediaSubcategoryRecord(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("active") val active: Boolean? = null
)

data class MediaTaxonomyPayloadResponse(
    @SerializedName("categories") val categories: List<MediaTaxonomyCategoryResponse>? = null,
    @SerializedName("subcategories") val subcategories: Map<String, List<String>>? = null
)

data class MediaTaxonomyCategoryResponse(
    @SerializedName(value = "category", alternate = ["name", "title"]) val category: String? = null,
    @SerializedName(value = "subcategories", alternate = ["subCategories", "children"]) val subcategories: List<String>? = null
)

data class MediaTaxonomy(
    val categories: List<MediaTaxonomyCategory> = emptyList(),
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class MediaTaxonomyCategory(
    val category: String,
    val subcategories: List<String> = emptyList()
)
