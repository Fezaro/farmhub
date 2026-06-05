# API Integration Audit

Date: 2026-06-05
Source of truth: `app/FarmHub APP.postman_collection.json`

## Existing Android Integrations

| Endpoint | Method | Retrofit Service | Repository | ViewModel | Screen/Feature | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|---|---|---|
| `/auth/login` | POST | `UserService.userLogin` | `LoginRepository` | `LoginViewModel` | Auth/Login | `LoginRequest` | `LoginResponse` | Implemented + used |
| `/auth/register` | POST | `UserService.registerUser` | `SignupRepository` | `SignupViewModel` | Auth/Signup | `RegisterRequest` | `RegisterResponse` | Implemented + used |
| `/auth/me` | GET | `UserService.getUserProfile` | `ProfileRepository` (+ `MessageRepository` hydration) | `ProfileViewModel`, `MessageViewModel` | Profile, Chat hydration | n/a | `UserProfileResponse` | Implemented + used |
| `/posts` | GET | `UserService.getAllPosts` | `PostRepository` | (used from feed/help flow) | Feed/FarmHelp flows | n/a | `GetAllPostsResponse` | Implemented + used |
| `/posts` | POST multipart | `UserService.createPost` | `PostRepository` | `FarmHelpViewModel` | FarmHelp post submit | multipart `image`,`description` | `CreatePostResponse` | Implemented + used |
| `/messaging` | POST multipart | `UserService.sendMessageWithAttachment` | `MessageRepository` | `MessageViewModel` | Chat send | multipart `text`,`phone`,`attachment?` | `SendMessageResponse` | Implemented + used |
| `/messaging` | GET | `UserService.getThreads` | `MessageRepository` | `MessageViewModel` | Chat thread list | n/a | `ThreadListResponse` | Implemented + used |
| `/messaging/{recipientId}` | GET | `UserService.getMessages` | `MessageRepository` | `MessageViewModel` | Chat conversation | path `recipientId` | `MessagesResponse` | Implemented + used |
| `/data/counties` | GET | `UserService.getCounties` | `CountiesRepository` | (no active screen wiring yet) | Signup future support | query `county?` | `CountiesResponse` | Implemented (repo only) |
| `/media` | GET | `UserService.getMediaFeed` | `MediaRepository` | `MediaViewModel` | Video feed | n/a | `MediaFeedResponse` | Implemented + used |
| `/posts/{id}` | GET | `UserService.getPost` | `PostDetailRepository` | (none yet) | Post detail future | path `id` | `PostDetailResponse` | Implemented (repo only) |
| `/auth/reset-password` | POST | `UserService.resetPassword` | `ResetPasswordRepository` | (none yet) | Future reset UI | `ResetPasswordRequest` | `GenericStatusResponse` | Implemented (repo only) |
| `/posts/specialist` | GET | `UserService.getSpecialistPosts` | `SpecialistPostRepository` | (none yet) | Specialist future | n/a | `GetAllPostsResponse` | Implemented (repo only) |
| `/posts/specialist/{id}` | POST multipart | `UserService.processSpecialistPost` | `SpecialistPostRepository` | (none yet) | Specialist future | multipart `image`,`description` | `GenericStatusResponse` | Implemented (repo only) |

## Missing Integrations (Collection vs Android)

No missing Postman endpoints remain at Retrofit/repository level after this pass.

## Broken Integrations (Found and Validated)

1. `Public endpoint auth gating` (fixed)
   - `ApiClient` previously blocked `/auth/reset-password` and `/data/counties` when unauthenticated.
   - Fixed by extending `publicPaths`.

2. `Architecture violation in Chat` (fixed)
   - `MessageViewModel` previously called `ApiClient.userService.getUserProfile()` directly.
   - Fixed by moving hydration to `MessageRepository.hydrateSessionFromProfileIfNeeded()`.

3. `Error handling inconsistency` (partially fixed)
   - Mixed callback string errors and raw `Response` handling across repositories.
   - Added centralized `NetworkResult`, `ApiException`, `ErrorMapper`, and `safeApiCall`; migrated suspend-based repositories.

4. `Video playback UX gaps` (fixed for core player)
   - Missing buffering/error/retry/fullscreen behavior in player component.
   - Added lifecycle-aware buffering indicator, error overlay + retry, fullscreen dialog mode.

## Endpoint Matrix

| Endpoint | Exists in Collection | Used in Android | Status | Notes |
|---|---|---|---|---|
| `/auth/register` | Yes | Yes | OK | Callback-based repo |
| `/auth/reset-password` | Yes | Not in UI | Implemented (repo only) | Ready for future screen |
| `/auth/me` | Yes | Yes | OK | Used by profile/chat hydration |
| `/auth/login` | Yes | Yes | OK | Token persisted and applied |
| `/posts` (POST) | Yes | Yes | OK | Multipart submit |
| `/posts/specialist/{id}` (POST) | Yes | No UI | Implemented (repo only) | Added Retrofit + repo |
| `/posts/{id}` (GET) | Yes | No UI | Implemented (repo only) | Optional post verification path |
| `/posts/specialist` (GET) | Yes | No UI | Implemented (repo only) | Specialist workload |
| `/posts` (GET) | Yes | Yes | OK | Feed listing |
| `/messaging` (GET) | Yes | Yes | OK | Thread listing in chat |
| `/messaging/{recipientId}` (GET) | Yes | Yes | OK | Conversation fetch |
| `/messaging` (POST) | Yes | Yes | OK | Text + attachment |
| `/data/counties` | Yes | Not in UI | Implemented (repo only) | Public access fix applied |
| `/data/counties?county=...` | Yes | Not in UI | Implemented (repo only) | Uses same Retrofit method |
| `/media` | Yes | Yes | OK | Remote feed + fallback UI |
| `/weather` | No | Yes | Backend Endpoint Missing | Used by Weather feature; not in Postman |

