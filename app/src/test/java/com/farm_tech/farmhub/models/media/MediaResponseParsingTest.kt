package com.farm_tech.farmhub.models.media

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MediaResponseParsingTest {

    private val gson = Gson()

    @Test
    fun mediaFeedResponse_parsesWrapperAndNestedCategoryObjects() {
        val json = """
            {
              "status": "success",
              "message": "ok",
              "media": [
                {
                  "id": "m-1",
                  "title": "Maize Training",
                  "description": "Practical maize planting guide",
                  "company": "FarmHub",
                  "author": "Agri Expert",
                  "categoryId": "crop_farming",
                  "subcategoryId": "maize",
                  "category": {
                    "id": "crop_farming",
                    "name": "Crop Farming"
                  },
                  "subcategory": {
                    "id": "maize",
                    "name": "Maize"
                  },
                  "mediaType": "VIDEO",
                  "thumbnailUrl": "https://cdn.example.com/thumb.jpg",
                  "streamUrl": "https://cdn.example.com/video.mp4",
                  "createdAt": "2026-07-01T08:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, MediaFeedResponse::class.java)

        assertEquals("success", response.status)
        assertEquals("ok", response.message)
        assertNotNull(response.media)
        assertEquals(1, response.media?.size)

        val item = response.media!!.first()
        assertEquals("m-1", item.id)
        assertEquals("Crop Farming", item.resolvedCategoryLabel())
        assertEquals("Maize", item.resolvedSubcategoryLabel())
        assertEquals("https://cdn.example.com/thumb.jpg", item.resolvedThumbnailUrl())
        assertEquals("https://cdn.example.com/video.mp4", item.resolvedMediaUrl())
    }

    @Test
    fun mediaItemResponse_acceptsFlatStringCategoryFields() {
        val json = """
            {
              "id": "m-2",
              "title": "Tea Market Update",
              "category": "Sector Updates",
              "subcategory": "Tea",
              "streamUrl": "https://cdn.example.com/tea.mp4"
            }
        """.trimIndent()

        val item = gson.fromJson(json, MediaItemResponse::class.java)

        assertEquals("Sector Updates", item.resolvedCategoryLabel())
        assertEquals("Tea", item.resolvedSubcategoryLabel())
        assertEquals("https://cdn.example.com/tea.mp4", item.resolvedMediaUrl())
    }
}