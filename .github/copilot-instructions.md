# FarmHub Mobile App – API Integration Plan

Last updated: 2025-09-11

## Objective
Integrate all missing backend API endpoints already exposed in the provided Postman collection into the existing Android app with minimal UI changes and no backend modifications.

## Guiding Principles
- No backend changes.
- Prefer adding repositories + models over modifying UI structure.
- Keep UI updates minimal (replace mock/static data with live data only where needed).
- Preserve current ViewModel patterns (state holders + repository callbacks / coroutines).
- Consistent error handling + loading indication.
- Centralize Retrofit changes (UserService + models).

## Current Implemented Endpoints
| Feature | Endpoint | Method | Status |
|---------|----------|--------|--------|
| Login | /auth/login | POST | Implemented |
| Register | /auth/register | POST | Implemented |
| Profile (self) | /auth/me | GET | Implemented |
| Posts (list general) | /posts | GET | Implemented |
| Create Post | /posts | POST (multipart) | Implemented |
| Send Message | /messaging | POST (multipart optional) | Implemented |

## Missing Endpoints (From Postman Collection)
| Category | Endpoint | Method | Purpose | Needed By |
|----------|----------|--------|---------|-----------|
| Password | /auth/reset-password | POST | Trigger reset flow | Future (not in UI yet) |
| Posts | /posts/{id} | GET | Fetch single post detail | Potential after create / detail screen |
| Posts (Specialist) | /posts/specialist | GET | Specialist workload listing | Pending specialist UI |
| Post Processing | /posts/specialist/{id} | POST multipart | Specialist response / processing | Not in current UI |
| Messaging | /messaging | GET | Threads / conversations | ChatScreen (replace mock) |
| Messaging | /messaging/{recipientId} | GET | Messages in thread | ChatScreen (after thread select) |
| Geo Data | /data/counties | GET | List counties | Signup (if county selection added) |
| Geo Data | /data/counties?county=NAME | GET | Subcounties by county | Signup enhancement |
| Media | /media | GET | Video/media feed | VideoScreen (currently static) |

## UI Components Affected
| Screen | Current State | Gap | Action |
|--------|---------------|-----|--------|
| ChatScreen | Local mock list + send API | No thread fetch | Integrate threads + per-thread messages; keep optimistic send |
| VideoScreen | Static list in VideoViewModel | Needs dynamic feed | Replace static provider with repository using /media |
| FarmHelp (Post flow) | Submits post only | No retrieval of created detail | (Optional) fetch /posts/{id} after success to confirm payload |
| Signup (not shown) | Registers only | Lacks county/subcounty population | Add optional lazy loading list if UI exists |
| Specialist features | Not present | Endpoints exist | Defer until UI added |

## Incremental Integration Order
1. Retrofit & Models scaffolding for all missing endpoints.
2. Add repositories:
   - MediaRepository (/media)
   - MessagingRepository extension (GET threads, GET messages)
   - CountiesRepository (/data/counties)
   - PostDetailRepository (/posts/{id}) – optional
3. Extend UserService interface safely (do not break existing signatures).
4. Add data models (see section below).
5. ChatScreen refactor:
   - Introduce ConversationList + SelectedThread state.
   - Load threads on first composition.
   - On thread select, fetch messages; continue using existing sendMessage for POST.
6. VideoScreen refactor:
   - Introduce MediaViewModel using MediaRepository.
   - Replace static list; fallback to cached in-memory list if API fails.
7. Optional: After post creation, call getPostById for verification (non-blocking UX update).
8. (Deferred) Signup county/subcounty dynamic support once UI exists.
9. Robust error & empty states (snackbar/toast or inline text) – minimal visual changes.
10. Add simple instrumentation / unit tests for repositories (where feasible) mocking Retrofit.

## Data Models To Add
(Names proposed; align with JSON structure once sample responses inspected.)
- media/MediaItemResponse.kt
  - id, title, channel/name, type, thumbnailUrl, createdAt
- messaging/ThreadResponse.kt
  - threadId, participants, lastMessage, updatedAt
- messaging/MessageItemResponse.kt
  - id, senderId, text, attachmentUrl?, createdAt
- messaging/ThreadListResponse.kt (status, threads: List<ThreadResponse>)
- messaging/MessagesResponse.kt (status, messages: List<MessageItemResponse>)
- geo/CountiesResponse.kt (counties: List<String>)
- geo/SubCountiesResponse.kt (subCounties: List<String>) – or reuse same with param
- posts/PostDetailResponse.kt (mirror existing Post + maybe processing metadata)
- auth/ResetPasswordRequest.kt (phone)
- auth/GenericStatusResponse.kt (status, message?)

(Verify actual backend response fields before finalizing — adjust naming to match.)

