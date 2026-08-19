package com.app.mtvdownloader.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.datasource.HttpDataSource
import java.security.MessageDigest

internal object DrmDebugLogger {
    private const val TAG = "MtvDrmOffline"

    fun stage(
        stage: String,
        contentId: String?,
        message: String
    ) {
        Log.i(TAG, "$stage contentId=${contentId.orEmpty()} $message")
    }

    fun failure(
        stage: String,
        contentId: String?,
        throwable: Throwable
    ) {
        val httpCode = throwable.findHttpResponseCode()
        val details = buildString {
            append("exception=${throwable::class.java.simpleName}")
            throwable.cause?.let { append(" cause=${it::class.java.simpleName}") }
            if (httpCode != null) append(" httpCode=$httpCode")
            throwable.message
                ?.take(180)
                ?.let { append(" message=$it") }
        }
        Log.e(TAG, "$stage contentId=${contentId.orEmpty()} $details", throwable)
    }

    fun licenseSummary(licenseUri: String): String {
        val uri = runCatching { Uri.parse(licenseUri) }.getOrNull()
            ?: return "licenseUri=invalid"

        val queryKeys = uri.queryParameterNames
            .sorted()
            .joinToString(",")

        return "licenseUri=${uri.scheme}://${uri.host}${uri.path}?queryKeys=$queryKeys"
    }

    fun logPackageSigning(
        context: Context,
        contentId: String?,
        licenseUri: String
    ) {
        stage(
            stage = "DRM_PACKAGE_SIGNING_CHECK",
            contentId = contentId,
            message = "package=${context.packageName} " +
                "certSha256=${context.signingSha256().joinToString(",").ifBlank { "unknown" }} " +
                licenseSummary(licenseUri)
        )
    }

    fun Throwable.findHttpResponseCode(): Int? {
        var current: Throwable? = this
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
        }
        return null
    }

    private fun Context.signingSha256(): List<String> {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull() ?: return emptyList()

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        return signatures
            ?.mapNotNull { signature ->
                runCatching {
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .joinToString(":") { "%02X".format(it) }
                }.getOrNull()
            }
            .orEmpty()
    }
}
