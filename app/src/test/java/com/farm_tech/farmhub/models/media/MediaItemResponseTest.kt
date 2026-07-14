package com.farm_tech.farmhub.models.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.google.gson.Gson

class MediaItemResponseTest {
    private val gson = Gson()

    @Test
    fun resolvedMediaUrl_usesStreamUrlWhenMediaUrlMissing() {
        val item = MediaItemResponse(
            mediaUrl = null,
            streamUrl = "http://api.farmers-hub.co.ke/files/video.mp4",
            video = null
        )

        assertEquals("http://api.farmers-hub.co.ke/files/video.mp4", item.resolvedMediaUrl())
    }

    @Test
    fun resolvedMediaUrl_prefersMediaUrlThenStreamUrlThenVideo() {
        val item = MediaItemResponse(
            mediaUrl = "https://cdn.example.com/media.mp4",
            streamUrl = "https://cdn.example.com/stream.mp4",
            video = "https://cdn.example.com/video.mp4"
        )

        assertEquals("https://cdn.example.com/media.mp4", item.resolvedMediaUrl())
    }

    @Test
    fun resolvedMediaUrl_returnsNullWhenAllCandidatesMissing() {
        val item = MediaItemResponse(mediaUrl = null, streamUrl = null, video = null)

        assertNull(item.resolvedMediaUrl())
    }

    @Test
    fun resolvedUploadedAt_prefersUploadedAtThenCreatedAt() {
        val withUploadedAt = MediaItemResponse(
            uploadedAt = "2026-06-01T08:00:00Z",
            createdAt = "2026-05-01T08:00:00Z"
        )
        val withCreatedAtOnly = MediaItemResponse(createdAt = "2026-05-01T08:00:00Z")

        assertEquals("2026-06-01T08:00:00Z", withUploadedAt.resolvedUploadedAt())
        assertEquals("2026-05-01T08:00:00Z", withCreatedAtOnly.resolvedUploadedAt())
    }

    @Test
    fun resolvedThumbnailUrl_acceptsImageUrlAlias() {
        val json = """
            {
              "id": "m-3",
              "imageUrl": "https://cdn.example.com/thumb.webp"
            }
        """.trimIndent()
        val item = gson.fromJson(json, MediaItemResponse::class.java)
        assertEquals("https://cdn.example.com/thumb.webp", item.resolvedThumbnailUrl())
    }
}
