package com.app.mtvdownloader.utils

import android.util.Base64

internal object DrmKeySetUtil {
    fun encode(keySetId: ByteArray?): String? {
        if (keySetId == null || keySetId.isEmpty()) return null
        return Base64.encodeToString(keySetId, Base64.NO_WRAP)
    }

    fun decode(keySetIdBase64: String?): ByteArray? {
        val value = keySetIdBase64
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null

        return runCatching {
            Base64.decode(value, Base64.DEFAULT)
        }.getOrNull()
    }
}
