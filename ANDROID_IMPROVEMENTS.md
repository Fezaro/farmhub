# Recommended Android Improvements
## Based on Backend Integration & Production Readiness

**Status:** Suggested Enhancements  
**Priority:** Post-Beta  
**Audience:** Android Development Team

---

## Overview

The Android implementation is **well-structured and production-ready**. This document suggests improvements for when the backend is fully integrated and stable. These are NOT blockers but quality-of-life improvements.

---

## High Priority Improvements

### 1. Persistent Token Storage

**Current Issue:** Token lost on app restart; user must re-login  
**Current Code:** Token stored in volatile `ApiClient.bearerToken` field  
**Impact:** Poor UX; user re-authentication required constantly

**Recommended Solution:**

```kotlin
// Use Android KeyStore + EncryptedSharedPreferences (Jetpack Security)
class SecureTokenManager {
    private val encryptedSharedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_tokens",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String, expiresAt: Long) {
        encryptedSharedPrefs.edit().apply {
            putString("auth_token", token)
            putLong("token_expires_at", expiresAt)
            apply()
        }
    }

    fun getToken(): String? {
        val token = encryptedSharedPrefs.getString("auth_token", null)
        val expiresAt = encryptedSharedPrefs.getLong("token_expires_at", 0)
        
        // Check if expired
        if (token != null && System.currentTimeMillis() >= expiresAt) {
            clearToken()  // Auto-logout on expiry
            return null
        }
        return token
    }

    fun clearToken() {
        encryptedSharedPrefs.edit().clear().apply()
    }
}

// Register in Application.onCreate()
SecureTokenManager.saveToken(loginResponse.token, loginResponse.expires)

// Load on app start
val savedToken = SecureTokenManager.getToken()
if (savedToken != null) {
    ApiClient.setBearerToken(savedToken)
    // Skip login screen, go to home
}
```

**Dependencies:**
```gradle
dependencies {
    implementation "androidx.security:security-crypto:1.1.0-alpha06"
}
```

**User Experience:** User stays logged in across app restarts (better retention)

---

### 2. Token Refresh Endpoint Support

**Current Issue:** 24-hour token expiry; user stuck if token expires during session  
**Recommended Backend Endpoint:**

```
POST /auth/refresh
Request: { "refreshToken": "refresh_token_jwt" }
Response: {
  "token": "new_access_token",
  "refreshToken": "new_refresh_token",
  "expires": 1234567890
}
```

**Android Implementation:**

```kotlin
// In ApiClient.kt - add token refresh interceptor
private val tokenRefreshInterceptor = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    
    if (response.code == 401 && !chain.request().url.encodedPath.contains("/auth/refresh")) {
        // Token expired; try refresh
        val refreshToken = SecureTokenManager.getRefreshToken()
        if (refreshToken != null) {
            try {
                val refreshResponse = ApiClient.userService.refreshToken(
                    RefreshTokenRequest(refreshToken)
                ).execute()
                
                if (refreshResponse.isSuccessful) {
                    val newToken = refreshResponse.body()?.token
                    val newRefresh = refreshResponse.body()?.refreshToken
                    SecureTokenManager.saveToken(newToken, newRefresh)
                    
                    // Retry original request with new token
                    val newRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@Interceptor chain.proceed(newRequest)
                }
            } catch (e: Exception) {
                // Refresh failed; clear and redirect to login
                SecureTokenManager.clearToken()
            }
        }
    }
    response
}

// Add to interceptor chain
OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .addInterceptor(tokenRefreshInterceptor)  // NEW
    .addInterceptor(httpResponseInterceptor)
    .build()

// In UserService.kt
@POST("auth/refresh")
fun refreshToken(@Body request: RefreshTokenRequest): Call<RefreshTokenResponse>
```

**User Experience:** Seamless token refresh without forcing re-login

---

### 3. Server-Side Pagination Support

**Current Issue:** All media downloaded in single request; scalability issue at volume  
**Current Implementation:** Client-side pagination (slicing in-memory list)

**Recommended Backend Enhancement:**

```
GET /media?limit=20&offset=0
GET /posts?limit=20&offset=0
```

**Android Implementation Update:**

```kotlin
class MediaViewModel(...) : ViewModel() {
    
    private var currentOffset = 0
    private val pageSize = 20
    private val allItems = mutableListOf<VideoItem>()
    
    fun loadMedia(refresh: Boolean = false) {
        if (refresh) {
            currentOffset = 0
            allItems.clear()
        }
        
        viewModelScope.launch {
            try {
                val resp = repository.getMediaFeed(limit = pageSize, offset = currentOffset)
                if (resp.isSuccessful) {
                    val newItems = resp.body()?.media ?: emptyList()
                    if (newItems.isNotEmpty()) {
                        allItems.addAll(newItems.map { /* convert to VideoItem */ })
                        currentOffset += pageSize
                        hasMore = newItems.size == pageSize
                    }
                    emitFilteredSuccess()
                }
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error(e.message)
            }
        }
    }
    
    fun loadNextPage() {
        if (!hasMore) return
        viewModelScope.launch {
            val resp = repository.getMediaFeed(limit = pageSize, offset = currentOffset)
            // ... append to allItems ...
        }
    }
}

// In MediaRepository
suspend fun getMediaFeed(limit: Int = 20, offset: Int = 0): Response<MediaFeedResponse> {
    return ApiClient.userService.getMediaFeed(limit, offset).execute()
}

// In UserService
@GET("media")
fun getMediaFeed(
    @Query("limit") limit: Int = 20,
    @Query("offset") offset: Int = 0
): Call<MediaFeedResponse>
```

