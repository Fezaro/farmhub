package com.farm_tech.farmhub.network

sealed class NetworkResult<out T> {
    data object Loading : NetworkResult<Nothing>()
    data class Success<T>(val data: T) : NetworkResult<T>()
    data object Empty : NetworkResult<Nothing>()
    data class Error(val exception: ApiException) : NetworkResult<Nothing>()
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

