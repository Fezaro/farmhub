package com.example.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureTokenManager - persists auth tokens using EncryptedSharedPreferences backed by Android Keystore.
 * Use SecureTokenManager.initialize(context) once (Application.onCreate) before other calls.
 */
object SecureTokenManager {
    private const val PREFS_NAME = "farmhub_secure_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun requirePrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("SecureTokenManager not initialized. Call initialize(context) first.")
    }

    fun saveToken(token: String, expiresAtMillis: Long) {
        requirePrefs().edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
            apply()
        }
    }

    fun getToken(): String? {
        val p = requirePrefs()
        val token = p.getString(KEY_AUTH_TOKEN, null)
        val expires = p.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (token != null && expires > 0L && System.currentTimeMillis() >= expires) {
            // expired - clear and return null
            clearToken()
            return null
        }
        return token
    }

    fun clearToken() {
        requirePrefs().edit().clear().apply()
    }

    fun saveRefreshToken(refreshToken: String) {
        requirePrefs().edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getRefreshToken(): String? = requirePrefs().getString(KEY_REFRESH_TOKEN, null)
}

