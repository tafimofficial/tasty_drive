package com.tastydrive.app

import android.app.Application
import android.util.Log
import com.tastydrive.app.util.NativeEngine

class TastyDriveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val nativeMsg = NativeEngine.initNative()
            Log.i("TastyDriveApp", "Native initialization: $nativeMsg")
        } catch (e: Throwable) {
            Log.e("TastyDriveApp", "Native initialization warning", e)
        }
    }
}

