package com.farm_tech.farmhub.models.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemResponseTest {

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
}

