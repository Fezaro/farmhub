package com.farm_tech.farmhub.models.media

import com.farm_tech.farmhub.api.ApiClient

object MediaUrlNormalizer {

    fun normalize(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.startsWith("http://", ignoreCase = true) -> value.replaceFirst("http://", "https://", ignoreCase = true)
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            else -> ApiClient.baseUrl().trimEnd('/') + "/" + value.trimStart('/')
        }
    }
}

