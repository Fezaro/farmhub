package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.signup.RegisterRequest
import com.farm_tech.farmhub.models.signup.RegisterResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class SignupRepository {
    companion object {
        private const val TAG = "SignupRepository"
    }

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
                                Log.e(TAG, "Signup failed: code=${response.code()} message=${response.message()} body=$errorMsg")
                                onError("Unable to sign up right now. Please check your details and try again.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unexpected signup response handling error", e)
                            onError("Unable to sign up right now. Please try again.")
                        }
                    }

                    override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                        Log.e(TAG, "Signup request failed", t)
                        onError("Check your internet connection and try again.")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Signup request setup failed", e)
            onError("Unable to sign up right now. Please try again.")
        }
    }
}
