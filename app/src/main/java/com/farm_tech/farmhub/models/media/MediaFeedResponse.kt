package com.farm_tech.farmhub.models.media

import com.google.gson.annotations.SerializedName

data class MediaFeedResponse(
    val status: String? = null,
    val media: List<MediaItemResponse>? = null,
    // Backend may expose taxonomy either directly or under a taxonomy object.
    @SerializedName("categories") val categories: List<MediaTaxonomyCategoryResponse>? = null,
    @SerializedName("subcategories") val subcategories: Map<String, List<String>>? = null,
    @SerializedName("taxonomy") val taxonomy: MediaTaxonomyPayloadResponse? = null
)

