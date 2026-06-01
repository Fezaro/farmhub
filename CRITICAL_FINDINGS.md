# Critical Findings & Implementation Summary
## FarmHub Backend - Derived from Android Implementation

**Date:** May 11, 2026  
**Prepared for:** Backend Development Team  
**Authority:** Android Source Code Analysis (100% complete)

---

## Executive Summary

The Android app is a **mature, well-structured Kotlin/Compose implementation** that provides a clear blueprint for backend requirements. All API contracts are defined by Android's Retrofit interfaces and data models.

### Key Facts
- **Language:** Kotlin (100% compiled, not interpreted)
- **State Management:** Coroutines + StateFlow (reactive Kotlin)
- **Network:** Retrofit 2 + OkHttp 3
- **Architecture:** Repository layer between viewmodels and API client
- **Authentication:** JWT bearer token (24-hour expiry)
- **File Uploads:** Multipart/form-data (image files up to 5MB assumed)
- **Database:** None on client (all data stateless, fetched from API)

---

## Critical Implementation Success Factors

### 1. Field Name Mapping (HIGHEST PRIORITY)

**Problem:** Android uses Gson with @SerializedName mappings

**Media Endpoint MUST return:** (exact field names required)
```json
{
  "id": "uuid",
  "title": "Video title",
  "company": "Channel name",           // NOT "channel"
  "thumbnail": "https://...",          // NOT "thumbnailUrl"
  "video": "https://...",              // NOT "mediaUrl"
  "type": "tutorial",
  "description": "...",
  "createdAt": "2024-01-15T08:00:00Z",
  "userId": "creator-uuid"
}
```

**If backend returns wrong names:** Android silently deserializes to null values → video feed displays nothing without error

**Test Command:**
```bash
curl -H "Authorization: Bearer <token>" https://api.farmers-hub.co.ke/media | jq '.media[0]'
# Verify keys: id, title, company (not channel), thumbnail (not thumbnailUrl), video (not mediaUrl)
```

---

### 2. HTTP Status Codes (CRITICAL)

Android httpResponseInterceptor specific behavior:

```
401 Unauthorized:
  → Triggers logout
  → AuthManager.handleUnauthorized() called
  → User redirected to login screen
  ✗ DO NOT return 403 Forbidden when token invalid
  ✗ DO NOT return custom status code

400 Bad Request:
  → Validation error from repository
  → Error message shown to user
  → Request not retried

404 Not Found:
  → Resource doesn't exist
  → Graceful error handling expected

500 Server Error:
  → Logged with full body/message
  → User sees generic "Server error"
  → Not retried automatically
```

**Test:** Expired token should return 401, not 403 or error JSON with custom codes.

---

### 3. Token Format (JWT Payload)

**Backend MUST generate JWT with this exact payload structure:**

```json
{
  "createdAt": 1710857321887,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "USER",
  "issued": 1710857321887,
  "expires": 1710944721887
}
```

**MUST use:**
- Algorithm: HS512 (not HS256, not RS256)
- Timestamps in **milliseconds** (not seconds)
- expiresIn: should be 86400000ms (24 hours)

**Test Command:**
```bash
# Decode JWT from login response
echo "token_value" | cut -d. -f2 | base64 -d | jq
# Verify: createdAt, userId, role, issued, expires all present
# Verify: expires is milliseconds (~13 digits)
```

---

### 4. Phone Normalization

**Android normalizes phone numbers:**
- Input: "0719697174" → Output: "+254719697174"
- Input: "+254719697174" → Output: "+254719697174" (unchanged)
- Input: "+27..." → Not handled (not SA numbers supported)

**Backend MUST:**
- Accept both formats: "0719..." and "+254..."
- Store in normalized format: "+254..."
- Return normalized format in all responses
- Use normalized format for thread/message lookups

**Failure Mode:** User sends message to "+254719697174", backend stores message to "0719697174" → threads don't match → messages appear in wrong conversation

---

### 5. Multipart Field Names (STRICT)

**Android sends these exact field names - they are NOT negotiable:**

**POST /posts:**
```
field: "image"        (binary file)
field: "description"  (text)
```

**POST /messaging:**
```
field: "text"         (text message)
field: "phone"        (recipient phone)
field: "attachment"   (optional binary file)
```

**Backend MUST:**
- Expect these exact field names (case-sensitive)
- Return 400 if missing required fields
- Ignore additional fields
- Validate file types

---

### 6. Response Wrapper Patterns

**Android expects specific response structures:**

**Pattern 1: Generic wrapper**
```json
{
  "status": "success",
  "message": "Optional message",
  "data": { ... }
}
```

**Pattern 2: Post wrapper (extra indirection)**
```json
{
  "status": "success",
  "posts": [
    {
      "post": { id, description, imageUrl, ... }
    }
  ]
}
```

**Pattern 3: Media wrapper**
```json
{
  "status": "success",
  "media": [ ... items with company/thumbnail/video fields ... ]
}
```

