package com.example.app.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.app.models.profile.ProfileUiModel
import com.example.app.models.profile.toUiModelOrNull
import com.example.app.repository.ProfileRepository

class ProfileViewModel : ViewModel() {
    var profile by mutableStateOf<ProfileUiModel?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    private val repository = ProfileRepository()

    fun fetchProfile() {
        isLoading = true
        error = null
        repository.getProfile(
            onResult = {
                isLoading = false
                profile = it?.toUiModelOrNull()
            },
            onError = {
                isLoading = false
                error = it
            }
        )
    }

    fun clearState() {
        profile = null
        error = null
        isLoading = false
    }
}