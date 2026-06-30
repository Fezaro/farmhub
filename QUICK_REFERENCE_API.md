# Quick Reference: Backend API Implementation Guide
## For Express.js/Node.js developers

**Purpose:** Rapid implementation reference derived from Android requirements  
**Usage:** Bookmark this for endpoint implementation  
**Format:** Endpoint → Exact Request/Response Requirements

---

## Authentication API

### POST /auth/login

```
Request: POST /auth/login
Content-Type: application/json

{
  "phone": "0719697174",
  "password": "user_password"
}

Response: 200 OK
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

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Invalid phone or password"
}

Implementation Notes:
✓ Normalize phone before lookup: "0719..." → "+254719..."
✓ Hash password with bcryptjs before storage
✓ JWT payload: HS512, timestamp in milliseconds
✓ expires = issued + 86400000 (24 hours in ms)
✓ paidUser MUST be JSON boolean, not string
```

### POST /auth/register

```
Request: POST /auth/register
Content-Type: application/json

{
  "names": "Jane Smith",
  "email": "jane@example.com",
  "phone": "0715204181",
  "county": "Nairobi",
  "subCounty": "Westlands",
  "password": "secure_password_123"
}

Response: 201 Created
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

Error: 409 Conflict (phone exists)
{
  "status": "error",
  "message": "Phone number already registered"
}

Error: 400 Bad Request
{
  "status": "error",
  "message": "Invalid email format" (or other validation error)
}

Implementation Notes:
✓ Normalize phone before storage
✓ Check duplicate phone before creating
✓ Hash password with bcryptjs (10 rounds)
✓ Default role: "USER"
✓ Return normalized phone in response
✗ Never return password in response
```

### GET /auth/me

```
Request: GET /auth/me
Authorization: Bearer <jwt_token>

Response: 200 OK
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

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Unauthorized"
}

Implementation Notes:
✓ Extract userId from JWT payload
✓ Fetch user from database by id
✓ Return current user data
✓ Validate token expiry
```

### POST /auth/reset-password

```
Request: POST /auth/reset-password
Content-Type: application/json

{
  "phone": "0715204181"
}

Response: 200 OK
{
  "status": "success",
  "message": "Password reset email sent. Please check your mail or SMS."
}

Error: 404 Not Found
{
  "status": "error",
  "message": "User with this phone not found"
}

Implementation Notes:
✓ Normalize phone
✓ Find user by phone
✓ Generate reset token (temporary, short-lived)
✓ Send email/SMS with reset link (future enhancement)
✓ Do NOT reset password immediately; require confirmation
```

---

## Posts API

### GET /posts

```
Request: GET /posts
Authorization: Bearer <jwt_token>

Response: 200 OK
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

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Unauthorized"
}

Implementation Notes:
✓ Require Bearer token (protected endpoint)
✓ Return posts ordered by createdAt DESC
✓ Wrap each post in {post: {...}} object (Android expects this structure)
✓ imageUrl MUST be full HTTPS URL
✓ Page with limit/offset (default limit=100, offset=0) - not used by current Android
```

### POST /posts

```
Request: POST /posts
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data

image: <binary_file>
description: "My field is suffering from blight"

Response: 200 OK
{
  "status": "success",
  "id": "770e8400-e29b-41d4-a716-446655440001",
  "image": "posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg",
  "imageUrl": "https://storage.farmers-hub.co.ke/posts/770e8400-e29b-41d4-a716-446655440001/photo.jpg"
}

Error: 400 Bad Request
{
  "status": "error",
  "message": "Missing required fields: image, description"
}

Error: 413 Payload Too Large
{
  "status": "error",
  "message": "Image file exceeds maximum size of 5MB"
}

Implementation Notes:
✓ Field name MUST be "image" (not "file", not "photo")
✓ Field name MUST be "description" (for text)
✓ Validate: image required, must be valid image file
✓ Validate: description required, 1-500 characters
✓ Max file size: 5MB recommended
✓ Upload to storage service (S3, etc.)
✓ Return full HTTPS URL in imageUrl field
✓ Extract userId from JWT to set created_by
✓ Set created_at timestamp
```

### GET /posts/{id}

