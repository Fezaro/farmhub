package com.example.app.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.*
import androidx.core.content.edit
import com.example.app.api.ApiClient

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
    private const val TOKEN_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L // 12 hours

    private val authState = MutableStateFlow(false)
    private var timeoutJob: Job? = null

    /**
     * Checks if the user is currently logged in with a valid token.
     * Performs automatic logout if token has expired.
     *
     * Senior practice: This method serves as the single source of truth for authentication state,
     * centralizing token validation logic and ensuring consistent behavior across the app.
     *
     * @param context The Android context for accessing SharedPreferences
     * @return true if user is logged in with a valid token, false otherwise
     */
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        
        if (token != null && now - timestamp < TOKEN_TIMEOUT_MILLIS) {
            // Set bearer token for ApiClient if valid
            ApiClient.setBearerToken(token)
            Log.d(TAG, "User is logged in. Token valid (age: ${now - timestamp}ms)")
            return true
        }
        
        // If expired, logout (clear token from both places)
        if (token != null && now - timestamp >= TOKEN_TIMEOUT_MILLIS) {
            Log.w(TAG, "Token expired (age: ${now - timestamp}ms >= $TOKEN_TIMEOUT_MILLIS). Performing automatic logout.")
            logout(context)
        }
        
        Log.d(TAG, "User is not logged in.")
        return false
    }

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TOKEN, token)
            putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
        }
        authState.value = true
        ApiClient.setBearerToken(token)
        startTimeout(context)
        Log.d(TAG, "Token saved successfully (length: ${token.length}, will expire in $TOKEN_TIMEOUT_MILLIS ms)")
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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_TOKEN_TIMESTAMP)
        }
        com.example.app.session.UserSession.clear()
        authState.value = false
        ApiClient.clearBearerToken()
        timeoutJob?.cancel()
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
        Log.w(TAG, "Received 401 Unauthorized response. Triggering logout.")
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
     * @param context The Android context
     * @return true if token is expired or missing, false if token is still valid
     */
    fun isTokenExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        val isExpired = now - timestamp >= TOKEN_TIMEOUT_MILLIS
        if (isExpired) {
            Log.d(TAG, "Token is expired (age: ${now - timestamp}ms >= $TOKEN_TIMEOUT_MILLIS)")
        }
        return isExpired
    }

    private fun startTimeout(context: Context) {
        timeoutJob?.cancel()
        timeoutJob = CoroutineScope(Dispatchers.Default).launch {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
            val now = System.currentTimeMillis()
            val remaining = TOKEN_TIMEOUT_MILLIS - (now - timestamp)
            if (remaining > 0) {
                delay(remaining)
            }
            logout(context)
        }
    }
}