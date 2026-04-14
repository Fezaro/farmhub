package com.example.app.auth

import com.example.app.api.ApiClient
import com.example.app.session.UserSession
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TokenValidator.
 *
 * Tests pre-flight token validation logic.
 * Mocks ApiClient.currentToken() and UserSession data.
 */
class TokenValidatorTest {

    @Before
    fun setUp() {
        // Reset to known state before each test
        ApiClient.clearBearerToken()
        UserSession.clear()
    }

    @Test
    fun testIsTokenValidReturnsTrueForValidToken() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token.1234567890"
        ApiClient.setBearerToken(validToken)

        // Act
        val result = TokenValidator.isTokenValid()

        // Assert
        assert(result) { "isTokenValid should return true for valid token" }
    }

    @Test
    fun testIsTokenValidReturnsFalseForNullToken() {
        // Arrange
        ApiClient.clearBearerToken()

        // Act
        val result = TokenValidator.isTokenValid()

        // Assert
        assert(!result) { "isTokenValid should return false for null token" }
    }

    @Test
    fun testIsTokenValidReturnsFalseForBlankToken() {
        // Arrange
        ApiClient.setBearerToken("   ")

        // Act
        val result = TokenValidator.isTokenValid()

        // Assert
        assert(!result) { "isTokenValid should return false for blank token" }
    }

    @Test
    fun testIsTokenValidReturnsFalseForMalformedToken() {
        // Arrange
        val malformedToken = "short"  // Less than 20 characters
        ApiClient.setBearerToken(malformedToken)

        // Act
        val result = TokenValidator.isTokenValid()

        // Assert
        assert(!result) { "isTokenValid should return false for malformed token (too short)" }
    }

    @Test
    fun testIsSessionValidReturnsTrueForCompleteSession() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token.1234567890"
        ApiClient.setBearerToken(validToken)
        UserSession.userId = "user123"
        UserSession.phone = "0712345678"

        // Act
        val result = TokenValidator.isSessionValid()

        // Assert
        assert(result) { "isSessionValid should return true for complete session" }
    }

    @Test
    fun testIsSessionValidReturnsFalseWithoutToken() {
        // Arrange
        ApiClient.clearBearerToken()
        UserSession.userId = "user123"
        UserSession.phone = "0712345678"

        // Act
        val result = TokenValidator.isSessionValid()

        // Assert
        assert(!result) { "isSessionValid should return false without valid token" }
    }

    @Test
    fun testIsSessionValidReturnsFalseWithoutUserId() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token.1234567890"
        ApiClient.setBearerToken(validToken)
        UserSession.userId = null
        UserSession.phone = "0712345678"

        // Act
        val result = TokenValidator.isSessionValid()

        // Assert
        assert(!result) { "isSessionValid should return false without userId" }
    }

    @Test
    fun testIsSessionValidReturnsFalseWithoutPhone() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token.1234567890"
        ApiClient.setBearerToken(validToken)
        UserSession.userId = "user123"
        UserSession.phone = null

        // Act
        val result = TokenValidator.isSessionValid()

        // Assert
        assert(!result) { "isSessionValid should return false without phone" }
    }

    @Test
    fun testHasTokenReturnsTrueWhenTokenSet() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token"
        ApiClient.setBearerToken(validToken)

        // Act
        val result = TokenValidator.hasToken()

        // Assert
        assert(result) { "hasToken should return true when token is set" }
    }

    @Test
    fun testHasTokenReturnsFalseWhenTokenCleared() {
        // Arrange
        ApiClient.clearBearerToken()

        // Act
        val result = TokenValidator.hasToken()

        // Assert
        assert(!result) { "hasToken should return false when token is cleared" }
    }

    @Test
    fun testLogTokenStatusDoesNotCrash() {
        // Arrange
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid.token"
        ApiClient.setBearerToken(validToken)
        UserSession.userId = "user123"

        // Act & Assert - Should not throw exception
        try {
            TokenValidator.logTokenStatus()
            assert(true) { "logTokenStatus should not crash" }
        } catch (e: Exception) {
            assert(false) { "logTokenStatus should not throw: ${e.message}" }
        }
    }
}

