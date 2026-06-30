package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.auth.GenericStatusResponse
import com.farm_tech.farmhub.models.auth.ResetPasswordRequest
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResetPasswordRepository {
    suspend fun resetPassword(phone: String): NetworkResult<GenericStatusResponse> = withContext(Dispatchers.IO) {
        safeApiCall {
            ApiClient.userService.resetPassword(ResetPasswordRequest(phone = phone)).execute()
        }
    }
}

