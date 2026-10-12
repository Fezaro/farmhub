package com.farm_tech.farmhub.models

/** Filter the complete catalogue before taking a display page. */
internal fun List<VideoItem>.filterBySearch(query: String): List<VideoItem> {
    val needle = query.trim().lowercase()
    if (needle.isBlank()) return this
    return filter { video ->
        buildString {
            append(video.title)
            append(' ')
            append(video.channel)
            append(' ')
            append(video.description)
            append(' ')
            append(video.category)
            append(' ')
            append(video.subcategory)
            append(' ')
            append(video.tags.joinToString(" "))
        }.lowercase().contains(needle)
    }
}
