package com.example.app.repository

import com.example.app.api.ApiClient
import com.example.app.auth.SecureTokenManager
import com.example.app.models.login.LoginRequest
import com.example.app.models.login.LoginResponse
import com.example.app.session.UserSession
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
                                // Store token for Bearer authentication
                                ApiClient.setBearerToken(loginResponse.token)
                                // Persist token securely for app restarts
                                try {
                                    SecureTokenManager.saveToken(loginResponse.token, loginResponse.expires)
                                } catch (e: Exception) {
                                    // Don't fail login if persistence fails; ignore and continue
                                }
                                // Store user session data for later use (phone, id, etc.)
                                UserSession.setSessionFromLoginResponse(loginResponse)
                                onResult(loginResponse)
                            } else {
                                onError("Invalid credentials or server error.")
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
}