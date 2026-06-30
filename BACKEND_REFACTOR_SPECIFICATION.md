# FarmHub Backend Refactor Specification
## Derived from Android Implementation Analysis
**Analysis Date:** May 11, 2026  
**Status:** Production-Ready Specification  
**Derived From:** Complete Android Implementation (Kotlin/Compose)

---

## Executive Summary

This document details the backend API structure that **MUST** support the existing Android implementation. The Android app is the **source of truth** for requirements. All backend endpoints, payloads, and responses have been reverse-engineered from the implemented Android code.

**Key Finding:** The Android app assumes specific payload structures that must be matched exactly by the backend. Misalignments will cause crashes or data loss.

---

## Part 1: Current Android Implementation Analysis

### 1.1 API Client Architecture

**File:** `ApiClient.kt`

#### Base Configuration
- **Base URL:** `https://api.farmers-hub.co.ke/`
- **Authentication:** Bearer Token (JWT)
- **Interceptors:**
  1. Auth Interceptor - adds `Authorization: Bearer {token}` header
  2. HTTP Response Interceptor - handles 401/403 status codes
  3. Logging Interceptor - logs all request/response bodies

#### Public Endpoints (no token required)
- `POST /auth/login`
- `POST /auth/register`

#### Protected Endpoints (token required, all others)
- Token validation performed before each request
- 401 triggers automatic logout and AuthManager.handleUnauthorized()
- 403 logs but does not logout (insufficient permissions, not auth failure)

#### Key Implementation Detail
```kotlin
private fun isPublicEndpoint(path: String): Boolean {
    val normalized = path.trimStart('/')
    return normalized in publicPaths  // {"auth/login", "auth/register"}
}

// If token missing on protected endpoint → 401 response generated locally
if (!isPublicEndpoint(path) && bearerToken.isNullOrBlank()) {
    return missingTokenResponse(original)  // 401 with "Missing authentication token"
}
```

**Backend Impact:** Must return 401 for invalid/expired tokens. Android expects HTTP 401 status code, not 403 or custom responses.

---

### 1.2 Retrofit Service Interface

**File:** `UserService.kt`

All endpoints currently implemented or scaffolded:

#### Authentication Endpoints

**POST /auth/login**
```kotlin
fun userLogin(@Body loginRequest: LoginRequest): Call<LoginResponse>

// Request payload
data class LoginRequest(
    val phone: String,        // e.g., "0719697174"
    val password: String      // plaintext, should be hashed server-side
)

// Expected response
data class LoginResponse(
    val status: String,       // "success"
    val token: String,        // JWT token, stored in ApiClient
    val issued: Long,         // timestamp in ms
    val expires: Long,        // timestamp in ms
    val newUser: Boolean,     // true if first login
    val userDetails: UserDetails
)

data class UserDetails(
    val id: String,           // UUID
    val createdAt: String,    // ISO 8601 datetime
    val updatedAt: String,
    val names: String,
    val role: String,         // "USER", "SPECIALIST", "ADMINISTRATOR"
    val phone: String,        // normalized, e.g., "+254719697174"
    val county: String?,
    val subCounty: String?,
    val paidUser: String?     // nullable, type mismatch (should be Boolean)
)
```

**POST /auth/register**
```kotlin
fun registerUser(@Body registerRequest: RegisterRequest): Call<RegisterResponse>

// Request payload
data class RegisterRequest(
    val names: String,                        // full name
    val email: String?,                       // optional but expected
    val phone: String,                        // e.g., "0719697174"
    val county: String?,                      // optional geographic info
    val subCounty: String?,                   // optional geographic info
    val password: String                      // plaintext → hash server-side
)

// Expected response
data class RegisterResponse(
    val user: RegisteredUser? = null,
    val status: String? = null,               // "success", error message on failure
    val message: String? = null
)

data class RegisteredUser(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val names: String? = null,
    val email: String? = null,
    val role: String? = null,                 // default "USER"
    val phone: String? = null,
    val county: String? = null,
    val subCounty: String? = null
)
```

**GET /auth/me**
```kotlin
fun getUserProfile(): Call<UserProfileResponse>

// No request body. Requires Bearer token.
// Expected response
data class UserProfileResponse(
    val data: UserProfileData? = null,
    val status: String? = null
)

data class UserProfileData(
    val id: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val names: String?,
    val role: String?,
    val phone: String?,
    val county: String?,
    val subCounty: String?,
    val paidUser: String?  // BUG: Should be Boolean
)
```

**POST /auth/reset-password**
```kotlin
fun resetPassword(@Body body: ResetPasswordRequest): Call<GenericStatusResponse>

// Request payload
data class ResetPasswordRequest(
    val phone: String  // e.g., "0715204181"
)

// Expected response
data class GenericStatusResponse(
    val status: String? = null,   // "success"
    val message: String? = null
)
```

---

#### Posts Endpoints

**GET /posts**
```kotlin
fun getAllPosts(): Call<GetAllPostsResponse>

// No request body. Requires Bearer token.
// Expected response
data class GetAllPostsResponse(
    val status: String,           // "success"
    val posts: List<PostWrapper>
)

data class PostWrapper(
    val post: Post
)

data class Post(
    val description: String,      // user-submitted text
    val createdBy: String,        // userId who created it
    val image: String,            // filename or key
    val imageUrl: String,         // full URL to access image
    val updatedAt: String,        // ISO 8601
    val id: String,               // UUID
    val createdAt: String         // ISO 8601
)
```

**POST /posts (Create Post)**
```kotlin
@Multipart
@POST("posts")
fun createPost(
    @Part image: MultipartBody.Part,          // part name: "image"
    @Part("description") description: RequestBody  // part name: "description"
): Call<CreatePostResponse>

// Multipart payload structure
// {
//   "image": <binary file content, field name must be "image">,
//   "description": "text content as RequestBody"
// }

// Expected response
data class CreatePostResponse(
    val imageUrl: String,         // full URL
    val id: String,               // UUID of created post
    val image: String,            // filename
    val status: String            // "success"
)
```

**GET /posts/{id}**
```kotlin
fun getPost(@Path("id") id: String): Call<PostDetailResponse>

// Requires Bearer token.
// Expected response
data class PostDetailResponse(
    val status: String? = null,
    val post: Post? = null
)
```

---

#### Media/Video Endpoints

**GET /media**
```kotlin
fun getMediaFeed(): Call<MediaFeedResponse>

// No request body. Requires Bearer token.
// Expected response
data class MediaFeedResponse(
    val status: String? = null,
    val media: List<MediaItemResponse>? = null
)

data class MediaItemResponse(
    val id: String? = null,                       // UUID
    val title: String? = null,                    // video title
    @SerializedName("company")
    val channel: String? = null,                  // video creator/channel
    val type: String? = null,                     // optional, e.g., "video", "tutorial"
    @SerializedName("thumbnail")
    val thumbnailUrl: String? = null,             // full URL to thumbnail image
    @SerializedName("video")
    val mediaUrl: String? = null,                 // full URL to video/media file
    val description: String? = null,
    val createdAt: String? = null,                // ISO 8601
    val updatedAt: String? = null,
    val userId: String? = null                    // creator user ID
)
```

**Critical Note on Field Mapping:**
Android uses @SerializedName to map backend field names:
- Backend field `company` → Android field `channel`
- Backend field `thumbnail` → Android field `thumbnailUrl`
- Backend field `video` → Android field `mediaUrl`

If backend returns `{"channel": "..."}` instead of `{"company": "..."}`, Android will fail to deserialize.

---

#### Messaging Endpoints

**POST /messaging (Send Message)**
```kotlin
@Multipart
@POST("messaging")
fun sendMessageWithAttachment(
    @Part("text") text: RequestBody,              // message text
    @Part("phone") phone: RequestBody,            // recipient phone number
    @Part attachment: MultipartBody.Part?         // optional attachment file
): Call<SendMessageResponse>

// Multipart payload structure
// {
//   "text": "message content",
//   "phone": "+254719697170",          // must be normalized with country code
//   "attachment": <optional binary file>
// }

// Expected response
data class SendMessageResponse(
    val message: String,          // confirmation message
    val status: String            // "success"
)
```

