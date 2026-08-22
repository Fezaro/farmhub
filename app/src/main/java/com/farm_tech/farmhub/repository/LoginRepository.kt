package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.auth.SecureTokenManager
import com.farm_tech.farmhub.models.login.LoginRequest
import com.farm_tech.farmhub.models.login.LoginResponse
import com.farm_tech.farmhub.session.UserSession
import android.util.Log
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginRepository {
    companion object {
        private const val TAG = "LoginRepository"
    }

    fun login(
        phone: String,
        password: String,
        onResult: (LoginResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = LoginRequest(phone, password)
        try {
            ApiClient.userService.userLogin(request)
                .enqueue(object : Callback<LoginResponse> {
                    override fun onResponse(
                        call: Call<LoginResponse>,
                        response: Response<LoginResponse>
                    ) {
                        val requestId = response.headers()["x-request-id"]
                            ?: call.request().header("X-Request-ID")
                        try {
                            if (response.isSuccessful && response.body() != null) {
                                val loginResponse = response.body()!!
                                val token = loginResponse.token?.trim().orEmpty()
                                val user = loginResponse.userDetails

                                if (token.isBlank() || user?.id.isNullOrBlank() || user?.phone.isNullOrBlank()) {
                                    onError(withReference("Login succeeded but response data was incomplete. Please try again.", requestId))
                                    return
                                }

                                // Store token for Bearer authentication
                                ApiClient.setBearerToken(token)
                                // Persist token securely for app restarts
                                try {
                                    val expiresAt = loginResponse.expires ?: Long.MAX_VALUE
                                    SecureTokenManager.saveToken(token, expiresAt)
                                } catch (e: Exception) {
                                    // Don't fail login if persistence fails; ignore and continue
                                }
                                // Store user session data for later use (phone, id, etc.)
                                UserSession.setSessionFromLoginResponse(loginResponse)
                                onResult(loginResponse)
                            } else {
                                val message = parseErrorMessage(response)
                                    ?: if (response.code() >= 500) {
                                        "Sign-in is temporarily unavailable. Please try again."
                                    } else {
                                        "Invalid credentials or server error."
                                    }
                                onError(if (response.code() >= 500) withReference(message, requestId) else message)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unexpected login response handling error", e)
                            onError(withReference("Unable to sign in right now. Please try again.", requestId))
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Log.e(TAG, "Login request failed", t)
                        onError(withReference("Check your internet connection and try again.", call.request().header("X-Request-ID")))
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Login request setup failed", e)
            onError("Unable to sign in right now. Please try again.")
        }
    }

    private fun parseErrorMessage(response: Response<LoginResponse>): String? {
        return try {
            val body = response.errorBody()?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            when {
                json.optString("message").isNotBlank() -> json.optString("message")
                json.optString("error").isNotBlank() -> json.optString("error")
                json.optString("status").isNotBlank() -> json.optString("status")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun withReference(message: String, requestId: String?): String {
        return requestId?.takeIf { it.isNotBlank() }?.let { "$message\nReference: $it" } ?: message
    }
}