```
Request: GET /posts/770e8400-e29b-41d4-a716-446655440001
Authorization: Bearer <jwt_token>

Response: 200 OK
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

Error: 404 Not Found
{
  "status": "error",
  "message": "Post not found"
}

Implementation Notes:
✓ Require Bearer token
✓ Fetch post by UUID id
✓ Return 404 if not found
✓ Post not wrapped in {post: ...} when returning detail
```

---

## Media API

### GET /media

```
Request: GET /media
Authorization: Bearer <jwt_token>

Response: 200 OK
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
    }
  ]
}

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Unauthorized"
}

⚠️ CRITICAL: Field Names
✗ WRONG: "channel" (Android won't see it)
✓ CORRECT: "company"  (Android maps to channel via @SerializedName)

✗ WRONG: "thumbnailUrl"
✓ CORRECT: "thumbnail"  (Android maps via @SerializedName)

✗ WRONG: "mediaUrl"
✓ CORRECT: "video"  (Android maps via @SerializedName)

Implementation Notes:
✓ Require Bearer token
✓ Return media items ordered by createdAt DESC (newest first)
✓ MUST use field name "company" (not "channel")
✓ MUST use field name "thumbnail" (not "thumbnailUrl")
✓ MUST use field name "video" (not "mediaUrl")
✓ All URLs must be full HTTPS URLs
✓ Support ?limit and ?offset query params (optional, not used by current Android)
```

---

## Messaging API

### POST /messaging

```
Request: POST /messaging
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data

text: "Hi, I need advice on pest control"
phone: "+254715204181"
attachment: <optional_binary_file>

Response: 200 OK
{
  "status": "success",
  "message": "Message sent successfully"
}

Error: 400 Bad Request
{
  "status": "error",
  "message": "Invalid recipient phone"
}

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Unauthorized"
}

Implementation Notes:
✓ Field name MUST be "text" (message content)
✓ Field name MUST be "phone" (recipient phone)
✓ Field name MUST be "attachment" (optional file)
✓ Normalize phone numbers: "0719..." → "+254719..."
✓ recipient must exist in database
✓ sender determined from JWT userId
✓ Create or append to thread for this sender+recipient pair
✓ Max attachment size: 5MB
✓ Do NOT return message details (only confirmation)
```

### GET /messaging

```
Request: GET /messaging
Authorization: Bearer <jwt_token>

Response: 200 OK
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
    }
  ]
}

Error: 401 Unauthorized
{
  "status": "error",
  "message": "Unauthorized"
}

Implementation Notes:
✓ Require Bearer token
✓ Return all conversation threads for current user
✓ Include both camelCase and snake_case field names (for robustness)
✓ lastMessage should be most recent message text
✓ updatedAt should be most recent message timestamp
✓ Return threads ordered by updatedAt DESC
```

### GET /messaging/{recipientId}

```
Request: GET /messaging/550e8400-e29b-41d4-a716-446655440001
Authorization: Bearer <jwt_token>

Response: 200 OK
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
    }
  ]
}

Error: 404 Not Found
{
  "status": "error",
  "message": "Thread not found"
}

Implementation Notes:
✓ Require Bearer token
✓ Path param is recipient ID (UUID)
✓ Fetch all messages between current user and recipient ID
✓ Sort by createdAt ASC (oldest first)
✓ Include multiple field name variants for robustness
✓ Use camelCase primary names, snake_case as fallback
✓ from/to fields help Android determine message direction
✓ attachmentUrl optional, null if no attachment
```

---

## Geographic Data API

### GET /data/counties

```
Request: GET /data/counties
(No authentication required - public endpoint)

Response: 200 OK
{
  "status": "success",
  "counties": [
    "Baringo",
    "Bomet",
    "Bungoma",
    "Busia",
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
    "West Pokot"
  ]
}

Implementation Notes:
✓ Can be public endpoint (no Bearer token required)
✓ Return all counties
✓ Consistent spelling/capitalization
```

### GET /data/counties?county=Kiambu

```
Request: GET /data/counties?county=Kiambu
(No authentication required - public endpoint)

Response: 200 OK
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

Error: 400 Bad Request
{
  "status": "error",
  "message": "County not found"
}

Implementation Notes:
✓ Return all subcounties for given county
✓ Query param: ?county=<county_name>
✓ Return 400 if county not found
✓ Can be public endpoint
```

