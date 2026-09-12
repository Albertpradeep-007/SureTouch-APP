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
import java.time.Instant
import java.util.concurrent.TimeUnit

class SureProEdMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { NotificationSyncWorker.triggerImmediateSync(this) }

    override fun onMessageReceived(message: RemoteMessage) {
        // Capture the device arrival time before authentication, parsing or I/O.
        val receivedAt = Instant.now()
        val manager = TokenManager(this)
        val session = message.data["account_session"] ?: return
        val id = message.data["notification_id"] ?: return
        if (!manager.isLoggedIn() || !manager.isCurrentSession(session)) return
        val fcmSentAt = message.sentTime.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        val liveClassPush = LiveClassPushPayload.parse(message.data, fcmSentAt)
        if (liveClassPush != null && liveClassPush.accountSession == session && liveClassPush.notificationId == id) {
            val display = SureProEdNotificationManager.showLiveClassPush(this, liveClassPush, receivedAt)
            if (!display.duplicate) {
                MobilePushDeliveryWorker.enqueue(
                    context = this,
                    payload = liveClassPush,
                    presentation = display.presentation,
                    receivedAt = receivedAt,
                    displayedAt = display.displayedAt
                )
            }
        }
        // The authoritative fetch still reconciles read/deleted state. Complete
        // class payloads are already visible before this network work begins.
        val work = OneTimeWorkRequestBuilder<MobilePushWorker>()
            .setInputData(workDataOf("account_session" to session, "notification_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        WorkManager.getInstance(this).enqueueUniqueWork("mobile_push_${session}_$id", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
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
            if (response.code() in setOf(401, 403, 404)) {
                manager.withCurrentSession(session) { SureProEdNotificationManager.dismissNotification(applicationContext, id) }
                return Result.success()
            }
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

class MobilePushDeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = TokenManager(applicationContext)
        val session = inputData.getString(KEY_SESSION) ?: return Result.success()
        if (!manager.isLoggedIn() || !manager.isCurrentSession(session)) return Result.success()
        val body = mutableMapOf<String, Any?>(
            "notification_id" to (inputData.getString(KEY_NOTIFICATION_ID) ?: return Result.success()),
            "account_session" to session,
            "event_type" to (inputData.getString(KEY_EVENT_TYPE) ?: return Result.success()),
            "class_id" to (inputData.getString(KEY_CLASS_ID) ?: return Result.success()),
            "scheduled_at" to (inputData.getString(KEY_SCHEDULED_AT) ?: return Result.success()),
            "sent_at" to (inputData.getString(KEY_SENT_AT) ?: return Result.success()),
            "device_received_at" to (inputData.getString(KEY_RECEIVED_AT) ?: return Result.success()),
            "transport_latency_ms" to inputData.getLong(KEY_TRANSPORT_LATENCY, 0L),
            "schedule_lateness_ms" to inputData.getLong(KEY_SCHEDULE_LATENESS, 0L)
        )
        inputData.getString(KEY_DISPLAYED_AT)?.let { body["notification_displayed_at"] = it }
        return try {
            manager.requireCurrentSession(session)
            val response = ApiClient.getService(manager).recordMobilePushDelivery(body)
            when {
                response.isSuccessful -> Result.success()
                response.code() in setOf(400, 401, 403, 404) -> Result.success()
                runAttemptCount >= 4 -> Result.success()
                else -> Result.retry()
            }
        } catch (_: Exception) {
            if (!manager.isCurrentSession(session) || runAttemptCount >= 4) Result.success() else Result.retry()
        }
    }

    companion object {
        private const val KEY_SESSION = "account_session"
        private const val KEY_NOTIFICATION_ID = "notification_id"
        private const val KEY_EVENT_TYPE = "event_type"
        private const val KEY_CLASS_ID = "class_id"
        private const val KEY_SCHEDULED_AT = "scheduled_at"
        private const val KEY_SENT_AT = "sent_at"
        private const val KEY_RECEIVED_AT = "device_received_at"
        private const val KEY_DISPLAYED_AT = "notification_displayed_at"
        private const val KEY_TRANSPORT_LATENCY = "transport_latency_ms"
        private const val KEY_SCHEDULE_LATENESS = "schedule_lateness_ms"

        fun enqueue(
            context: Context,
            payload: LiveClassPushPayload,
            presentation: LiveClassPushPresentation,
            receivedAt: Instant,
            displayedAt: Instant?
        ) {
            val input = Data.Builder()
                .putString(KEY_SESSION, payload.accountSession)
                .putString(KEY_NOTIFICATION_ID, payload.notificationId)
                .putString(KEY_EVENT_TYPE, payload.type.name)
                .putString(KEY_CLASS_ID, payload.classId)
                .putString(KEY_SCHEDULED_AT, payload.scheduledAt.toString())
                .putString(KEY_SENT_AT, payload.sentAt.toString())
                .putString(KEY_RECEIVED_AT, receivedAt.toString())
                .putLong(KEY_TRANSPORT_LATENCY, presentation.transportLatency.toMillis())
                .putLong(KEY_SCHEDULE_LATENESS, presentation.scheduleLateness.toMillis())
                .apply { displayedAt?.let { putString(KEY_DISPLAYED_AT, it.toString()) } }
                .build()
            val work = OneTimeWorkRequestBuilder<MobilePushDeliveryWorker>()
                .setInputData(input)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "push_delivery_${payload.accountSession}_${payload.notificationId}_${payload.type.name}",
                ExistingWorkPolicy.KEEP,
                work
            )
        }
    }
}
