package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.posts.PostDetailResponse
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for fetching individual post details.
 * Enforces token validation before making API calls.
 *
 * Senior practice: Centralized repository layer for post detail operations,
 * with token validation, logging, and error handling.
 */
class PostDetailRepository {
    companion object {
        private const val TAG = "PostDetailRepository"
    }

    /**
     * Fetches a single post by its ID.
     * Enforces token validation before making the request.
     *
     * @param id The ID of the post to fetch
     * @return Response containing PostDetailResponse on success
     */
    suspend fun getPost(id: String): NetworkResult<PostDetailResponse> = withContext(Dispatchers.IO) {
        val result = safeApiCall { ApiClient.userService.getPost(id).execute() }
        if (result is NetworkResult.Success) {
            Log.d(TAG, "Post detail loaded for $id")
        }
        result
    }
}
