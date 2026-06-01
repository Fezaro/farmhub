package com.farm_tech.farmhub.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class FarmHelpUiState(
    val currentStep: Int = 1,
    val description: String = "",
    val selectedImageUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showConfirmation: Boolean = false,
    val submissionSuccess: Boolean = false
)

/**
 * ViewModel for FarmHelp feature with authentication enforcement.
 *
 * Senior practice: Validates authentication before attempting to submit posts,
 * ensuring no network calls are made with invalid tokens.
 */
class FarmHelpViewModel : ViewModel() {
    companion object {
        private const val TAG = "FarmHelpViewModel"
    }

    private val _uiState = MutableStateFlow(FarmHelpUiState())
    val uiState: StateFlow<FarmHelpUiState> = _uiState

    fun nextStep() {
        _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep + 1)
    }

    fun prevStep() {
        _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep - 1)
    }

    fun setDescription(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc)
    }

    fun setSelectedImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri)
    }

    fun showConfirmationDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showConfirmation = show)
    }

    fun reset() {
        _uiState.value = FarmHelpUiState()
    }

    fun createImageUri(context: Context): Uri {
        val imageFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider", // This must match <provider> in the manifest
            imageFile
        )
    }

    fun setErrorMessage(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    /**
     * Submits a post with authentication validation.
     * Checks token validity before attempting submission to prevent orphaned requests.
     *
     * @param context Application context for token validation
     */
    fun submitPost(context: Context) {
        val state = _uiState.value

        // Validate authentication before submission
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot submit post: token invalid or missing")
            _uiState.value = state.copy(
                errorMessage = "Your session has expired. Please log in again.",
                isLoading = false,
                showConfirmation = false
            )
            return
        }

        if (state.selectedImageUri == null || state.description.isBlank()) {
            Log.w(TAG, "Cannot submit post: missing image or description")
            _uiState.value = state.copy(
                errorMessage = "Image and description required.",
                isLoading = false,
                showConfirmation = false
            )
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null, showConfirmation = false)

        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(state.selectedImageUri)
                val file = File(context.cacheDir, "upload.jpg")
                inputStream?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val requestFile = MultipartBody.Part.createFormData(
                    "image",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val descBody = state.description.toRequestBody("text/plain".toMediaTypeOrNull())
                PostRepository().createPost(
                    image = requestFile,
                    description = descBody,
                    onResult = { response ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            submissionSuccess = response?.status == "success",
                            errorMessage = if (response?.status != "success") "Server error" else null,
                            currentStep = if (response?.status == "success") 3 else _uiState.value.currentStep
                        )
                    },
                    onError = { error ->
                        Log.e(TAG, "Error submitting post: $error")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during submission: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "An error occurred while processing your request."
                )
            }
        }
    }
}