**Android field extraction:**
```kotlin
// For GetAllPostsResponse
response.body()?.posts?.map { it.post }  // Extra unwrapping layer required

// For MediaFeedResponse
response.body()?.media  // Direct array access
```

---

## Data Model Highlights

### User Model
```
id (UUID)
phone (string, normalized +254XXXXXXXXX)
password_hash (bcrypt)
names (string)
email (string, optional)
role (enum: USER, SPECIALIST, ADMINISTRATOR)
county (string)
sub_county (string)
paid_user (boolean, NOT string)
created_at (ISO 8601)
updated_at (ISO 8601)
```

**Common Errors:**
- ✗ `paid_user: "true"` (string) → Android fails to deserialize
- ✓ `paid_user: true` (boolean) → Correct

### Post Model
```
id (UUID)
created_by (UUID, user_id)
description (text)
image_key (string, storage path)
image_url (string, full public HTTPS URL)
created_at (ISO 8601)
updated_at (ISO 8601)
```

**Must provide:** `image_url` as full HTTPS URL (not relative path)

### Message Model
```
id (UUID)
sender_id (UUID or phone)
recipient_id (UUID or phone)
text (string)
attachment_url (string, optional full HTTPS URL)
thread_id (UUID, optional, groups related messages)
created_at (ISO 8601)
```

**Multiple ID field support (for robustness):**
Android MessageItemResponse handles:
- `senderId` / `sender_id` / `from` (tries all three)
- `recipientId` / `recipient_id`

Backend should return ONE consistent field name, but Android defensive coding handles variations.

---

## Security Considerations

