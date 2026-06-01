package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.farm_tech.farmhub.models.signup.RegisterRequest
import com.farm_tech.farmhub.models.signup.RegisterResponse
import com.farm_tech.farmhub.repository.SignupRepository

class SignupViewModel : ViewModel() {
    var signupResult by mutableStateOf<RegisterResponse?>(null)
        private set
    var signupError by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
        private set

    private val repository = SignupRepository()

    fun signup(registerRequest: RegisterRequest) {
        signupError = null
        isLoading = true
        try {
            repository.signup(registerRequest,
                onResult = { response ->
                    isLoading = false
                    signupResult = response
                },
                onError = { errorMsg ->
                    isLoading = false
                    signupError = errorMsg ?: "Unknown error"
                }
            )
        } catch (e: Exception) {
            isLoading = false
            signupError = e.localizedMessage ?: "Unexpected error"
        }
    }

    fun clearState() {
        signupError = null
        signupResult = null
        isLoading = false
    }
}
