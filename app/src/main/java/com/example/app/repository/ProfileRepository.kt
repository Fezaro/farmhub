package com.example.app.repository

import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.auth.TokenValidator
import com.example.app.models.profile.UserProfileResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileRepository {
    companion object {
        private const val TAG = "ProfileRepository"
    }

    fun getProfile(
        onResult: (UserProfileResponse?) -> Unit,
        onError: (String?) -> Unit
    ) {
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch profile: token invalid or missing")
            onError("Authentication required")
            return
        }

        ApiClient.userService.getUserProfile()
            .enqueue(object : Callback<UserProfileResponse> {
                override fun onResponse(
                    call: Call<UserProfileResponse>,
                    response: Response<UserProfileResponse>
                ) {
                    if (response.isSuccessful) {
                        onResult(response.body())
                    } else {
                        val errorMsg = response.errorBody()?.string()
                        onError(errorMsg ?: "Failed to fetch profile: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<UserProfileResponse>, t: Throwable) {
                    onError("Network error: ${t.localizedMessage}")
                }
            })
    }
}