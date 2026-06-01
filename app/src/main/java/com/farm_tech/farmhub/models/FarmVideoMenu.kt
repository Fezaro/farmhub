package com.farm_tech.farmhub.models

data class FarmVideoSubcategory(
    val id: String,
    val label: String,
    val keywords: List<String>
)

data class FarmVideoCategory(
    val id: String,
    val label: String,
    val keywords: List<String>,
    val subcategories: List<FarmVideoSubcategory> = emptyList()
)

data class FarmVideoSelection(
    val categoryId: String? = null,
    val subcategoryId: String? = null
)

object FarmVideoMenuCatalog {
    const val CATEGORY_CROP = "crop_farming"
    const val CATEGORY_ANIMAL = "animal_farming"
    const val CATEGORY_SECTOR_UPDATES = "sector_updates"
    const val CATEGORY_AGRIBUSINESS = "agribusiness"
    const val CATEGORY_ENVIRONMENT_CARE = "environment_care"

    private fun normalizedKeywords(label: String, extras: List<String> = emptyList()): List<String> {
        val labelTerms = label
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in setOf("and", "other", "care", "farming", "topics") }

        return (labelTerms + extras.map { it.lowercase() }).distinct()
    }

    private fun subcategory(id: String, label: String, vararg extras: String): FarmVideoSubcategory =
        FarmVideoSubcategory(id = id, label = label, keywords = normalizedKeywords(label, extras.toList()))

    private fun category(
        id: String,
        label: String,
        extras: List<String>,
        subcategories: List<FarmVideoSubcategory> = emptyList()
    ): FarmVideoCategory = FarmVideoCategory(
        id = id,
        label = label,
        keywords = normalizedKeywords(label, extras),
        subcategories = subcategories
    )

    val cropFarming = category(
        id = CATEGORY_CROP,
        label = "Crop Farming",
        extras = listOf("soil", "seed", "harvest", "crop production"),
        subcategories = listOf(
            subcategory("pests_diseases", "Pests & Diseases", "pest", "disease", "blight", "aphid", "fall armyworm", "fungus"),
            subcategory("avocados", "Avocados", "avocado", "hass"),
            subcategory("bananas", "Bananas", "banana", "matoke"),
            subcategory("beans", "Beans", "bean", "legume"),
            subcategory("brassicas", "Cabbages, Kales & other Brassicas", "cabbage", "kale", "brassica", "sukuma wiki"),
            subcategory("carrot", "Carrot", "carrot"),
            subcategory("coffee", "Coffee", "coffee", "arabica", "robusta"),
            subcategory("conservation_agriculture", "Conservation Agriculture", "mulch", "minimum tillage", "soil cover"),
            subcategory("green_gram", "Green Gram", "green gram", "ndengu", "mung bean"),
            subcategory("groundnut", "Groundnut", "groundnut", "peanut"),
            subcategory("indigenous_vegetables", "Indigenous Vegetables", "traditional vegetables", "amaranth", "managu"),
            subcategory("maize", "Maize", "maize", "corn"),
            subcategory("mango", "Mango", "mango"),
            subcategory("mushroom", "Mushroom", "mushroom", "oyster mushroom"),
            subcategory("onion", "Onion", "onion"),
            subcategory("other_topics", "Other Topics", "crop management", "general farming", "field practices"),
            subcategory("papaya", "Papaya", "papaya", "pawpaw"),
            subcategory("passion_fruit", "Passion Fruit", "passion fruit", "passiflora"),
            subcategory("peas", "Peas", "peas", "garden pea"),
            subcategory("peppers", "Peppers", "pepper", "capsicum", "chilli"),
            subcategory("pineapple", "Pineapple", "pineapple"),
            subcategory("potato", "Potato", "potato", "irish potato"),
            subcategory("pumpkin", "Pumpkin", "pumpkin"),
            subcategory("rice", "Rice", "rice", "paddy"),
            subcategory("spinach", "Spinach", "spinach"),
            subcategory("sweet_potato", "Sweet Potato", "sweet potato"),
            subcategory("tea", "Tea", "tea"),
            subcategory("tomato", "Tomato", "tomato"),
            subcategory("watermelon", "Watermelon", "watermelon"),
            subcategory("wheat", "Wheat", "wheat"),
            subcategory("yam", "Yam", "yam"),
            subcategory("zucchini", "Zucchini / Courgette", "zucchini", "courgette")
        )
    )

    val animalFarming = category(
        id = CATEGORY_ANIMAL,
        label = "Animal Farming",
        extras = listOf("livestock", "animal health", "feeds"),
        subcategories = listOf(
            subcategory("dairy", "Dairy", "cow", "milk", "zero grazing"),
            subcategory("chicken", "Chicken", "poultry", "broiler", "layer"),
            subcategory("goats", "Goats", "goat"),
            subcategory("sheep", "Sheep", "sheep", "wool"),
            subcategory("fish", "Fish", "fish", "aquaculture", "pond"),
            subcategory("pigs", "Pigs", "pig", "swine"),
            subcategory("bee_keeping", "Bee Keeping", "bee keeping", "apiary", "honey"),
            subcategory("rabbits", "Rabbits", "rabbit"),
            subcategory("pets", "Pets – Dogs & Cats", "dog", "dogs", "cat", "cats", "pets"),
            subcategory("other_animals", "Other Animals", "livestock", "animal production", "farm animals")
        )
    )

    val sectorUpdates = category(
        id = CATEGORY_SECTOR_UPDATES,
        label = "Sector Updates",
        extras = listOf("news", "market", "policy", "outlook", "prices", "trends")
    )

    val agribusiness = category(
        id = CATEGORY_AGRIBUSINESS,
        label = "Agribusiness",
        extras = listOf("business", "value chain", "marketing", "export", "finance", "profit")
    )

    val environmentCare = category(
        id = CATEGORY_ENVIRONMENT_CARE,
        label = "Environment Care",
        extras = listOf("soil conservation", "climate", "sustainability", "tree planting", "water conservation")
    )

    val categories = listOf(cropFarming, animalFarming, sectorUpdates, agribusiness, environmentCare)

    fun categoryById(id: String?): FarmVideoCategory? = categories.firstOrNull { it.id == id }

    fun subcategoryById(categoryId: String?, subcategoryId: String?): FarmVideoSubcategory? =
        categoryById(categoryId)?.subcategories?.firstOrNull { it.id == subcategoryId }

    fun selectionTitle(selection: FarmVideoSelection): String =
        subcategoryById(selection.categoryId, selection.subcategoryId)?.label
            ?: categoryById(selection.categoryId)?.label
            ?: "All FarmVideos"

    fun selectionDescription(selection: FarmVideoSelection): String = when {
        selection.subcategoryId != null -> {
            val category = categoryById(selection.categoryId)?.label ?: "FarmVideos"
            "Showing videos related to ${selectionTitle(selection)} in $category."
        }
        selection.categoryId != null -> "Showing curated videos from ${selectionTitle(selection)}."
        else -> "Discover practical farming videos across crops, livestock, markets, and sustainability."
    }

    fun filterVideos(videos: List<VideoItem>, selection: FarmVideoSelection): List<VideoItem> {
        if (selection.categoryId == null) return videos

        val category = categoryById(selection.categoryId) ?: return videos
        val subcategory = subcategoryById(selection.categoryId, selection.subcategoryId)
        val keywords = when {
            subcategory != null -> subcategory.keywords
            category.subcategories.isNotEmpty() -> category.keywords + category.subcategories.flatMap { it.keywords }
            else -> category.keywords
        }.distinct()

        return videos.filter { video ->
            val searchableText = buildString {
                append(video.title)
                append(' ')
                append(video.channel)
                append(' ')
                append(video.description)
                append(' ')
                append(video.tags.joinToString(" "))
            }.lowercase()

            keywords.any { keyword -> searchableText.contains(keyword.lowercase()) }
        }
    }
}


