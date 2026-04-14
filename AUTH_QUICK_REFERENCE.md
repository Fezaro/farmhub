# FarmHub Authentication - Quick Reference Guide

## 🔐 Protected vs Public Endpoints

### Public Endpoints (No Token Required)
```
POST /auth/login          → LoginRepository.login()
POST /auth/register       → SignUpRepository.signup()
```

### Protected Endpoints (Token Required)
```
GET  /auth/me             → ProfileRepository.getUserProfile()
GET  /posts               → PostRepository.getAllPosts()
POST /posts               → PostRepository.createPost()
POST /messaging           → MessageRepository.sendMessage()
GET  /messaging           → MessageRepository.getThreads()
GET  /messaging/{id}      → MessageRepository.getMessages()
GET  /media               → MediaRepository.getMediaFeed()
GET  /data/counties       → CountiesRepository.getCounties()
GET  /data/counties?c=    → CountiesRepository.getSubCounties()
GET  /posts/{id}          → PostDetailRepository.getPost()
```

## 🛡️ Token Validation Checks

### Before Every API Call
```kotlin
if (!TokenValidator.isTokenValid()) {
    return Response.error(401, errorBody)
}
```

### Before Protected Routes
```kotlin
if (!TokenValidator.isTokenValid()) {
    AuthManager.logout(context)
    navController.navigate(AppRoutes.AUTH)
}
```

### Before Form Submission
```kotlin
if (!TokenValidator.isTokenValid()) {
    _uiState.value = state.copy(
        errorMessage = "Session expired. Please log in again."
    )
    return
}
```

## 📱 Protected Screens

| Screen | Route | Requires Auth |
|--------|-------|---------------|
| FarmHelp | `/help` | ✅ YES |
| Videos | `/videos` | ✅ YES |
| Chat | `/chat` | ✅ YES |
| Video Detail | `/video_detail/{id}` | ✅ YES |
| Auth | `/auth` | ❌ NO |
| Intro | `/intro` | ❌ NO |
| Profile | `/profile` | ❌ NO (but mostly for logged-in users) |

## 🔄 Token Lifecycle

```
Save Token (After Login)
├─ AuthManager.saveToken(context, token)
├─ Persist in SharedPreferences
├─ Set timestamp for 12-hour timeout
└─ ApiClient.setBearerToken() → added to all requests

Use Token (On Protected API Call)
├─ TokenValidator.isTokenValid() → check before request
├─ authInterceptor → adds "Authorization: Bearer {token}"
├─ httpResponseInterceptor → validates response (401/403)
└─ Proceed or handle error

Check Expiration (Every 12 Hours)
├─ AuthManager.isTokenExpired(context)
├─ If true → AuthManager.logout() called
├─ Persist clearing triggered
└─ AppNavigation redirects to AUTH

Clear Token (On Logout)
├─ AuthManager.logout(context)
├─ SharedPreferences cleared
├─ UserSession cleared
├─ ApiClient token cleared
└─ Auth state set to false → navigation redirects
```

## 🚀 Common Operations

### Check if Logged In
```kotlin
val context = LocalContext.current
if (AuthManager.isLoggedIn(context)) {
    // Show authenticated UI
}
```

### Save Token After Login
```kotlin
AuthManager.saveToken(context, loginResponse.token)
UserSession.setSessionFromLoginResponse(loginResponse)
```

### Logout User
```kotlin
AuthManager.logout(context)
navController.navigate(AppRoutes.AUTH)
```

### Validate Token Before API Call
```kotlin
if (!TokenValidator.isTokenValid()) {
    return Response.error(401, errorBody)
}
val response = ApiClient.userService.getData().execute()
```

### Handle Expired Session in ViewModel
```kotlin
if (!TokenValidator.isTokenValid()) {
    _uiState.value = state.copy(
        errorMessage = "Your session has expired. Please log in."
    )
    return
}
```

## 📊 Response Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 400 | Bad Request | Show user error |
| 401 | Unauthorized | Logout & redirect |
| 403 | Forbidden | Log but don't logout |
| 500 | Server Error | Show user error |

## 🧪 Testing

