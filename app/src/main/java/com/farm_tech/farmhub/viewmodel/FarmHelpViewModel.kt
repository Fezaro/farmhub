package com.farm_tech.farmhub.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.repository.PostRepository
import com.farm_tech.farmhub.util.CountingRequestBody
import com.farm_tech.farmhub.util.ImageUploadCompressor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/**
 * UI state for the multi-step FarmHelp post submission flow.
 *
 * @param uploadProgress  A value in [0, 1] while work is in progress, null otherwise.
 * @param uploadStage     Human-readable label for the current phase, shown below the
 *                        progress bar (e.g. "Compressing image…", "Uploading…").
 */
data class FarmHelpUiState(
    val currentStep: Int = 1,
    val description: String = "",
    val selectedImageUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showConfirmation: Boolean = false,
    val submissionSuccess: Boolean = false,
    val uploadProgress: Float? = null,
    val uploadStage: String? = null
)

/**
 * ViewModel for the FarmHelp post-submission feature.
 *
 * Responsibilities:
 * ─ Validates authentication before any network call.
 * ─ Delegates image preparation to [ImageUploadCompressor] (background IO).
 * ─ Reports fine-grained progress: per-phase label + real upload byte progress.
 * ─ Cleans up all temporary files in [onCleared] so cacheDir never accumulates
 *   large camera JPEGs after the session ends.
 * ─ Uses the suspend [PostRepository.createPost] so the upload is tied to
 *   [viewModelScope] and cancelled automatically if the ViewModel is destroyed.
 */
class FarmHelpViewModel : ViewModel() {

    companion object {
        private const val TAG = "FarmHelpViewModel"
    }

    private val postRepository = PostRepository()

    private val _uiState = MutableStateFlow(FarmHelpUiState())
    val uiState: StateFlow<FarmHelpUiState> = _uiState

    /**
     * The underlying temp file created for the camera capture URI.
     * Tracked so it can be deleted after upload (or in [onCleared]).
     */
    private var cameraSourceFile: File? = null

    // ─── Step navigation ─────────────────────────────────────────────────────

