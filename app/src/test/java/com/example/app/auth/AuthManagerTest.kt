package com.example.app.auth

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.*

/**
 * Unit tests for AuthManager.
 *
 * Tests token persistence, expiration, and logout logic.
 * Mocks SharedPreferences to isolate AuthManager logic.
 */
class AuthManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPrefs: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Setup default mocks
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs)
        `when`(mockSharedPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
    }

    @Test
    fun testSaveTokenPersistsTokenAndTimestamp() {
        // Arrange
        val testToken = "test_bearer_token_1234567890"

        // Act
        AuthManager.saveToken(mockContext, testToken)

        // Assert
        verify(mockEditor).putString("token", testToken)
        verify(mockEditor).putLong(eq("token_timestamp"), anyLong())
        verify(mockEditor).apply()
    }

    @Test
    fun testIsLoggedInWithValidTokenReturnsTrueAndSetsBearer() {
        // Arrange
        val testToken = "test_bearer_token"
        val now = System.currentTimeMillis()
        `when`(mockSharedPrefs.getString("token", null)).thenReturn(testToken)
        `when`(mockSharedPrefs.getLong("token_timestamp", 0L)).thenReturn(now)

        // Act
        val result = AuthManager.isLoggedIn(mockContext)

        // Assert
        assert(result) { "isLoggedIn should return true for valid token" }
    }

    @Test
    fun testIsLoggedInWithExpiredTokenReturnsfalse() {
        // Arrange
        val testToken = "test_bearer_token"
        val expiredTime = System.currentTimeMillis() - (13 * 60 * 60 * 1000L) // 13 hours ago
        `when`(mockSharedPrefs.getString("token", null)).thenReturn(testToken)
        `when`(mockSharedPrefs.getLong("token_timestamp", 0L)).thenReturn(expiredTime)

        // Act
        val result = AuthManager.isLoggedIn(mockContext)

        // Assert
        assert(!result) { "isLoggedIn should return false for expired token" }
    }

    @Test
    fun testIsLoggedInWithNoTokenReturnsFalse() {
        // Arrange
        `when`(mockSharedPrefs.getString("token", null)).thenReturn(null)

        // Act
        val result = AuthManager.isLoggedIn(mockContext)

        // Assert
        assert(!result) { "isLoggedIn should return false when no token exists" }
    }

    @Test
    fun testLogoutClearsSharedPreferences() {
        // Arrange
        `when`(mockSharedPrefs.edit()).thenReturn(mockEditor)

        // Act
        AuthManager.logout(mockContext)

        // Assert
        verify(mockEditor).remove("token")
        verify(mockEditor).remove("token_timestamp")
        verify(mockEditor).apply()
    }

    @Test
    fun testIsTokenExpiredReturnsTrueForOldToken() {
        // Arrange
        val expiredTime = System.currentTimeMillis() - (13 * 60 * 60 * 1000L) // 13 hours ago
        `when`(mockSharedPrefs.getLong("token_timestamp", 0L)).thenReturn(expiredTime)

        // Act
        val result = AuthManager.isTokenExpired(mockContext)

        // Assert
        assert(result) { "isTokenExpired should return true for token older than 12 hours" }
    }

    @Test
    fun testIsTokenExpiredReturnsFalseForFreshToken() {
        // Arrange
        val now = System.currentTimeMillis()
        `when`(mockSharedPrefs.getLong("token_timestamp", 0L)).thenReturn(now)

        // Act
        val result = AuthManager.isTokenExpired(mockContext)

        // Assert
        assert(!result) { "isTokenExpired should return false for fresh token" }
    }
}

