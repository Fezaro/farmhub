package com.example.app.api

import com.example.app.models.auth.GenericStatusResponse
import com.example.app.models.auth.ResetPasswordRequest
import com.example.app.models.login.LoginRequest
import com.example.app.models.login.LoginResponse
import com.example.app.models.message.SendMessageResponse
import com.example.app.models.media.MediaFeedResponse
import com.example.app.models.posts.CreatePostResponse
import com.example.app.models.posts.GetAllPostsResponse
import com.example.app.models.posts.PostDetailResponse
import com.example.app.models.profile.UserProfileResponse
import com.example.app.models.signup.RegisterRequest
import com.example.app.models.signup.RegisterResponse
import com.example.app.models.geo.CountiesResponse
import com.example.app.models.messaging.MessagesResponse
import com.example.app.models.messaging.ThreadListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface UserService {

    @POST("auth/login")
    fun userLogin(@Body loginRequest: LoginRequest): Call<LoginResponse>

    @POST("auth/register")
    fun registerUser(@Body registerRequest: RegisterRequest): Call<RegisterResponse>

    @GET("auth/me")
    fun getUserProfile(): Call<UserProfileResponse>

    @GET("posts")
    fun getAllPosts(): Call<GetAllPostsResponse>

    @Multipart
    @POST("posts")
    fun createPost(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Call<CreatePostResponse>

    @Multipart
    @POST("messaging")
    fun sendMessageWithAttachment(
        @Part("text") text: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part attachment: MultipartBody.Part?
    ): Call<SendMessageResponse>

    // New endpoints (additive)
    @POST("auth/reset-password")
    fun resetPassword(@Body body: ResetPasswordRequest): Call<GenericStatusResponse>

    @GET("posts/{id}")
    fun getPost(@Path("id") id: String): Call<PostDetailResponse>

    @GET("messaging")
    fun getThreads(): Call<ThreadListResponse>

    @GET("messaging/{recipientId}")
    fun getMessages(@Path("recipientId") id: String): Call<MessagesResponse>

    @GET("data/counties")
    fun getCounties(@Query("county") county: String? = null): Call<CountiesResponse>

    @GET("media")
    fun getMediaFeed(): Call<MediaFeedResponse>

    // Deferred specialist endpoints not yet needed for current UI integration:
    // @GET("posts/specialist")
    // fun getSpecialistPosts(): Call<GetAllPostsResponse>
    // @Multipart
    // @POST("posts/specialist/{id}")
    // fun processPost(...): Call<GenericStatusResponse>
}