**GET /messaging (Get Threads)**
```kotlin
fun getThreads(): Call<ThreadListResponse>

// No request body. Requires Bearer token.
// Expected response
data class ThreadListResponse(
    val status: String? = null,
    val threads: List<ThreadResponse>? = null
)

data class ThreadResponse(
    val id: String? = null,                   // thread UUID
    val threadId: String? = null,             // alternative ID field
    val recipientId: String? = null,          // UUID of other party
    val participants: List<String>? = null,   // phone numbers or IDs
    val lastMessage: String? = null,          // last message text
    val last_message: String? = null,         // snake_case alternative
    val updatedAt: String? = null,            // ISO 8601
    val updated_at: String? = null            // snake_case alternative
)

// Android helper methods:
fun derivedId(): String? = id ?: threadId ?: recipientId
fun derivedLastMessage(): String? = lastMessage ?: last_message
fun derivedUpdatedAt(): String? = updatedAt ?: updated_at
fun otherParty(currentUserPhone: String?): String? = /* extract non-current participant */
```

**GET /messaging/{recipientId} (Get Messages in Thread)**
```kotlin
fun getMessages(@Path("recipientId") id: String): Call<MessagesResponse>

// No request body. Requires Bearer token.
// Path param is recipient ID (UUID or phone).
// Expected response
data class MessagesResponse(
    val status: String? = null,
    val messages: List<MessageItemResponse>? = null
)

data class MessageItemResponse(
    val id: String? = null,                       // message UUID
    val messageId: String? = null,
    val senderId: String? = null,                 // UUID or phone of sender
    val sender_id: String? = null,
    val from: String? = null,                     // alternative sender field
    val to: String? = null,                       // recipient identifier
    val text: String? = null,                     // message body
    val message: String? = null,                  // alternative text field
    val content: String? = null,
    val attachmentUrl: String? = null,            // full URL
    val attachment_url: String? = null,           // snake_case alternative
    val createdAt: String? = null,                // ISO 8601
    val created_at: String? = null
)

// Android helper methods:
fun derivedId(): String? = id ?: messageId
fun derivedText(): String = text ?: message ?: content ?: ""
fun derivedAttachment(): String? = attachmentUrl ?: attachment_url
fun derivedCreatedAt(): String? = createdAt ?: created_at
fun isFromCurrentUser(): Boolean = /* check senderId against UserSession.userId */
fun otherPartyPhone(): String? = /* return recipient if sender is me, else sender */
```

---

#### Geographic Data Endpoints

**GET /data/counties**
```kotlin
fun getCounties(@Query("county") county: String? = null): Call<CountiesResponse>

// Optional query parameter:
// - No params: returns list of all counties
// - ?county=Kiambu: returns subcounties for that county

// Expected response
data class CountiesResponse(
    val counties: List<String>? = null,           // ["Kiambu", "Nairobi", ...]
    val subCounties: List<String>? = null,        // populated when ?county param used
    val status: String? = null
)
```

---

#### Specialist Endpoints (DEFERRED - not yet in UI)

These endpoints exist in Postman collection but are not yet integrated into Android UI:

```kotlin
// @GET("posts/specialist")
// fun getSpecialistPosts(): Call<GetAllPostsResponse>

// @Multipart
// @POST("posts/specialist/{id}")
// fun processPost(
//     @Path("id") postId: String,
//     @Part image: MultipartBody.Part,
//     @Part("description") description: RequestBody
// ): Call<GenericStatusResponse>
```

---

### 1.3 Repository Layer Architecture

**Pattern:** Each repository handles API interactions, validation, logging, and error handling.

#### Authentication Flow (LoginRepository)
```
1. App calls LoginRepository.login(phone, password)
2. Repository sends POST /auth/login with LoginRequest
3. Backend returns LoginResponse with JWT token
4. App extracts token → ApiClient.setBearerToken(token)
5. App stores token in UserSession singleton
6. All subsequent calls automatically include Bearer header
```

#### Post Creation Flow (PostRepository + FarmHelpViewModel)
```
1. User selects image via camera/gallery
2. FarmHelpViewModel converts Uri to File in cache directory
3. ViewModel creates MultipartBody.Part for "image" field
4. ViewModel creates RequestBody for "description" text field
5. PostRepository.createPost(image, description, callbacks)
6. Repository validates token with TokenValidator.isTokenValid()
7. If invalid → error callback invoked (no network call made)
8. If valid → POST /posts with multipart payload
9. On success → response contains imageUrl (used by UI to display)
10. ViewModel moves to success screen (step 3)
```

**Critical Detail:** Image multipart field name MUST be "image", not "file" or "photo".

#### Media Feed (MediaViewModel + MediaRepository)
```
1. VideoScreen mounted → MediaViewModel.loadMedia()
2. MediaViewModel sets uiState = Loading
3. MediaRepository.getMediaFeed() called
4. Token validation → if invalid, return 401 Response
5. GET /media executed, returns MediaFeedResponse
6. Response body mapped to List<VideoItem>:
   - id: incrementing counter (100000+) to avoid static ID collisions
   - title, channel, description: from response
   - thumbnailUrl: from response @SerializedName("thumbnail")
   - mediaUrl: from response @SerializedName("video")
   - views, time: empty (not provided by backend)
7. Videos filtered by category/subcategory using keywords
8. Pagination: slice by (currentPage * pageSize)
9. On error → MediaUiState.Error shown to user
10. User can pull-to-refresh → MediaViewModel.refresh()
```

#### Messaging Flow (MessageRepository)
```
Sending:
1. User types message + optional attachment
2. MessageRepository.sendMessage(message, attachmentUri?, recipientPhone)
3. Phone normalized: "0719..." → "+254719..."
4. If no recipient provided: use UserSession.phone
5. Create multipart with text, phone, optional attachment
6. POST /messaging executed
7. Response contains SendMessageResponse

Loading Threads:
1. ChatScreen mounted → MessageViewModel.loadThreads()
2. MessageRepository.getThreads()
3. GET /messaging executed → ThreadListResponse
4. Threads mapped to UI model with helper methods
5. First thread auto-selected or user picks thread

Loading messages in thread:
1. User selects thread
2. MessageViewModel.loadMessages(threadId)
3. MessageRepository.getMessages(threadRecipientId)
4. GET /messaging/{recipientId} executed
5. Messages mapped, sorted by createdAt
6. isFromCurrentUser() helper determines message direction
```

---

### 1.4 Data Model Pattern Analysis

#### Common Patterns Observed

**1. Nullable fields with defaults**
```kotlin
data class FooResponse(
    val id: String? = null,
    val name: String? = null
)
// Android defensive programming: assumes backend may return null or omit fields
```

**2. Field name mapping via @SerializedName**
```kotlin
@SerializedName("company")
val channel: String? = null,        // backend sends "company", Android expects "channel"

@SerializedName("thumbnail")
val thumbnailUrl: String? = null,   // backend sends "thumbnail", Android expects "thumbnailUrl"
```

**3. Multiple alternative fields for robustness**
```kotlin
data class MessageItemResponse(
    val senderId: String? = null,    // try this first
    val sender_id: String? = null,   // fallback to snake_case
    val from: String? = null         // fallback to alternative name
)
// Backend likely returns ONLY ONE of these, but Android tries all
```

**4. Helper derived methods**
```kotlin
fun derivedId(): String? = id ?: threadId ?: recipientId
// Android tries multiple possible ID fields to handle backend variations
```

**5. Type inconsistencies**
```kotlin
data class UserProfileData(
    val paidUser: String?  // ISSUE: Should be Boolean, but backend sends String
)
```

---

### 1.5 Token Management & Session Handling

**UserSession Singleton**
```kotlin
object UserSession {
    var userId: String? = null        // from UserDetails.id
    var phone: String? = null         // from UserDetails.phone
    var role: String? = null          // "USER", "SPECIALIST", "ADMINISTRATOR"
    // ... other profile fields
}
```

**Token Storage**
- JWT token stored in `ApiClient.bearerToken` (volatile field, not persistent)
- Token expires at `LoginResponse.expires` (timestamp in ms)
- No persistent token storage = token lost on app restart
- This is acceptable for dev but **production should use encrypted SharedPreferences**

**Token Validation**
```kotlin
object TokenValidator {
    fun isTokenValid(): Boolean {
        val token = ApiClient.currentToken()
        val expiry = UserSession.tokenExpiry  // stored in milliseconds
        return !token.isNullOrBlank() && System.currentTimeMillis() < expiry
    }
}
```

**Logout Flow**
```kotlin
1. HTTP 401 received on any endpoint
2. ApiClient.httpResponseInterceptor triggers
3. AuthManager.handleUnauthorized(context) called
4. Auth state cleared: ApiClient.clearBearerToken()
5. UserSession cleared
6. Navigator redirects to LoginScreen
```

---

### 1.6 Error Handling Strategy

