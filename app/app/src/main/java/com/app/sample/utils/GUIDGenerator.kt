package com.app.sample.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

object GUIDGenerator {

    // Generates a GUID and converts it to Base64 string representation
    fun generateGUID(context: Context?): String {
        // Generate GUID using Android ID and Serial
        val guid = createGUIDFromAndroidIDAndSerial(context)

        // Convert GUID to Base64 string and return
        return convertGUIDToBase64(guid)
    }

    // Converts GUID (UUID) to Base64 string
    fun convertGUIDToBase64(guid: UUID): String {
        // Convert UUID to Base64 string using an external helper
        return ApiEncryptionHelper.convertStringToBase64(guid.toString())
    }

    // Creates GUID from Android ID and Serial ID
    fun createGUIDFromAndroidIDAndSerial(context: Context?): UUID {
        // Fetch Android ID and Serial from the device settings
        val androidId = Settings.Secure.getString(context?.contentResolver, Settings.Secure.ANDROID_ID)
        val serialId = Build.SERIAL

        // Concatenate the Android ID and Serial to generate a unique GUID
        val guidString = "$androidId$serialId"

        // Convert the concatenated string to UUID
        return UUID.nameUUIDFromBytes(guidString.toByteArray())
    }
}
