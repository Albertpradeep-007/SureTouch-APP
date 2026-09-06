package com.example.suretouchapp.data.ota

internal fun canDismissUpdate(isMandatory: Boolean, isDownloading: Boolean): Boolean =
    !isMandatory && !isDownloading

internal fun downloadedSizeError(
    expectedBytes: Long,
    actualBytes: Long,
    responseBytes: Long = -1L
): String? = when {
    actualBytes <= 0L -> "The update download is empty. Please retry."
    expectedBytes > 0L && actualBytes != expectedBytes ->
        "The update download does not match its published size. Please retry."
    responseBytes > 0L && actualBytes != responseBytes ->
        "The update download is incomplete. Please retry."
    else -> null
}

internal fun downloadedPackageError(
    expectedPackage: String,
    actualPackage: String?,
    installedVersion: Long,
    expectedVersion: Long,
    actualVersion: Long?
): String? = when {
    actualPackage == null || actualVersion == null ->
        "The downloaded file is not a valid Android update. Please retry."
    actualPackage != expectedPackage -> "The downloaded update is for a different app."
    actualVersion != expectedVersion -> "The downloaded update has an unexpected version."
    actualVersion <= installedVersion -> "This update is not newer than the installed app."
    else -> null
}
