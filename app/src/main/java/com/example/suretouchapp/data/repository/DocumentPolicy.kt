package com.example.suretouchapp.data.repository

import java.io.InputStream
import java.io.IOException
import java.net.URI

object DocumentPolicy {
    const val MAX_RESUME_BYTES = 5 * 1024 * 1024
    fun trustedUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host.equals("sureproed.com", true) &&
            (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null &&
            (uri.path.startsWith("/api/") || uri.path.startsWith("/media/"))
    }.getOrDefault(false)

    fun readBounded(input: InputStream, limit: Int = MAX_RESUME_BYTES): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) throw IOException("Resume must be 5 MB or smaller.")
            output.write(buffer, 0, read)
        }
        if (output.size() == 0) throw IOException("The selected document is empty.")
        return output.toByteArray()
    }
}
