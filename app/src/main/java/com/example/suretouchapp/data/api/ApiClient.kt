package com.example.suretouchapp.data.api

import com.example.suretouchapp.BuildConfig
import com.example.suretouchapp.data.model.RefreshTokenRequest
import com.example.suretouchapp.data.model.TokenResponse
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit

object ApiClient {
    // Production SURE ProEd backend domain
    private const val BASE_URL = "https://sureproed.com/api/"

    @Volatile private var apiService: ApiService? = null
    private var apiSessionId: String? = null
    private val refreshLock = Any()
    internal var testServiceFactory: ((TokenManager, String) -> ApiService)? = null

    fun resolveServerUrl(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.contains("106.51.129.34:8000") -> trimmed.replace("http://106.51.129.34:8000", "https://sureproed.com")
            trimmed.contains("10.0.2.2:8000") -> trimmed.replace("http://10.0.2.2:8000", "https://sureproed.com")
            trimmed.contains("127.0.0.1:8000") -> trimmed.replace("http://127.0.0.1:8000", "https://sureproed.com")
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "https://sureproed.com$trimmed"
            trimmed.startsWith("media/") -> "https://sureproed.com/$trimmed"
            else -> URI(BASE_URL).resolve(trimmed).toString()
        }
    }

    fun getService(tokenManager: TokenManager): ApiService = synchronized(this) {
        val sessionId = tokenManager.getSessionId()
        if (BuildConfig.DEBUG) testServiceFactory?.let { return@synchronized it(tokenManager, sessionId) }
        apiService?.takeIf { apiSessionId == sessionId }
            ?: createService(tokenManager, sessionId).also {
                apiService = it
                apiSessionId = sessionId
            }
    }

    internal fun createService(tokenManager: TokenManager, sessionId: String, baseUrl: String = BASE_URL): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                // BODY logging materially slows large list responses and may expose student data.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = Interceptor { chain ->
                val request = tokenManager.withCurrentSession(sessionId) {
                    chain.request().newBuilder().apply {
                        tokenManager.getAccessToken()?.takeIf { it.isNotBlank() && !chain.request().url.encodedPath.contains("/auth/token/") }?.let {
                            header("Authorization", "Bearer $it")
                        }
                        header("Accept", "application/json")
                    }.build()
                }
                val response = chain.proceed(request)
                if (!tokenManager.isCurrentSession(sessionId)) {
                    response.close()
                    throw java.io.IOException("Account session changed. Please reload.")
                }
                response
            }

            val dispatcher = Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 8
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .authenticator { _, response ->
                    if (response.retryCount() >= 2 || response.request.url.encodedPath.contains("/auth/token/")) {
                        return@authenticator null
                    }
                    synchronized(refreshLock) {
                        val (storedRefreshToken, latestAccessToken) = tokenManager.withCurrentSession(sessionId) {
                            tokenManager.getRefreshToken() to tokenManager.getAccessToken()
                        }
                        val refreshToken = storedRefreshToken?.takeIf(String::isNotBlank)
                            ?: return@synchronized null
                        val requestAccessToken = response.request.header("Authorization")
                            ?.removePrefix("Bearer ")
                        if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestAccessToken) {
                            return@synchronized response.request.newBuilder()
                                .header("Authorization", "Bearer $latestAccessToken")
                                .build()
                        }

                        val refreshRequest = Request.Builder()
                            .url("${baseUrl}auth/token/refresh/")
                            .post(
                                Gson().toJson(RefreshTokenRequest(refreshToken))
                                    .toRequestBody("application/json".toMediaType())
                            )
                            .header("Accept", "application/json")
                            .build()
                        val refreshResponse = OkHttpClient.Builder()
                            .connectTimeout(6, TimeUnit.SECONDS)
                            .readTimeout(12, TimeUnit.SECONDS)
                            .build()
                            .newCall(refreshRequest)
                            .execute()
                        refreshResponse.use { tokenResponse ->
                            if (!tokenResponse.isSuccessful) {
                                if (tokenResponse.code == 400 || tokenResponse.code == 401) {
                                    tokenManager.logoutIfCurrentSession(sessionId)
                                    return@synchronized null
                                }
                                throw java.io.IOException("Account refresh temporarily unavailable. Please retry.")
                            }
                            val refreshed = runCatching {
                                Gson().fromJson(tokenResponse.body?.string(), TokenResponse::class.java)
                            }.getOrNull()
                            if (refreshed == null) {
                                throw java.io.IOException("Invalid refresh response. Please retry.")
                            }
                            if (refreshed.access.isNullOrBlank()) throw java.io.IOException("Incomplete refresh response. Please retry.")
                            if (!tokenManager.saveRefreshedToken(
                                    sessionId, refreshed.access, refreshed.refresh ?: refreshToken
                                )) return@synchronized null
                            response.request.newBuilder()
                                .header("Authorization", "Bearer ${refreshed.access}")
                                .build()
                        }
                    }
                }
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(ApiService::class.java)
    }

    private fun okhttp3.Response.retryCount(): Int {
        var count = 1
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
