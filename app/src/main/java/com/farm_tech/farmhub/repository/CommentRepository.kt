package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.models.Comment

/**
 * Repository interface for video comments.
 *
 * Currently backed by static data. When the backend exposes a comments endpoint,
 * replace [StaticCommentRepository] with a network implementation without touching
 * the ViewModel or UI layer.
 */
interface CommentRepository {
    /** Returns the comments for the given [videoId]. Non-suspend; implementations must return quickly. */
    fun getComments(videoId: Int): List<Comment>
}

/**
 * Static implementation used until a backend comments API is available.
 *
 * To integrate a real API: create a class implementing [CommentRepository] that
 * calls the endpoint, then swap it in [com.farm_tech.farmhub.models.VideoViewModel].
 */
object StaticCommentRepository : CommentRepository {
    private val staticComments = listOf(
        Comment("Alice", "Great tutorial! Learned a lot."),
        Comment("Bob", "Thanks for sharing, very helpful."),
        Comment("Charlie", "Can you make a video on organic fertilizers?"),
        Comment("Daisy", "Loved the explanation, clear and concise."),
        Comment("Eve", "More videos like this please!")
    )

    override fun getComments(videoId: Int): List<Comment> = staticComments
}
