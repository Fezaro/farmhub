package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.models.posts.CreatePostResponse
import com.farm_tech.farmhub.models.posts.GetAllPostsResponse
import com.farm_tech.farmhub.network.ApiException
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Repository for posts endpoints.
 *
 * Provides both a legacy callback API (preserved for existing call-sites) and a
 * modern suspend API that integrates cleanly with coroutine scopes and structured
 * cancellation. New code should prefer [createPost] (suspend).
 */
class PostRepository {
    companion object {
        private const val TAG = "PostRepository"
    }

    // ─── Suspend API (preferred) ──────────────────────────────────────────────

    /**
     * Creates a new post with image + description using a suspend call.
     *
     * Runs on [Dispatchers.IO]. Validates token before calling the network.
     * Returns a typed [NetworkResult] — callers should handle Success, Error, and
     * Empty without needing to catch exceptions themselves.
     *
     * @param image       Multipart image part (use [CountingRequestBody] for progress).
     * @param description Plain-text request body for the post description.
     */
    suspend fun createPost(
        image: MultipartBody.Part,
        description: RequestBody
    ): NetworkResult<CreatePostResponse> = withContext(Dispatchers.IO) {
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "createPost blocked: token invalid or missing")
            return@withContext NetworkResult.Error(
                ApiException.Unauthorized("Your session has expired. Please log in again.")
            )
        }
        Log.d(TAG, "POST /posts imageSize=${image.body?.contentLength()} bytes")
        safeApiCall { ApiClient.userService.createPost(image, description).execute() }
    }

    // ─── Suspend: fetch all posts ─────────────────────────────────────────────

    /**
     * Fetches all posts. Validates token before calling the network.
     */
    suspend fun getAllPosts(): NetworkResult<GetAllPostsResponse> = withContext(Dispatchers.IO) {
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "getAllPosts blocked: token invalid or missing")
            return@withContext NetworkResult.Error(
                ApiException.Unauthorized("Your session has expired. Please log in again.")
            )
        }
        Log.d(TAG, "GET /posts")
        safeApiCall { ApiClient.userService.getAllPosts().execute() }
    }

    // ─── Legacy callback API (kept for any remaining call-sites) ─────────────

    /**
     * @deprecated Prefer the suspend [createPost] overload. This callback variant
     * does not integrate with structured cancellation; the network call will continue
     * even if the ViewModel scope is cancelled.
     */
    @Deprecated("Use suspend createPost(image, description) instead")
    fun createPostCallback(
        image: MultipartBody.Part,
        description: RequestBody,
        onResult: (CreatePostResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!TokenValidator.isTokenValid()) {
            onError("Cannot create post: token invalid or missing")
            return
        }
        ApiClient.userService.createPost(image, description)
            .enqueue(object : Callback<CreatePostResponse> {
                override fun onResponse(call: Call<CreatePostResponse>, response: Response<CreatePostResponse>) {
                    if (response.isSuccessful) onResult(response.body())
                    else onError("Failed to create post: ${response.code()}")
                }
                override fun onFailure(call: Call<CreatePostResponse>, t: Throwable) {
                    onError("Network error: ${t.localizedMessage}")
                }
            })
    }

    /**
     * @deprecated Prefer the suspend [getAllPosts] overload.
     */
    @Deprecated("Use suspend getAllPosts() instead")
    fun getAllPostsCallback(
        onResult: (GetAllPostsResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!TokenValidator.isTokenValid()) {
            onError("Cannot fetch posts: token invalid or missing")
            return
        }
        ApiClient.userService.getAllPosts()
            .enqueue(object : Callback<GetAllPostsResponse> {
                override fun onResponse(call: Call<GetAllPostsResponse>, response: Response<GetAllPostsResponse>) {
                    if (response.isSuccessful) onResult(response.body())
                    else onError("Failed to fetch posts: ${response.code()}")
                }
                override fun onFailure(call: Call<GetAllPostsResponse>, t: Throwable) {
                    onError("Network error: ${t.localizedMessage}")
                }
            })
    }
}

