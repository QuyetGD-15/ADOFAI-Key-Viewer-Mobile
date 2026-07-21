package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private const val LOG_FILE_NAME = "app_execution_log.txt"
    private const val OLD_LOG_FILE_NAME = "app_execution_log_old.txt"
    // Cài đặt giới hạn cứng: 2MB (Có thể chỉnh sửa nếu muốn)
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024L

    private val diskIoExecutor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    enum class Level(val label: String) {
        DEBUG("DEBUG"),
        INFO("INFO "),
        WARN("WARN "),
        ERROR("ERROR")
    }

    fun log(context: Context, message: String) {
        writeLog(context, Level.INFO, "App", message)
    }

    fun d(context: Context, tag: String, message: String) {
        writeLog(context, Level.DEBUG, tag, message)
        Log.d(tag, message)
    }

    fun i(context: Context, tag: String, message: String) {
        writeLog(context, Level.INFO, tag, message)
        Log.i(tag, message)
    }

    fun w(context: Context, tag: String, message: String) {
        writeLog(context, Level.WARN, tag, message)
        Log.w(tag, message)
    }

    fun e(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        writeLog(context, Level.ERROR, tag, fullMessage)
        Log.e(tag, message, throwable)
    }

    private fun writeLog(context: Context, level: Level, tag: String, message: String) {
        val filesDir = context.filesDir

        // Đưa TẤT CẢ (kể cả hàm lấy giờ) vào luồng ngầm để đảm bảo an toàn đa luồng tuyệt đối
        diskIoExecutor.execute {
            try {
                val timestamp = dateFormat.format(Date())
                val logLine = "[$timestamp] [${level.label}] [$tag] $message\n"

                val currentFile = File(filesDir, LOG_FILE_NAME)

                // CƠ CHẾ ROLLING LOG: Kiểm tra dung lượng trước khi ghi
                if (currentFile.exists() && currentFile.length() > MAX_LOG_SIZE) {
                    val oldFile = File(filesDir, OLD_LOG_FILE_NAME)
                    // Xóa file backup cũ đi
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                    // Đẩy file hiện tại thành file backup
                    currentFile.renameTo(oldFile)
                }

                // Ghi vào file mới/file hiện tại
                File(filesDir, LOG_FILE_NAME).appendText(logLine)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLog(context: Context): String {
        return try {
            // Chỉ đọc file log mới nhất để tránh tràn bộ nhớ khi view
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
                val oldFile = File(context.filesDir, OLD_LOG_FILE_NAME)
                if (file.exists()) file.delete()
                if (oldFile.exists()) oldFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}