### Passwords
- ✓ Hash with bcryptjs before storage
- ✗ Never log passwords
- ✗ Never return passwords in API responses
- Transmission: HTTPS only (assumed, not Android's concern)

### JWT Tokens
- ✓ Sign with HS512 algorithm + secret key
- ✓ Include expiration (24 hours recommended)
- ✓ Validate signature on every request
- ✗ Don't store user role in token without verification
- ✗ Don't trust client to modify token

### Authorization
- ✓ Verify Bearer token on protected endpoints
- ✓ Verify role on specialist endpoints
- ✗ Don't allow cross-user access to private data
- ✗ Don't allow user to delete/modify other users' posts

---

## Common Failure Modes

### Failure #1: Fields Deserialized as Null
**Symptom:** Video feed shows empty list, no error message  
**Cause:** Field names in /media don't match @SerializedName mappings  
**Verification:** Check response JSON for "company", "thumbnail", "video" keys

### Failure #2: User Logged Out on Each Request
**Symptom:** 401 responses logged repeatedly  
**Cause:** Backend returning 403 instead of 401 for invalid tokens  
**Fix:** Return HTTP 401 Unauthorized for expired/invalid tokens

### Failure #3: Messages Sent to Wrong Recipient
**Symptom:** Messages appear in different conversations  
**Cause:** Backend not normalizing phone numbers consistently  
**Fix:** Normalize all phone numbers to +254... format before storage

### Failure #4: Image Upload Fails Silently
**Symptom:** Post submission hangs, no error displayed  
**Cause:** Multipart field names don't match (e.g., backend expects "file" but Android sends "image")  
**Fix:** Check server logs for missing field errors

### Failure #5: Timestamp Parsing Errors
**Symptom:** App crashes with date formatting exception  
**Cause:** Inconsistent timestamp formats (milliseconds vs seconds vs ISO 8601)  
**Fix:** Use consistent ISO 8601 format throughout

---

## Testing Checklist for Backend Team

### Pre-Deployment QA

**Authentication**
- [ ] Login with valid credentials returns 200 + token
- [ ] Login with invalid credentials returns 401
- [ ] Expired token returns 401 (not 403)
- [ ] GET /auth/me with invalid token returns 401
- [ ] Register new user with all fields works
- [ ] Register with duplicate phone returns 409
- [ ] Passwords not returned in responses

**Posts**
- [ ] GET /posts returns list of PostWrapper objects
- [ ] POST /posts with image + description returns imageUrl
- [ ] POST /posts without image returns 400
- [ ] POST /posts without Bearer token returns 401
- [ ] GET /posts/{id} returns detail
- [ ] GET /posts/{id} with invalid ID returns 404

**Media**
- [ ] GET /media returns list with "company", "thumbnail", "video" field names (**CRITICAL**)
- [ ] GET /media with invalid token returns 401
- [ ] All media items have thumbnail_url and video_url as full HTTPS URLs
- [ ] `type` field optional but present for filtering

**Messaging**
- [ ] POST /messaging with text + phone sends successfully
- [ ] GET /messaging returns thread list
- [ ] GET /messaging/{recipientId} returns messages sorted by createdAt
- [ ] Message senderId field allows Android to determine direction (isFromCurrentUser)
- [ ] Phone numbers in messages are normalized

**Geo Data**
- [ ] GET /data/counties returns list of counties
- [ ] GET /data/counties?county=Kiambu returns subcounties
- [ ] Endpoint accessible without Bearer token (public)

**Error Handling**
- [ ] 400 errors include helpful message field
- [ ] 401 errors on protected endpoints
- [ ] 404 errors for missing resources
- [ ] No 500 errors in normal operation
- [ ] Server logs don't contain sensitive information

---

## Deployment Verification

### Manual Android Testing

1. **Install APK on device/emulator**
2. **Network traffic inspection:**
   - Enable Burp Suite or Charles proxy
   - Inspect /media response JSON for exact field names
   - Verify Authorization header: "Bearer <token>"
3. **Specific test flows:**
   - User registration → login → feed view
   - Create post with image → verify imageUrl returned
   - Send message → verify to correct recipient
   - Pull-to-refresh media feed
4. **Error scenarios:**
   - Kill backend → verify app shows connection error
   - Logout → verify 401 triggers
   - Navigate between screens → verify state preserved

---

## Backend-Android Integration Points

### Point 1: Image URL Generation
**Android expects:** Full HTTPS URL (e.g., `https://storage.co.ke/posts/uuid/file.jpg`)  
**Not acceptable:** Relative path (`/posts/uuid/file.jpg`) or partial URL

### Point 2: Token Refresh (Future)
**Currently:** Single 24-hour token, user must re-login when expired  
**Recommended:** Implement refresh endpoint for better UX  
```
POST /auth/refresh
Body: { "refreshToken": "..." }
Response: { "token": "...", "refreshToken": "...", "expires": ... }
```

### Point 3: Pagination Support (Future)
**Currently:** Client-side pagination (inefficient)  
**Recommended:** Add query params to /media
```
GET /media?limit=10&offset=0
```

### Point 4: Real-time Messaging (Future)
**Currently:** Polling GET /messaging/{recipientId}  
**Recommended:** WebSocket support for live message delivery

---

## Performance Expectations

### Expected Response Times
- Login: <500ms
- Get all posts: <1s (for ~100 posts)
- Get media feed: <2s (for ~100 videos)
- Send message: <500ms
- Get threads: <500ms

### Database Indexes Required
```sql
-- User lookups by phone
CREATE INDEX idx_users_phone ON users(phone);

-- Post filtering and sorting
CREATE INDEX idx_posts_created_by ON posts(created_by);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- Message filtering by sender/recipient
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_recipient_id ON messages(recipient_id);
CREATE INDEX idx_messages_thread_id ON messages(thread_id);
CREATE INDEX idx_messages_created_at ON messages(created_at DESC);

-- Media sorting
CREATE INDEX idx_media_created_at ON media(created_at DESC);
CREATE INDEX idx_media_type ON media(type);
```

---

## Troubleshooting Guide

**Problem:** "Unable to load feed" error in VideoScreen  
**Debug Steps:**
1. Check server logs for /media endpoint errors
2. Verify Bearer token valid: `GET /auth/me`
3. Check /media response for null values in "company", "thumbnail", "video" fields
4. Verify media records exist in database
5. Check video_url and thumbnail_url are full HTTPS URLs

**Problem:** Message delivery to wrong recipient  
**Debug Steps:**
1. Verify phone numbers normalized: all should be "+254..."
2. Check message sender_id matches current user
3. Verify thread_id consistent for conversation
4. Check recipient user exists in database with that phone

**Problem:** JWT token rejection  
**Debug Steps:**
1. Verify token algorithm is HS512
2. Verify token not expired (check `expires` field > current time in ms)
3. Verify secret key matches between token generation and validation
4. Decode token to inspect payload: `curl ... | jq '.payload'`

---

## Recommended Monitoring

### Application Metrics
- API response times (histogram)
- 401/403/404/500 error rates (counter)
- Request volume per endpoint (gauge)
- Message queue depth (if async processing)

### Business Metrics
- Active users (daily/monthly)
- Posts created per day
- Messages sent per day
- Video feed engagement

### Infrastructure Metrics
- Database connection pool utilization
- Disk usage for image/video storage
- Network bandwidth
- CPU/memory utilization

---

## Summary

The Android implementation is **production-ready and well-engineered**. Backend development should focus on:

1. **Exact API contract compliance** (field names, status codes, response structure)
2. **Robust error handling** (proper HTTP status codes, helpful messages)
3. **Data consistency** (normalized phone numbers, consistent timestamps)
4. **Security** (password hashing, JWT validation, authorization checks)
5. **Performance** (database indexes, efficient queries, connection pooling)

Backend team should reference the `BACKEND_REFACTOR_SPECIFICATION.md` document for detailed implementation guidance.

---

**Questions?** Please review the full specification document or review Android source files:
- `/app/src/main/java/com/example/app/api/UserService.kt` (all API endpoints)
- `/app/src/main/java/com/example/app/models/` (all data models)
- `/app/src/main/java/com/example/app/repository/` (how data flows to UI)

