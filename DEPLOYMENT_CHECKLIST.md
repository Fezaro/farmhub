# Session Persistence Implementation - Developer Checklist

## Pre-Deployment Verification

### Code Changes Verification
- [ ] **SessionRestoration.kt created**
  - [ ] `restoreSessionOnStartup()` method implemented
  - [ ] `establishSession()` method implemented
  - [ ] `destroySession()` method implemented
  - [ ] All methods have proper error handling
  
- [ ] **Splash.kt created**
  - [ ] `SplashScreen` composable created
  - [ ] Shows loading indicator for 300ms
  - [ ] Calls `SessionRestoration.restoreSessionOnStartup()`
  - [ ] Callback invoked with correct result
  
- [ ] **AuthManager.kt modified**
  - [ ] Removed 12-hour timeout constant
  - [ ] Removed `startTimeout()` method
  - [ ] `isLoggedIn()` checks token existence only
  - [ ] `saveToken()` passes `Long.MAX_VALUE` to SecureTokenManager
  - [ ] `logout()` still clears UserSession and token
  - [ ] Unnecessary imports removed
  
- [ ] **AppNavigation.kt modified**
  - [ ] Imports SessionRestoration and SplashScreen
  - [ ] `startDestination = AppRoutes.SPLASH`
  - [ ] SPLASH route defined with SplashScreen composable
  - [ ] SplashScreen callback properly updates state
  - [ ] Auth state listener includes SPLASH route
  - [ ] All other routes unchanged
  
- [ ] **AppRoutes.kt modified**
  - [ ] `const val SPLASH = "splash"` added at top
  
- [ ] **Auth.kt modified**
  - [ ] SessionRestoration imported
  - [ ] LoginForm uses `SessionRestoration.establishSession()`
  - [ ] All LoginResponse fields passed correctly
  - [ ] Callback still redirects to home screen

### Build Verification
- [ ] Project builds without compilation errors
- [ ] No missing import statements
- [ ] No undefined class references
- [ ] Lint warnings reviewed (should have none related to auth)

### Local Testing
- [ ] [ ] **Test Case 1: Login and Restart**
  - [ ] Launch app - see AUTH/INTRO screen
  - [ ] Login with valid credentials
  - [ ] Wait for redirect to INTRO
  - [ ] Force close app (kill from recent apps)
  - [ ] Reopen app
  - [ ] Should see SPLASH briefly, then INTRO (NOT AUTH)
  - [ ] Can access protected screens without re-login
  - [ ] **Status:** ✅ PASS / ❌ FAIL

- [ ] **Test Case 2: Logout and Restart**
  - [ ] Login with valid credentials
  - [ ] Navigate to Profile screen
  - [ ] Click "Sign Out"
  - [ ] Should see AUTH screen
  - [ ] Force close app
  - [ ] Reopen app
  - [ ] Should see SPLASH, then AUTH (NOT INTRO)
  - [ ] Cannot access protected screens
  - [ ] **Status:** ✅ PASS / ❌ FAIL

- [ ] **Test Case 3: Clear App Data**
  - [ ] Login with valid credentials
  - [ ] Settings → Apps → FarmHub → Storage → Clear All Data
  - [ ] Reopen app
  - [ ] Should see SPLASH, then AUTH (no session)
  - [ ] Must log in again
  - [ ] **Status:** ✅ PASS / ❌ FAIL

- [ ] **Test Case 4: Long Session**
  - [ ] Login with valid credentials
  - [ ] Leave app running for 10+ minutes
  - [ ] Navigate to different screens
  - [ ] Should NOT be logged out (unless 401 from backend)
  - [ ] **Status:** ✅ PASS / ❌ FAIL

- [ ] **Test Case 5: 401 Response Handling**
  - [ ] Login with valid credentials
  - [ ] In backend, manually expire the user's token
  - [ ] Try to access any protected screen
  - [ ] Make any API call
  - [ ] Should receive 401 response
  - [ ] App should redirect to AUTH automatically
  - [ ] **Status:** ✅ PASS / ❌ FAIL

### Crash Testing
- [ ] No crashes on app startup with no previous session
- [ ] No crashes on app startup with valid stored session
- [ ] No crashes on login
- [ ] No crashes on logout
- [ ] No crashes on 401 response
- [ ] No crashes with invalid token data
- [ ] No crashes if SecureTokenManager unavailable

### Log Analysis
```bash
# Run on device and check logs:
adb logcat | grep -E "(SessionRestoration|AuthManager|TokenValidator|OAuth|AppNavigation)"

# Should see logs like:
# SessionRestoration: Session successfully restored
# AuthManager: User is logged in
# TokenValidator: Token validation passed
# AppNavigation: Auth state changed

# Should NOT see:
# Any "NullPointerException"
# Any "UnknownClassException" 
# Any "Failed to initialize"
```

### Device Testing
- [ ] Tested on Android 10 device (minimum API 28)
- [ ] Tested on Android 12+ device
- [ ] Tested on tablet/landscape orientation
- [ ] Tested on device with low memory
- [ ] Tested on non-Google Play device (if applicable)

