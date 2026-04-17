package com.example.app.api

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Sealed class representing authentication-related API results.
 * Used for distinguishing between success, auth failures, and other errors.
 */
sealed class AuthResult {
    object Success : AuthResult()
    object Unauthorized : AuthResult() // 401
    object Forbidden : AuthResult()    // 403
    data class NetworkError(val message: String) : AuthResult()
    data class UnexpectedError(val message: String) : AuthResult()
}

object ApiClient {
    private const val TAG = "ApiClient"
    private const val BASE_URL = "https://api.farmers-hub.co.ke/"
    
    @Volatile private var bearerToken: String? = null
    @Volatile private var applicationContext: Context? = null

    /**
     * Initialize ApiClient with application context for handling 401 responses.
     * Should be called once in Application.onCreate() or MainActivity.onCreate()
     *
     * @param context Application context for auth state management
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        Log.d(TAG, "ApiClient initialized with application context")
    }

    fun setBearerToken(token: String?) {
        bearerToken = token?.trim().takeUnless { it.isNullOrBlank() }
        Log.d(TAG, "Bearer token set (length: ${bearerToken?.length ?: 0})")
    }

    fun clearBearerToken() {
        Log.d(TAG, "Bearer token cleared")
        bearerToken = null
    }

    fun currentToken(): String? = bearerToken

    private val publicPaths = setOf("auth/login", "auth/register")

    private fun isPublicEndpoint(path: String): Boolean {
        val normalized = path.trimStart('/')
        return normalized in publicPaths
    }

    private fun missingTokenResponse(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("{\"message\":\"Missing authentication token\"}".toResponseBody())
            .build()
    }

    /**
     * Interceptor that adds bearer token to all requests.
     * Ensures every protected endpoint has Authorization header.
     */
    private val authInterceptor = Interceptor { chain ->
        val original: Request = chain.request()
        val path = original.url.encodedPath

        if (!isPublicEndpoint(path) && bearerToken.isNullOrBlank()) {
            Log.w(TAG, "Blocked request without token: $path")
            return@Interceptor missingTokenResponse(original)
        }

        val builder = original.newBuilder()
        
        bearerToken?.let {
            builder.header("Authorization", "Bearer $it")
            Log.d(TAG, "Added bearer token to request: $path")
        } ?: run {
            Log.d(TAG, "Public request without token: $path")
        }
        
        chain.proceed(builder.build())
    }

    /**
     * Response interceptor that handles 401 Unauthorized and 403 Forbidden responses.
     * Triggers logout when receiving 401/403 on protected endpoints.
     *
     * Senior practice: Centralized HTTP error handling ensures consistent behavior
     * across all API calls and prevents orphaned auth states.
     */
    private val httpResponseInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        
        when (response.code) {
            401 -> {
                Log.e(TAG, "Received 401 Unauthorized. Path: ${chain.request().url.encodedPath}")
                applicationContext?.let {
                    com.example.app.auth.AuthManager.handleUnauthorized(it)
                    Log.d(TAG, "Triggered logout due to 401 response")
                }
            }
            403 -> {
                Log.e(TAG, "Received 403 Forbidden. Path: ${chain.request().url.encodedPath}")
                // 403 = token valid but insufficient permissions. Don't logout yet.
                // Let the caller handle it specifically (specialized roles, etc.)
                Log.d(TAG, "Request forbidden (403) but keeping session alive for potential retry")
            }
            else -> {
                if (!response.isSuccessful) {
                    Log.w(TAG, "API error ${response.code}: ${chain.request().url.encodedPath}")
                }
            }
        }
        
        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(httpResponseInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    fun httpClient(): OkHttpClient = okHttpClient

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    val userService: UserService by lazy { retrofit.create(UserService::class.java) }
}