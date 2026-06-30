package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.auth.SecureTokenManager
import com.farm_tech.farmhub.models.login.LoginRequest
import com.farm_tech.farmhub.models.login.LoginResponse
import com.farm_tech.farmhub.session.UserSession
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginRepository {
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
                        try {
                            if (response.isSuccessful && response.body() != null) {
                                val loginResponse = response.body()!!
                                val token = loginResponse.token?.trim().orEmpty()
                                val user = loginResponse.userDetails

                                if (token.isBlank() || user?.id.isNullOrBlank() || user?.phone.isNullOrBlank()) {
                                    onError("Login succeeded but response data was incomplete. Please try again.")
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
                                onError(parseErrorMessage(response) ?: "Invalid credentials or server error.")
                            }
                        } catch (e: Exception) {
                            onError("Unexpected error: ${e.localizedMessage ?: "Something went wrong."}")
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        onError("Network error: ${t.localizedMessage ?: "Please check your connection and try again."}")
                    }
                })
        } catch (e: Exception) {
            onError("Unexpected error: ${e.localizedMessage ?: "Something went wrong."}")
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
}