**Repository Level**
```kotlin
// Pre-flight validation - prevents unnecessary network calls
if (!TokenValidator.isTokenValid()) {
    Log.w(TAG, "Cannot fetch ...")
    return Response.error(401, ResponseBody.create(...))
}

// Network execution with exception catch
val resp = try {
    ApiClient.userService.getFoo().execute()
} catch (e: Exception) {
    Log.e(TAG, "Network exception: ${e.message}")
    return Response.error(500, ResponseBody.create(..., e.message))
}

// Log success/failure
if (!resp.isSuccessful) {
    Log.e(TAG, "API error code=${resp.code()} body=${resp.errorBody()?.string()}")
} else {
    Log.d(TAG, "Success. Items=${resp.body()?.items?.size ?: 0}")
}
```

**ViewModel Level**
```kotlin
try {
    val resp = repository.getFoo()
    if (resp.isSuccessful && resp.body() != null) {
        _uiState.value = MediaUiState.Success(data, hasMore)
    } else {
        _uiState.value = MediaUiState.Error("API error: ${resp.code()}")
    }
} catch (e: Exception) {
    Log.e("TAG", "Exception: ${e.message}")
    _uiState.value = MediaUiState.Error(e.localizedMessage ?: "Unexpected error")
}
```

**UI Level**
```kotlin
val state = mediaViewModel.uiState.collectAsState()
when (state.value) {
    is MediaUiState.Loading → CircularProgressIndicator()
    is MediaUiState.Success -> LazyColumn(items = videos) { ... }
    is MediaUiState.Error -> Text((state.value as Error).message)
}
```

---

### 1.7 Upload Handling Deep Dive

**Post Image Upload (POST /posts)**

Flow:
```
1. User selects image via camera/gallery
2. FarmHelpViewModel.createImageUri() creates temp file in cacheDir via FileProvider
3. Camera/gallery writes to URI
4. FarmHelpViewModel.submitPost():
   a. Reads URI via contentResolver.openInputStream()
   b. Copies stream to cacheDir/upload.jpg
   c. Creates MultipartBody.Part with:
      - form data field name: "image"
      - filename: "upload.jpg"
      - content type: "image/jpeg"
      - body: file.asRequestBody("image/jpeg")
   d. Creates RequestBody for description:
      - content type: "text/plain"
      - body: description string
5. PostRepository.createPost(image, description)
6. Retrofit sends as multipart/form-data:
   POST /posts
   Content-Type: multipart/form-data; boundary=----...
   
   ------WebKitFormBoundary...
   Content-Disposition: form-data; name="image"; filename="upload.jpg"
   Content-Type: image/jpeg
   
   [binary image data]
   ------WebKitFormBoundary...
   Content-Disposition: form-data; name="description"
   
   This is my issue description
   ------WebKitFormBoundary...--
7. Backend receives image file + description text
8. Backend responds:
   {
     "status": "success",
     "id": "uuid-xxx",
     "image": "filename.jpg",
     "imageUrl": "https://storage.com/posts/uuid-xxx/filename.jpg"
   }
9. FarmHelpViewModel extracts imageUrl and stores in CreatePostResponse
10. UI displays success, stores imageUrl for confirmation
```

**Message Attachment (Optional)**

Flow:
```
1. User selects attachment (if present)
2. MessageRepository.sendMessage() called with attachmentUri
3. Multipart created with:
   - text: message body (RequestBody with "text/plain")
   - phone: recipient (RequestBody with "text/plain")
   - attachment: file (MultipartBody.Part, optional)
4. POST /messaging sent
5. Backend processes attachment if present
6. Response: SendMessageResponse
```

