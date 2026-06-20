package com.quyetgd.keyvieweroverlay

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object GitHubUpdateManager {
    private const val GITHUB_OWNER = "QuyetGD-15"
    private const val GITHUB_REPO = "ADOFAI-Key-Viewer-Mobile"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    fun checkForUpdate(activity: Activity) {
        thread {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    
                    val latestVersionTag = jsonObject.getString("tag_name")
                    val releaseNotes = jsonObject.getString("body")
                    val assets = jsonObject.getJSONArray("assets")
                    
                    if (assets.length() > 0) {
                        val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
                        
                        // Lấy version hiện tại của App
                        val currentVersion = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0.0"
                        
                        // So sánh phiên bản (Bỏ chữ 'v' nếu có)
                        val latestClean = latestVersionTag.replace("v", "").trim()
                        val currentClean = currentVersion.replace("v", "").trim()
                        
                        if (latestClean != currentClean) {
                            Handler(Looper.getMainLooper()).post {
                                showUpdateDialog(activity, latestVersionTag, releaseNotes, apkUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, version: String, notes: String, downloadUrl: String) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_update, null)
        
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Làm trong suốt viền mặc định của Dialog hệ thống để hiển thị bo góc của CardView
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val tvUpdateTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvUpdateTitle)
        val tvUpdateNotes = dialogView.findViewById<android.widget.TextView>(R.id.tvUpdateNotes)
        val btnLater = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLater)
        val btnUpdateNow = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdateNow)

        tvUpdateTitle.text = activity.getString(R.string.update_available_title, version)
        tvUpdateNotes.text = activity.getString(R.string.update_available_msg, notes)
        btnLater.text = activity.getString(R.string.update_btn_later)
        btnUpdateNow.text = activity.getString(R.string.update_btn_now)

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdateNow.setOnClickListener {
            downloadAndInstallUpdate(activity, downloadUrl, version)
            dialog.dismiss()
        }

        dialog.show()
        
        // Đảm bảo Dialog không bị bóp chiều ngang quá mức
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun downloadAndInstallUpdate(context: Context, url: String, version: String) {
        Toast.makeText(context, context.getString(R.string.update_toast_downloading), Toast.LENGTH_SHORT).show()
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(context.getString(R.string.update_notif_title))
            setDescription(context.getString(R.string.update_notif_desc, version))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ADOFAI_Key_Viewer_$version.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Đăng ký Receiver để lắng nghe khi tải xong
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        installApk(ctxt, uri)
                    }
                    ctxt.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, apkUri: Uri) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.update_error_install), Toast.LENGTH_LONG).show()
        }
    }
}
