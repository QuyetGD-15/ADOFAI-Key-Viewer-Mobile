package com.quyetgd.keyvieweroverlay

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 1. Chuyển Throwable thành String (Stack Trace)
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            // 2. Lưu vào SharedPreferences
            val pref = getSharedPreferences("CrashPrefs", Context.MODE_PRIVATE)
            pref.edit().putString("CRASH_LOG", stackTrace).apply()

            // 3. Gọi Handler mặc định để App crash như bình thường
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
