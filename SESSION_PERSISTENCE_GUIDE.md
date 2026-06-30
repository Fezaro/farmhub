# Session Persistence & Authentication Integration Guide

## Overview

This document describes the session persistence and authentication integration for the FarmHub Android app. The implementation follows WhatsApp/Telegram UX patterns where users remain logged in until explicitly logging out.

## Architecture

### Key Components

1. **SessionRestoration** (`SessionRestoration.kt`)
   - Handles session restoration on app startup
   - Persists complete session data (token + user info)
   - Centralizes session lifecycle management

2. **SecureTokenManager** (`SecureTokenManager.kt`)
   - Encrypts and stores authentication tokens using Android Keystore
   - Secured by AES256-GCM encryption
   - Handles token expiration validation

3. **AuthManager** (`AuthManager.kt`)
   - Provides single source of truth for authentication state
   - Respects backend token expiration (no local forced timeouts)
   - Observes auth state changes for reactive UI updates

4. **ApiClient** (`ApiClient.kt`)
   - Automatically injects bearer token into all requests
   - Centralizes 401/403 handling
   - Triggers logout only on backend-explicit unauthorized responses

5. **SplashScreen** (`Splash.kt`)
   - Handles app startup flow
   - Restores session before showing main UI
   - Provides smooth UX transition

6. **AppNavigation** (`AppNavigation.kt`)
   - Dynamic routing based on session status
   - Determines startup destination automatically
   - Maintains navigation guards on protected routes

## Flow Diagrams

### App Startup Flow

```
App Launch
    ↓
MainActivity.onCreate()
    ↓
ApiClient.initialize()
    ├─ SecureTokenManager.initialize()
    └─ Hydrate bearerToken if exists
    ↓
AppNavigation composable
    ↓
NavHost(startDestination = SPLASH)
    ↓
SplashScreen (shows temporary UI)
    ├─ SessionRestoration.restoreSessionOnStartup()
    │  ├─ Load token from SecureTokenManager
    │  ├─ Restore to ApiClient.setBearerToken()
    │  └─ Restore UserSession data
    ├─ Wait 300ms for smooth transition
    └─ Callback: onStartupCheckComplete()
       ├─ If logged in: Navigate to INTRO
       └─ If not logged in: Navigate to INTRO (will show auth prompt)
```

### Login Flow

```
User clicks Login
    ↓
LoginForm (Auth.kt)
    ├─ Calls viewModel.login()
    └─ LoginViewModel → LoginRepository.login()
       ├─ POST /auth/login
       └─ Callback: LoginResponse received
          ├─ ApiClient.setBearerToken()
          ├─ SecureTokenManager.saveToken()
          └─ onResult(loginResponse)
    ↓
LaunchedEffect(loginResult)
    ├─ SessionRestoration.establishSession()
    │  ├─ Save token to SecureTokenManager
    │  ├─ Save user data to SharedPreferences
    │  ├─ Update in-memory UserSession
    │  └─ AuthManager.saveToken() (compatibility)
    └─ onLoginSuccess()
       ├─ Update isLoggedIn state
       └─ Navigate to INTRO
```

### Logout Flow

```
User clicks Logout
    ↓
ProfileScreen → onSignOutClick callback
    ├─ AuthManager.logout(context)
    │  ├─ SessionRestoration.destroySession()
    │  │  ├─ SecureTokenManager.clearToken()
    │  │  ├─ SharedPreferences.clear()
    │  │  ├─ AuthManager.logout()
    │  │  └─ UserSession.clear()
    │  └─ ApiClient.clearBearerToken()
    ├─ isLoggedIn = false
    └─ Navigate to AUTH
```

### Token Expiration (401 Response)

```
Any Protected API Call
    ↓
ApiClient Interceptor
    ├─ Attach Authorization header
    └─ Make request
    ↓
Backend Response: 401 Unauthorized
    ↓
httpResponseInterceptor (ApiClient.kt)
    ├─ Detect 401 status
    ├─ AuthManager.handleUnauthorized()
    │  ├─ SessionRestoration.destroySession()
    │  └─ Clear all auth data
    └─ User continues; 401 may be caught by caller
    ↓
User navigates to protected route
    ├─ isLoggedIn is now false (due to auth state listener)
    └─ Navigation guard redirects to AUTH
```

## Key Features

### 1. **Persistent Sessions**
- ✅ Token persists across app restarts
- ✅ Token persists across device reboots
- ✅ Token persists across app updates
- ✅ Token restored automatically on startup

