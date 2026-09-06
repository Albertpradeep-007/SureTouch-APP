package com.example.suretouchapp

import com.example.suretouchapp.data.ota.canDismissUpdate
import com.example.suretouchapp.data.ota.downloadedPackageError
import com.example.suretouchapp.data.ota.downloadedSizeError
import org.junit.Assert.*
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun mandatoryUpdatesCannotBeDismissedBeforeOrAfterDownload() {
        assertFalse(canDismissUpdate(isMandatory = true, isDownloading = false))
        assertFalse(canDismissUpdate(isMandatory = true, isDownloading = true))
        assertFalse(canDismissUpdate(isMandatory = false, isDownloading = true))
        assertTrue(canDismissUpdate(isMandatory = false, isDownloading = false))
    }

    @Test fun completeDownloadsMatchBothPublishedAndResponseSizes() {
        assertNull(downloadedSizeError(expectedBytes = 100, actualBytes = 100, responseBytes = 100))
        assertNull(downloadedSizeError(expectedBytes = 100, actualBytes = 100))
        // Legacy releases can omit size metadata; package validation still applies.
        assertNull(downloadedSizeError(expectedBytes = 0, actualBytes = 100, responseBytes = 100))
    }

    @Test fun emptyTruncatedAndOversizedDownloadsAreRejected() {
        assertNotNull(downloadedSizeError(expectedBytes = 100, actualBytes = 0))
        assertNotNull(downloadedSizeError(expectedBytes = 100, actualBytes = 99))
        assertNotNull(downloadedSizeError(expectedBytes = 100, actualBytes = 101))
        assertNotNull(downloadedSizeError(expectedBytes = 0, actualBytes = 99, responseBytes = 100))
    }

    @Test fun onlyTheAdvertisedNewVersionOfThisAppCanBeInstalled() {
        assertNull(downloadedPackageError("app.id", "app.id", 14, 15, 15))
        assertNotNull(downloadedPackageError("app.id", "other.app", 14, 15, 15))
        assertNotNull(downloadedPackageError("app.id", null, 14, 15, null))
        assertNotNull(downloadedPackageError("app.id", "app.id", 14, 15, 16))
        assertNotNull(downloadedPackageError("app.id", "app.id", 14, 14, 14))
        assertNotNull(downloadedPackageError("app.id", "app.id", 14, 13, 13))
    }
}
