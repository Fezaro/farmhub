# FarmHub Authentication & Security Implementation

## Overview

This document outlines the authentication enforcement system implemented across the FarmHub Android app. The system ensures that all protected API endpoints require a valid bearer token, with proper validation, logging, and error handling following Senior Android developer best practices.

## Key Features

✅ **Token-Based Authentication**: All protected endpoints require a bearer token in the `Authorization` header  
✅ **Pre-Flight Validation**: TokenValidator prevents network calls with invalid tokens  
✅ **Two-Level Access Control**: Route-level and screen-level authentication checks  
✅ **Automatic Logout**: 401 responses trigger automatic logout and redirect  
✅ **Token Timeout**: 12-hour token expiration with automatic cleanup  
✅ **Comprehensive Logging**: All auth events logged with structured tags  
✅ **Secure Storage**: Token persisted in SharedPreferences with timestamp validation  
✅ **Error Handling**: Graceful error messages for expired sessions  

## Architecture

### Components

#### 1. **TokenValidator** (`auth/TokenValidator.kt`)
Pre-flight token validation utility used by repositories to prevent unnecessary network calls with invalid tokens.

**Key Methods:**
- `isTokenValid()` - Checks token exists and has valid format (≥20 chars)
- `isSessionValid()` - Checks token AND session data (userId, phone)
- `hasToken()` - Simple boolean check if token is set
- `logTokenStatus()` - Debug logging of current auth state

**Usage:**
```kotlin
if (!TokenValidator.isTokenValid()) {
    return Response.error(401, errorBody)
}
// Proceed with API call
```

#### 2. **AuthManager** (`auth/AuthManager.kt`)
Singleton managing authentication state, token persistence, and session lifecycle.

**Key Methods:**
- `isLoggedIn(context)` - Single source of truth for auth state
- `saveToken(context, token)` - Persist token with 12-hour expiration
- `logout(context)` - Hard logout clearing all auth data
- `handleUnauthorized(context)` - Handle 401 responses
- `isTokenExpired(context)` - Check if token expired without logout
- `getAuthState()` - Get StateFlow for reactive updates

**Token Lifecycle:**
- Save: Timestamp recorded in SharedPreferences
- Check: Age compared to 12-hour timeout
- Expire: Automatic logout if age exceeds timeout
- Clear: All auth data and token removed on logout

#### 3. **ApiClient** (`api/ApiClient.kt`)
Retrofit HTTP client with authentication interceptors.

**Interceptors:**
- **authInterceptor**: Adds `Authorization: Bearer <token>` header to all requests
- **httpResponseInterceptor**: Handles 401/403 responses
  - 401: Triggers `AuthManager.handleUnauthorized()` → logout
  - 403: Logs but doesn't logout (permission issue, not auth issue)
  - Other: Normal error handling

**Initialization:**
```kotlin
// Must be called in MainActivity.onCreate()
ApiClient.initialize(this)
```

#### 4. **Repositories** (All with Token Validation)
All repositories validate token before making API calls:

**PostRepository**
- `createPost()` - Create new post with image and description
- `getAllPosts()` - Fetch all posts

**MessageRepository**
- `sendMessage()` - Send message with optional attachment
- `getThreads()` - Fetch conversation threads
- `getMessages()` - Fetch messages in a thread

**MediaRepository**
- `getMediaFeed()` - Fetch media/video feed

**CountiesRepository**
- `getCounties()` - Fetch all counties with caching
- `getSubCounties()` - Fetch sub-counties for a county

**PostDetailRepository**
- `getPost()` - Fetch single post by ID

#### 5. **Navigation** (`features/AppNavigation.kt`)
Two-level authentication checks on all protected routes.

**Protected Routes:**
- `HELP` - FarmHelp assistance screen
- `VIDEOS` - Video/media feed screen
- `CHAT` - Messaging screen
- `VIDEO_DETAIL` - Individual video details

**Checks:**
```
Route Level:
- isLoggedIn check before navigation

Screen Level:
- TokenValidator.isTokenValid() at composition
- Automatic logout and redirect if invalid
```

#### 6. **ViewModels** (Enhanced)
FarmHelpViewModel and others validate token before operations.

**Example:**
```kotlin
fun submitPost(context: Context) {
    if (!TokenValidator.isTokenValid()) {
        _uiState.value = state.copy(
            errorMessage = "Your session has expired. Please log in again."
        )
        return
    }
    // Proceed with submission
}
```

