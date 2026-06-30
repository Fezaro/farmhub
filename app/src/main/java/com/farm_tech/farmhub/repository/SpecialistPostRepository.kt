package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.auth.GenericStatusResponse
import com.farm_tech.farmhub.models.posts.GetAllPostsResponse
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody

class SpecialistPostRepository {
    suspend fun getSpecialistPosts(): NetworkResult<GetAllPostsResponse> = withContext(Dispatchers.IO) {
        safeApiCall { ApiClient.userService.getSpecialistPosts().execute() }
    }

    suspend fun processPost(
        id: String,
        image: MultipartBody.Part,
        description: RequestBody
    ): NetworkResult<GenericStatusResponse> = withContext(Dispatchers.IO) {
        safeApiCall { ApiClient.userService.processSpecialistPost(id, image, description).execute() }
    }
}

