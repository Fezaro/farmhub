# Session Persistence - Quick Start Guide

## For Developers Implementing Features

### Check If User Is Logged In

```kotlin
import com.farmtech.farmhub.auth.AuthManager

@Composable
fun MyScreen() {
    val context = LocalContext.current
    val isLoggedIn = AuthManager.isLoggedIn(context)
    
    if (isLoggedIn) {
        // Show protected content
    } else {
        // Prompt to login
    }
}
```

### Guard Protected Routes

This is already done in AppNavigation.kt for these routes:
- `/help` - FarmHelp
- `/videos` - Video feed
- `/video_detail/{id}` - Video details
- `/chat` - Messaging

**No changes needed** - navigation guards are automatic.

### Check Token Before API Calls

```kotlin
import com.farmtech.farmhub.auth.TokenValidator

fun makeProtectedApiCall() {
    if (!TokenValidator.isTokenValid()) {
        Log.w(TAG, "Token not valid. User not authenticated.")
        return
    }
    // Safe to make API call - token injected automatically
}
```

### Access Current User Data

```kotlin
import com.farmtech.farmhub.session.UserSession

val userId = UserSession.userId
val userName = UserSession.userName
val phone = UserSession.phone
val role = UserSession.role
val county = UserSession.county
```

### Handle Logout

Logout is handled automatically when:
- Backend returns 401 Unauthorized
- User clicks "Sign Out" in profile

**Automatic behavior:**
- Token cleared from storage
- User session cleared
- Navigation redirected to auth
- No manual action needed

## For QA / Testing

### Test Session Persistence