**Critical Implementation Details:**
- Field names must match: "image", "description", "text", "phone", "attachment"
- Content types must be correct: image/* for files, text/* for text
- Boundaries handled automatically by Retrofit/OkHttp
- No custom multipart builder needed

---

### 1.8 Authentication & Authorization

**JWT Token Structure (from Postman examples)**

Payload contains:
```
{
  "createdAt": 6384645412188870000,
  "userId": "b9fb09d9-2e3a-4711-a3f1-aaed5e79e080",
  "role": "ADMINISTRATOR",  // or "USER", "SPECIALIST"
  "issued": 1710857321887,
  "expires": 1710858221887
}
```

**Role-Based Access Control (if implemented)**
- Android doesn't enforce role-based UI filtering yet (all users can attempt all endpoints)
- Backend MUST validate authorization:
  - `/posts` (GET/POST) → requires any authenticated user
  - `/posts/specialist` (GET) → requires role="SPECIALIST"
  - `/posts/specialist/{id}` (POST) → requires role="SPECIALIST"
  - `/messaging` → requires authenticated user
  - `/media` → requires authenticated user
  - `/auth/me` → requires authenticated user

**No Role UI Yet:**
The Android app doesn't have a specialist workflow implemented. These endpoints exist in API but aren't called by current UI.

---

### 1.9 Caching & Persistence Strategy

**Currently Implemented:**
- In-memory video list caching in MediaViewModel.lastVideos
- Pagination memory state: currentPage, pageSize
- Saved state for menu selections via SavedStateHandle

**NOT Implemented:**
- Persistent token storage (token lost on app restart with current architecture)
- Database persistence (Room, Realm, etc.)
- HTTP cache headers (no If-Modified-Since, ETag support)
- Offline mode

**Android Constraints:**
- No backend persistence cache configured
- Each app restart clears all cached data
- Cold start requires full API refetch

---

## Part 2: Detected Issues & Inconsistencies

### Critical Issues

**1. Type Mismatch: `paidUser` field**

**Location:** `UserProfileData`, `UserDetails`

**Issue:**
```kotlin
val paidUser: String?  // WRONG: should be Boolean
```

**Android Workaround:** treats as nullable String "true"/"false"

**Backend Fix Required:**
```json
{
  "paidUser": true  // should be boolean, not string
}
```

**Impact:** If backend returns `"paidUser": true`, Gson will fail to deserialize. Must be JSON boolean `true/false`, not string `"true"/"false"`.

---

**2. Field Name Inconsistency in Media Response**

**Issue:** Android uses `@SerializedName` mapping because backend field names don't match:
- Backend: `"company"` → Android: `channel`
- Backend: `"thumbnail"` → Android: `thumbnailUrl`
- Backend: `"video"` → Android: `mediaUrl`

**Risk:** If backend endpoint returns `{"channel": "..."}` instead of `{"company": "..."}`, deserialization silently fails with null values.

**Current Fragility:**
```kotlin
@SerializedName("company")
val channel: String? = null,  // Assumes backend sends "company", not "channel"
```

---

**3. Multiple Possible Field Names in Responses**

**Location:** `ThreadResponse`, `MessageItemResponse`

**Issue:** Android defensively handles multiple possible field names:
```kotlin
data class ThreadResponse(
    val id: String? = null,
    val threadId: String? = null,
    val recipientId: String? = null
)
// Backend returns exactly ONE of these, but Android tries all
```

**Indicates:** Backend API response structure is uncertain/undocumented.

**Risk:** If backend adds fields inconsistently across responses, deserialization succeeds but helperderivations may pick wrong field.

---

**4. Phone Number Normalization Mismatch**

**Location:** `MessageRepository.normalizePhone()`

**Issue:**
```kotlin
fun normalizePhone(raw: String?): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("+254") -> trimmed          // Already normalized
        trimmed.startsWith("0") && trimmed.length == 10 -> "+254" + trimmed.drop(1)  // Convert local format
        else -> trimmed                                // Pass through else
    }
}
```

**Problem:** Backend also normalizes phone numbers, but timing is unclear:
- Android normalizes before sending (good)
- Backend might normalize on receipt (duplication)

**Risk:** If backend doesn't normalize and receives "0719697174", it may not match Stored "+254719697174" in database queries.

---

**5. Token Persistence Gap**

**Issue:** JWT token stored in volatile `ApiClient.bearerToken` field only

```kotlin
@Volatile private var bearerToken: String? = null
```

**Impact:**
- Token lost on app restart/kill
- User forced to re-login even with valid token
- No persistent auth state

**Production Issue:** Not acceptable for mobile apps. User expects to stay logged in.

---

**6. Inconsistent Pagination Support**

**Location:** `MediaViewModel`

**Issue:**
```kotlin
private var currentPage = 1
private val pageSize = 10

fun loadNextPage() {
    if (lastVideos.isEmpty()) return
    val filtered = filteredVideos(lastVideos)
    if (!hasMoreInternal(filtered)) return
    currentPage++
    _uiState.value = MediaUiState.Success(visibleSubset(filtered), hasMoreInternal(filtered))
}
```

**Problem:** Pagination happens client-side (slice of in-memory list), not server-side

**Risk:**
- If media feed has 1000+ videos, entire list downloaded in one request
- No offset/limit query params sent to backend
- Scalability issue at volume

---

**7. No Attachment Field Handling in SendMessage Response**

**Location:** `SendMessageResponse`

**Issue:**
```kotlin
data class SendMessageResponse(
    val message: String,
    val status: String
)
```

**Missing:** Confirmation details like messageId, createdAt, etc.

**Workaround:** UI doesn't display sent message confirmation from backend, relies on optimistic append to UI list.

---

### Security Issues

**1. Password Sent in Plaintext**

**Location:** All authentication requests

```kotlin
data class LoginRequest(
    val phone: String,
    val password: String        // Sent as plaintext JSON
)
```

**Issue:** Passwords visible in HTTP log interceptor output, potentially in server logs.

**Mitigation:** HTTPS/TLS encryption (assumed), but not hashed at transmission layer.

**Backend Fix:** Hash passwords before storage, never log passwords.

---

**2. JWT Payload Contains No Signature Verification**

**Location:** `ApiClient` doesn't verify JWT signature

```kotlin
fun currentToken(): String? = bearerToken
// Token used as-is, no signature verification
```

**Issue:** Malicious client could tamper with JWT payload (role, userId, expiry).

**Assumed:** Backend validates JWT signature on receipt with secret key.

---

**3. No Rate Limiting Enforcement**

**Issue:** Android makes unlimited requests; no backoff or throttling.

**Risk:** Malicious actor could brute-force APIs.

**Backend Fix:** Implement rate limiting per user/IP.

---

### Scalability Issues

**1. Full Media Feed Downloaded on Each Load**

**Impact:** 1000+ videos downloaded, entire list held in memory

**Fix:** Implement server-side pagination with limit/offset query params

---

**2. No Lazy Loading of Images**

**Impact:** Thumbnails & media files loaded upfront, not progressively

**Fix:** Coil image library supports lazy loading, but backend not optimized for it

---

**3. No Data Compression**

**Issue:** JSON responses sent without gzip compression

**Backend Fix:** Enable Content-Encoding: gzip

---

## Part 3: Recommended Backend Payload Contracts

### 3.1 Authentication Payloads

#### POST /auth/login

**Request:**
```json
{
  "phone": "0719697174",
  "password": "user_password"
}
```

**Validation Rules:**
- `phone`: required, string, 10-13 characters, digits + optional leading +
- `password`: required, string, 8-256 characters
- Both fields required; return 400 if missing

**Response Success (200):**
```json
{
  "status": "success",
  "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9...",
  "issued": 1710857321887,
  "expires": 1710944721887,
  "newUser": false,
  "userDetails": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2021-06-15T10:30:00Z",
    "updatedAt": "2024-03-19T10:30:00Z",
    "names": "John Doe",
    "role": "USER",
    "phone": "+254719697174",
    "county": "Kiambu",
    "subCounty": "Thika",
    "paidUser": true
  }
}
```

**Response Error (401):**
```json
{
  "status": "error",
  "message": "Invalid phone or password"
}
```

**Response Error (400):**
```json
{
  "status": "error",
  "message": "Missing required fields: phone, password"
}
```

**Contract Notes:**
- All timestamps in milliseconds since epoch (not ISO 8601)
- `paidUser` must be JSON boolean `true`/`false`, not string
- `role` must be one of: "USER", "SPECIALIST", "ADMINISTRATOR"
- `expires` is computed as `issued + tokenTTL` (where tokenTTL = 86400000 = 24 hours)

---

#### POST /auth/register

**Request:**
```json
{
  "names": "Jane Smith",
  "email": "jane@example.com",
  "phone": "0715204181",
  "county": "Nairobi",
  "subCounty": "Westlands",
  "password": "secure_password_123"
}
```

**Validation Rules:**
- `names`: required, string, 2-100 characters
- `email`: optional, string, valid email format if provided
- `phone`: required, string, 10-13 characters
- `county`: optional, string
- `subCounty`: optional, string
- `password`: required, string, 8-256 characters, no plaintext logging
- Return 400 if required fields missing
- Return 409 if phone already registered

**Response Success (201 or 200):**
```json
{
  "status": "success",
  "message": "User registered successfully",
  "user": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "createdAt": "2024-03-20T10:30:00Z",
    "updatedAt": "2024-03-20T10:30:00Z",
    "names": "Jane Smith",
    "email": "jane@example.com",
    "role": "USER",
    "phone": "+254715204181",
    "county": "Nairobi",
    "subCounty": "Westlands"
  }
}
```

**Response Error (409 - Conflict):**
```json
{
  "status": "error",
  "message": "Phone number already registered"
}
```

**Response Error (400 - Validation):**
```json
{
  "status": "error",
  "message": "Invalid email format"
}
```

**Contract Notes:**
- User created with default role "USER"
- Phone normalized to +254XXXXXXXXX before storage
- Password never returned in response
- Email validation should accept any RFC 5322 format (but clearly formatted emails expected)

---

#### POST /auth/reset-password

**Request:**
```json
{
  "phone": "0715204181"
}
```

**Validation Rules:**
- `phone`: required, string, 10-13 characters
- Must correspond to an existing user
- Should trigger password reset flow (SMS OTP or email link)

**Response Success (200):**
```json
{
  "status": "success",
  "message": "Password reset email sent. Please check your mail or SMS."
}
```

**Response Error (404):**
```json
{
  "status": "error",
  "message": "User with this phone not found"
}
```

---

#### GET /auth/me

**Request:** Bearer token in Authorization header

**Response Success (200):**
```json
{
  "status": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2021-06-15T10:30:00Z",
    "updatedAt": "2024-03-19T10:30:00Z",
    "names": "John Doe",
    "role": "USER",
    "phone": "+254719697174",
    "county": "Kiambu",
    "subCounty": "Thika",
    "paidUser": true
  }
}
```

**Response Error (401):**
```json
{
  "status": "error",
  "message": "Unauthorized"
}
```

---

### 3.2 Posts Payloads

#### GET /posts

**Request:** Bearer token required

**Query Parameters (optional):**
```
?limit=10&offset=0  // Future pagination support (not yet used by Android)
?category=crop_farming  // Future filtering (not yet used)
```

**Response Success (200):**
```json
{
  "status": "success",
  "posts": [
    {
      "post": {
        "id": "770e8400-e29b-41d4-a716-446655440001",
        "description": "My field is suffering from blight",
        "createdBy": "550e8400-e29b-41d4-a716-446655440000",
        "image": "posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
        "imageUrl": "https://storage.farmers-hub.co.ke/posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
        "createdAt": "2024-03-19T14:30:00Z",
        "updatedAt": "2024-03-19T14:30:00Z"
      }
    }
  ]
}
```

**Contract Notes:**
- `createdBy` is user ID, not name (Android doesn't use currently)
- `image` is storage key/path, `imageUrl` is public URL
- PostWrapper indirection (wrapping Post inside) is current structure; could be simplified
- Timestamps are ISO 8601 strings (not milliseconds like login response - **inconsistency**)

---

#### POST /posts (Create Post)

**Request:** multipart/form-data with Bearer token

```
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary
Authorization: Bearer <token>

------WebKitFormBoundary
Content-Disposition: form-data; name="image"; filename="upload.jpg"
Content-Type: image/jpeg

[binary image data]
------WebKitFormBoundary
Content-Disposition: form-data; name="description"

My field is suffering from blight
------WebKitFormBoundary--
```

**Validation Rules:**
- `image` field: required, must be valid image file (JPEG, PNG, WebP)
- `image` size: max 5MB (recommended)
- `description` field: required, string, 1-500 characters
- User must be authenticated (Bearer token required)

**Response Success (200 or 201):**
```json
{
  "status": "success",
  "id": "770e8400-e29b-41d4-a716-446655440001",
  "image": "posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
  "imageUrl": "https://storage.farmers-hub.co.ke/posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg"
}
```

**Response Error (413 - Payload Too Large):**
```json
{
  "status": "error",
  "message": "Image file exceeds maximum size of 5MB"
}
```

**Response Error (400 - Bad Request):**
```json
{
  "status": "error",
  "message": "Missing required fields: image, description"
}
```

**Contract Notes:**
- Image MUST be stored with public URL (Android expects full HTTPS URL in response)
- ID field returned to allow future detail lookups
- Response should include image dimensions if available (not currently used by Android)

---

#### GET /posts/{id}

**Request:** Bearer token required

**Response Success (200):**
```json
{
  "status": "success",
  "post": {
    "id": "770e8400-e29b-41d4-a716-446655440001",
    "description": "My field is suffering from blight",
    "createdBy": "550e8400-e29b-41d4-a716-446655440000",
    "image": "posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
    "imageUrl": "https://storage.farmers-hub.co.ke/posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
    "createdAt": "2024-03-19T14:30:00Z",
    "updatedAt": "2024-03-19T14:30:00Z"
  }
}
```

**Response Error (404):**
```json
{
  "status": "error",
  "message": "Post not found"
}
```

---

### 3.3 Media/Video Payloads

#### GET /media

**Request:** Bearer token required

**Query Parameters (optional - currently not used by Android):**
```
?limit=20&offset=0      // Pagination
?category=crop_farming  // Filtering
?search=maize          // Search
```

**Response Success (200):**
```json
{
  "status": "success",
  "media": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440001",
      "title": "How to Plant Maize for High Yields",
      "company": "AgriTech Kenya",
      "type": "tutorial",
      "thumbnail": "https://cdn.youtube.com/vi/xxx/default.jpg",
      "video": "https://cdn.youtube.com/vi/xxx/default.mp4",
      "description": "A step-by-step guide to planting maize with high yield outcomes.",
      "createdAt": "2024-01-15T08:00:00Z",
      "updatedAt": "2024-01-15T08:00:00Z",
      "userId": "550e8400-e29b-41d4-a716-446655440000"
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440002",
      "title": "Integrated Pest Management Basics",
      "company": "Healthy Crops",
      "type": "educational",
      "thumbnail": "https://storage.farmers-hub.co.ke/media/vid2/thumb.jpg",
      "video": "https://storage.farmers-hub.co.ke/media/vid2/video.mp4",
      "description": "Learn IPM techniques to reduce pesticide use.",
      "createdAt": "2024-02-20T10:15:00Z",
      "updatedAt": "2024-02-20T10:15:00Z",
      "userId": "550e8400-e29b-41d4-a716-446655440001"
    }
  ]
}
```

**Contract Notes:**
- **CRITICAL:** Field names MUST be:
  - `company` (not `channel`)
  - `thumbnail` (not `thumbnailUrl`)
  - `video` (not `mediaUrl`)
  - Android uses @SerializedName to map these; if field names differ, deserialization fails silently
- `type` optional but helpful for filtering
- `description` should be concise (used in list views)
- `thumbnail` and `video` must be full public HTTPS URLs
- Timestamps ISO 8601 format

---

### 3.4 Messaging Payloads

#### GET /messaging (Get Threads)

**Request:** Bearer token required

**Response Success (200):**
```json
{
  "status": "success",
  "threads": [
    {
      "id": "thread-001",
      "threadId": "thread-001",
      "recipientId": "550e8400-e29b-41d4-a716-446655440001",
      "participants": ["+254719697174", "+254715204181"],
      "lastMessage": "Thanks for the advice!",
      "last_message": "Thanks for the advice!",
      "updatedAt": "2024-03-19T14:30:00Z",
      "updated_at": "2024-03-19T14:30:00Z"
    },
    {
      "id": "thread-002",
      "threadId": "thread-002",
      "recipientId": "550e8400-e29b-41d4-a716-446655440002",
      "participants": ["+254719697174", "+254700000000"],
      "lastMessage": "Can you help with my soil?",
      "last_message": "Can you help with my soil?",
      "updatedAt": "2024-03-18T10:00:00Z",
      "updated_at": "2024-03-18T10:00:00Z"
    }
  ]
}
```

**Response Error (401):**
```json
{
  "status": "error",
  "message": "Unauthorized"
}
```

**Contract Notes:**
- Android expects multiple ID fields (`id`, `threadId`, `recipientId`) for robustness
- Returns both camelCase and snake_case variants (`lastMessage` and `last_message`)
- This redundancy needed because backend field naming is uncertain
- `participants` array should include all parties (minimum 2: current user + recipient)

---

#### GET /messaging/{recipientId} (Get Messages in Thread)

**Request:** Bearer token required

**Path Parameters:**
- `recipientId` (UUID or phone identifier)

**Response Success (200):**
```json
{
  "status": "success",
  "messages": [
    {
      "id": "msg-001",
      "messageId": "msg-001",
      "senderId": "550e8400-e29b-41d4-a716-446655440000",
      "sender_id": "550e8400-e29b-41d4-a716-446655440000",
      "from": "550e8400-e29b-41d4-a716-446655440000",
      "to": "+254715204181",
      "text": "Hi, I need advice on pest control",
      "message": "Hi, I need advice on pest control",
      "content": "Hi, I need advice on pest control",
      "attachmentUrl": null,
      "attachment_url": null,
      "createdAt": "2024-03-19T14:30:00Z",
      "created_at": "2024-03-19T14:30:00Z"
    },
    {
      "id": "msg-002",
      "messageId": "msg-002",
      "senderId": "550e8400-e29b-41d4-a716-446655440001",
      "sender_id": "550e8400-e29b-41d4-a716-446655440001",
      "from": "550e8400-e29b-41d4-a716-446655440001",
      "to": "+254719697174",
      "text": "Use organic neem oil spray",
      "message": "Use organic neem oil spray",
      "content": "Use organic neem oil spray",
      "attachmentUrl": "https://storage.farmers-hub.co.ke/attachments/guide.pdf",
      "attachment_url": "https://storage.farmers-hub.co.ke/attachments/guide.pdf",
      "createdAt": "2024-03-19T14:35:00Z",
      "created_at": "2024-03-19T14:35:00Z"
    }
  ]
}
```

**Contract Notes:**
- Multiple ID and text field variants for robustness (camelCase + snake_case + alternatives)
- `attachmentUrl` should be full public URL if present, null/omitted otherwise
- `to` field helps Android determine message direction
- Timestamps ISO 8601
- Messages should be sorted by `createdAt` ascending (oldest first)

---

#### POST /messaging (Send Message)

**Request:** multipart/form-data with Bearer token

```
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary
Authorization: Bearer <token>

------WebKitFormBoundary
Content-Disposition: form-data; name="text"

I need help with my crops
------WebKitFormBoundary
Content-Disposition: form-data; name="phone"

+254715204181
------WebKitFormBoundary
Content-Disposition: form-data; name="attachment"; filename="image.jpg"
Content-Type: image/jpeg

[binary image data]
------WebKitFormBoundary--
```

**Validation Rules:**
- `text` field: required, string, 1-5000 characters
- `phone` field: required, string, 10-13 characters (normalized by Android to +254... format)
- `attachment` field: optional, max 5MB, valid image/PDF/document format
- Recipient phone must correspond to existing user
- Sender determined from Bearer token

**Response Success (200 or 201):**
```json
{
  "status": "success",
  "message": "Message sent successfully"
}
```

**Response Error (400 - Validation):**
```json
{
  "status": "error",
  "message": "Invalid recipient phone"
}
```

**Response Error (413 - Payload Too Large):**
```json
{
  "status": "error",
  "message": "Attachment file too large"
}
```

**Contract Notes:**
- Response doesn't include sent message details (messageId, createdAt)
- Android relies on optimistic UI append (shows message immediately before server confirmation)
- This is acceptable for UX but limits error recovery

---

### 3.5 Geographic Data Payloads

#### GET /data/counties

**Request:** No Bearer token required (public endpoint, optional)

**Query Parameters:**
- `?county=Kiambu` - optional, returns subcounties for that county
- No parameter - returns list of all counties

**Response Success (200) - Without county param:**
```json
{
  "status": "success",
  "counties": [
    "Baringo",
    "Bomet",
    "Bungoma",
    "Busia",
    "Calibrated",
    "Elgeyo Marakwet",
    "Embu",
    "Garissa",
    "Homa Bay",
    "Isiolo",
    "Kakamet",
    "Kericho",
    "Kiambu",
    "Kilifi",
    "Kirinyaga",
    "Kisii",
    "Kisumu",
    "Kitui",
    "Kwale",
    "Laikipia",
    "Lamu",
    "Machakos",
    "Makueni",
    "Mandera",
    "Marsabit",
    "Meru",
    "Migori",
    "Mombasa",
    "Murang'a",
    "Nairobi",
    "Nakuru",
    "Nandi",
    "Narok",
    "Nyamira",
    "Nyandarua",
    "Nyeri",
    "Samburu",
    "Siaya",
    "Taita Taveta",
    "Tana River",
    "Transnzoia",
    "Turkana",
    "Tharaka Nithi",
    "Uasin Gishu",
    "Vihiga",
    "Wajir",
    "West Pokot",
    "Yesteryears"
  ]
}
```

**Response Success (200) - With county param:**
```json
{
  "status": "success",
  "subCounties": [
    "Gatundu North",
    "Gatundu South",
    "Juja",
    "Kikuyu",
    "Kiambu",
    "Limuru",
    "Thika",
    "Ruiru"
  ]
}
```

**Response Error (400 - Invalid county):**
```json
{
  "status": "error",
  "message": "County not found"
}
```

**Contract Notes:**
- Suggested: make this public endpoint (no Bearer token required)
- County names consistent case/capitalization
- Subcounties returned as flat list (not nested)
- Could be optimized with separate GET /data/subcounties endpoint

---

## Part 4: Backend Refactor Plan

### 4.1 Recommended Node.js Architecture

```
farmhub-backend/
├── src/
│   ├── config/
│   │   ├── database.ts           // Database connection (PostgreSQL)
│   │   ├── jwt.ts                // JWT secret & config
│   │   ├── auth.ts               // Authentication middleware
│   │   └── storage.ts            // File upload storage config (S3 or local)
│   ├── middleware/
│   │   ├── authMiddleware.ts     // Bearer token validation
│   │   ├── errorHandler.ts       // Centralized error handling
│   │   ├── rateLimiter.ts        // Rate limiting per user/IP
│   │   ├── validator.ts          // Request validation (Joi/Zod)
│   │   └── logger.ts             // Structured logging
│   ├── models/
│   │   ├── User.ts               // User entity & schema
│   │   ├── Post.ts               // Post entity & schema
│   │   ├── Message.ts            // Message entity & schema
│   │   ├── Thread.ts             // Message thread entity & schema
│   │   ├── Media.ts              // Media/Video entity & schema
│   │   └── County.ts             // Geographic data entity
│   ├── routes/
│   │   ├── auth.ts               // Authentication routes
│   │   ├── posts.ts              // Post CRUD routes
│   │   ├── media.ts              // Media feed routes
│   │   ├── messaging.ts          // Messaging routes
│   │   ├── users.ts              // User profile routes
│   │   └── geo.ts                // Geographic data routes
│   ├── controllers/
│   │   ├── AuthController.ts     // Auth logic
│   │   ├── PostController.ts     // Post logic
│   │   ├── MediaController.ts    // Media logic
│   │   ├── MessageController.ts  // Message logic
│   │   └── UserController.ts     // User logic
│   ├── services/
│   │   ├── AuthService.ts        // Auth business logic
│   │   ├── PostService.ts        // Post business logic
│   │   ├── MediaService.ts       // Media business logic
│   │   ├── MessageService.ts     // Message business logic
│   │   ├── UserService.ts        // User business logic
│   │   └── StorageService.ts     // File upload handling
│   ├── utils/
│   │   ├── phoneNormalizer.ts    // Phone formatting +254
│   │   ├── jwtHelper.ts          // JWT generation & verification
│   │   ├── passwordHash.ts       // BCrypt hashing
│   │   ├── validators.ts         // Input validation schemas
│   │   └── errors.ts             // Custom error classes
│   └── app.ts                     // Express app setup
├── migrations/                    // Database migrations
├── seeds/                         // Database seed data
├── tests/
│   ├── unit/
│   ├── integration/
│   └── e2e/
├── .env.example                   // Environment variables template
├── docker-compose.yml             // PostgreSQL + Redis (optional)
├── package.json
└── tsconfig.json
```

---

### 4.2 Technology Stack Recommendations

**Framework & Runtime:**
- Express.js (slim, mature, well-documented)
- TypeScript (type safety, better IDE support)
- Node.js 18 LTS or later

**Database:**
- PostgreSQL (relational data, strong type system, JSONB support)
- Sequelize or TypeORM for ORM (Sequelize recommended for simplicity)

**Authentication & Security:**
- JWT (jsonwebtoken library)
- bcryptjs for password hashing
- helmet for HTTP security headers
- express-rate-limit for rate limiting

**File Storage:**
- AWS S3 or Digital Ocean Spaces (production)
- Local filesystem (development/testing)
- multer for handling multipart/form-data
- sharp for image resizing/optimization

**Validation & Marshaling:**
- Joi or Zod for schema validation
- class-transformer for DTO serialization
- express-validator for field-level validation

**Logging & Monitoring:**
- Winston for structured logging
- Morgan for HTTP request logging
- Sentry for error tracking (optional)

**Testing:**
- Jest for unit & integration tests
- Supertest for HTTP endpoint testing
- Factory Boy or Faker for test data generation

---

### 4.3 Database Schema Design

#### User Table
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  names VARCHAR(255) NOT NULL,
  email VARCHAR(255),
  role VARCHAR(50) NOT NULL DEFAULT 'USER',  -- 'USER', 'SPECIALIST', 'ADMINISTRATOR'
  county VARCHAR(100),
  sub_county VARCHAR(100),
  paid_user BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  deleted_at TIMESTAMP,  -- Soft delete
  INDEX idx_phone (phone),
  INDEX idx_role (role),
  INDEX idx_created_at (created_at)
);
```

#### Post Table
```sql
CREATE TABLE posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  description TEXT NOT NULL,
  image_key VARCHAR(255),  -- Storage key/path
  image_url VARCHAR(512) NOT NULL,  -- Public URL
  image_width INT,
  image_height INT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  deleted_at TIMESTAMP,
  FOREIGN KEY (created_by) REFERENCES users(id),
  INDEX idx_created_by (created_by),
  INDEX idx_created_at (created_at)
);
```

#### Message Table
```sql
CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  attachment_url VARCHAR(512),
  thread_id UUID,  -- Thread grouping
  created_at TIMESTAMP DEFAULT NOW(),
  FOREIGN KEY (sender_id) REFERENCES users(id),
  FOREIGN KEY (recipient_id) REFERENCES users(id),
  INDEX idx_sender_id (sender_id),
  INDEX idx_recipient_id (recipient_id),
  INDEX idx_thread_id (thread_id),
  INDEX idx_created_at (created_at)
);
```

#### Media Table
```sql
CREATE TABLE media (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  company VARCHAR(255),
  type VARCHAR(50),  -- 'tutorial', 'educational', 'video', etc.
  thumbnail_url VARCHAR(512),
  video_url VARCHAR(512) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  INDEX idx_user_id (user_id),
  INDEX idx_created_at (created_at),
  INDEX idx_type (type)
);
```

#### County Table
```sql
CREATE TABLE counties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT NOW(),
  INDEX idx_name (name)
);

CREATE TABLE sub_counties (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  county_id UUID NOT NULL REFERENCES counties(id),
  name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  FOREIGN KEY (county_id) REFERENCES counties(id),
  UNIQUE (county_id, name),
  INDEX idx_county_id (county_id)
);
```

---

### 4.4 API Endpoint Implementation Plan

#### Authentication Routes (src/routes/auth.ts)

```typescript
import express from 'express';
import { AuthController } from '../controllers/AuthController';
import { validateRequest } from '../middleware/validator';

const router = express.Router();

// POST /auth/login
router.post('/login', 
  validateRequest.body({
    phone: Joi.string().required(),
    password: Joi.string().required()
  }),
  AuthController.login
);

// POST /auth/register
router.post('/register',
  validateRequest.body({
    names: Joi.string().required(),
    email: Joi.string().email().optional(),
    phone: Joi.string().required(),
    county: Joi.string().optional(),
    subCounty: Joi.string().optional(),
    password: Joi.string().min(8).required()
  }),
  AuthController.register
);

// POST /auth/reset-password
router.post('/reset-password',
  validateRequest.body({
    phone: Joi.string().required()
  }),
  AuthController.resetPassword
);

// GET /auth/me
router.get('/me', authMiddleware, AuthController.getProfile);

export default router;
```

#### Posts Routes (src/routes/posts.ts)

```typescript
import express from 'express';
import multer from 'multer';
import { PostController } from '../controllers/PostController';
import { authMiddleware } from '../middleware/authMiddleware';

const router = express.Router();
const upload = multer({ 
  dest: '/tmp',
  limits: { fileSize: 5 * 1024 * 1024 },  // 5MB
  fileFilter: (req, file, cb) => {
    if (!file.mimetype.startsWith('image/')) {
      return cb(new Error('Must be image file'));
    }
    cb(null, true);
  }
});

// GET /posts
router.get('/', authMiddleware, PostController.getAllPosts);

// POST /posts
router.post('/',
  authMiddleware,
  upload.single('image'),
  PostController.createPost
);

// GET /posts/:id
router.get('/:id', authMiddleware, PostController.getPostById);

export default router;
```

#### Media Routes (src/routes/media.ts)

```typescript
import express from 'express';
import { MediaController } from '../controllers/MediaController';
import { authMiddleware } from '../middleware/authMiddleware';

const router = express.Router();

// GET /media
router.get('/', 
  authMiddleware,
  MediaController.getMediaFeed
);

export default router;
```

#### Messaging Routes (src/routes/messaging.ts)

```typescript
import express from 'express';
import multer from 'multer';
import { MessageController } from '../controllers/MessageController';
import { authMiddleware } from '../middleware/authMiddleware';

const router = express.Router();
const upload = multer({ dest: '/tmp', limits: { fileSize: 5 * 1024 * 1024 } });

// GET /messaging
router.get('/', authMiddleware, MessageController.getThreads);

// GET /messaging/:recipientId
router.get('/:recipientId', authMiddleware, MessageController.getMessages);

// POST /messaging
router.post('/',
  authMiddleware,
  upload.single('attachment'),
  MessageController.sendMessage
);

export default router;
```

#### Geographic Routes (src/routes/geo.ts)

```typescript
import express from 'express';
import { GeoController } from '../controllers/GeoController';

const router = express.Router();

// GET /data/counties
router.get('/counties', GeoController.getCounties);

export default router;
```

---

### 4.5 Service Layer Implementation Examples

#### AuthService.ts

```typescript
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { User } from '../models/User';
import { phoneNormalizer } from '../utils/phoneNormalizer';

class AuthService {
  async login(phone: string, password: string): Promise<LoginResponse> {
    // Normalize phone
    const normalizedPhone = phoneNormalizer.toInternational(phone);
    
    // Find user
    const user = await User.findOne({ where: { phone: normalizedPhone } });
    if (!user) {
      throw new Error('Invalid phone or password');
    }
    
    // Verify password
    const isValid = await bcrypt.compare(password, user.password_hash);
    if (!isValid) {
      throw new Error('Invalid phone or password');
    }
    
    // Generate JWT
    const token = jwt.sign(
      {
        userId: user.id,
        role: user.role,
        createdAt: Date.now(),
        issued: Date.now(),
        expires: Date.now() + 24 * 60 * 60 * 1000  // 24 hours in ms
      },
      process.env.JWT_SECRET,
      { expiresIn: '24h' }
    );
    
    return {
      status: 'success',
      token,
      issued: Date.now(),
      expires: Date.now() + 24 * 60 * 60 * 1000,
      newUser: false,
      userDetails: {
        id: user.id,
        createdAt: user.created_at.toISOString(),
        updatedAt: user.updated_at.toISOString(),
        names: user.names,
        role: user.role,
        phone: user.phone,
        county: user.county,
        subCounty: user.sub_county,
        paidUser: user.paid_user
      }
    };
  }

  async register(data: RegisterRequest): Promise<RegisterResponse> {
    // Normalize phone
    const normalizedPhone = phoneNormalizer.toInternational(data.phone);
    
    // Check if user exists
    const existing = await User.findOne({ where: { phone: normalizedPhone } });
    if (existing) {
      const error = new Error('Phone number already registered');
      error['status'] = 409;
      throw error;
    }
    
    // Hash password
    const passwordHash = await bcrypt.hash(data.password, 10);
    
    // Create user
    const user = await User.create({
      phone: normalizedPhone,
      password_hash: passwordHash,
      names: data.names,
      email: data.email,
      county: data.county,
      sub_county: data.subCounty,
      role: 'USER'
    });
    
    return {
      status: 'success',
      message: 'User registered successfully',
      user: {
        id: user.id,
        createdAt: user.created_at.toISOString(),
        updatedAt: user.updated_at.toISOString(),
        names: user.names,
        email: user.email,
        role: user.role,
        phone: user.phone,
        county: user.county,
        subCounty: user.sub_county
      }
    };
  }
}

export default new AuthService();
```

#### PostService.ts

```typescript
import { Post } from '../models/Post';
import { StorageService } from './StorageService';

class PostService {
  async createPost(userId: string, description: string, imageFile: Express.Multer.File): Promise<CreatePostResponse> {
    // Upload image
    const imageUrl = await StorageService.uploadImage(imageFile, `posts/${userId}`);
    
    // Create post record
    const post = await Post.create({
      created_by: userId,
      description,
      image_key: `posts/${userId}/${imageFile.filename}`,
      image_url: imageUrl
    });
    
    return {
      status: 'success',
      id: post.id,
      image: `posts/${post.id}/image.jpg`,
      imageUrl,
      status: 'success'
    };
  }

  async getPostById(postId: string): Promise<Post> {
    const post = await Post.findByPk(postId);
    if (!post) {
      const error = new Error('Post not found');
      error['status'] = 404;
      throw error;
    }
    return post;
  }

  async getAllPosts(limit: number = 100, offset: number = 0): Promise<{ posts: Array<{post: Post}> }> {
    const posts = await Post.findAll({
      order: [['created_at', 'DESC']],
      limit,
      offset
    });
    
    return {
      status: 'success',
      posts: posts.map(post => ({ post }))
    };
  }
}

export default new PostService();
```

#### MediaService.ts

```typescript
import { Media } from '../models/Media';

class MediaService {
  async getMediaFeed(limit: number = 100, offset: number = 0): Promise<MediaFeedResponse> {
    const media = await Media.findAll({
      order: [['created_at', 'DESC']],
      limit,
      offset
    });
    
    return {
      status: 'success',
      media: media.map(item => ({
        id: item.id,
        title: item.title,
        company: item.company,  // CRITICAL: field name is "company"
        type: item.type,
        thumbnail: item.thumbnail_url,  // CRITICAL: field name is "thumbnail"
        video: item.video_url,  // CRITICAL: field name is "video"
        description: item.description,
        createdAt: item.created_at.toISOString(),
        updatedAt: item.updated_at.toISOString(),
        userId: item.user_id
      }))
    };
  }
}

export default new MediaService();
```

---

### 4.6 Critical Implementation Details

#### JWT Token Format

```
Header: {
  "alg": "HS512",
  "typ": "JWT"
}

Payload: {
  "createdAt": 1710857321887,      // milliseconds (matches Postman examples)
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "USER",
  "issued": 1710857321887,         // milliseconds
  "expires": 1710944721887         // milliseconds
}
```

**Implementation:**
```typescript
const token = jwt.sign(
  {
    createdAt: Date.now(),         // Returns milliseconds
    userId: user.id,
    role: user.role,
    issued: Date.now(),
    expires: Date.now() + 86400000  // Add 24 hours in milliseconds
  },
  process.env.JWT_SECRET,
  { algorithm: 'HS512' }
);
```

#### Phone Normalization Utility

```typescript
class PhoneNormalizer {
  toInternational(phone: string): string {
    const trimmed = phone.trim();
    
    if (trimmed.startsWith('+254')) {
      return trimmed;
    }
    
    if (trimmed.startsWith('0') && trimmed.length === 10) {
      return '+254' + trimmed.slice(1);
    }
    
    // Assume it's already formatted or pass through
    return trimmed;
  }

  toLocal(phone: string): string {
    if (phone.startsWith('+254')) {
      return '0' + phone.slice(4);
    }
    return phone;
  }
}

export const phoneNormalizer = new PhoneNormalizer();
```

#### Field Name Mapping

**Critical:** Field names in API responses MUST match Android @SerializedName mappings:

```typescript
// In MediaService.ts - CRITICAL field names
{
  id: item.id,
  title: item.title,
  
  // ✓ CORRECT: Use "company" field name (not "channel")
  company: item.company_name,  
  
  // ✓ CORRECT: Use "thumbnail" field name (not "thumbnailUrl")
  thumbnail: item.thumbnail_url,
  
  // ✓ CORRECT: Use "video" field name (not "mediaUrl")
  video: item.media_url,
  
  type: item.type,
  description: item.description,
  createdAt: item.created_at.toISOString(),
  updatedAt: item.updated_at.toISOString(),
  userId: item.user_id
}

// NOT:
{
  channel: item.company_name,       // ✗ WRONG - Android expects "company"
  thumbnailUrl: item.thumbnail_url, // ✗ WRONG - Android expects "thumbnail"
  mediaUrl: item.media_url          // ✗ WRONG - Android expects "video"
}
```

---

## Part 5: Recommended Production-Ready Structure

### 5.1 Express App Setup

```typescript
// src/app.ts
import express from 'express';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import { errorHandler } from './middleware/errorHandler';
import { logger } from './middleware/logger';

import authRoutes from './routes/auth';
import postRoutes from './routes/posts';
import mediaRoutes from './routes/media';
import messagingRoutes from './routes/messaging';
import geoRoutes from './routes/geo';

const app = express();

// Security middleware
app.use(helmet());
app.use(morgan('combined', { stream: logger.stream }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,  // 15 minutes
  max: 100,  // 100 requests per window
  message: 'Too many requests'
});
app.use(limiter);

// Parsing middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// CORS (configure based on environment)
if (process.env.NODE_ENV === 'development') {
  app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', 'http://localhost:3000');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    next();
  });
}

// Routes
app.use('/auth', authRoutes);
app.use('/posts', postRoutes);
app.use('/media', mediaRoutes);
app.use('/messaging', messagingRoutes);
app.use('/data', geoRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Error handling (must be last)
app.use(errorHandler);

export default app;
```

### 5.2 Environment Configuration

```bash
# .env.example
NODE_ENV=development
PORT=8080
DATABASE_URL=postgresql://user:password@localhost:5432/farmhub
JWT_SECRET=your-very-secure-random-secret-key-here
STORAGE_TYPE=local  # or 's3'
STORAGE_BUCKET=farmhub-storage
STORAGE_REGION=us-east-1  # for S3
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=xxx
LOG_LEVEL=debug
CORS_ORIGIN=https://api.farmers-hub.co.ke
```

---

### 5.3 Docker Setup

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: farmhub
      POSTGRES_USER: farmhub_user
      POSTGRES_PASSWORD: secure_password_here
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U farmhub_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

---

## Part 6: Detected Issues & Fixes Required

### Issue #1: Media Response Field Names

**Problem:** Android expects specific field names via @SerializedName

**Current Status:** ✗ Likely broken if backend returns wrong field names

**Fix Required:**
```typescript
// Backend MUST return these exact field names:
{
  "company": "AgriTech Kenya",        // NOT "channel"
  "thumbnail": "https://...",         // NOT "thumbnailUrl"
  "video": "https://..."              // NOT "mediaUrl"
}
```

**Validation Checklist:**
- [ ] Verify /media endpoint returns "company" field
- [ ] Verify /media endpoint returns "thumbnail" field
- [ ] Verify /media endpoint returns "video" field
- [ ] Run Android app in debug mode to confirm deserialization

---

### Issue #2: Timestamp Formats Inconsistent

**Problem:** Different timestamp formats across endpoints

**Current Status:** ✗ Mixed: milliseconds vs ISO 8601

**Where:**
- Login response: milliseconds (`"issued": 1710857321887`)
- Profile response: ISO 8601 (`"createdAt": "2021-06-15T10:30:00Z"`)
- Media response: ISO 8601 (`"createdAt": "2024-01-15T08:00:00Z"`)

**Android Impact:** Works because Android has helper methods to parse both formats, but inconsistency is poor API design

**Fix Required:**
- [ ] Choose ONE format: **milliseconds** (for consistency with existing JWT payload)
- [ ] Or: Use ISO 8601 everywhere (more modern/standard)
- [ ] Update all endpoints to matched chosen format

**Recommended:** Standardize on **ISO 8601** for REST API (milliseconds fine for JWT payload internal representation)

---

### Issue #3: Token Persistence

**Problem:** JWT token stored in volatile memory only

**Current Status:** ✗ Not production-ready

**Risk:** User loses authentication on app restart

**Fix Required (Android + Backend collaboration):**
1. Android: Use encrypted SharedPreferences or Android Keystore to persist token
2. Backend: Implement token refresh endpoint: `POST /auth/refresh`

**Recommended Endpoint (Android can add later):**
```
POST /auth/refresh
Request: 
{
  "refreshToken": "refresh_token_jwt"
}
Response:
{
  "token": "new_access_token",
  "refreshToken": "new_refresh_token",  // optional
  "expires": 1234567890
}
```

---

### Issue #4: POST Request Multipart Field Names

**Problem:** Android sends field names that may not match backend expectations

**Current Fields in Android:**
- Post creation: `image`, `description`
- Message sending: `text`, `phone`, `attachment`

**Backend Validation:**
- [ ] Verify form field names exactly match these (case-sensitive)
- [ ] Verify fields are required where expected
- [ ] Verify file types validated

---

### Issue #5: Missing User Roles in Responses

**Problem:** Android doesn't utilize specialist/admin features, but backend should still separate concerns

**Current Status:** ✓ Implemented in backend, ignored by Android

**Ensure Backend Implements:**
- [ ] Role-based endpoint restrictions
- `/posts/specialist` requires role="SPECIALIST"
- `/posts/specialist/{id}` requires role="SPECIALIST"

---

### Issue #6: Phone Number Validation Inconsistency

**Problem:** Backend and Android normalize differently or at different times

**Check These:**
- [ ] Backend accepts: "0719697174", "0719697174", "+254719697174"
- [ ] Backend stores all as: "+254719697174"
- [ ] Messaging endpoint uses normalized format
- [ ] Duplicate account check handles all formats

---

## Part 7: Implementation Checklist

### Phase 0: Foundation
- [ ] Set up Node.js project with TypeScript
- [ ] Set up PostgreSQL database
- [ ] Set up Redis (optional but recommended)
- [ ] Create environment variables
- [ ] Set up Docker Compose

### Phase 1: Authentication
- [ ] Implement User model & table
- [ ] Implement AuthService with login/register/profile
- [ ] Implement JWT token generation with correct payload format
- [ ] Implement password hashing with bcryptjs
- [ ] Implement authMiddleware for Bearer token validation
- [ ] Test all auth endpoints with Postman

### Phase 2: Posts
- [ ] Implement Post model & table
- [ ] Implement PostService with create/read/list
- [ ] Implement multer for file upload handling
- [ ] Implement StorageService for image persistence
- [ ] Test all post endpoints with image uploads
- [ ] **VERIFY:** Response includes imageUrl as full HTTPS URL

### Phase 3: Media
- [ ] Implement Media model & table
- [ ] Implement MediaService for feed retrieval
- [ ] **CRITICAL:** Ensure response uses "company", "thumbnail", "video" field names
- [ ] Populate media table with sample data
- [ ] Test /media endpoint with Android app
- [ ] **VERIFY:** Android receives video URLs correctly

### Phase 4: Messaging
- [ ] Implement Message & Thread models & tables
- [ ] Implement MessageService for send/read/list
- [ ] Implement thread grouping logic
- [ ] Test all messaging endpoints
- [ ] **VERIFY:** Field name handling for multiple ID/text variants

### Phase 5: Geographic Data
- [ ] Implement County & SubCounty models & tables
- [ ] Populate with Kenya county data
- [ ] Implement GeoService
- [ ] Test /data/counties endpoint

### Phase 6: Error Handling & Validation
- [ ] Implement centralized error handler
- [ ] Implement request validation middleware
- [ ] Implement logging middleware
- [ ] Test error responses match Android expectations

### Phase 7: Security & Production
- [ ] Implement rate limiting
- [ ] Implement CORS correctly
- [ ] Implement helmet security headers
- [ ] Set up HTTPS/SSL
- [ ] Implement request validation
- [ ] Implement input sanitization
- [ ] Test with OWASP Top 10

### Phase 8: Testing
- [ ] Unit tests for all services
- [ ] Integration tests for API endpoints
- [ ] E2E tests with Android app
- [ ] Load testing
- [ ] Security testing

### Phase 9: Deployment
- [ ] Set up CI/CD pipeline
- [ ] Deploy to staging environment
- [ ] QA testing with Android team
- [ ] Deploy to production
- [ ] Monitor logs & errors

---

## Part 8: API Documentation (OpenAPI/Swagger)

All endpoints should be documented with Swagger/OpenAPI:

```yaml
openapi: 3.0.0
info:
  title: FarmHub API
  version: 1.0.0
  description: Backend API for FarmHub Android App
servers:
  - url: https://api.farmers-hub.co.ke
paths:
  /auth/login:
    post:
      summary: User login
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/LoginRequest'
      responses:
        '200':
          description: Login successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LoginResponse'
        '401':
          description: Invalid credentials
# ... etc for all endpoints
```

---

## Conclusion

This specification provides a **complete, production-ready backend architecture** derived from the existing Android implementation. The Android app is the source of truth; the backend must conform to these contracts.

**Key Takeaways:**
1. **Field names matter:** Android uses `@SerializedName` mapping; backend field names must match exactly
2. **Timestamps:** Standardize format across all endpoints (recommend ISO 8601)
3. **Multipart uploads:** Field names are strict ("image", "description", "text", "phone")
4. **Phone normalization:** Consistent +254 format required for messaging
5. **Error handling:** Return proper HTTP status codes (401, 403, 404, 400, 500)
6. **Security:** Implement rate limiting, input validation, password hashing
7. **Scalability:** Use pagination, indexed queries, connection pooling

**Next Steps:**
1. Share this spec with backend team
2. Review Postman collection against this spec
3. Implement backends endpoints phase-by-phase
4. Test each endpoint with Android app in development
5. Deploy to staging, run full QA
6. Document any deviations from this spec

---

**Document Owner:** Android Implementation Analysis  
**Last Updated:** May 11, 2026  
**Status:** Ready for Backend Implementation

