package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.signup.RegisterRequest
import com.farm_tech.farmhub.models.signup.RegisterResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class SignupRepository {
    fun signup(
        registerRequest: RegisterRequest,
        onResult: (RegisterResponse?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            ApiClient.userService.registerUser(registerRequest)
                .enqueue(object : Callback<RegisterResponse> {
                    override fun onResponse(
                        call: Call<RegisterResponse>,
                        response: Response<RegisterResponse>
                    ) {
                        try {
                            if (response.isSuccessful && response.body() != null) {
                                onResult(response.body())
                            } else {
                                // Try to read error body, but don't crash if it fails
                                val errorMsg = try {
                                    response.errorBody()?.string()
                                } catch (e: IOException) {
                                    null
                                }
                                onError("Signup failed: ${response.code()} ${response.message()}${if (errorMsg != null) "\n$errorMsg" else ""}")
                            }
                        } catch (e: Exception) {
                            onError("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
                        }
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        onError("Network error: ${t.localizedMessage ?: "Unknown error"}")
                    }
                })
        } catch (e: Exception) {
            onError("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
