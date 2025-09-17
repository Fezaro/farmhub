package com.example.app.models

import androidx.lifecycle.ViewModel
import com.example.app.R

// ---------------- Video Data ----------------
data class VideoItem(
    val id: Int,
    val title: String,
    val channel: String,
    val views: String,
    val time: String,
    val thumbnail: Int, // fallback drawable resource
    val thumbnailUrl: String? = null, // remote thumbnail if provided by /media
    val mediaUrl: String? = null // remote video/media url
)

data class Comment(
    val user: String,
    val text: String
)

// ---------------- Filters ----------------
val videoFilters = listOf("All", "Farming", "Irrigation", "Fertilizers", "Poultry", "Diseases")

// ---------------- ViewModel ----------------
class VideoViewModel : ViewModel() {

    // Video feed
    private val _videos = listOf(
        VideoItem(id = 1, title = "How to Plant Maize", channel = "AgriTech Kenya", views = "12k views", time = "2 days ago", thumbnail = R.drawable.ic_launcher_background),
        VideoItem(id = 2, title = "Irrigation Tips for Dry Season", channel = "Farm Pro", views = "8.5k views", time = "1 week ago", thumbnail = R.drawable.ic_launcher_background),
        VideoItem(id = 3, title = "Best Fertilizers for Beans", channel = "Green Farm", views = "5.3k views", time = "3 days ago", thumbnail = R.drawable.ic_launcher_background),
        VideoItem(id = 4, title = "Modern Poultry Housing", channel = "Smart Farming", views = "20k views", time = "1 month ago", thumbnail = R.drawable.ic_launcher_background),
        VideoItem(id = 5, title = "How to Prevent Crop Diseases", channel = "Healthy Crops", views = "15k views", time = "5 days ago", thumbnail = R.drawable.ic_launcher_background)
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
                    it.channel.contains(filter, ignoreCase = true)
        }
    }
}
