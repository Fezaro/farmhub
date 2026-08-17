package com.farm_tech.farmhub.network

import android.util.Log
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.Response

object ErrorMapper {
    private const val TAG = "ErrorMapper"

    fun fromHttpCode(code: Int, fallback: String? = null): ApiException {
        return when (code) {
            400 -> ApiException.BadRequest(fallback ?: "Invalid request")
            401 -> ApiException.Unauthorized(fallback ?: "Session expired. Please log in again.")
            403 -> ApiException.Forbidden(fallback ?: "You do not have access to this resource.")
            404 -> ApiException.NotFound(fallback ?: "Resource not found")
            409 -> ApiException.Conflict(fallback ?: "Conflict error")
            422 -> ApiException.Validation(fallback ?: "Validation failed")
            429 -> ApiException.RateLimit(fallback ?: "Rate limit reached. Try again later.")
            500 -> ApiException.Server(fallback ?: "Internal server error")
            502 -> ApiException.BadGateway(fallback ?: "Bad gateway")
            503 -> ApiException.ServiceUnavailable(fallback ?: "Service unavailable")
            else -> ApiException.Unknown(fallback ?: "HTTP $code")
        }
    }

    fun fromThrowable(t: Throwable): ApiException {
        Log.e(TAG, "Network throwable mapped: ${t::class.java.simpleName}: ${t.message}", t)
        return when (t) {
            is UnknownHostException -> ApiException.Dns("DNS failure or no internet connection")
            is SocketTimeoutException -> ApiException.SocketTimeout("Socket timeout")
            is InterruptedIOException -> ApiException.Timeout("Request timeout")
            is SSLException -> ApiException.Ssl("Secure connection failed")
            else -> ApiException.Network("Network request failed")
        }
    }

    fun toUserMessage(exception: ApiException): String {
        return when (exception) {
            is ApiException.Dns,
            is ApiException.Network,
            is ApiException.Timeout,
            is ApiException.SocketTimeout,
            is ApiException.Ssl -> "Check your internet connection."

            is ApiException.ServiceUnavailable,
            is ApiException.Server,
            is ApiException.BadGateway,
            is ApiException.RateLimit -> "Service is temporarily unavailable. Please try again."

            is ApiException.Unauthorized -> "Your session expired. Please sign in again."
            is ApiException.Forbidden -> "You do not have access to this content."
            is ApiException.NotFound -> "We couldn't load this right now. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }

    fun toMediaUserMessage(exception: ApiException): String {
        return when (exception) {
            is ApiException.Dns,
            is ApiException.Network,
            is ApiException.Timeout,
            is ApiException.SocketTimeout,
            is ApiException.Ssl -> "Check your internet connection. Pull down to refresh."

            is ApiException.ServiceUnavailable,
            is ApiException.Server,
            is ApiException.BadGateway,
            is ApiException.RateLimit -> "We couldn't retrieve videos right now."

            else -> "Unable to load videos. Pull down to refresh."
        }
    }
}

suspend fun <T> safeApiCall(block: suspend () -> Response<T>): NetworkResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) NetworkResult.Empty else NetworkResult.Success(body)
        } else {
            val errorBody = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            NetworkResult.Error(ErrorMapper.fromHttpCode(response.code(), errorBody))
        }
    } catch (t: Throwable) {
        NetworkResult.Error(ErrorMapper.fromThrowable(t))
    }
}