## Authentication Flow

### User Login
```
1. User enters phone/password on AuthScreen
2. LoginViewModel calls LoginRepository.login()
3. LoginRepository makes POST /auth/login (public endpoint, no token)
4. On success:
   - AuthManager.saveToken() persists token with timestamp
   - ApiClient.setBearerToken() updates HTTP interceptor
   - User navigated to INTRO screen
5. Subsequent requests now include token via authInterceptor
```

### Protected API Call
```
1. ViewModel/Composable calls Repository method
2. Repository checks TokenValidator.isTokenValid()
3. If invalid, returns 401 error immediately
4. If valid, makes API call with token
5. authInterceptor adds Authorization header
6. httpResponseInterceptor validates response:
   - 200-399: Success, process response
   - 401: Call AuthManager.handleUnauthorized() → logout
   - 403: Log but don't logout
   - 500+: Log error
```

### Token Expiration
```
1. AppNavigation observes AuthManager auth state
2. After 12 hours:
   - AuthManager.isLoggedIn() returns false
   - Auth state changes to false
   - Navigation redirects to AUTH screen
OR
2. On next API call:
   - TokenValidator.isTokenValid() returns false
   - Repository returns 401 without network call
   - ViewModel shows "Session expired" message
```

### User Logout
```
1. User taps "Sign Out" on ProfileScreen, OR
2. Server returns 401, OR
3. Token expires after 12 hours
4. AuthManager.logout(context):
   - SharedPreferences cleared
   - UserSession cleared
   - ApiClient token cleared
   - Auth state set to false
5. Navigation redirects to AUTH screen
```

## Security Practices

### 1. No Public Endpoints with Sensitive Data
Only `/auth/login` and `/auth/register` are public. All other endpoints require token.

### 2. Pre-Flight Validation
```kotlin
// Prevents unnecessary network requests with invalid tokens
if (!TokenValidator.isTokenValid()) {
    return Response.error(401, errorBody)
}
```

### 3. Two-Level Authentication Checks
- **Route Level**: Prevents navigation to protected screens
- **Screen Level**: Validates token at composition time (defense-in-depth)

### 4. 401 Response Handling
```kotlin
// httpResponseInterceptor catches 401
401 → AuthManager.handleUnauthorized() 
    → logout() 
    → AppNavigation.redirectToAuth()
```

### 5. Token Storage
- SharedPreferences with `MODE_PRIVATE`
- Token timestamp for validation (not plain text storage)
- 12-hour timeout with automatic cleanup
- Cleared immediately on logout

### 6. Comprehensive Logging
```
Log Tags:
- AuthManager: Token lifecycle
- TokenValidator: Token validation
- ApiClient: HTTP/interceptor logs
- Repositories: API call logs
- AppNavigation: Navigation logs
- ViewModels: ViewModel logic
```

## Usage Examples

### Example 1: Check if Logged In
```kotlin
val context = LocalContext.current

if (AuthManager.isLoggedIn(context)) {
    // Show authenticated UI
} else {
    // Navigate to login
    navController.navigate(AppRoutes.AUTH)
}
```

### Example 2: Token Validation Before API Call
```kotlin
suspend fun fetchData(): Response<MyData> = withContext(Dispatchers.IO) {
    if (!TokenValidator.isTokenValid()) {
        Log.w(TAG, "Token invalid, aborting API call")
        return@withContext Response.error(401, errorBody)
    }
    
    Log.d(TAG, "Token valid, making API call")
    ApiClient.userService.getData().execute()
}
```

### Example 3: Handle Expiration in ViewModel
```kotlin
fun submitForm(context: Context) {
    if (!TokenValidator.isTokenValid()) {
        _uiState.value = state.copy(
            errorMessage = "Your session has expired. Please log in again."
        )
        return
    }
    
    // Proceed with submission
}
```

### Example 4: Protected Route with Double-Check
```kotlin
composable(AppRoutes.VIDEOS) {
    AppScaffold(...) {
        // Route-level check
        if (!isLoggedIn) {
            LaunchedEffect(Unit) {
                navController.navigate(AppRoutes.AUTH)
            }
            return@AppScaffold
        }
        
        // Screen-level check
        if (!TokenValidator.isTokenValid()) {
            LaunchedEffect(Unit) {
                AuthManager.logout(context)
                navController.navigate(AppRoutes.AUTH)
            }
            return@AppScaffold
        }
        
        VideoScreen(navController)
    }
}
```

