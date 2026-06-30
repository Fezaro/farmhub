package com.farm_tech.farmhub.models.posts

data class GetAllPostsResponse(
    val status: String,
    val posts: List<PostWrapper>
)