### 2. **Secure Storage**
- ✅ Tokens encrypted using Android Keystore (AES256-GCM)
- ✅ Protected against:
  - Unencrypted file access
  - Weak device encryption
  - App uninstall/reinstall (data cleared)
- ⚠️ NOT protected against:
  - Rooted devices with disabled SELinux
  - Compromised platform security

### 3. **Backend-Driven Expiration**
- ✅ Token expiration controlled by backend
- ✅ No arbitrary local timeout (removed 12-hour limit)
- ✅ 401 response from backend triggers logout
- ✅ Backend refresh tokens supported (future enhancement)

### 4. **Automatic Token Injection**
- ✅ Every request includes `Authorization: Bearer <token>` header
- ✅ Public endpoints excluded (login, register)
- ✅ Token added transparently via interceptor

### 5. **Session Restoration on Startup**
- ✅ User sees splash screen briefly
- ✅ Session restored before main UI shown
- ✅ Logged-in users skip auth screen automatically

## Implementation Details

### Session Restoration on Startup

```kotlin
// Called from SplashScreen
SessionRestoration.restoreSessionOnStartup(context)
    ├─ SecureTokenManager.getToken() // Encrypted storage
    ├─ ApiClient.setBearerToken(token)
    ├─ restoreUserSessionData(context) // From SharedPreferences
    └─ TokenValidator.isSessionValid() // Check required fields
```

### Establishing Session After Login

```kotlin
// Called after successful login
SessionRestoration.establishSession(
    context = context,
    token = loginResponse.token,
    userId = userDetails.id,
    userName = userDetails.names,
    phone = userDetails.phone,
    role = userDetails.role,
    county = userDetails.county,
    subCounty = userDetails.subCounty,
    paidUser = userDetails.paidUser,
    issued = loginResponse.issued,
    expires = loginResponse.expires
)
```

This performs:
1. Saves token to SecureTokenManager with backend-provided expiration
2. Saves user data to SharedPreferences for quick restoration
3. Updates in-memory UserSession for immediate access
4. Updates ApiClient bearer token

### Destroying Session on Logout

```kotlin
SessionRestoration.destroySession(context)
    ├─ SecureTokenManager.clearToken()
    ├─ SharedPreferences clear
    ├─ AuthManager.logout()
    └─ UserSession.clear()
    └─ ApiClient.clearBearerToken()
```

## Configuration & Customization

### Token Expiration Handling

The backend controls token expiration through:
- Token issued timestamp
- Token expiration timestamp
- 401 response code

Local app behavior:
- Stores backend-provided expiration time
- SecureTokenManager validates this on retrieval
- Any 401 response triggers immediate logout
- No local timeout overrides backend decision

### Session Timeout (Optional Future Enhancement)

To add automatic logout after N minutes of inactivity:
```kotlin
// In AuthManager
private var inactivityTimer: Job? = null

fun resetInactivityTimer(context: Context, timeoutMillis: Long = 15 * 60 * 1000) {
    inactivityTimer?.cancel()
    inactivityTimer = viewModelScope.launch {
        delay(timeoutMillis)
        logout(context)
    }
}
```

### Custom User Session Restoration

If your app needs to restore user profile data on startup:

```kotlin
// In SessionRestoration.restoreUserSessionData()
// Add API call to /auth/me if needed
val userProfile = withContext(Dispatchers.IO) {
    ApiClient.userService.getUserProfile().execute().body()?.data
}
userProfile?.let {
    UserSession.userId = it.id
    // ... etc
}
```

## Navigation Routes

| Route | Purpose | Requires Auth |
|-------|---------|---------------|
| `splash` | Startup/session restoration | N/A |
| `intro` | Home screen (unauthenticated) | ❌ |
| `auth` | Login/Signup forms | ❌ |
| `help` | FarmHelp (question submission) | ✅ |
| `videos` | Video feed | ✅ |
| `video_detail/{id}` | Video detail page | ✅ |
| `chat` | Messaging | ✅ |
| `profile` | User profile | ✅ |

## Error Handling

### Network Errors
- Connection timeout → Show "Unable to connect" message
- DNS failure → Show "Network unreachable" message
- Socket closed → Resume from splash on app restart

### Authentication Errors
- 401 Unauthorized → Logout, redirect to AUTH
- 403 Forbidden → Show "Access denied" message
- Missing token → Cannot make protected request (blocked by interceptor)

### Session Restoration Errors
- SecureTokenManager not initialized → Falls back to SharedPreferences
- Corrupted encryption data → Clears and forces re-login
- Missing UserSession data → App shows auth prompt

## Testing

### Manual Testing Checklist

