package com.quyetgd.keyvieweroverlay

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private const val LOG_FILE_NAME = "app_execution_log.txt"
    // Tạo một luồng ngầm duy nhất chuyên lo việc ghi file để không block UI (Game)
    private val diskIoExecutor = Executors.newSingleThreadExecutor()

    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS]", Locale.getDefault()).format(Date())
        val logLine = "$timestamp $message\n"
        val filesDir = context.filesDir // Lấy biến an toàn trên MainThread

        diskIoExecutor.execute {
            try {
                val file = File(filesDir, LOG_FILE_NAME)
                file.appendText(logLine)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLog(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun clearLog(context: Context) {
        diskIoExecutor.execute {
            try {
                val file = File(context.filesDir, LOG_FILE_NAME)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}