    fun nextStep() { _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep + 1) }
    fun prevStep() { _uiState.value = _uiState.value.copy(currentStep = _uiState.value.currentStep - 1) }
    fun setDescription(desc: String) { _uiState.value = _uiState.value.copy(description = desc) }
    fun setSelectedImageUri(uri: Uri?) { _uiState.value = _uiState.value.copy(selectedImageUri = uri) }
    fun showConfirmationDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showConfirmation = show) }
    fun setErrorMessage(message: String?) { _uiState.value = _uiState.value.copy(errorMessage = message) }

    fun reset() {
        _uiState.value = FarmHelpUiState()
        // Old camera file no longer needed after reset
        cameraSourceFile?.delete()
        cameraSourceFile = null
    }

    // ─── Camera URI creation ──────────────────────────────────────────────────

    /**
     * Creates a FileProvider URI for a new camera capture and tracks the underlying
     * file so it can be cleaned up later.
     *
     * Previous camera files are deleted here to prevent cacheDir accumulation
     * when the user retakes photos multiple times within the same session.
     */
    fun createImageUri(context: Context): Uri {
        // Clean up the previous capture file if the user is retaking a photo
        cameraSourceFile?.delete()

        val imageFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
        cameraSourceFile = imageFile
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
    }

    // ─── Upload pipeline ──────────────────────────────────────────────────────

    /**
     * Full upload pipeline:
     * 1. Token validation.
     * 2. Input validation.
     * 3. Image compression with granular progress (0 % → 70 %).
     * 4. Multipart upload with real byte-level progress (70 % → 98 %).
     * 5. State update and temp-file cleanup.
     *
     * All heavyweight work runs on [Dispatchers.IO] via coroutines. The coroutine
     * is bound to [viewModelScope] and will be cancelled automatically if the user
     * navigates away, preventing orphaned network calls.
     */
    fun submitPost(context: Context) {
        val state = _uiState.value

        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "submitPost blocked: token invalid")
            _uiState.value = state.copy(
                errorMessage = "Your session has expired. Please log in again.",
                isLoading = false,
                showConfirmation = false
            )
            return
        }

        if (state.selectedImageUri == null || state.description.isBlank()) {
            Log.w(TAG, "submitPost blocked: missing image or description")
            _uiState.value = state.copy(
                errorMessage = "Image and description are required.",
                isLoading = false,
                showConfirmation = false
            )
            return
        }

        _uiState.value = state.copy(
            isLoading = true,
            errorMessage = null,
            showConfirmation = false,
            uploadProgress = 0.02f,
            uploadStage = "Preparing…"
        )

        viewModelScope.launch {
            var uploadFile: File? = null
            try {
                // ── Phase 1: compression (2 % → 70 %) ────────────────────────
                uploadFile = ImageUploadCompressor.prepareImageForUpload(
                    context = context,
                    sourceUri = state.selectedImageUri,
                    onProgress = { compressionFraction ->
                        // Map compression [0,1] to overall [0.02, 0.70]
                        val overall = 0.02f + compressionFraction * 0.68f
                        _uiState.value = _uiState.value.copy(
                            uploadProgress = overall,
                            uploadStage = when {
                                compressionFraction < 0.20f -> "Reading image…"
                                compressionFraction < 0.70f -> "Compressing image…"
                                else -> "Finalising image…"
                            }
                        )
                    }
                )

                Log.d(TAG, "Compression complete: ${uploadFile.length() / 1024} KB")

                _uiState.value = _uiState.value.copy(
                    uploadProgress = 0.72f,
                    uploadStage = "Uploading to server…"
                )

                // ── Phase 2: upload (70 % → 98 %) ────────────────────────────
                val fileBody = uploadFile.asRequestBody("image/jpeg".toMediaTypeOrNull())

                // Wrap with CountingRequestBody for real byte-level progress
                val countingBody = CountingRequestBody(fileBody) { bytesWritten, total ->
                    if (total > 0) {
                        // Map upload [0,1] to overall [0.72, 0.98]
                        val uploadFraction = bytesWritten.toFloat() / total
                        _uiState.value = _uiState.value.copy(
                            uploadProgress = 0.72f + uploadFraction * 0.26f,
                            uploadStage = "Uploading… ${(uploadFraction * 100).toInt()}%"
                        )
                    }
                }

                val imagePart = MultipartBody.Part.createFormData(
                    "image", uploadFile.name, countingBody
                )
                val descBody = state.description.toRequestBody("text/plain".toMediaTypeOrNull())

                when (val result = postRepository.createPost(imagePart, descBody)) {
                    is NetworkResult.Success -> {
                        val success = result.data.status == "success"
                        Log.d(TAG, "Upload result: status=${result.data.status}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            submissionSuccess = success,
                            errorMessage = if (!success) "Server returned an unexpected status." else null,
                            currentStep = if (success) 3 else _uiState.value.currentStep,
                            uploadProgress = null,
                            uploadStage = null
                        )
                    }
                    is NetworkResult.Error -> {
                        val msg = ErrorMapper.toUserMessage(result.exception)
                        Log.e(TAG, "Upload error: $msg")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = msg,
                            uploadProgress = null,
                            uploadStage = null
                        )
                    }
                    is NetworkResult.Empty -> {
                        Log.w(TAG, "Upload returned empty body")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "No response from server. Please try again.",
                            uploadProgress = null,
                            uploadStage = null
                        )
                    }
                    NetworkResult.Loading -> Unit // not reachable from execute()
                }

            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError during upload pipeline", oom)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    uploadStage = null,
                    errorMessage = "The image is too large to process. " +
                            "Please try a lower-resolution photo or close other apps and retry."
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    uploadStage = null,
                    errorMessage = "Permission denied while reading the selected image."
                )
            } catch (e: IOException) {
                Log.e(TAG, "IOException: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    uploadStage = null,
                    errorMessage = "Unable to process the image file. Please try another photo."
                )
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    uploadStage = null,
                    errorMessage = "Invalid image selected. Please choose a different photo."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    uploadProgress = null,
                    uploadStage = null,
                    errorMessage = "An unexpected error occurred. Please try again."
                )
            } finally {
                // Always delete the compressed upload file; never leave temp files behind.
                uploadFile?.delete()
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled automatically. Clean up any remaining files.
        cameraSourceFile?.delete()
        cameraSourceFile = null
        Log.d(TAG, "ViewModel cleared; camera temp file cleaned up")
    }
}

