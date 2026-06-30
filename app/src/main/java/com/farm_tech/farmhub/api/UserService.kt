package com.farm_tech.farmhub.api

import com.farm_tech.farmhub.models.auth.GenericStatusResponse
import com.farm_tech.farmhub.models.auth.ResetPasswordRequest
import com.farm_tech.farmhub.models.login.LoginRequest
import com.farm_tech.farmhub.models.login.LoginResponse
import com.farm_tech.farmhub.models.message.SendMessageResponse
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.models.posts.CreatePostResponse
import com.farm_tech.farmhub.models.posts.GetAllPostsResponse
import com.farm_tech.farmhub.models.posts.PostDetailResponse
import com.farm_tech.farmhub.models.profile.UserProfileResponse
import com.farm_tech.farmhub.models.signup.RegisterRequest
import com.farm_tech.farmhub.models.signup.RegisterResponse
import com.farm_tech.farmhub.models.geo.CountiesResponse
import com.farm_tech.farmhub.models.messaging.MessagesResponse
import com.farm_tech.farmhub.models.messaging.ThreadListResponse
import com.farm_tech.farmhub.models.weather.WeatherForecastResponse
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
    fun getMediaFeed(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): Call<MediaFeedResponse>

    @GET("weather")
    fun getWeatherForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Call<WeatherForecastResponse>

    @GET("posts/specialist")
    fun getSpecialistPosts(): Call<GetAllPostsResponse>

    @Multipart
    @POST("posts/specialist/{id}")
    fun processSpecialistPost(
        @Path("id") id: String,
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Call<GenericStatusResponse>

    // Deferred specialist endpoints not yet needed for current UI integration:
    // @GET("posts/specialist")
    // fun getSpecialistPosts(): Call<GetAllPostsResponse>
    // @Multipart
    // @POST("posts/specialist/{id}")
    // fun processPost(...): Call<GenericStatusResponse>
}
