package com.app.mtvdownloader.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val PREF_NAME = "app_prefs"

/**
 * DataStore delegate with automatic migration from SharedPreferences named PREF_NAME.
 * Keep this at top level (recommended pattern).
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREF_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREF_NAME))
    }
)

object PrefsManager {

    // --- Helpers to create keys ---
    private fun stringKey(key: String) = stringPreferencesKey(key)
    private fun intKey(key: String) = intPreferencesKey(key)
    private fun boolKey(key: String) = booleanPreferencesKey(key)
    private fun doubleKey(key: String) = doublePreferencesKey(key)

    // ------------------------
    // Suspend APIs (recommended)
    // ------------------------

    /** Save a nullable string. Passing null removes the key. */
    suspend fun setStringSuspend(context: Context, key: String, value: String?) {
        val prefsKey = stringKey(key)
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(prefsKey)
            else prefs[prefsKey] = value
        }
    }

    /** Read a string (or [default]). */
    suspend fun getStringSuspend(context: Context, key: String, default: String? = null): String? {
        val prefsKey = stringKey(key)
        val prefs = context.dataStore.data.first()
        return prefs[prefsKey] ?: default
    }

    suspend fun setIntSuspend(context: Context, key: String, value: Int) {
        val prefsKey = intKey(key)
        context.dataStore.edit { it[prefsKey] = value }
    }

    suspend fun getIntSuspend(context: Context, key: String, default: Int = 0): Int {
        val prefsKey = intKey(key)
        val prefs = context.dataStore.data.first()
        return prefs[prefsKey] ?: default
    }

    suspend fun setBooleanSuspend(context: Context, key: String, value: Boolean) {
        val prefsKey = boolKey(key)
        context.dataStore.edit { it[prefsKey] = value }
    }

    suspend fun getBooleanSuspend(context: Context, key: String, default: Boolean = false): Boolean {
        val prefsKey = boolKey(key)
        val prefs = context.dataStore.data.first()
        return prefs[prefsKey] ?: default
    }

    suspend fun setDoubleSuspend(context: Context, key: String, value: Double) {
        val prefsKey = doubleKey(key)
        context.dataStore.edit { prefs ->
            prefs[prefsKey] = value
        }
    }

    suspend fun getDoubleSuspend(context: Context, key: String, default: Double = 0.0): Double {
        val prefsKey = doubleKey(key)
        val prefs = context.dataStore.data.first()
        return prefs[prefsKey] ?: default
    }

    suspend fun removeSuspend(context: Context, key: String) {
        val prefsKey = stringKey(key) // safe: remove by string key works for other types too
        context.dataStore.edit { it.remove(prefsKey) }
    }

    suspend fun clearAllSuspend(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    // ----------------------------------------------------
    // Blocking compatibility shims (use only temporarily)
    // ----------------------------------------------------
    // These keep existing synchronous calls working while migrating.
    // WARNING: blocking calls on the main thread can cause ANR — prefer suspend or Flow APIs.

    fun setString(context: Context, key: String, value: String?) {
        runBlocking(Dispatchers.IO) { setStringSuspend(context, key, value) }
    }

    fun getString(context: Context, key: String, default: String? = null): String? {
        return runBlocking(Dispatchers.IO) { getStringSuspend(context, key, default) }
    }


    suspend fun setStringSuspendAsync(context: Context, key: String, value: String?) {
        withContext(Dispatchers.IO) {
            Log.d("MainData", "setStringSuspendAsync $key")
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
                apply()
            }

        }
    }

    suspend fun getStringSuspendAsync(context: Context, key: String, default: String? = null): String? {
        return withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.getString(key, default)
        }
    }

    // For backward compatibility (still blocking, but safer)
    fun <T> setObject(context: Context, key: String, value: T?) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("MainData", "setObjectCalled $key")

        }
    }

    fun setStringAsync(context: Context, key: String, value: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            setStringSuspendAsync(context, key, value)
        }
    }

    fun getStringAsync(context: Context, key: String, default: String? = null): String? {
        return runBlocking { getStringSuspendAsync(context, key, default) }
    }

    fun setInt(context: Context, key: String, value: Int) {
        runBlocking(Dispatchers.IO) { setIntSuspend(context, key, value) }
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int {
        return runBlocking(Dispatchers.IO) { getIntSuspend(context, key, default) }
    }

    fun setBoolean(context: Context, key: String, value: Boolean) {
        runBlocking(Dispatchers.IO) { setBooleanSuspend(context, key, value) }
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return runBlocking(Dispatchers.IO) { getBooleanSuspend(context, key, default) }
    }

    fun setDouble(context: Context, key: String, value: Double) {
        runBlocking { setDoubleSuspend(context, key, value) }
    }

    fun getDouble(context: Context, key: String, default: Double = 0.0): Double {
        return runBlocking { getDoubleSuspend(context, key, default) }
    }

    fun remove(context: Context, key: String) {
        runBlocking(Dispatchers.IO) { removeSuspend(context, key) }
    }

}
