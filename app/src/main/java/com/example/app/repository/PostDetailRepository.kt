package com.example.app.repository

import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.auth.TokenValidator
import com.example.app.models.posts.PostDetailResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

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
    suspend fun getPost(id: String): Response<PostDetailResponse> = withContext(Dispatchers.IO) {
        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch post $id: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                okhttp3.ResponseBody.create(null, "No valid authentication token")
            )
        }

        Log.d(TAG, "GET /posts/$id tokenPresent=true, initiating request...")
        val resp = ApiClient.userService.getPost(id).execute()

        if (!resp.isSuccessful) {
            Log.e(TAG, "Post detail fetch error for $id code=${resp.code()}")
        } else {
            Log.d(TAG, "Post detail fetch success for $id")
        }

        resp
    }
}