### Network Testing
- [ ] Login on WiFi
- [ ] Restart app on WiFi → session persists
- [ ] Login on Mobile Data
- [ ] Restart app on Mobile Data → session persists
- [ ] Login online, disable network, reopen app
  - [ ] Should show splash + cached session
  - [ ] Cannot make API calls but session visible

### Edge Cases
- [ ] User taps "Sign Out" while API call in progress
  - [ ] Should complete logout, not crash
- [ ] Actor immediately toggles network while restoring
  - [ ] Should handle gracefully, not crash
- [ ] Device timezone changes between app sessions
  - [ ] Token still valid (backend controls expiration)
- [ ] Device runs out of disk space
  - [ ] Should fallback to unencrypted storage
  - [ ] Should not crash

## Release Notes Preparation

### For Users
```
What's New:
- You now stay logged in until you explicitly sign out
- No more surprise logouts after 12 hours
- Session automatically restored when you reopen the app
- Same security - all data still encrypted

This works like WhatsApp, Telegram, and Instagram.
```

### For Support Team
```
Common Issues:

Q: Why do I see a loading screen on startup?
A: Session restoration (300ms). Shows while we restore your login info.

Q: Will I stay logged in forever?
A: No. When you click "Sign Out" or your account is disabled/deleted.

Q: Is my account secure?
A: Yes. Tokens are encrypted same as before. Device lock screen recommended.

Q: I got logged out, why?
A: Backend said your session is invalid (account change, login from new device).

Debugging:
- Logcat "SessionRestoration" to check session restoration
- Logcat "AuthManager" to check auth state changes
- Logcat "TokenValidator" to check token validation
```

## Rollback Plan

If issues discovered in production:

### Immediate Rollback (5 minutes)
```
1. Revert changes to AppNavigation.kt (change startDestination back to INTRO)
2. Revert changes to Auth.kt (remove SessionRestoration.establishSession call)
3. Build and deploy hotfix
4. Users can still login but won't have session persistence
5. No data loss
```

### Full Rollback (30 minutes)
```
1. Revert all files to previous version
2. Remove SessionRestoration.kt and Splash.kt
3. Restore App AppNavigation.kt and AppRoutes.kt
4. Build and deploy full rollback
5. App works exactly like before
6. No data loss
```

**Rollback Impact:** Users lose session persistence feature, but app works normally. No breaking changes.

## Monitoring Post-Deployment

### First Week - Critical Metrics
- [ ] Monitor crash rate (should be 0% change)
- [ ] Monitor login success rate (should be same or higher)
- [ ] Monitor session duration (should increase significantly)
- [ ] Monitor 401 error rate (should be same - backend controlled)
- [ ] Check support tickets for authentication issues

### Search Terms
```
- "Keep logging out"
- "Session expired"
- "Have to login every time"
- "Stays logged in forever"
- "Splash screen"
- "Loading screen"
```

### Success Metrics
✅ Users report being logged in longer/automatically
✅ Session-related support tickets decrease
✅ No increase in crash reports
✅ No increase in failed login reports

## Deployment Steps

### Step 1: Pre-Release Testing (Local)
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on test device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run test plan above
```

### Step 2: Pre-Release Testing (Staging)
```
1. Deploy to internal testing via Play Store
2. Let testers use for 1-2 days
3. Collect crash reports and feedback
4. Fix any critical issues found
```

### Step 3: Phased Production Rollout
```
Option A (Recommended):
- Day 1: Deploy to 5% of users, monitor metrics
- Day 2: Deploy to 25% of users
- Day 3: Deploy to 100% of users

Option B (Conservative):
- Day 1: Deploy to 1% of users
- Day 2: Deploy to 5% of users
- Day 3: Deploy to 25% of users
- Week 2: Deploy to 100% of users
```

### Step 4: Post-Deployment Monitoring (First Week)
```
- Monitor crash dashboard hourly for first day
- Monitor user feedback on store reviews
- Check support email for auth-related issues
- Monitor error logs for SessionRestoration errors
```

## Documentation for Developers

### If Adding New Protected Screen
Your screen will automatically benefit from session persistence:
```kotlin
composable("my_screen") {
    if (!isLoggedIn) {
        // Already guarded - user won't reach here if not logged in
        return@composable
    }
    MyScreen()
}
```

No changes needed to your code!

### If Creating New Login Flow
Must call SessionRestoration.establishSession():
```kotlin
SessionRestoration.establishSession(
    context = context,
    token = response.token,
    userId = response.userId,
    userName = response.userName,
    phone = response.phone
    // ... other fields
)
```

### If Creating New Logout Flow
Use SessionRestoration.destroySession():
```kotlin
SessionRestoration.destroySession(context)
```

This ensures complete cleanup.

## Final Sign-Off

- [ ] **Code Review:** All changes reviewed and approved
- [ ] **Testing:** All test cases passed
- [ ] **Documentation:** All docs updated
- [ ] **Release Notes:** Ready for customer communication
- [ ] **Rollback:** Plan documented and tested
- [ ] **Monitoring:** Dashboard ready to track metrics
- [ ] **Support:** Team briefed on changes

## Sign-Off

Ready for Production Deployment: **YES / NO**

- Developer: ________________  Date: ________
- QA Lead: ________________  Date: ________
- Deployment Lead: ________________  Date: ________


