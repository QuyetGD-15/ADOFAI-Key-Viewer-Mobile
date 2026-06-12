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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private data class AppItem(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable?,
        var isChecked: Boolean = false,
        val isHeader: Boolean = false,
        val headerTitle: String = ""
    )

    private inner class AppAdapter(
        val items: List<AppItem>,
        val onCheckedChange: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = if (items[position].isHeader) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.YELLOW)
                }
                object : RecyclerView.ViewHolder(tv) {}
            } else {
                val layout = LinearLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                    isClickable = true
                    isFocusable = true
                    val outValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    setBackgroundResource(outValue.resourceId)
                }
                
                val icon = ImageView(parent.context).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
                }
                
                val name = TextView(parent.context).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dpToPx(12)
                    }
                    setTextColor(Color.WHITE)
                }
                
                val cb = CheckBox(parent.context).apply {
                    id = View.generateViewId()
                    isFocusable = false
                    isClickable = false
                }
                
                layout.addView(icon)
                layout.addView(name)
                layout.addView(cb)
                
                layout.setTag(R.id.hitbox1, icon) // Temporary storage
                layout.setTag(R.id.hitbox2, name)
                layout.setTag(R.id.hitbox3, cb)
                
                object : RecyclerView.ViewHolder(layout) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (item.isHeader) {
                (holder.itemView as TextView).text = item.headerTitle
            } else {
                val icon = holder.itemView.getTag(R.id.hitbox1) as ImageView
                val name = holder.itemView.getTag(R.id.hitbox2) as TextView
                val cb = holder.itemView.getTag(R.id.hitbox3) as CheckBox
                
                icon.setImageDrawable(item.icon)
                name.text = item.label
                cb.isChecked = item.isChecked
                
                holder.itemView.setOnClickListener {
                    item.isChecked = !item.isChecked
                    cb.isChecked = item.isChecked
                    onCheckedChange(position, item.isChecked)
                }
            }
        }

        override fun getItemCount(): Int = items.size
        private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var btnCheck: Button
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var btnConfigHitbox: Button
    private lateinit var btnConfigKeyViewer: Button
    private lateinit var btnSelectApps: Button
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var btnToggleLanguage: Button
    private var layoutAccessibilityPrompt: View? = null
    private var loadingDialog: android.app.Dialog? = null
    
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

        // 2. Cài đặt mặc định cho Hitbox (ĐÃ ĐỒNG BỘ CÔNG THỨC TỈ LỆ VÀNG & BÙ TRỪ 20px)
        val hitboxPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        if (hitboxPref.getBoolean("is_first_run", true)) {
            val realMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)

            val screenWidth = kotlin.math.max(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
            val screenHeight = kotlin.math.min(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
            val dotOffset = 20f

            // Khai báo tỉ lệ vàng: 10% - 15% - 25% - 25% - 15% - 10%
            val w1 = screenWidth * 0.10f
            val w2 = screenWidth * 0.15f
            val w3 = screenWidth * 0.25f
            val widths = floatArrayOf(w1, w2, w3, w3, w2, w1)

            hitboxPref.edit().apply {
                var currentX = 0f
                for (i in 0 until 6) {
                    val startX = currentX.toInt()
                    val endX = (currentX + widths[i]).toInt()

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

                    // Cộng dồn tọa độ X
                    currentX += widths[i]
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
        btnSelectApps = findViewById(R.id.btnSelectApps)
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
            showLoading()
            startActivity(Intent(this, HitboxConfigActivity::class.java))
        }

        btnConfigKeyViewer.setOnClickListener {
            showLoading()
            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
            }
            startActivity(Intent(this, KeyViewerConfigActivity::class.java))
        }

        btnSelectApps.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                Toast.makeText(this, getString(R.string.usage_stats_required), Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } else {
                lifecycleScope.launch {
                    showLoading()
                    val result = withContext(Dispatchers.IO) {
                        loadAndCategorizeApps()
                    }
                    hideLoading()
                    showAppSelectionDialog(result.first, result.second)
                }
            }
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
            showLoading()
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            val newLocale = if (currentLocales.toLanguageTags() == "en") "vi" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
        }

        handleIncomingIntent(intent)
    }

    private fun showLoading() {
        if (loadingDialog?.isShowing == true) return
        loadingDialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setContentView(ProgressBar(this@MainActivity))
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    private fun hideLoading() {
        loadingDialog?.let { if (it.isShowing) it.dismiss() }
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
        hideLoading()
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

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun loadAndCategorizeApps(): Pair<List<AppItem>, List<AppItem>> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
        
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val savedApps = pref.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        
        val recommendedKeywords = listOf("adofai", "a dance of fire and ice", "geometry dash")
        
        val allApps = resolvedInfos
            .filter { it.activityInfo.packageName != packageName }
            .map { 
                AppItem(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm),
                    isChecked = savedApps.contains(it.activityInfo.packageName)
                )
            }

        val recommended = allApps.filter { item -> 
            recommendedKeywords.any { kw -> item.label.contains(kw, ignoreCase = true) } 
        }.sortedBy { it.label }
        
        val others = allApps.filter { item -> 
            !recommendedKeywords.any { kw -> item.label.contains(kw, ignoreCase = true) } 
        }.sortedBy { it.label }
        
        return recommended to others
    }

    private fun showAppSelectionDialog(recommended: List<AppItem>, others: List<AppItem>) {
        val finalItems = mutableListOf<AppItem>()
        if (recommended.isNotEmpty()) {
            finalItems.add(AppItem("", "", null, isHeader = true, headerTitle = getString(R.string.header_recommended)))
            finalItems.addAll(recommended)
        }
        finalItems.add(AppItem("", "", null, isHeader = true, headerTitle = getString(R.string.header_others)))
        finalItems.addAll(others)

        val rv = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        
        rv.adapter = AppAdapter(finalItems) { _, _ -> }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_apps_title))
            .setView(rv)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selectedSet = finalItems.filter { !it.isHeader && it.isChecked }.map { it.packageName }.toSet()
                getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).edit().putStringSet("allowed_apps", selectedSet).apply()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER)
        Shizuku.removeRequestPermissionResultListener(this)
        mainHandler.removeCallbacksAndMessages(null)
    }
}
