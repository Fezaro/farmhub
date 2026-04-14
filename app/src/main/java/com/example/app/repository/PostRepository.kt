package com.example.app.repository

import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.auth.TokenValidator
import com.example.app.models.posts.CreatePostResponse
import com.example.app.models.posts.GetAllPostsResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Repository for posts endpoints.
 * Enforces token validation before making API calls.
 *
 * Senior practice: Centralized repository layer manages all post API interactions,
 * validation, logging, and error handling.
 */
class PostRepository {
    companion object {
        private const val TAG = "PostRepository"
    }

    /**
     * Creates a new post with an image and description.
     * Enforces token validation before attempting the request.
     *
     * @param image The multipart image file
     * @param description The post description
     * @param onResult Callback when request succeeds
     * @param onError Callback when request fails
     */
    fun createPost(
        image: MultipartBody.Part,
        description: RequestBody,
        onResult: (CreatePostResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            val error = "Cannot create post: token invalid or missing"
            Log.w(TAG, error)
            onError(error)
            return
        }

        Log.d(TAG, "POST /posts imageSize=${image.body?.contentLength()} descriptionSize=${description.contentLength()} tokenPresent=true")

        ApiClient.userService.createPost(image, description)
            .enqueue(object : Callback<CreatePostResponse> {
                override fun onResponse(
                    call: Call<CreatePostResponse>,
                    response: Response<CreatePostResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Create post success")
                        onResult(response.body())
                    } else {
                        val error = "Failed to create post: ${response.code()}"
                        Log.e(TAG, error)
                        onError(error)
                    }
                }

                override fun onFailure(call: Call<CreatePostResponse>, t: Throwable) {
                    val error = "Network error: ${t.localizedMessage}"
                    Log.e(TAG, error)
                    onError(error)
                }
            })
    }

    /**
     * Fetches all posts from the feed.
     * Enforces token validation before attempting the request.
     *
     * @param onResult Callback when request succeeds
     * @param onError Callback when request fails
     */
    fun getAllPosts(
        onResult: (GetAllPostsResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            val error = "Cannot fetch posts: token invalid or missing"
            Log.w(TAG, error)
            onError(error)
            return
        }

        Log.d(TAG, "GET /posts tokenPresent=true, initiating request...")

        ApiClient.userService.getAllPosts()
            .enqueue(object : Callback<GetAllPostsResponse> {
                override fun onResponse(
                    call: Call<GetAllPostsResponse>,
                    response: Response<GetAllPostsResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Fetch posts success. Count=${response.body()?.posts?.size ?: 0}")
                        onResult(response.body())
                    } else {
                        val error = "Failed to fetch posts: ${response.code()}"
                        Log.e(TAG, error)
                        onError(error)
                    }
                }

                override fun onFailure(call: Call<GetAllPostsResponse>, t: Throwable) {
                    val error = "Network error: ${t.localizedMessage}"
                    Log.e(TAG, error)
                    onError(error)
                }
            })
    }
}