**Benefits:** 
- Efficient data transfer (only 20 items per request)
- Better memory usage on device
- Scales to thousands of videos

---

## Medium Priority Improvements

### 4. Real-Time Messaging with WebSocket

**Current Issue:** Polling GET /messaging/{recipientId} at fixed intervals  
**Inefficient:** Wasteful network calls, messages have latency

**Recommended Architecture:**

```kotlin
// Add OkHttp WebSocket client
class ChatWebSocketClient {
    private val webSocket: WebSocket by lazy {
        val request = Request.Builder()
            .url("wss://api.farmers-hub.co.ke/ws/messages")
            .header("Authorization", "Bearer ${ApiClient.currentToken()}")
            .build()
        
        httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                // Parse incoming message
                val message = gson.fromJson(text, MessageItemResponse::class.java)
                _incomingMessages.tryEmit(message)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("ChatWebSocket", "Connection failed: ${t.message}")
                // Auto-reconnect with exponential backoff
            }
        })
    }
}

// Use in MessageViewModel
class MessageViewModel(...) : ViewModel() {
    private val chatClient = ChatWebSocketClient()
    
    fun subscribeToMessages(recipientId: String) {
        viewModelScope.launch {
            chatClient.incomingMessages.collect { message ->
                onMessageReceived(message)
            }
        }
    }
}
```

**Benefits:**
- Real-time message delivery
- Reduced network traffic
- Better user experience

---

### 5. Image Upload Progress Tracking

**Current Issue:** Large image uploads show no progress  
**User Experience:** Upload appears frozen

**Recommended Implementation:**

```kotlin
class FarmHelpViewModel : ViewModel() {
    
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress
    
    fun submitPost(context: Context) {
        // ... existing validation ...
        
        viewModelScope.launch {
            try {
                val file = /* prepare file */
                
                // Use custom RequestBody that reports progress
                val progressRequestBody = ProgressRequestBody(file) { progress ->
                    _uploadProgress.value = progress
                }
                
                val requestFile = MultipartBody.Part.createFormData(
                    "image",
                    file.name,
                    progressRequestBody
                )
                
                PostRepository().createPost(
                    image = requestFile,
                    description = descBody,
                    onResult = { response -> /* ... */ }
                )
            } catch (e: Exception) {
                // ...
            }
        }
    }
}

// Custom RequestBody for progress tracking
class ProgressRequestBody(
    private val file: File,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    
    override fun contentType() = "image/jpeg".toMediaType()
    
    override fun contentLength() = file.length()
    
    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8192)
        file.inputStream().use { inputStream ->
            var uploaded = 0L
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                uploaded += read
                sink.write(buffer, 0, read)
                onProgress(uploaded.toFloat() / file.length())
            }
        }
    }
}

// In UI - display progress bar
@Composable
fun UploadProgressBar(progress: Float) {
    Column {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${(progress * 100).toInt()}% uploaded")
    }
}
```

**User Experience:** Visual feedback during upload

---

### 6. Offline Mode with Cache

**Current Issue:** App completely non-functional offline  
**Recommended Solution:** Cache API responses locally

```kotlin
// Add local persistence
@Database(entities = [CachedPost::class, CachedMedia::class], version = 1)
abstract class FarmHubDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun mediaDao(): MediaDao
}

// In MediaRepository - add offline fallback
class MediaRepository {
    suspend fun getMediaFeed(): Response<MediaFeedResponse> {
        return try {
            val response = ApiClient.userService.getMediaFeed().execute()
            if (response.isSuccessful) {
                // Cache successful response
                database.mediaDao().insertAll(response.body()?.media ?: emptyList())
            }
            response
        } catch (e: Exception) {
            // Network error; try cached data
            val cached = database.mediaDao().getAll()
            if (cached.isNotEmpty()) {
                Response.success(MediaFeedResponse(
                    status = "cached",
                    media = cached
                ))
            } else {
                Response.error(500, /* ... */)
            }
        }
    }
}

// In UI - indicate cached data
if (state.status == "cached") {
    Text("Offline - showing cached data", modifier = Modifier.fillMaxWidth().background(Color.Yellow))
}
```

---

### 7. Error Analytics & Crash Reporting

**Current Issue:** Errors logged locally; no visibility into production failures  
**Recommended Library:** Sentry or Firebase Crashlytics