## Testing

### Unit Tests Included

#### AuthManagerTest.kt
- ✓ Token save persists token and timestamp
- ✓ isLoggedIn returns true for valid token
- ✓ isLoggedIn returns false for expired token
- ✓ isLoggedIn returns false for no token
- ✓ logout clears SharedPreferences
- ✓ isTokenExpired works correctly

#### TokenValidatorTest.kt
- ✓ isTokenValid returns true for valid token
- ✓ isTokenValid returns false for null/blank/malformed token
- ✓ isSessionValid requires token and session data
- ✓ hasToken works correctly
- ✓ logTokenStatus doesn't crash

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests AuthManagerTest

# Run test with logging
./gradlew test --tests AuthManagerTest --info
```

## Logging & Debugging

### Debug Authentication Issues
```bash
# Filter logs to auth-related
adb logcat | grep -E "AuthManager|TokenValidator|ApiClient"

# View full auth state
adb logcat | grep "AuthManager.*Status\|Token.*Status"

# Monitor token lifecycle
adb logcat | grep -E "saved|expired|logout|cleared"
```

### Common Issues

**Issue: "No valid authentication token" errors**
- Check: Is `ApiClient.initialize(this)` called in MainActivity?
- Check: Is `AuthManager.saveToken()` called after login?
- Check: Is TokenValidator check happening before API call?

**Issue: App keeps redirecting to AUTH screen**
- Check: Is token in SharedPreferences?
- Check: Is token timestamp valid?
- Check: Did server return 401?

**Issue: "Session expired" appears randomly**
- Check: 12-hour timeout may have elapsed
- Check: Server may have invalidated token
- Check: AppNavigation auth state observer working?

## Migration Guide

For existing code without authentication enforcement:

### Step 1: Ensure ApiClient Initialized
```kotlin
// In MainActivity.onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ApiClient.initialize(this)  // Add this
    // ... rest of onCreate
}
```

### Step 2: Update Repositories
```kotlin
// Before
suspend fun getData(): Response<MyData> = withContext(Dispatchers.IO) {
    ApiClient.userService.getData().execute()
}

// After
suspend fun getData(): Response<MyData> = withContext(Dispatchers.IO) {
    if (!TokenValidator.isTokenValid()) {
        return@withContext Response.error(401, errorBody)
    }
    ApiClient.userService.getData().execute()
}
```

### Step 3: Protect Routes
```kotlin
// Before
composable(AppRoutes.VIDEOS) {
    VideoScreen()
}

// After
composable(AppRoutes.VIDEOS) {
    if (!isLoggedIn) {
        LaunchedEffect(Unit) { navController.navigate(AppRoutes.AUTH) }
        return@composable
    }
    if (!TokenValidator.isTokenValid()) {
        LaunchedEffect(Unit) { navController.navigate(AppRoutes.AUTH) }
        return@composable
    }
    VideoScreen()
}
```

## Future Enhancements

1. **Token Refresh**: Implement refresh token endpoint for seamless re-authentication
2. **Biometric Auth**: Add fingerprint/face unlock with Keystore encryption
3. **Session Persistence**: Store session history in Room database
4. **Multi-Device Support**: Register devices and allow remote logout
5. **Rate Limiting**: Track failed login attempts and implement lockout

## References

- **AUTHENTICATION_ARCHITECTURE.kt**: Detailed architecture documentation
- **AuthManager.kt**: Token lifecycle management
- **TokenValidator.kt**: Pre-flight validation utility
- **ApiClient.kt**: HTTP client and interceptors
- **AuthManagerTest.kt**: Unit tests for auth logic
- **TokenValidatorTest.kt**: Unit tests for validation logic

## Support

For authentication-related issues:

1. Enable verbose logging: See "Logging & Debugging" section
2. Check tests: Run test suite to verify assumptions
3. Review authentication flow: See "Authentication Flow" section
4. Check implementation checklist: Ensure all steps completed

---

**Last Updated**: April 14, 2026  
**Status**: Production Ready  
**Enforced Standards**: Senior Android Developer Practices

