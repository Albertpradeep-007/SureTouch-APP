package com.example.suretouchapp.data.api

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
    private val refreshLock = Any()

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

    fun getService(tokenManager: TokenManager): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: createService(tokenManager).also { apiService = it }
        }
    }

    private fun createService(tokenManager: TokenManager): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                // BODY logging materially slows large list responses and may expose student data.
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                requestBuilder.header("Accept", "application/json")
                chain.proceed(requestBuilder.build())
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
                    val refreshToken = tokenManager.getRefreshToken()?.takeIf(String::isNotBlank)
                        ?: return@authenticator null

                    synchronized(refreshLock) {
                        val requestAccessToken = response.request.header("Authorization")
                            ?.removePrefix("Bearer ")
                        val latestAccessToken = tokenManager.getAccessToken()
                        if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestAccessToken) {
                            return@synchronized response.request.newBuilder()
                                .header("Authorization", "Bearer $latestAccessToken")
                                .build()
                        }

                        val refreshRequest = Request.Builder()
                            .url("${BASE_URL}auth/token/refresh/")
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
                                tokenManager.logout()
                                return@synchronized null
                            }
                            val refreshed = runCatching {
                                Gson().fromJson(tokenResponse.body?.string(), TokenResponse::class.java)
                            }.getOrNull()
                            if (refreshed == null) {
                                tokenManager.logout()
                                return@synchronized null
                            }
                            tokenManager.saveToken(refreshed.access, refreshed.refresh ?: refreshToken)
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
                .baseUrl(BASE_URL)
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