```kotlin
// Setup in Application.onCreate()
Sentry.init { options ->
    options.dsn = "https://xxx@sentry.io/xxxx"
    options.environment = BuildConfig.BUILD_TYPE
    options.attachStacktrace = true
    options.tracesSampleRate = 1.0
}

// Capture exceptions
try {
    // API call
} catch (e: Exception) {
    Sentry.captureException(e)
    val context = mapOf(
        "endpoint" to "/media",
        "userId" to UserSession.userId
    )
    Sentry.addBreadcrumb("API Error", category = "api", level = SentryLevel.ERROR)
}

// Breadcrumbs for navigation
Sentry.addBreadcrumb(
    "Navigation",
    category = "navigation",
    message = "User navigated to DetailScreen",
    level = SentryLevel.INFO
)
```

---

## Low Priority (Nice-to-Have)

### 8. Dark Mode Support

**Current Status:** Partially implemented  
**Enhancement:** Add system theme detection

```kotlin
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val isDarkMode = isSystemInDarkTheme()
    
    MaterialTheme(
        colorScheme = if (isDarkMode) darkColorScheme else lightColorScheme,
        content = content
    )
}
```

---

### 9. Search & Filtering Enhancement

**Current Issue:** Basic category filtering; no full-text search

**Recommendation:** Add search endpoint to backend

```
GET /media/search?q=maize&category=crop_farming
```

```kotlin
@GET("media/search")
fun searchMedia(
    @Query("q") query: String,
    @Query("category") category: String? = null,
    @Query("limit") limit: Int = 20
): Call<MediaFeedResponse>
```

---

### 10. Share & Social Features

**Current Issue:** No share buttons  
**Recommendation:** Add native share deeplinks

```kotlin
fun sharePost(post: Post) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, buildString {
            append("Check this post: ${post.description}\n")
            append("https://farmhub.app/post/${post.id}\n")
            append("Shared from FarmHub Android App")
        })
        type = "text/plain"
    }
    startActivity(Intent.createChooser(intent, "Share Post"))
}
```

---

## Architecture & Testing Improvements

### 11. Dependency Injection (Hilt)

**Current Status:** Manual repository creation in ViewModels

```kotlin
// Current
class MediaViewModel(...) : ViewModel() {
    private val repository = MediaRepository()  // Hard-coded dependency
}

// Recommended: Use Hilt for dependency injection
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {
    // Dependencies injected
}

// Setup in Application
@HiltAndroidApp
class FarmHubApplication : Application()

// Provide dependencies
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Singleton
    @Provides
    fun provideMediaRepository(): MediaRepository = MediaRepository()
}
```

**Benefits:**
- Easier testing (mock dependencies)
- Decoupled architecture
- Configuration flexibility

---

### 12. Unit & Integration Tests

**Current Status:** Minimal test coverage

**Recommended:**

```kotlin
// Test MediaRepository
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryTest {
    private val mockApiClient = mockk<UserService>()
    private val repository = MediaRepository()
    
    @Test
    fun `getMediaFeed returns success response`() = runTest {
        // Arrange
        val mockResponse = Response.success(MediaFeedResponse(
            status = "success",
            media = listOf(/* sample media items */)
        ))
        coEvery { mockApiClient.getMediaFeed() } returns mockk {
            every { execute() } returns mockResponse
        }
        
        // Act
        val result = repository.getMediaFeed()
        
        // Assert
        assertTrue(result.isSuccessful)
        assertTrue(result.body()?.media?.size == 1)
    }
    
    @Test
    fun `getMediaFeed returns error on network failure`() = runTest {
        // Arrange
        coEvery { mockApiClient.getMediaFeed() } throws IOException()
        
        // Act
        val result = repository.getMediaFeed()
        
        // Assert
        assertEquals(500, result.code())
    }
}

// Test MediaViewModel
@RunWith(RobolectricTestRunner::class)
class MediaViewModelTest {
    private val mockRepository = mockk<MediaRepository>()
    private val viewModel = MediaViewModel(
        mockRepository,
        SavedStateHandle()
    )
    
    @Test
    fun `loadMedia sets state to Loading then Success`() = runTest {
        // ... test state transitions ...
    }
}
```

---

## Deployment & Release Checklist

### Before Release to Production

- [ ] Token persistence implemented (no re-login required)
- [ ] Error logging/crash reporting configured
- [ ] Offline mode with caching working
- [ ] All API endpoints tested with actual backend
- [ ] Image upload progress tracking working
- [ ] Unit tests for critical paths (>80% coverage)
- [ ] Integration tests with staging backend
- [ ] Performance testing (large media feeds)
- [ ] Battery/data usage optimized
- [ ] Privacy policy updated
- [ ] Rate limiting handled gracefully
- [ ] Timeout configuration appropriate
- [ ] ProGuard rules in place (if minifying)

---

## Summary

**Priority Tiers:**

**Tier 1 (Essential):**
- Persistent token storage
- Server-side pagination
- Error reporting

**Tier 2 (Recommended):**
- Token refresh endpoint
- Real-time messaging
- Offline caching

**Tier 3 (Nice-to-Have):**
- Upload progress
- Dependency injection
- Comprehensive testing
- Dark mode
- Search enhancement

**Estimated Effort:**
- Tier 1: 2-3 weeks
- Tier 2: 3-4 weeks  
- Tier 3: 2-3 weeks

---

**Next Steps:** Share these recommendations with the Android team after backend is stable and initial integration testing is complete.