1. **Login and Restart App**
   - Login with valid credentials
   - Kill app (don't just minimize)
   - Reopen app
   - ✓ Should show home screen (not login)
   - ✓ Can access protected features

2. **Logout and Restart App**
   - Login normally
   - Go to Profile → Sign Out
   - Reopen app
   - ✓ Should show login screen
   - ✓ Cannot access protected features

3. **Backend Token Expiration**
   - Login normally
   - In backend, mark user's token as expired
   - Try to access any protected feature
   - ✓ App should redirect to login
   - ✓ User cannot proceed without re-login

4. **Network Offline**
   - Login successfully
   - Turn off network
   - Kill and reopen app
   - ✓ Splash screen briefly shown
   - ✓ Session restored from cache
   - Turn on network
   - ✓ Can make API calls normally

## For Backend Integration

### Assumptions About Backend

1. **Token Expiration**
   - Backend sends `expires` timestamp in LoginResponse
   - Backend returns 401 on expired tokens
   - Backend doesn't auto-refresh tokens

2. **Login Response Format**
   ```json
   {
     "status": "success",
     "token": "eyJ...",
     "issued": 1692547200000,
     "expires": 1692633600000,
     "newUser": false,
     "userDetails": {
       "id": "user123",
       "names": "John Doe",
       "phone": "0712345678",
       "role": "farmer",
       "county": "Nairobi",
       "subCounty": "Westlands",
       "paidUser": "true",
       "createdAt": "2023-08-20T10:00:00Z",
       "updatedAt": "2023-08-20T10:00:00Z"
     }
   }
   ```

3. **Required Empty Response for Logout**
   - POST `/auth/logout` returns 200 OK
   - Not implemented currently (users logout client-only)

### Updating Backend Role

If these situations happen on backend:

| Situation | Backend Action | App Behavior |
|-----------|----------------|--------------|
| User account deleted | Return 401 | Logout automatically |
| User account disabled | Return 401 | Logout automatically |
| Token expired | Return 401 | Logout automatically |
| Token still valid | Return 200 | User stays logged in |
| User password changed | Manual action needed* | App doesn't force re-login |
| Session from another device | Not supported yet | Login always accepted |

*For password changes: Currently no backend mechanism to invalidate sessions. Consider adding `/auth/invalidate-other-sessions` endpoint for security.

## Common Issues & Fixes

### Issue: Users Logged Out on Every App Restart

**Cause:** Token not being persisted to SecureTokenManager

**Fix:**
```kotlin
// Check that ApiClient.initialize() is called in MainActivity.onCreate()
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✓ This line MUST be present
        ApiClient.initialize(this)
        
        // ... rest of code
    }
}
```

### Issue: Persistent "Session Expired" After Login

**Cause:** Token validation failing or session data incomplete

**Fix:**
```kotlin
// In Auth.kt LoginForm, ensure SessionRestoration.establishSession() 
// is called with all required fields:

SessionRestoration.establishSession(
    context = context,
    token = it.token,                          // ✓ Required
    userId = it.userDetails.id,                // ✓ Required
    userName = it.userDetails.names,           // ✓ Required
    phone = it.userDetails.phone,              // ✓ Required
    role = it.userDetails.role,                // ✓ Recommended
    county = it.userDetails.county,            // Optional
    subCounty = it.userDetails.subCounty,      // Optional
    paidUser = it.userDetails.paidUser,        // Optional
    issued = it.issued,                        // ✓ Recommended
    expires = it.expires                       // ✓ Recommended
)
```

### Issue: Protected Screens Show "Not Authenticated" on Restart

**Cause:** UserSession not restored quickly enough

**Fix:** This is expected on first startup. The SplashScreen:
1. Loads token from secure storage (~50ms)
2. Restores UserSession data (~10ms)
3. Waits 300ms for smooth transition
4. Navigates to INTRO

User sees blank screen briefly, then content loads. This is by design (like WhatsApp/Telegram).

### Issue: "Authorization Header Missing" API Errors

**Cause:** Token not set in ApiClient

**Fix:**
1. Ensure login flow calls SessionRestoration.establishSession()
2. Verify SecureTokenManager.saveToken() succeeds
3. Check that ApiClient.setBearerToken() is called
4. Verify authInterceptor is added to OkHttpClient

```kotlin
// In ApiClient.kt - verify this exists:
private val authInterceptor = Interceptor { chain ->
    val original = chain.request()
    val builder = original.newBuilder()
    
    bearerToken?.let {
        builder.header("Authorization", "Bearer $it")
    }
    
    chain.proceed(builder.build())
}
```

## Configuration Checklist

- [ ] MainActivity calls ApiClient.initialize(this)
- [ ] ApiClient imports/uses SecureTokenManager
- [ ] authInterceptor added to OkHttpClient
- [ ] httpResponseInterceptor handles 401/403
- [ ] AppNavigation starts at SPLASH route
- [ ] SplashScreen component created
- [ ] SessionRestoration component created
- [ ] LoginForm calls SessionRestoration.establishSession()
- [ ] ProfileScreen calls SessionRestoration.destroySession() on logout
- [ ] AuthManager.isLoggedIn() checks SecureTokenManager first
- [ ] Protected routes have TokenValidator checks

## Performance Notes

### App Startup Time Impact

```
Old behavior:
- Start at INTRO instantly (~0ms)
- Load auth state lazily on demand

New behavior:
- Show SPLASH for 300-350ms
- Restore session in parallel (~50-100ms)
- Navigate to INTRO
- Total overhead: ~250-300ms
```

**Impact:** Negligible for mobile UX (users expect brief splash screens)

### Storage Impact

```
Token storage: ~1KB (encrypted)
Session data: ~0.5KB
Total: ~1.5KB per user

Affects: Device storage not significantly
Affects: Battery (encryption at startup ~50ms, negligible)
```

### Network Impact

```
Session restoration makes NO network calls.
Token validation happens client-side only.
If token truly expired, backend will respond with 401 on first call.
```

## Security Considerations

### ✅ What's Secured

- Tokens encrypted with AES256-GCM
- Protected by Android Keystore
- Cannot be read by other apps
- Cannot be read from device backup
- Cleared on app uninstall

### ⚠️ What's NOT Secured

- Tokens in memory could be read by:
  - Rooted devices with disabled SELinux
  - Debuggers attached to app
  - Compromised platform libraries
- UserSession data in memory is unencrypted
- Logout on other devices not implemented

### 🔒 Recommendations

1. **For Production:**
   - Use HTTPS only (already done)
   - Validate SSL certificates
   - Use certificate pinning (future enhancement)
   - Implement token refresh tokens
   - Add session invalidation endpoint

2. **For User Safety:**
   - Recommend users set device lock screen
   - Warn users about rooted devices
   - Implement inactivity timeout (future)
   - Log user activities for audit trail

## Upgrade Path

### From Old 12-Hour Timeout (v1)

```kotlin
// Old code - remove this:
private const val TOKEN_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L
fun startTimeout(context: Context) { ... } // Remove entire method

// New code - already implemented:
SessionRestoration.restoreSessionOnStartup() // Automatic
// No local timeouts - backend controls expiration
```

**Automatically handled:**
- Old tokens in SharedPreferences still loaded
- Migrated to SecureTokenManager on restart
- No manual intervention needed

### From OAuth/Firebase (v0)

Not applicable - this implementation is for backend-only auth.

## Monitoring & Debugging

### Enable Verbose Logs

```kotlin
// In any class
import android.util.Log

Log.d("AuthManager", "User is logged in")
Log.w("SessionRestoration", "Token expired")
Log.e("ApiClient", "401 response received")
```

### Check Session Status

```kotlin
import com.farmtech.farmhub.auth.TokenValidator

TokenValidator.logTokenStatus() // Prints token status to logs
```

Output example:
```
Token Status: hasToken=true, tokenLength=156, 
userId=user123, phone=0712345678
```

### Inspect Secure Storage (Developer Only)

```kotlin
// DO NOT USE IN PRODUCTION
// For testing only - view encrypted data

val token = SecureTokenManager.getToken()
Log.d("DEBUG", "Token: $token")

val loginPrefs = context.getSharedPreferences("user_session_prefs", Context.MODE_PRIVATE)
val userId = loginPrefs.getString("user_id", "NOT_SET")
Log.d("DEBUG", "UserId: $userId")
```

## Support & Contact

For issues with session persistence:

1. Check this troubleshooting guide
2. Review SESSION_PERSISTENCE_GUIDE.md
3. Check app logs (Android Studio Logcat)
4. Contact development team

Include in bug reports:
- Device OS version
- App version
- Steps to reproduce
- Logcat output showing auth-related messages

