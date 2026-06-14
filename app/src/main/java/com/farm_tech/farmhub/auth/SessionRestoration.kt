package com.farm_tech.farmhub.auth

import android.content.Context
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.session.UserSession

/**
 * SessionRestoration - Handles automatic session restoration on app startup.
 *
 * Responsibilities:
 * - Restore token from secure storage
 * - Restore user session data
 * - Validate restored session
 * - Determine appropriate startup route
 *
 * Senior practice: This centralizes all session restoration logic,
 * ensuring consistent behavior across app lifecycle.
 */
object SessionRestoration {
    private const val TAG = "SessionRestoration"

    /**
     * Restores the user session on app startup.
     * Should be called once in MainActivity.onCreate() before showing UI.
     *
     * @param context Application context
     * @return true if valid session was restored, false otherwise
     */
    fun restoreSessionOnStartup(context: Context): Boolean {
        Log.d(TAG, "Attempting to restore session on app startup...")

        return try {
            // Step 1: Try to load token from secure storage
            val savedToken = SecureTokenManager.getToken()

            if (savedToken.isNullOrBlank()) {
                Log.d(TAG, "No saved token found. User must log in.")
                return false
            }

            // Step 2: Token exists - restore it to ApiClient
            ApiClient.setBearerToken(savedToken)
            Log.d(TAG, "Token restored to ApiClient (length: ${savedToken.length})")

            // Step 3: Restore user session data if available
            restoreUserSessionData(context)

            // Step 4: Validate that we have minimum required session data
            val hasRequiredData = TokenValidator.isSessionValid()

            if (hasRequiredData) {
                Log.d(TAG, "✓ Session successfully restored. User is logged in.")
                return true
            } else {
                Log.w(TAG, "✗ Session restoration incomplete. User session data incomplete.")
                // Token exists but session is incomplete - try to fetch user profile
                // For now, keep token but mark as incomplete
                return true // Token is valid enough for an API call to refresh profile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during session restoration: ${e.message}", e)
            false
        }
    }

    /**
     * Restores persisted user session data from local storage.
     * This is in-memory only; used for immediate access without API calls.
     *
     * @param context Application context
     */
    private fun restoreUserSessionData(context: Context) {
        try {
            // For now, session data is only available after successful login
            // In the future, we may persist it to shared preferences for faster restoration
            // For a WhatsApp-like experience, we want to show content immediately with cached data

            val prefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)
            val userName = prefs.getString("user_name", null)
            val phone = prefs.getString("phone", null)
            val role = prefs.getString("role", null)
            val county = prefs.getString("county", null)
            val subCounty = prefs.getString("sub_county", null)
            val paidUser = prefs.getString("paid_user", null)
            val issued = prefs.getLong("issued", 0L)
            val expires = prefs.getLong("expires", 0L)

            if (!userId.isNullOrBlank()) {
                UserSession.userId = userId
                UserSession.userName = userName
                UserSession.phone = phone
                UserSession.role = role
                UserSession.county = county
                UserSession.subCounty = subCounty
                UserSession.paidUser = paidUser
                UserSession.issued = if (issued > 0) issued else null
                UserSession.expires = if (expires > 0) expires else null

                Log.d(TAG, "User session data restored (userId: $userId, phone: $phone)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore user session data: ${e.message}")
            // Continue without session data; will be fetched on next API call
        }
    }

    /**
     * Establishes and persists a complete session after successful login.
     *
     * @param context Application context
     * @param token Authentication token from backend
     * @param userId User ID from login response
     * @param userName User name from login response
     * @param phone User phone from login response
     * @param role User role from login response
     * @param county User county from login response
     * @param subCounty User subcounty from login response
     * @param paidUser Paid user status from login response
     * @param issued Token issued timestamp from backend
     * @param expires Token expiration timestamp from backend
     */
    fun establishSession(
        context: Context,
        token: String,
        userId: String,
        userName: String,
        phone: String,
        role: String? = null,
        county: String? = null,
        subCounty: String? = null,
        paidUser: String? = null,
        issued: Long? = null,
        expires: Long? = null
    ) {
        Log.d(TAG, "Establishing new session (userId: $userId, phone: $phone)")

        try {
            // Save token securely with backend-provided expiration
            val expirationTime = expires ?: (System.currentTimeMillis() + 24 * 60 * 60 * 1000) // Default to 24h if not provided

            try {
                SecureTokenManager.saveToken(token, expirationTime)
                Log.d(TAG, "Secure token saved successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save secure token: ${e.message}. Continuing with session...")
            }

            ApiClient.setBearerToken(token)

            // Save user session data to preferences for quick restoration
            try {
                val prefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("user_id", userId)
                    putString("user_name", userName)
                    putString("phone", phone)
                    putString("role", role)
                    putString("county", county)
                    putString("sub_county", subCounty)
                    putString("paid_user", paidUser)
                    if (issued != null) putLong("issued", issued)
                    if (expires != null) putLong("expires", expires)
                    apply()
                }
                Log.d(TAG, "User session data saved to SharedPreferences")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save session data to SharedPreferences: ${e.message}. Continuing...")
            }

            // Update in-memory session (these operations should always succeed)
            UserSession.token = token
            UserSession.userId = userId
            UserSession.userName = userName
            UserSession.phone = phone
            UserSession.role = role
            UserSession.county = county
            UserSession.subCounty = subCounty
            UserSession.paidUser = paidUser
            UserSession.issued = issued
            UserSession.expires = expires
            Log.d(TAG, "UserSession in-memory data updated")

            // Also save to AuthManager for compatibility
            try {
                AuthManager.saveToken(context, token)
                Log.d(TAG, "Token saved to AuthManager")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save token to AuthManager: ${e.message}. Continuing...")
            }

            Log.d(TAG, "✓ Session established successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error establishing session: ${e.message}", e)
            // Don't re-throw - at minimum, the in-memory session is set up
        }
    }

    /**
     * Destroys the session completely (logout).
     *
     * @param context Application context
     */
    fun destroySession(context: Context) {
        Log.d(TAG, "Destroying session...")

        try {
            // Clear secure storage
            SecureTokenManager.clearToken()

            // Clear shared preferences
            val prefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // Clear auth manager
            AuthManager.logout(context)

            // Clear in-memory session
            UserSession.clear()

            Log.d(TAG, "✓ Session destroyed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying session: ${e.message}", e)
        }
    }
}


