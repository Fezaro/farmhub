package com.farm_tech.farmhub.api

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import android.content.pm.ApplicationInfo
import com.farm_tech.farmhub.BuildConfig
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
    private val BASE_URL = BuildConfig.API_BASE_URL
    
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
        // Initialize secure token storage and hydrate bearer token if present
        try {
            com.farm_tech.farmhub.auth.SecureTokenManager.initialize(context.applicationContext)
            val saved = com.farm_tech.farmhub.auth.SecureTokenManager.getToken()
            saved?.let { bearerToken = it }
            Log.d(TAG, "ApiClient initialized; token loaded=${!saved.isNullOrBlank()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize SecureTokenManager: ${e.message}")
        }
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

    fun baseUrl(): String = BASE_URL

    private val publicPaths = setOf(
        "auth/login",
        "auth/register",
        "auth/reset-password",
        "data/counties"
    )

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
                    com.farm_tech.farmhub.auth.AuthManager.handleUnauthorized(it)
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

    // Configure logging level based on whether the app is debuggable (release builds will not log body)
    private val loggingInterceptor: HttpLoggingInterceptor
        get() {
            val interceptor = HttpLoggingInterceptor()
            val isDebuggable = try {
                applicationContext?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0
            } catch (e: Exception) {
                false
            }
            interceptor.level = if (isDebuggable) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            return interceptor
        }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(httpResponseInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

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