---

## HTTP Status Codes Reference

| Code | Meaning | Common Trigger |
|------|---------|---|
| 200 | OK | Successful GET, PUT, DELETE |
| 201 | Created | Successful POST |
| 400 | Bad Request | Validation error, missing fields |
| 401 | Unauthorized | Invalid/missing/expired JWT |
| 403 | Forbidden | User lacks authorization |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource (e.g., phone exists) |
| 413 | Payload Too Large | File exceeds size limit |
| 500 | Server Error | Unexpected error |

---

## JWT Token Generation

```typescript
import jwt from 'jsonwebtoken';

const generateToken = (userId: string, role: string) => {
  const now = Date.now();
  const expiresIn = now + 24 * 60 * 60 * 1000; // 24 hours in milliseconds
  
  const payload = {
    createdAt: now,
    userId: userId,
    role: role,
    issued: now,
    expires: expiresIn
  };
  
  return jwt.sign(payload, process.env.JWT_SECRET, {
    algorithm: 'HS512',
    expiresIn: '24h'
  });
};

const verifyToken = (token: string) => {
  try {
    return jwt.verify(token, process.env.JWT_SECRET, {
      algorithms: ['HS512']
    });
  } catch (error) {
    return null;  // Invalid or expired
  }
};
```

---

## Common Implementation Mistakes

**❌ WRONG:** Return `channel` instead of `company` in /media
```json
{ "channel": "AgriTech Kenya" }
```
→ Android receives null value, video list appears empty

**❌ WRONG:** Return status 403 for invalid token
```
HTTP 403 Forbidden
```
→ Android doesn't trigger logout on 403 (assumes permission issue)

**❌ WRONG:** Send `paidUser: "true"` (string)
```json
{ "paidUser": "true" }
```
→ Gson fails to deserialize boolean field

**❌ WRONG:** Multipart field name is "file" instead of "image"
```form
image part name: "file"
```
→ Android sends "image", backend expects "file", form parse fails

**❌ WRONG:** Return relative image URL instead of full URL
```json
{ "imageUrl": "/posts/123/photo.jpg" }
```
→ Android can't load image (missing domain)

**❌ WRONG:** Phone numbers not normalized
```
User sends: "+254719697174"
Backend stores: "0719697174"
Lookup doesn't match → messages in wrong thread
```

**✓ CORRECT:** Always normalize to +254... format before any lookup

---

## Testing Checklist

### Pre-Deployment
- [ ] `/auth/login` returns JWT token with correct payload
- [ ] `/auth/login` with invalid credentials returns 401 (not 403)
- [ ] `/auth/me` with valid token returns user data
- [ ] `/auth/me` with invalid token returns 401
- [ ] `/posts` returns list with imageUrl (full HTTPS URL)
- [ ] `POST /posts` with multipart "image" and "description" creates post
- [ ] `GET /media` returns items with exact field names: "company", "thumbnail", "video"
- [ ] **All URLs in responses are full HTTPS URLs** (not relative paths)
- [ ] `POST /messaging` with "text", "phone", optional "attachment"
- [ ] `GET /messaging` returns threads with participants
- [ ] `GET /messaging/{recipientId}` returns messages sorted by createdAt
- [ ] `GET /data/counties` works without Bearer token
- [ ] `GET /data/counties?county=Kiambu` returns subcounties
- [ ] All error responses include "status" and "message" fields
- [ ] All responses include proper HTTP status codes

---

## Useful Debug Commands

```bash
# Test login
curl -X POST https://api.farmers-hub.co.ke/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "0719697174",
    "password": "password"
  }'

# Test protected endpoint
curl -X GET https://api.farmers-hub.co.ke/auth/me \
  -H "Authorization: Bearer <token>"

# Test media endpoint
curl -X GET https://api.farmers-hub.co.ke/media \
  -H "Authorization: Bearer <token>" \
  | jq '.media[0]'  # Check for "company", "thumbnail", "video" fields

# Decode JWT payload
echo "token_value" | cut -d. -f2 | base64 -d | jq
```

---

**Use this guide as a checklist during implementation.** Reference specific endpoint section when building each route.

