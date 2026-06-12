package com.quyetgd.keyvieweroverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnCheck: Button
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var btnConfigHitbox: Button
    private lateinit var btnConfigKeyViewer: Button
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var btnToggleLanguage: Button
    private var layoutAccessibilityPrompt: View? = null
    
    private val SHIZUKU_ACTION = "moe.shizuku.privileged.api.intent.action.BINDER_RECEIVED"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 1. Kiểm tra trạng thái Service và đồng bộ Switch
            val isRunning = OverlayService.isRunning
            if (switchOverlay.isChecked != isRunning) {
                switchOverlay.setOnCheckedChangeListener(null) // Tạm tắt listener
                switchOverlay.isChecked = isRunning
                setupSwitchListener() // Bật lại listener
            }

            // 2. Kiểm tra trạng thái Shizuku
            updateShizukuStatusUI()

            mainHandler.postDelayed(this, 500)
        }
    }

    private val shizukuReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SHIZUKU_ACTION) {
                checkShizukuPermission()
            }
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            updateStatus(getString(R.string.shizuku_permission_granted))
        } else {
            updateStatus(getString(R.string.shizuku_permission_denied))
        }
    }

    private val BINDER_DEAD_LISTENER = Shizuku.OnBinderDeadListener {
        updateStatus(getString(R.string.shizuku_disconnected))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Cài đặt mặc định cho KeyViewer
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        if (pref.getBoolean("is_first_run", true)) {
            pref.edit().apply {
                putFloat("viewer_x", 0f)
                putFloat("viewer_y", 0f)
                putFloat("viewer_scale", 0.5f)
                putFloat("trail_speed", 0.7f)
                putInt("trail_limit_px", 280)
                putInt("key_width", 55)
                putInt("key_height", 60)
                putInt("key_spacing", 5)
                putBoolean("is_first_run", false)
            }.apply()
        }

        // 2. Cài đặt mặc định cho Hitbox (ĐÃ ĐỒNG BỘ CÔNG THỨC BÙ TRỪ 20px GIẤU CHẤM ĐỎ)
        val hitboxPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        if (hitboxPref.getBoolean("is_first_run", true)) {
            val realMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)

            val screenWidth = kotlin.math.max(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
            val screenHeight = kotlin.math.min(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
            val dotOffset = 20f

            hitboxPref.edit().apply {
                for (i in 0 until 6) {
                    val startX = (i * screenWidth / 6f).toInt()
                    val endX = ((i + 1) * screenWidth / 6f).toInt()

                    val trueBoxWidth = endX - startX
                    val trueBoxHeight = screenHeight.toInt()

                    val viewX = startX.toFloat() - dotOffset
                    val viewY = 0f - dotOffset
                    val viewWidth = trueBoxWidth + (dotOffset * 2f)
                    val viewHeight = trueBoxHeight + (dotOffset * 2f)

                    putFloat("hitbox_${i + 1}_x", viewX)
                    putFloat("hitbox_${i + 1}_y", viewY)
                    putInt("hitbox_${i + 1}_w", viewWidth.toInt())
                    putInt("hitbox_${i + 1}_h", viewHeight.toInt())
                }
                putBoolean("is_first_run", false)
            }.apply()
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        btnCheck = findViewById(R.id.btnCheck)
        switchOverlay = findViewById(R.id.switchOverlay)
        btnConfigHitbox = findViewById(R.id.btnConfigHitbox)
        btnConfigKeyViewer = findViewById(R.id.btnConfigKeyViewer)
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        btnToggleLanguage = findViewById(R.id.btnToggleLanguage)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnCheck.setOnClickListener {
            checkShizukuPermission()
            updateShizukuStatusUI()
        }

        setupSwitchListener()
        switchOverlay.text = if (OverlayService.isRunning) getString(R.string.overlay_use_notification) else getString(R.string.overlay_start_hint)

        btnConfigHitbox.setOnClickListener {
            startActivity(Intent(this, HitboxConfigActivity::class.java))
        }

        btnConfigKeyViewer.setOnClickListener {
            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
            }
            startActivity(Intent(this, KeyViewerConfigActivity::class.java))
        }

        Shizuku.addBinderDeadListener(BINDER_DEAD_LISTENER)
        Shizuku.addRequestPermissionResultListener(this)

        layoutAccessibilityPrompt = findViewById(R.id.layoutAccessibilityPrompt)

        findViewById<Button>(R.id.btnAgreeAccessibility)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            layoutAccessibilityPrompt?.visibility = View.GONE
        }

        findViewById<Button>(R.id.btnCancelAccessibility)?.setOnClickListener {
            layoutAccessibilityPrompt?.visibility = View.GONE
        }

        updateLanguageUI()
        btnToggleLanguage.setOnClickListener {
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val newLocale = if (currentLocales.toLanguageTags() == "en") "vi" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
        }

        handleIncomingIntent(intent)
    }

    private fun updateLanguageUI() {
        tvCurrentLanguage.text = getString(R.string.current_language)
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Cập nhật intent mới
        handleIncomingIntent(intent)
    }

    /**
     * Kiến trúc Direct Intent: Xử lý thông báo gọi thẳng vào Activity.
     * Cơ chế này giúp hiển thị hộp thoại xin quyền Trợ năng (Accessibility) một cách an toàn,
     * tránh việc tạo Window Token mới từ Service gây crash hoặc lỗi quyền hiển thị trên một số dòng máy.
     */
    private fun handleIncomingIntent(currentIntent: Intent?) {
        if (currentIntent?.getBooleanExtra("EXTRA_SHOW_ACC_PROMPT", false) == true) {
            // XÓA CỜ HIỆU NGAY LẬP TỨC để tránh vòng lặp vô hạn khi xoay màn hình
            currentIntent.removeExtra("EXTRA_SHOW_ACC_PROMPT")
            
            // Hiển thị Panel nội bộ một cách an toàn, không sinh Window Token mới
            layoutAccessibilityPrompt?.visibility = View.VISIBLE
            
            // Tự động đóng thanh thông báo hệ thống xuống để lộ diện App
            try {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
                // Ignore error
            }
        }
    }

    private fun setupSwitchListener() {
        switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checkOverlayPermission()) {
                    switchOverlay.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
                        switchOverlay.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                }

                val intent = Intent(this, OverlayService::class.java).apply {
                    action = "com.quyetgd.keyvieweroverlay.ACTION_START_FOREGROUND"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                switchOverlay.text = getString(R.string.overlay_use_notification)
            } else {
                stopService(Intent(this, OverlayService::class.java))
                switchOverlay.text = getString(R.string.overlay_start_hint)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SHIZUKU_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shizukuReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(shizukuReceiver, filter)
        }
        if (Shizuku.pingBinder()) checkShizukuPermission()
    }

    override fun onResume() {
        super.onResume()
        AppState.isAppVisible = true
        mainHandler.post(updateRunnable) // Bắt đầu vòng lặp cập nhật UI
    }

    override fun onPause() {
        super.onPause()
        AppState.isAppVisible = false
        mainHandler.removeCallbacks(updateRunnable) // Dừng vòng lặp khi app không hiển thị
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(shizukuReceiver) } catch (e: Exception) { }
    }

    private fun updateShizukuStatusUI() {
        try {
            val isConnected = Shizuku.pingBinder()
            val hasPermission = if (isConnected) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }

            if (isConnected && hasPermission) {
                tvShizukuStatus.text = getString(R.string.shizuku_running)
                tvShizukuStatus.setTextColor(Color.GREEN)

                // Đã sẵn sàng: Mở khóa và khôi phục độ sáng 100%
                switchOverlay.isEnabled = true
                switchOverlay.alpha = 1.0f
                (switchOverlay.parent as? View)?.alpha = 1.0f
            } else {
                if (isConnected) {
                    tvShizukuStatus.text = getString(R.string.shizuku_not_connected_or_no_permission)
                } else {
                    tvShizukuStatus.text = getString(R.string.shizuku_not_running)
                }
                tvShizukuStatus.setTextColor(Color.RED)

                // Mất kết nối: Tắt công tắc, khóa nhấn và làm mờ 60%
                if (switchOverlay.isChecked) {
                    switchOverlay.setOnCheckedChangeListener(null)
                    switchOverlay.isChecked = false
                    setupSwitchListener()
                }
                switchOverlay.isEnabled = false
                switchOverlay.alpha = 0.4f
                (switchOverlay.parent as? View)?.alpha = 0.4f

                // Ép dừng Service nếu đang chạy ngầm
                if (OverlayService.isRunning) {
                    stopService(Intent(this, OverlayService::class.java))
                }
            }

            // Cập nhật text của công tắc
            switchOverlay.text = if (OverlayService.isRunning) getString(R.string.overlay_use_notification) else getString(R.string.overlay_start_hint)

        } catch (e: Exception) {
            tvShizukuStatus.text = getString(R.string.shizuku_error)
            tvShizukuStatus.setTextColor(Color.RED)

            // Bắt lỗi cũng khóa cứng công tắc luôn
            if (switchOverlay.isChecked) {
                switchOverlay.setOnCheckedChangeListener(null)
                switchOverlay.isChecked = false
                setupSwitchListener()
            }
            switchOverlay.isEnabled = false
            switchOverlay.alpha = 0.4f
            (switchOverlay.parent as? View)?.alpha = 0.4f

            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
            }
            switchOverlay.text = getString(R.string.overlay_start_hint)
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            updateStatus(getString(R.string.shizuku_not_found))
            return false
        }
        val isGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            updateStatus(getString(R.string.shizuku_ready))
            return true
        }
        updateStatus(getString(R.string.shizuku_requesting_permission))
        Shizuku.requestPermission(100)
        return false
    }

    private fun updateStatus(message: String) {
        runOnUiThread { tvStatus.text = message }
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER)
        Shizuku.removeRequestPermissionResultListener(this)
        mainHandler.removeCallbacksAndMessages(null)
    }
}