- [ ] **Login persistence**
  - [ ] Login successfully
  - [ ] Close app completely
  - [ ] Reopen app → Should go to INTRO (not AUTH)
  - [ ] Can access protected features without re-login

- [ ] **Device restart**
  - [ ] Login successfully
  - [ ] Restart device
  - [ ] Open app → Should restore session automatically

- [ ] **App update**
  - [ ] Login successfully
  - [ ] "Update" app (clear build cache)
  - [ ] Open updated app → Session still valid

- [ ] **Token expiration (401)**
  - [ ] Manually set token to expire in backend
  - [ ] Make any API call
  - [ ] App receives 401 response
  - [ ] Automatically redirected to AUTH

- [ ] **Manual logout**
  - [ ] Login successfully
  - [ ] Click Profile → Sign Out
  - [ ] Verify redirected to AUTH
  - [ ] Reopen app → Should show AUTH (not skip to home)

- [ ] **Network offline**
  - [ ] Login successfully
  - [ ] Disable network
  - [ ] Close and reopen app
  - [ ] Should show splash, then auth content
  - [ ] Re-enable network
  - [ ] Session should be restored on next network call

### Unit Testing

```kotlin
// Example: Test session restoration
@Test
fun testSessionRestoration_WithValidToken() {
    // Arrange
    SecureTokenManager.saveToken("test_token_123", Long.MAX_VALUE)
    
    // Act
    val result = SessionRestoration.restoreSessionOnStartup(context)
    
    // Assert
    assertTrue(result)
    assertEquals("test_token_123", ApiClient.currentToken())
}

@Test
fun testSessionRestoration_NoToken() {
    // Arrange
    SecureTokenManager.clearToken()
    
    // Act
    val result = SessionRestoration.restoreSessionOnStartup(context)
    
    // Assert
    assertFalse(result)
    assertNull(ApiClient.currentToken())
}

@Test
fun testLogout_ClearsAllData() {
    // Arrange
    SessionRestoration.establishSession(
        context, "token", "user123", "John", "0712345678"
    )
    
    // Act
    SessionRestoration.destroySession(context)
    
    // Assert
    assertNull(SecureTokenManager.getToken())
    assertNull(ApiClient.currentToken())
    assertNull(UserSession.userId)
}
```

## Migration From Old Implementation

If upgrading from the old 12-hour timeout system:

*Old behavior:*
```kotlin
// Old: 12-hour hardcoded timeout
private const val TOKEN_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L
// Would force logout after 12 hours regardless of backend
```

*New behavior:*
```kotlin
// New: Backend-driven expiration
SecureTokenManager.saveToken(token, Long.MAX_VALUE) // No local timeout
// Backend controls when user is logged out (via 401 responses)
```

### Data Migration:
- Old SharedPreferences tokens automatically loaded on first run
- Tokens migrated to SecureTokenManager automatically
- No manual migration needed

## Future Enhancements

1. **Token Refresh**
   - Implement automatic token refresh before expiration
   - Use refresh token if available

2. **Inactivity Timeout**
   - Auto-logout after N minutes of no activity
   - Keep-alive pings to backend

3. **Session Sync**
   - Sync logout across multiple devices
   - Backend push notification on session invalidation

4. **Biometric Authentication**
   - Optional: Biometric unlock after restart
   - Keep session secure with fingerprint/face verification

5. **Offline Support**
   - Cache profile data for offline use
   - Sync on reconnection

## Troubleshooting

### Users Logged Out Unexpectedly
1. Check backend token expiration time
2. Verify `expires` field in LoginResponse is correct
3. Check for unexpected 401 responses in logs
4. Verify SecureTokenManager encryption is working

### Session Not Restored on App Restart
1. Check SecureTokenManager.initialize() called in MainActivity.onCreate()
2. Verify EncryptedSharedPreferences dependency is available
3. Check device Keystore is accessible
4. Verify token not expired (check expires timestamp)

### Token Not Injected Into Requests
1. Verify ApiClient.initialize() called before first request
2. Check authInterceptor is added to OkHttpClient
3. Verify endpoint is not in publicPaths list
4. Check `Authorization` header in network logs

### Infinite Login Loop
1. Check SessionRestoration.restoreSessionOnStartup() return value
2. Verify AppNavigation.startDestination is SPLASH
3. Check SplashScreen callback is invoked with correct value
4. Review TokenValidator.isSessionValid() logic

## References

- [Android Keystore System Documentation](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences Documentation](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Retrofit Authentication Documentation](https://square.github.io/retrofit/2.x/retrofit/)
- [OkHttp Interceptor Documentation](https://square.github.io/okhttp/interceptors/)

