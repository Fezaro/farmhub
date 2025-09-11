package com.example.app.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // Updated base URL (must end with a slash for Retrofit when using relative endpoints)
    private const val BASE_URL = "https://575acf203d52.ngrok-free.app/"
    @Volatile private var bearerToken: String? = null

    fun setBearerToken(token: String?) { bearerToken = token?.trim().takeUnless { it.isNullOrBlank() } }
    fun clearBearerToken() { bearerToken = null }
    fun currentToken(): String? = bearerToken

    private val authInterceptor = Interceptor { chain ->
        val original: Request = chain.request()
        val builder = original.newBuilder()
        bearerToken?.let { builder.header("Authorization", "Bearer $it") }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val userService: UserService by lazy { retrofit.create(UserService::class.java) }
}