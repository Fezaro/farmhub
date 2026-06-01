package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Repository for accessing media feed endpoints.
 * Enforces token validation before making API calls.
 *
 * Senior practice: Centralized repository layer handles all media API interactions,
 * validation, logging, and error handling in one place.
 */
class MediaRepository {
    companion object {
        private const val TAG = "MediaRepository"
    }

    /**
     * Fetches the media feed from the remote API.
     * Performs token validation before making the request.
     *
     * @return Response containing MediaFeedResponse on success, or error response
     * @throws IllegalStateException if token is invalid before attempting the call
     */
    suspend fun getMediaFeed(): Response<MediaFeedResponse> = withContext(Dispatchers.IO) {
        try {
            // Pre-flight token validation to prevent unnecessary network requests
            if (!TokenValidator.isTokenValid()) {
                Log.w(TAG, "Cannot fetch media feed: token invalid or missing. Aborting request.")
                return@withContext Response.error(
                    401,
                    okhttp3.ResponseBody.create(null, "No valid authentication token")
                )
            }

            Log.d(TAG, "GET /media tokenPresent=true, initiating request...")
            val resp = ApiClient.userService.getMediaFeed().execute()

            if (!resp.isSuccessful) {
                Log.e(
                    TAG,
                    "Media feed error code=${resp.code()} body=${resp.errorBody()?.string()}"
                )
            } else {
                Log.d(
                    TAG,
                    "Media feed success. Items=${resp.body()?.media?.size ?: 0}"
                )
            }
            resp
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching media: ${e.message}", e)
            Response.error(500, okhttp3.ResponseBody.create(null, e.message ?: "error"))
        }
    }
}


