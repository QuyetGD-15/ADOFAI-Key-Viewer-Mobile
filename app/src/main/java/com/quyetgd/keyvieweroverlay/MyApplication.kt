package com.quyetgd.keyvieweroverlay

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()

        AppLogger.log(this, "=== APP STARTED ===")
        AppLogger.log(this, "Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL}")
        AppLogger.log(this, "Phiên bản Android: ${Build.VERSION.RELEASE}")
        AppLogger.log(this, "Cấp API: ${Build.VERSION.SDK_INT}")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) {
                    AppLogger.log(this@MyApplication, "=== APP MỞ LÊN (FOREGROUND) ===")
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    AppLogger.log(this@MyApplication, "=== APP ẨN ĐI (BACKGROUND) ===")
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

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
