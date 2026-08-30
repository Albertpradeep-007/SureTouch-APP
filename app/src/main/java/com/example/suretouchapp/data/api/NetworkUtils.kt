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
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
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
                message = "The SURE Trust server took too long to respond. Please check your connection speed and retry.",
                actionLabel = "Retry Connection"
            )
            is ConnectException, is UnknownHostException -> NetworkErrorInfo(
                isOffline = false,
                title = "SURE Trust Cloud Unreachable",
                message = "Unable to reach the SURE Trust backend server. The server may be temporarily down or undergoing maintenance.",
                actionLabel = "Retry Server Connection"
            )
            else -> NetworkErrorInfo(
                isOffline = false,
                title = "Backend Connection Error",
                message = throwable?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Unable to establish a secure real-time session with the SURE Trust backend server.",
                actionLabel = "Retry"
            )
        }
    }
}

data class NetworkErrorInfo(
    val isOffline: Boolean,
    val title: String,
    val message: String,
    val actionLabel: String
)
