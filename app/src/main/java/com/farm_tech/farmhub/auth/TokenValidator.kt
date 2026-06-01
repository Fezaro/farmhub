package com.farm_tech.farmhub.auth

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient

/**
 * Utility for validating tokens before making protected API calls.
 * Performs pre-flight checks to prevent unnecessary network requests with invalid tokens.
 *
 * A Senior-level implementation that centralizes token validation logic,
 * reducing defensive checks scattered across repositories.
 */
object TokenValidator {
    private const val TAG = "TokenValidator"

    /**
     * Validates that a token exists and appears valid.
     * This is a pre-flight check to prevent making API calls with invalid tokens.
     *
     * @return true if token exists and passes basic validation checks, false otherwise
     */
    fun isTokenValid(): Boolean {
        val token = ApiClient.currentToken()

        if (token.isNullOrBlank()) {
            Log.d(TAG, "Token validation failed: token is null or blank")
            return false
        }

        // Basic format check: bearer tokens should have reasonable length
        if (token.length < 20) {
            Log.w(TAG, "Token validation failed: token appears malformed (length < 20)")
            return false
        }

        Log.d(TAG, "Token validation passed (length=${token.length})")
        return true
    }

    /**
     * Validates that a token exists and user session is properly initialized.
     * More comprehensive than isTokenValid() - checks session data as well.
     *
     * @return true if token valid AND user session has required data (userId, phone), false otherwise
     */
    fun isSessionValid(): Boolean {
        if (!isTokenValid()) {
            return false
        }

        val userId = com.farm_tech.farmhub.session.UserSession.userId
        val phone = com.farm_tech.farmhub.session.UserSession.phone

        if (userId.isNullOrBlank()) {
            Log.w(TAG, "Session validation failed: userId is null or blank")
            return false
        }

        if (phone.isNullOrBlank()) {
            Log.w(TAG, "Session validation failed: phone is null or blank")
            return false
        }

        Log.d(TAG, "Session validation passed (userId present, phone present)")
        return true
    }

    /**
     * Checks if token has been explicitly set in ApiClient.
     * Useful for determining if user has attempted login.
     *
     * @return true if ApiClient has a non-null bearer token, false otherwise
     */
    fun hasToken(): Boolean = !ApiClient.currentToken().isNullOrBlank()

    /**
     * Logs token status for debugging authentication issues.
     * Call this when troubleshooting auth problems.
     */
    fun logTokenStatus() {
        val token = ApiClient.currentToken()
        val hasToken = !token.isNullOrBlank()
        val userId = com.farm_tech.farmhub.session.UserSession.userId
        val phone = com.farm_tech.farmhub.session.UserSession.phone

        Log.d(
            TAG,
            "Token Status: hasToken=$hasToken, tokenLength=${token?.length ?: 0}, " +
            "userId=$userId, phone=$phone"
        )
    }
}


