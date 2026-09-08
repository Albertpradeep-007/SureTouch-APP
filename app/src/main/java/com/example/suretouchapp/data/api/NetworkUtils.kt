package com.example.suretouchapp.data.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkUtils {
    /**
     * Checks if the device has an active internet connection (Wi-Fi, Cellular, Ethernet).
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true // Optimistic default
            val activeNetwork = connectivityManager.activeNetwork ?: return true
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return true
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    /**
     * Categorizes a failure into a user-friendly message and error classification.
     */
    fun getNetworkErrorInfo(context: Context, throwable: Throwable?): NetworkErrorInfo {
        val isOnline = isNetworkAvailable(context)
        if (!isOnline) {
            return NetworkErrorInfo(
                isOffline = true,
                title = "No Internet Connection",
                message = "Your device appears to be offline. Please verify that your Wi-Fi or mobile data is turned on and try again.",
                actionLabel = "Check Connection & Retry"
            )
        }

        return when (throwable) {
            is SocketTimeoutException -> NetworkErrorInfo(
                isOffline = false,
                title = "Connection Timed Out",
                message = "The server took too long to respond. Tap retry to reconnect.",
                actionLabel = "Retry Connection"
            )
            is ConnectException, is UnknownHostException -> NetworkErrorInfo(
                isOffline = false,
                title = "Server Connecting...",
                message = "Connecting to SURE Trust server. Tap retry to reconnect.",
                actionLabel = "Retry Server Connection"
            )
            else -> NetworkErrorInfo(
                isOffline = false,
                title = "Sync Incomplete",
                message = throwable?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Unable to sync latest data with server. Tap retry to refresh.",
                actionLabel = "Retry"
            )
        }
    }

    /**
     * Extracts a readable error description from Django REST Framework or API JSON responses.
     */
    fun parseBackendErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return runCatching {
            val trimmed = errorBody.trim()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return trimmed.take(200)
            }
            if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                if (array.length() > 0) return array.optString(0) else return null
            }
            val json = org.json.JSONObject(trimmed)
            when {
                json.has("detail") -> json.optString("detail")
                json.has("error") -> json.optString("error")
                json.has("message") -> json.optString("message")
                json.has("non_field_errors") -> {
                    val arr = json.optJSONArray("non_field_errors")
                    if (arr != null && arr.length() > 0) arr.optString(0) else json.optString("non_field_errors")
                }
                json.has("submission_url") -> {
                    val arr = json.optJSONArray("submission_url")
                    val msg = if (arr != null && arr.length() > 0) arr.optString(0) else json.optString("submission_url")
                    "Submission URL: $msg"
                }
                json.has("assignment") -> {
                    val arr = json.optJSONArray("assignment")
                    val msg = if (arr != null && arr.length() > 0) arr.optString(0) else json.optString("assignment")
                    "Assignment: $msg"
                }
                else -> {
                    val keys = json.keys()
                    if (keys.hasNext()) {
                        val firstKey = keys.next()
                        val value = json.opt(firstKey)
                        val msg = if (value is org.json.JSONArray && value.length() > 0) value.optString(0) else value?.toString().orEmpty()
                        val cleanKey = firstKey.replace("_", " ").replaceFirstChar { it.uppercase() }
                        "$cleanKey: $msg"
                    } else null
                }
            }
        }.getOrNull()
    }
}

data class NetworkErrorInfo(
    val isOffline: Boolean,
    val title: String,
    val message: String,
    val actionLabel: String
)
