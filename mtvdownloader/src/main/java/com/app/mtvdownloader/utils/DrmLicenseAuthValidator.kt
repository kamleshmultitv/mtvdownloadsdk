package com.app.mtvdownloader.utils

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

internal object DrmLicenseAuthValidator {

    data class Result(
        val checked: Boolean,
        val expired: Boolean,
        val expiresAtMillis: Long?
    )

    fun inspect(licenseUri: String): Result {
        val authorization = runCatching {
            Uri.parse(licenseUri).getQueryParameter("authorization")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return Result(
            checked = false,
            expired = false,
            expiresAtMillis = null
        )

        val expiresAtMillis = tokenCandidates(authorization)
            .firstNotNullOfOrNull(::readJwtExpiryMillis)

        return Result(
            checked = expiresAtMillis != null,
            expired = expiresAtMillis?.let { it <= System.currentTimeMillis() } == true,
            expiresAtMillis = expiresAtMillis
        )
    }

    private fun tokenCandidates(authorization: String): Sequence<String> = sequence {
        yield(authorization)
        runCatching {
            String(
                Base64.decode(authorization, Base64.DEFAULT),
                StandardCharsets.UTF_8
            )
        }.getOrNull()?.let { yield(it) }
    }

    private fun readJwtExpiryMillis(token: String): Long? {
        val payload = token.split(".").getOrNull(1) ?: return null
        val normalizedPayload = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')

        return runCatching {
            val json = String(
                Base64.decode(normalizedPayload, Base64.URL_SAFE or Base64.NO_WRAP),
                StandardCharsets.UTF_8
            )
            JSONObject(json).optLong("exp").takeIf { it > 0L }?.times(1000L)
        }.getOrNull()
    }
}
