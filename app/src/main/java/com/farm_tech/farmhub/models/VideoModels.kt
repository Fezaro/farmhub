package com.farm_tech.farmhub.models

import androidx.lifecycle.ViewModel
import com.farm_tech.farmhub.R

// ---------------- Video Data ----------------
data class VideoItem(
    val id: Int,
    val title: String,
    val channel: String,
    val views: String,
    val time: String,
    val thumbnail: Int, // fallback drawable resource
    val thumbnailUrl: String? = null, // remote thumbnail if provided by /media
    val mediaUrl: String? = null, // remote video/media url
    val description: String = "",
    val tags: List<String> = emptyList()
)

data class Comment(
    val user: String,
    val text: String
)

// ---------------- Filters ----------------
val videoFilters = listOf("All", "Crop Farming", "Animal Farming", "Sector Updates", "Agribusiness", "Environment Care")

// ---------------- ViewModel ----------------
class VideoViewModel : ViewModel() {

    // Video feed
    private val _videos = listOf(
        VideoItem(
            id = 1,
            title = "How to Plant Maize for High Yields",
            channel = "AgriTech Kenya",
            views = "12k views",
            time = "2 days ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "A step-by-step maize planting guide covering land prep, spacing, and early care.",
            tags = listOf("crop farming", "maize", "seed", "planting")
        ),
        VideoItem(
            id = 2,
            title = "Conservation Agriculture for Dry Season Success",
            channel = "Farm Pro",
            views = "8.5k views",
            time = "1 week ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Learn moisture-saving field practices that improve resilience in low-rainfall seasons.",
            tags = listOf("crop farming", "conservation agriculture", "soil health", "mulching")
        ),
        VideoItem(
            id = 3,
            title = "Best Fertilizer Program for Beans",
            channel = "Green Farm",
            views = "5.3k views",
            time = "3 days ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Practical nutrient planning to improve bean growth, flowering, and pod filling.",
            tags = listOf("crop farming", "beans", "fertilizer", "legumes")
        ),
        VideoItem(
            id = 4,
            title = "Modern Poultry Housing for Broilers and Layers",
            channel = "Smart Farming",
            views = "20k views",
            time = "1 month ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Design a productive chicken house with proper airflow, hygiene, and feeding zones.",
            tags = listOf("animal farming", "chicken", "poultry", "housing")
        ),
        VideoItem(
            id = 5,
            title = "How to Prevent Crop Pests and Diseases",
            channel = "Healthy Crops",
            views = "15k views",
            time = "5 days ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Identify early signs of crop stress and build an integrated pest management routine.",
            tags = listOf("crop farming", "pests", "diseases", "IPM")
        ),
        VideoItem(
            id = 6,
            title = "Zero-Grazing Dairy Feeding Guide",
            channel = "Livestock Focus",
            views = "9.2k views",
            time = "4 days ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Daily dairy ration planning for healthier cows and improved milk production.",
            tags = listOf("animal farming", "dairy", "cow feed", "milk")
        ),
        VideoItem(
            id = 7,
            title = "Kenya Tea Market Outlook and Sector Updates",
            channel = "Agri News Desk",
            views = "3.1k views",
            time = "6 hours ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "A quick round-up of tea prices, export performance, and new agriculture sector policies.",
            tags = listOf("sector updates", "tea", "market", "policy")
        ),
        VideoItem(
            id = 8,
            title = "Starting a Profitable Avocado Export Business",
            channel = "AgriBiz Africa",
            views = "11k views",
            time = "2 weeks ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Understand aggregation, grading, and export readiness for avocado agribusiness.",
            tags = listOf("agribusiness", "avocados", "export", "value chain")
        ),
        VideoItem(
            id = 9,
            title = "Tree Planting and Soil Conservation on the Farm",
            channel = "Eco Farming Hub",
            views = "4.7k views",
            time = "1 week ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Environment care practices that protect soil, conserve water, and restore biodiversity.",
            tags = listOf("environment care", "soil conservation", "trees", "water")
        ),
        VideoItem(
            id = 10,
            title = "Bee Keeping Basics for New Farmers",
            channel = "Smart Apiary",
            views = "6.4k views",
            time = "9 days ago",
            thumbnail = R.drawable.ic_launcher_background,
            description = "Set up your first beehives, manage colony health, and harvest quality honey.",
            tags = listOf("animal farming", "bee keeping", "apiary", "honey")
        )
    )
    val videos: List<VideoItem> get() = _videos

    // Comments
    private val _comments = listOf(
        Comment("Alice", "Great tutorial! Learned a lot."),
        Comment("Bob", "Thanks for sharing, very helpful."),
        Comment("Charlie", "Can you make a video on organic fertilizers?"),
        Comment("Daisy", "Loved the explanation, clear and concise."),
        Comment("Eve", "More videos like this please!")
    )
    val comments: List<Comment> get() = _comments

    // Get video by id
    fun getVideoById(id: Int): VideoItem? = _videos.find { it.id == id }

    // Related videos (exclude current)
    fun getRelatedVideos(currentId: Int): List<VideoItem> =
        _videos.filter { it.id != currentId }

    // Filter videos
    fun getFilteredVideos(filter: String): List<VideoItem> {
        return if (filter == "All") _videos
        else _videos.filter {
            it.title.contains(filter, ignoreCase = true) ||
                    it.channel.contains(filter, ignoreCase = true) ||
                    it.description.contains(filter, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(filter, ignoreCase = true) }
        }
    }
}