### Run Tests
```bash
./gradlew test
```

### Test Specific Class
```bash
./gradlew test --tests AuthManagerTest
./gradlew test --tests TokenValidatorTest
```

### View Test Results
```bash
# Find report at:
app/build/reports/tests/testDebugUnitTest/index.html
```

## 🔍 Debugging

### View Auth Logs
```bash
adb logcat | grep -E "AuthManager|TokenValidator|ApiClient"
```

### View Token Status
```bash
adb logcat | grep "Token.*status\|Auth.*state"
```

### View API Errors
```bash
adb logcat | grep -E "code=401|code=403|Unauthorized"
```

### Enable Full Logging
```bash
adb logcat | grep -E "AuthManager|TokenValidator|ApiClient|Repository|Navigation"
```

## ⚙️ Configuration

### Token Timeout
```kotlin
// In AuthManager.kt
private const val TOKEN_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L  // 12 hours
```

### Minimum Token Length
```kotlin
// In TokenValidator.kt
if (token.length < 20) {  // Adjust if needed
    return false
}
```

## 📋 Checklist for New Protected Endpoint

When adding a new protected endpoint, ensure:

- [ ] Endpoint in UserService.kt (authenticated or public?)
- [ ] Repository method validates with `TokenValidator.isTokenValid()`
- [ ] Repository returns 401 if token invalid
- [ ] ViewModel calls repository method
- [ ] ViewModel handles errors gracefully
- [ ] Screen checks `TokenValidator.isTokenValid()`
- [ ] Route protected by `isLoggedIn` check
- [ ] Logging added (Tag: "RepositoryName")
- [ ] Tests added for validation logic

## 🎯 Key Components

| Component | Purpose | Location |
|-----------|---------|----------|
| TokenValidator | Pre-flight token checks | auth/TokenValidator.kt |
| AuthManager | Token lifecycle | auth/AuthManager.kt |
| ApiClient | HTTP client + interceptors | api/ApiClient.kt |
| Repositories | API calls with validation | repository/* |
| AppNavigation | Route protection | features/AppNavigation.kt |
| ViewModels | Business logic + validation | viewmodel/* |

## 📚 Documentation

- **AUTHENTICATION.md** - Complete reference guide
- **AUTHENTICATION_ARCHITECTURE.kt** - Detailed architecture
- **IMPLEMENTATION_SUMMARY.md** - Implementation checklist
- **THIS FILE** - Quick reference

## 🆘 Troubleshooting

### Problem: "No valid authentication token" on every request
**Solution**: Call `ApiClient.initialize(this)` in MainActivity.onCreate()

### Problem: Token not persisting after login
**Solution**: Call `AuthManager.saveToken(context, token)` after successful login

### Problem: Infinite redirect loop
**Solution**: 
1. Check SharedPreferences has token after login
2. Verify `AuthManager.isLoggedIn()` returns true with valid token
3. Check timestamp is being saved

### Problem: Session expires too quickly
**Solution**: Check 12-hour timeout in AuthManager is appropriate

### Problem: 401 errors on valid requests
**Solution**:
1. Verify token is in ApiClient (check logs)
2. Verify token is in Authorization header (check Logcat)
3. Verify server isn't invalidating tokens

## 💡 Tips & Tricks

1. **Debug Auth State**: Add logging to `AppNavigation` to see state changes
2. **Monitor Token**: Use `TokenValidator.logTokenStatus()` to check current state
3. **Test Expiration**: Can mock time or use shorter timeout in testing
4. **API Testing**: Use Postman to verify token is being sent
5. **Local Testing**: Use proxy (Charles/Fiddler) to inspect requests

## 🔗 Related Files

- `/app/src/main/java/com/example/app/auth/` - Auth components
- `/app/src/main/java/com/example/app/api/` - API client
- `/app/src/main/java/com/example/app/repository/` - Repositories
- `/app/src/main/java/com/example/app/features/AppNavigation.kt` - Navigation
- `/app/src/test/java/com/example/app/auth/` - Unit tests

---

**Quick Reference for FarmHub Authentication System**  
Keep this file handy for quick lookups during development!

