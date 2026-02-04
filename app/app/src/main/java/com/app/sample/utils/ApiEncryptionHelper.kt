package com.app.sample.utils

import android.util.Base64
import java.nio.charset.StandardCharsets

class ApiEncryptionHelper {
    companion object {
        fun convertStringToBase64(text: String): String {
            val data = text.toByteArray(StandardCharsets.UTF_8)
            return Base64.encodeToString(data, Base64.DEFAULT)
        }
    }
}