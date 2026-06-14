package com.quyetgd.keyvieweroverlay

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE_NAME = "app_execution_log.txt"

    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS]", Locale.getDefault()).format(Date())
        val logLine = "$timestamp $message\n"
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.appendText(logLine)
        } catch (e: Exception) {
            e.printStackTrace()
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
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
