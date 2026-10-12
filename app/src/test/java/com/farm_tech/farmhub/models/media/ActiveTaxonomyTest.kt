package com.farm_tech.farmhub.models.media

import org.junit.Assert.*
import org.junit.Test

class ActiveTaxonomyTest {
    @Test fun onlyActiveBackendRecordsBecomeSelectable() {
        val response = MediaCategoriesResponse(MediaCategoriesPayload(listOf(
            MediaCategoryRecord("animal", "Animal Farming", true, listOf(
                MediaSubcategoryRecord("old-chicken", "Chicken", false),
                MediaSubcategoryRecord("poultry", "Poultry", true)
            )),
            MediaCategoryRecord("old-crop", "Agriculture", false)
        )))
        val taxonomy = response.activeTaxonomy()
        assertEquals(1, taxonomy.categories.size)
        assertEquals("animal", taxonomy.categories.single().id)
        assertEquals(listOf("Poultry"), taxonomy.categories.single().subcategories)
        assertEquals("poultry", taxonomy.categories.single().subcategoryIds["Poultry"])
    }

    @Test fun successfulEmptyResponseDoesNotInventCategories() {
        assertTrue(MediaCategoriesResponse(MediaCategoriesPayload(emptyList())).activeTaxonomy().categories.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedResponseIsNotTreatedAsEmptySuccess() {
        MediaCategoriesResponse().activeTaxonomy()
    }

    @Test fun renameKeepsTheBackendIdentifier() {
        val category = MediaCategoryRecord("same-id", "Renamed", true, emptyList())
        assertEquals("same-id", MediaCategoriesResponse(MediaCategoriesPayload(listOf(category))).activeTaxonomy().categories.single().id)
    }
}
