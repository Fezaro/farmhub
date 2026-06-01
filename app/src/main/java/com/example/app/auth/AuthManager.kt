package com.example.app.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.core.content.edit
import com.example.app.api.ApiClient

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"

    private val authState = MutableStateFlow(false)

    /**
     * Checks if the user is currently logged in with a valid token.
     * Relies on backend token validation and 401 responses for expiration.
     * Does NOT force logout based on local timeout (unlike old 12-hour logic).
     *
     * Senior practice: This method serves as the single source of truth for authentication state,
     * centralizing token validation logic and ensuring consistent behavior across the app.
     * Now respects backend's token expiration policies rather than forcing arbitrary timeouts.
     *
     * @param context The Android context for accessing SharedPreferences
     * @return true if user is logged in with a valid token, false otherwise
     */
    fun isLoggedIn(context: Context): Boolean {
        // Prefer secure storage when available
        var token: String? = null
        try {
            token = SecureTokenManager.getToken()
        } catch (e: Exception) {
            // fallback to legacy prefs
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            token = prefs.getString(KEY_TOKEN, null)
        }

        if (token != null) {
            // Set bearer token for ApiClient if valid
            ApiClient.setBearerToken(token)
            Log.d(TAG, "User is logged in. Token present (length: ${token.length})")
            return true
        }

        Log.d(TAG, "User is not logged in.")
        return false
    }

    fun saveToken(context: Context, token: String) {
        // Save to encrypted storage with backend-provided expiration
        // Note: Backend is responsible for token expiration policy
        try {
            // If expiration is known (from backend), use it; otherwise let backend handle it
            SecureTokenManager.saveToken(token, Long.MAX_VALUE) // No local timeout
        } catch (e: Exception) {
            // ignore and fallback to prefs below
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TOKEN, token)
            putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
        }
        authState.value = true
        ApiClient.setBearerToken(token)
        Log.d(TAG, "Token saved successfully (length: ${token.length}). Backend handles expiration.")
    }

    /**
     * Logs out the current user by clearing all authentication data.
     * This is a hard logout - no refresh token attempt.
     *
     * Senior practice: Centralized logout logic ensures consistent cleanup across the app,
     * preventing orphaned sessions or partial logout states.
     *
     * @param context The Android context for accessing SharedPreferences
     */
    fun logout(context: Context) {
        // Clear secure storage if available
        try {
            SecureTokenManager.clearToken()
        } catch (e: Exception) {
            // ignore
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_TOKEN_TIMESTAMP)
        }
        com.example.app.session.UserSession.clear()
        authState.value = false
        ApiClient.clearBearerToken()
        Log.d(TAG, "User logged out. Token and session cleared.")
    }

    /**
     * Gets the current authentication state as a Flow for reactive updates.
     * Use this for observing auth state changes in ViewModels/Composables.
     *
     * @return Flow that emits true when logged in, false when logged out
     */
    fun getAuthState(): MutableStateFlow<Boolean> = authState

    /**
     * Checks if a 401 response should trigger logout.
     * Used by the HTTP response interceptor to handle unauthorized responses.
     *
     * @param context The Android context
     * @return true if we should perform logout and redirect, false if 401 should be retried/handled elsewhere
     */
    fun handleUnauthorized(context: Context) {
        Log.w(TAG, "Received 401 Unauthorized response. Backend indicates session is invalid. Triggering logout.")
        logout(context)
    }

    // ...existing code...
    fun addAuthStateListener(context: Context, listener: (Boolean) -> Unit) {
        listener(isLoggedIn(context))
    }

    /**
     * Checks if the current token is expired based on timestamp.
     * Useful for pre-flight validation before making API calls.
     *
     * Note: This is a legacy method. Expiration is now handled by the backend via 401 responses.
     *
     * @param context The Android context
     * @return true if token is expired or missing, false if token is still valid
     */
    fun isTokenExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        return token == null
    }
}