## Retrofit Interface Additions (UserService)
(Additions only; existing remains untouched.)
- @POST("auth/reset-password") fun resetPassword(@Body body: ResetPasswordRequest): Call<GenericStatusResponse>
- @GET("posts/{id}") fun getPost(@Path("id") id: String): Call<PostDetailResponse>
- @GET("posts/specialist") fun getSpecialistPosts(): Call<GetAllPostsResponse>
- @Multipart @POST("posts/specialist/{id}") fun processPost(...)
- @GET("messaging") fun getThreads(): Call<ThreadListResponse>
- @GET("messaging/{recipientId}") fun getMessages(@Path("recipientId") id: String): Call<MessagesResponse>
- @GET("data/counties") fun getCounties(@Query("county") county: String? = null): Call<CountiesResponse>
- @GET("media") fun getMediaFeed(): Call<MediaFeedResponse>

## Repository Layer Plan
| Repository | Responsibilities | Concurrency |
|------------|------------------|-------------|
| MediaRepository | Fetch media feed; simple memory cache | Callback or suspend wrapper |
| MessagingRepository (extend current) | getThreads(), getMessages(threadId) | Use suspend + withContext(IO) |
| CountiesRepository | getCounties(), getSubCounties(county) | Cache results in-memory |
| PostDetailRepository | fetchPost(id) | Suspend |

Migrate existing MessageRepository to unify style (optionally) but keep sendMessage signature stable.

## ViewModel Changes
- MessageViewModel: add
  - threads: StateFlow<List<ThreadUiModel>>
  - selectedThreadId
  - loadThreads(), loadMessages(threadId)
  - messages: StateFlow<List<MessageUiModel>>
  - Distinguish Sending vs LoadingThreads vs LoadingMessages states.
- MediaViewModel: new
  - uiState: (Loading / Success(list) / Error)
  - refresh() method.

Keep current simple composables; conditionally display lists or loading indicator.

## Minimal UI Adjustments
- ChatScreen: add thread sidebar / dropdown (if space) OR first thread auto-selected (pref minimal: show a thread selector dropdown above messages).
- VideoScreen: same layout, just swap data source + add pull-to-refresh (optional later).
- Avoid redesign; only integrate dynamic data.

## Error Handling Strategy
- Repositories: map network + HTTP errors to sealed Result (Success / NetworkError / HttpError(code, body) / Unexpected).
- ViewModels: convert to user-facing short message ("Unable to load feed. Pull to retry.").
- For sendMessage keep optimistic append; if failure, mark last message as failed (future enhancement – for now append error bot response as current behavior).

## Caching (Phase 2 - Optional)
- In-memory only; no persistence required now.
- Add simple timestamp to avoid refetch within 30s for media + counties.

## Testing (Lightweight)
- Add mockable Retrofit instance via dependency injection (simple provider object) – optional if time.
- Unit test: MediaRepository success + HTTP 500 path.
- Unit test: MessageViewModel loadThreads happy path.

## Acceptance Criteria
- All listed missing endpoints (except deferred ones) callable via repositories.
- Video feed displays remote data when API available; falls back to empty state with retry.
- ChatScreen loads threads + messages before sending new message.
- No crashes if endpoints return unexpected empty/ null lists.
- Existing working features (login, signup, create post) unchanged.

## Deferred Items
- Specialist processing endpoints (/posts/specialist, /posts/specialist/{id}) until specialist UI emerges.
- Password reset screen (backend endpoint prepared but UI not present).
- County/subcounty dynamic selection until signup UI confirms fields.

## Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| Backend response shape mismatch | Log + fail gracefully; adjust models iteratively |
| Chat threads large payload | Paginate later if needed (add ?page support when backend provides) |
| Media endpoint latency | Show loading shimmer (future) |

## Implementation Sequence Checklist
1. Add new model data classes.
2. Extend UserService with endpoints.
3. Create new repositories.
4. Implement MediaViewModel.
5. Refactor ChatScreen + MessageViewModel for threads/messages.
6. Wire UI to new flows (guarded by feature flags if needed – optional).
7. Manual QA (login, feed, chat send, chat load threads, media feed fallback).
8. Add minimal tests.
9. Update README (optional) documenting new APIs.

## Coding Conventions (Follow Existing Style)
- Data classes: snake_case fields mapped via @SerializedName if backend uses snake_case.
- Use suspend + execute() for new GETs (consistent with existing sendMessage pattern) OR standard enqueue with callbacks—prefer suspend for new code.
- Avoid introducing coroutines into legacy callback repositories unless refactoring whole file.

## Example Pattern (Suspend Wrapper)
```
val response = api.getMediaFeed().execute()
if (response.isSuccessful) response.body() else handleError()
```
Wrap in withContext(Dispatchers.IO) inside repository.

## Migration Notes
No breaking change expected to existing ApiClient. Adding endpoints is additive. Optionally introduce DI later (Hilt/Koin) – out of scope.

## For Future Contributors (Copilot Prompt Hints)
When implementing missing endpoints:
- Search for existing repository first.
- If extending Message flow, keep SendMessageUiState intact; add parallel states for threads/messages.
- Do not remove existing static video list until remote feed verified; keep as fallback.
- Always update ApiClient.setBearerToken when login completes (already done) before calling protected endpoints.

END OF PLAN

