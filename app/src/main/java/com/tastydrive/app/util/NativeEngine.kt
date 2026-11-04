package com.tastydrive.app.util

import android.util.Log

object NativeEngine {
    private const val TAG = "NativeEngine"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("tastydrive")
            isLoaded = true
            Log.i(TAG, "Native C tastydrive library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isLoaded = false
        }
    }

    fun isNativeReady(): Boolean = isLoaded

    // Native C methods
    external fun initNative(): String
    external fun fastChecksum(data: ByteArray, length: Int): Long
    external fun isOversized(fileSize: Long): Boolean
    external fun getOptimalBufferSize(): Int
    external fun formatSpeed(bytesPerSec: Long): String
    external fun formatFileSize(bytes: Long): String
    external fun validateTelegramToken(token: String): Boolean

    // Fallbacks if native fails for any reason
    fun safeFormatSpeed(bytesPerSec: Long): String {
        return if (isLoaded) {
            try {
                formatSpeed(bytesPerSec)
            } catch (e: Exception) {
                fallbackFormatSpeed(bytesPerSec)
            }
        } else {
            fallbackFormatSpeed(bytesPerSec)
        }
    }

    fun safeFormatFileSize(bytes: Long): String {
        return if (isLoaded) {
            try {
                formatFileSize(bytes)
            } catch (e: Exception) {
                fallbackFormatFileSize(bytes)
            }
        } else {
            fallbackFormatFileSize(bytes)
        }
    }

    fun safeIsOversized(fileSize: Long): Boolean {
        return if (isLoaded) {
            try {
                isOversized(fileSize)
            } catch (e: Exception) {
                fileSize > (50L * 1024L * 1024L)
            }
        } else {
            fileSize > (50L * 1024L * 1024L)
        }
    }

    private fun fallbackFormatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 B/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.2f MB/s", mb)
            kb >= 1.0 -> String.format("%.1f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun fallbackFormatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }
}

