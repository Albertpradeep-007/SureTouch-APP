package com.example.suretouchapp.ui.screens.notifications

import android.content.Context
import androidx.work.*
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SureProEdMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { NotificationSyncWorker.triggerImmediateSync(this) }

    override fun onMessageReceived(message: RemoteMessage) {
        val manager = TokenManager(this)
        val session = message.data["account_session"] ?: return
        val id = message.data["notification_id"] ?: return
        if (!manager.isLoggedIn() || !manager.isCurrentSession(session)) return
        val work = OneTimeWorkRequestBuilder<MobilePushWorker>()
            .setInputData(workDataOf("account_session" to session, "notification_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        WorkManager.getInstance(this).enqueueUniqueWork("mobile_push_$id", ExistingWorkPolicy.KEEP, work)
    }
}

object MobilePushRegistration {
    suspend fun register(context: Context, manager: TokenManager, session: String) = withContext(Dispatchers.IO) {
        if (!manager.isLoggedIn() || !manager.isCurrentSession(session)) return@withContext
        val token = Tasks.await(FirebaseMessaging.getInstance().token, 20, TimeUnit.SECONDS)
        manager.requireCurrentSession(session)
        val prefs = context.getSharedPreferences("sure_proed_push_registration", Context.MODE_PRIVATE)
        if (prefs.getString("token", null) == token && prefs.getString("session", null) == session) return@withContext
        val response = ApiClient.getService(manager).registerMobilePush(mapOf("token" to token, "account_session" to session))
        check(response.isSuccessful) { "Push registration unavailable" }
        manager.withCurrentSession(session) { prefs.edit().putString("token", token).putString("session", session).apply() }
    }
}

class MobilePushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = TokenManager(applicationContext)
        val session = inputData.getString("account_session") ?: return Result.success()
        val id = inputData.getString("notification_id") ?: return Result.success()
        if (!manager.isLoggedIn() || !manager.isCurrentSession(session)) return Result.success()
        return try {
            // Only the account's authenticated API can supply the notification text.
            val response = ApiClient.getService(manager).getNotification(id)
            if (response.code() in setOf(401, 403, 404)) return Result.success()
            if (!response.isSuccessful) return Result.retry()
            val notification = response.body() ?: return Result.success()
            manager.withCurrentSession(session) {
                SureProEdNotificationManager.syncUnread(applicationContext, listOf(notification))
            }
            Result.success()
        } catch (_: Exception) {
            if (!manager.isCurrentSession(session) || runAttemptCount >= 4) Result.success() else Result.retry()
        }
    }
}
