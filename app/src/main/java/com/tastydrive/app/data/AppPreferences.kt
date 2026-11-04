package com.tastydrive.app.data

import android.content.Context
import android.content.SharedPreferences

enum class NetworkPreference {
    BOTH,
    WIFI_ONLY,
    MOBILE_ONLY
}

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tasty_drive_prefs", Context.MODE_PRIVATE)

    var botToken: String
        get() = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BOT_TOKEN, value.trim()).apply()

    var channelId: String
        get() = prefs.getString(KEY_CHANNEL_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CHANNEL_ID, value.trim()).apply()

    var networkPreference: NetworkPreference
        get() {
            val name = prefs.getString(KEY_NETWORK_PREF, NetworkPreference.BOTH.name)
            return try {
                NetworkPreference.valueOf(name ?: NetworkPreference.BOTH.name)
            } catch (e: Exception) {
                NetworkPreference.BOTH
            }
        }
        set(value) = prefs.edit().putString(KEY_NETWORK_PREF, value.name).apply()

    var concurrency: Int
        get() = prefs.getInt(KEY_CONCURRENCY, 2).coerceIn(1, 4)
        set(value) = prefs.edit().putInt(KEY_CONCURRENCY, value.coerceIn(1, 4)).apply()

    var autoScanOnStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    var sendAsOriginalDocument: Boolean
        get() = prefs.getBoolean(KEY_ORIGINAL_QUALITY, true)
        set(value) = prefs.edit().putBoolean(KEY_ORIGINAL_QUALITY, value).apply()

    fun isConfigured(): Boolean {
        return botToken.isNotBlank() && channelId.isNotBlank()
    }

    fun isUploaded(mediaId: Long, name: String? = null, size: Long = 0L): Boolean {
        if (prefs.getBoolean("uploaded_id_$mediaId", false)) return true
        if (prefs.getBoolean("uploaded_$mediaId", false)) return true
        if (!name.isNullOrEmpty() && size > 0) {
            val fileKey = "uploaded_file_${name.hashCode()}_$size"
            if (prefs.getBoolean(fileKey, false)) return true
        }
        return false
    }

    fun markUploaded(mediaId: Long, name: String? = null, size: Long = 0L) {
        val editor = prefs.edit()
            .putBoolean("uploaded_id_$mediaId", true)
            .putBoolean("uploaded_$mediaId", true)
        if (!name.isNullOrEmpty() && size > 0) {
            val fileKey = "uploaded_file_${name.hashCode()}_$size"
            editor.putBoolean(fileKey, true)
        }
        editor.apply()
        incrementUploadedCount()
    }

    fun unmarkUploaded(mediaId: Long, name: String? = null, size: Long = 0L) {
        val editor = prefs.edit()
            .remove("uploaded_id_$mediaId")
            .remove("uploaded_$mediaId")
        if (!name.isNullOrEmpty() && size > 0) {
            editor.remove("uploaded_file_${name.hashCode()}_$size")
        }
        editor.apply()
    }

    private fun incrementUploadedCount() {
        val count = prefs.getInt(KEY_TOTAL_UPLOADED_COUNT, 0)
        prefs.edit().putInt(KEY_TOTAL_UPLOADED_COUNT, count + 1).apply()
    }

    fun getTotalUploadedCount(): Int {
        return prefs.getInt(KEY_TOTAL_UPLOADED_COUNT, 0)
    }

    fun resetUploadedHistory() {
        val editor = prefs.edit()
        val allKeys = prefs.all.keys
        for (k in allKeys) {
            if (k.startsWith("uploaded_")) {
                editor.remove(k)
            }
        }
        editor.putInt(KEY_TOTAL_UPLOADED_COUNT, 0)
        editor.apply()
    }

    companion object {
        private const val KEY_BOT_TOKEN = "key_bot_token"
        private const val KEY_CHANNEL_ID = "key_channel_id"
        private const val KEY_NETWORK_PREF = "key_network_pref"
        private const val KEY_CONCURRENCY = "key_concurrency"
        private const val KEY_AUTO_SCAN = "key_auto_scan"
        private const val KEY_ORIGINAL_QUALITY = "key_original_quality"
        private const val KEY_TOTAL_UPLOADED_COUNT = "key_total_uploaded_count"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
