package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.farm_tech.farmhub.models.VideoItem
import com.farm_tech.farmhub.models.media.MediaTaxonomy
import com.farm_tech.farmhub.models.media.MediaTaxonomyCategory
import org.junit.Assert.*
import org.junit.Test

/** Tests the actual view-model paging/filtering with larger, synthetic catalogues.
 * Reflection seeds an already-loaded feed, avoiding production requests, Android
 * dispatchers and changes to the release code solely for test injection.
 * These are logic checks, not HTTP pagination or device performance tests.
 */
class MediaScaleTest {
    private fun model(count: Int, handle: SavedStateHandle = SavedStateHandle()): MediaViewModel {
        val model = MediaViewModel(handle)
        val videos = (1..count).map { index ->
            VideoItem(index, "Video $index", "Test company", "", "", 0,
                categoryId = if (index % 2 == 0) "crop" else "animal",
                subcategoryId = if (index % 2 == 0) "avocado" else "poultry")
        }
        MediaViewModel::class.java.getDeclaredField("lastVideos").apply { isAccessible = true }.set(model, videos)
        MediaViewModel::class.java.getDeclaredMethod("applyTaxonomy", MediaTaxonomy::class.java).apply {
            isAccessible = true
        }.invoke(model, MediaTaxonomy(listOf(
            MediaTaxonomyCategory("Crop Farming", listOf("Avocado"), "crop", mapOf("Avocado" to "avocado")),
            MediaTaxonomyCategory("Animal Farming", listOf("Poultry"), "animal", mapOf("Poultry" to "poultry"))
        )))
        MediaViewModel::class.java.getDeclaredMethod("emitFilteredSuccess").apply { isAccessible = true }.invoke(model)
        return model
    }

    private fun loaded(model: MediaViewModel) = model.uiState.value as MediaUiState.Success

    private fun reachEnd(model: MediaViewModel, expectedIds: List<Int>) {
        var advances = 0
        while (loaded(model).hasMore) {
            val before = loaded(model).videos.size
            model.loadNextPage()
            assertTrue("Paging must advance", loaded(model).videos.size > before)
            assertTrue("Paging must terminate", ++advances <= (expectedIds.size + 19) / 20)
        }
        assertEquals(expectedIds, loaded(model).videos.map { it.id })
        assertEquals(expectedIds.size, loaded(model).videos.map { it.id }.distinct().size)
        model.loadNextPage()
        assertEquals(expectedIds, loaded(model).videos.map { it.id })
    }

    @Test fun cataloguesCrossThirtyAndOneHundredWithoutTruncation() {
        for (count in listOf(31, 100, 101, 125, 250, 500, 1000)) {
            val model = model(count)
            assertEquals(20, loaded(model).videos.size)
            assertEquals(count, model.allVideos().size)
            reachEnd(model, (1..count).toList())
            assertEquals(count, model.getVideoById(count)?.id)
        }
    }

    @Test fun filtersUseTheWholeCatalogueIncludingItemsBeyondOneHundred() {
        val model = model(500)
        model.selectSubcategory("crop", "avocado")
        assertEquals(20, loaded(model).videos.size)
        reachEnd(model, (2..500 step 2).toList())
        model.selectCategory("animal")
        assertEquals(20, loaded(model).videos.size)
        reachEnd(model, (1..500 step 2).toList())
        model.selectAllVideos()
        assertEquals(20, loaded(model).videos.size)
        reachEnd(model, (1..500).toList())
    }

    @Test fun exactDisplayBatchBoundariesDoNotLoseTheLastItem() {
        for (count in listOf(0, 1, 19, 20, 21, 40, 200)) {
            val model = model(count)
            assertEquals(minOf(20, count), loaded(model).videos.size)
            reachEnd(model, (1..count).toList())
        }
    }

    @Test fun searchFindsLastVideoWithoutScrollingThroughEarlierBatches() {
        val model = model(1000)
        model.setSearchQuery("  VIDEO 1000  ")
        assertEquals(listOf(1000), loaded(model).videos.map { it.id })
        assertFalse(loaded(model).hasMore)
        model.setSearchQuery("not in this catalogue")
        assertTrue(loaded(model).videos.isEmpty())
        assertFalse(loaded(model).hasMore)
        model.setSearchQuery("")
        assertEquals(20, loaded(model).videos.size)
        assertTrue(loaded(model).hasMore)
    }

    @Test fun searchComposesWithCategoryFiltersAndPaginatesMatches() {
        val model = model(500)
        model.setSearchQuery("Video 500")
        model.selectSubcategory("crop", "avocado")
        assertEquals(listOf(500), loaded(model).videos.map { it.id })
        model.selectCategory("animal")
        assertTrue(loaded(model).videos.isEmpty())
        model.setSearchQuery("Video")
        reachEnd(model, (1..500 step 2).toList())
    }

    @Test fun restoredSearchIsAppliedToTheFullCatalogue() {
        val handle = SavedStateHandle(mapOf("media_search_query" to "Video 250"))
        val model = model(250, handle)
        assertEquals("Video 250", model.searchQuery.value)
        assertEquals(listOf(250), loaded(model).videos.map { it.id })
        model.setSearchQuery("Video 249")
        assertEquals("Video 249", handle.get<String>("media_search_query"))
        assertEquals(listOf(249), loaded(model).videos.map { it.id })
    }
}
