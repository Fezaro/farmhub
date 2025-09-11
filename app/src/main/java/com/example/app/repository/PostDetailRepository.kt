package com.example.app.repository

import com.example.app.api.ApiClient
import com.example.app.models.posts.PostDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class PostDetailRepository {
    suspend fun getPost(id: String): Response<PostDetailResponse> = withContext(Dispatchers.IO) {
        ApiClient.userService.getPost(id).execute()
    }
}

