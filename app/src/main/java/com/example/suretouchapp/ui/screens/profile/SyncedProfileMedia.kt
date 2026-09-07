package com.example.suretouchapp.ui.screens.profile

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.suretouchapp.data.api.ApiClient
import com.example.suretouchapp.data.api.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class SyncedProfileMedia {
    var url by mutableStateOf<String?>(null)
    var uploading by mutableStateOf(false)
    lateinit var update: (Uri?) -> Unit
}

/** The server is authoritative: failed uploads never replace a saved image locally. */
@Composable
fun rememberSyncedProfileMedia(tokenManager: TokenManager, field: String = "banner_image"): SyncedProfileMedia {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = tokenManager.getSessionId()
    val state = remember(session, field) { SyncedProfileMedia() }
    val api = remember(tokenManager) { ApiClient.getService(tokenManager) }
    fun accept(user: com.example.suretouchapp.data.model.UserResponse) {
        tokenManager.withCurrentSession(session) {
            state.url = if (field == "banner_image") user.bannerImage else user.profilePhoto
            if (field == "banner_image") tokenManager.saveCoverPhotoUrl(state.url)
            else tokenManager.saveProfilePhotoUrl(state.url.orEmpty())
        }
    }
    LaunchedEffect(session, field) {
        runCatching { api.getCurrentUser() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
            if (tokenManager.isCurrentSession(session)) accept(it)
        }
    }
    state.update = { uri ->
        if (!state.uploading) scope.launch {
            state.uploading = true
            try {
                tokenManager.requireCurrentSession(session)
                val response = if (uri == null) {
                    api.patchCurrentUser(mapOf(field to null))
                } else {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPG, PNG, or WEBP image." }
                    val limit = (if (field == "banner_image") 10 else 5) * 1024 * 1024
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= limit) { "Image exceeds the ${limit / 1024 / 1024} MB limit." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("Unable to read selected image.")
                    }
                    tokenManager.requireCurrentSession(session)
                    api.uploadCurrentUserMedia(MultipartBody.Part.createFormData(field,
                        "image.${mime.substringAfter('/')}", bytes.toRequestBody(mime.toMediaTypeOrNull())))
                }
                check(response.isSuccessful) { "Image could not be saved. Check the image and try again." }
                accept(response.body() ?: error("No profile returned."))
                Toast.makeText(context, if (uri == null) "Image removed" else "Image saved", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                if (tokenManager.isCurrentSession(session)) Toast.makeText(context, error.message ?: "Unable to update image", Toast.LENGTH_LONG).show()
            } finally { state.uploading = false }
        }
    }
    return state
}
