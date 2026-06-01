package com.farm_tech.farmhub.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.farm_tech.farmhub.models.login.LoginResponse
import com.farm_tech.farmhub.repository.LoginRepository

class LoginViewModel : ViewModel() {
    var loginResult by mutableStateOf<LoginResponse?>(null)
        private set
    var loginError by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
        private set

    private val repository = LoginRepository()

    fun login(phone: String, password: String) {
        loginError = null
        isLoading = true
        try {
            repository.login(phone, password,
                onResult = {
                    isLoading = false
                    loginResult = it
                    // Removed: UserSession.setSessionFromLoginResponse(it)
                },
                onError = {
                    isLoading = false
                    loginError = it
                }
            )
        } catch (e: Exception) {
            isLoading = false
            loginError = e.localizedMessage ?: "Unexpected error occurred. Please try again."
        }
    }

    fun clearState() {
        loginError = null
        loginResult = null
        isLoading = false
    }
}
