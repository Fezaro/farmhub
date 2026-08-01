package com.farm_tech.farmhub.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * SecureTokenManager - persists auth tokens using EncryptedSharedPreferences backed by Android Keystore.
 * Use SecureTokenManager.initialize(context) once (Application.onCreate) before other calls.
 *
 * All public methods degrade gracefully when initialization has failed:
 * reads return null, writes are no-ops, clears are skipped.
 */
object SecureTokenManager {
    private const val TAG = "SecureTokenManager"
    private const val PREFS_NAME = "farmhub_secure_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Log.d(TAG, "EncryptedSharedPreferences initialised successfully")
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences initialisation failed — secure storage unavailable", e)
            prefs = null
        }
    }

    /** Returns the underlying prefs, or null if initialisation failed. */
    private fun prefs(): SharedPreferences? = prefs

    fun saveToken(token: String, expiresAtMillis: Long) {
        prefs()?.edit()?.apply {
            putString(KEY_AUTH_TOKEN, token)
            putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
            apply()
        } ?: Log.w(TAG, "saveToken skipped — secure storage not available")
    }

    fun getToken(): String? {
        val p = prefs() ?: run {
            Log.w(TAG, "getToken returning null — secure storage not available")
            return null
        }
        val token = p.getString(KEY_AUTH_TOKEN, null)
        val expires = p.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (token != null && expires > 0L && System.currentTimeMillis() >= expires) {
            // expired — clear and return null
            clearToken()
            return null
        }
        return token
    }

    fun clearToken() {
        prefs()?.edit()?.clear()?.apply()
            ?: Log.w(TAG, "clearToken skipped — secure storage not available")
    }

    fun saveRefreshToken(refreshToken: String) {
        prefs()?.edit()?.putString(KEY_REFRESH_TOKEN, refreshToken)?.apply()
            ?: Log.w(TAG, "saveRefreshToken skipped — secure storage not available")
    }

    fun getRefreshToken(): String? = prefs()?.getString(KEY_REFRESH_TOKEN, null)
}