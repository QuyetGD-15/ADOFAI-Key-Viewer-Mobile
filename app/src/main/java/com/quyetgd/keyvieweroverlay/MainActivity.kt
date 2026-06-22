package com.quyetgd.keyvieweroverlay

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var switchOverlay: MaterialSwitch
    private lateinit var btnConfigHitbox: Button
    private lateinit var btnConfigKeyViewer: Button
    private lateinit var btnSelectApps: Button
    private lateinit var dropdownKeyMode: AutoCompleteTextView
    private lateinit var toggleInputSource: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var cardShizuku: View
    private lateinit var cardAccessibility: View
    private lateinit var btnAccessibility: Button
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var btnToggleLanguage: Button
    private var layoutAccessibilityPrompt: View? = null
    private var loadingDialog: android.app.Dialog? = null
    
    private val exportLogLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val logData = AppLogger.getLog(this)
                    outputStream.write(logData.toByteArray())
                }
                Toast.makeText(this, getString(R.string.log_save_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.log_save_error, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private val SHIZUKU_ACTION = "moe.shizuku.privileged.api.intent.action.BINDER_RECEIVED"

    private var isShizukuPollingActive = false
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

            // 2. Kiểm tra trạng thái Shizuku (Nếu đang bật chế độ Cảm ứng)
            if (isShizukuPollingActive) {
                updateShizukuStatusUI()
            }
            
            // 3. Kiểm tra trạng thái Trợ năng
            updateAccessibilityStatusUI()

            mainHandler.postDelayed(this, 500)
        }
    }

    private fun startShizukuPolling() {
        isShizukuPollingActive = true
    }

    private fun stopShizukuPolling() {
        isShizukuPollingActive = false
        // Reset UI Shizuku về trạng thái không hoạt động
        tvShizukuStatus.text = getString(R.string.shizuku_paused)
        tvShizukuStatus.setTextColor(Color.GRAY)
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
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)

        // RẼ NHÁNH KHỞI ĐỘNG (ROUTING)
        if (!pref.getBoolean("is_first_setup_done", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        initDefaultLanguage()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 1. Cài đặt mặc định cho Hitbox (ĐÃ ĐỒNG BỘ CÔNG THỨC TỈ LỆ VÀNG & BÙ TRỪ 20px)
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
        dropdownKeyMode = findViewById(R.id.dropdownKeyMode)
        
        toggleInputSource = findViewById(R.id.toggleInputSource)
        cardShizuku = findViewById(R.id.cardShizuku)
        cardAccessibility = findViewById(R.id.cardAccessibility)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        
        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        btnToggleLanguage = findViewById(R.id.btnToggleLanguage)

        // Xử lý nguồn đầu vào (Cảm ứng/Bàn phím)
        val initialInputSource = pref.getString("input_source", "touch")
        if (initialInputSource == "keyboard") {
            toggleInputSource.check(R.id.btnSourceKeyboard)
            cardShizuku.visibility = View.GONE
            cardAccessibility.visibility = View.VISIBLE
        } else {
            toggleInputSource.check(R.id.btnSourceTouch)
            cardShizuku.visibility = View.VISIBLE
            cardAccessibility.visibility = View.GONE
        }

        toggleInputSource.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newSource = if (checkedId == R.id.btnSourceKeyboard) "keyboard" else "touch"
                pref.edit().putString("input_source", newSource).apply()
                
                if (newSource == "keyboard") {
                    cardShizuku.visibility = View.GONE
                    cardAccessibility.visibility = View.VISIBLE
                    btnConfigHitbox.text = getString(R.string.main_btn_mapping)
                    stopShizukuPolling()
                } else {
                    cardShizuku.visibility = View.VISIBLE
                    cardAccessibility.visibility = View.GONE
                    btnConfigHitbox.text = getString(R.string.config_hitbox)
                    startShizukuPolling()
                }

                // 1. Ép công tắc về trạng thái TẮT
                switchOverlay.isChecked = false 
                // 2. Tắt Service hiện tại
                stopService(Intent(this, OverlayService::class.java))
                
                // 3. Cập nhật trạng thái enabled của switch
                updateSwitchEnableState()
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnExportLog).setOnClickListener {
            AppLogger.log(this, getString(R.string.log_msg_export))
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "KeyViewer_Log_$timestamp.txt"
            exportLogLauncher.launch(fileName)
        }

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

        // Cập nhật text cho nút Config tùy theo nguồn đầu vào
        val currentInputSource = pref.getString("input_source", "touch")
        btnConfigHitbox.text = if (currentInputSource == "keyboard") "Gán phím" else getString(R.string.config_hitbox)

        btnConfigHitbox.setOnClickListener {
            val inputSource = pref.getString("input_source", "touch")
            if (inputSource == "keyboard") {
                showKeyMappingDialog()
            } else {
                showLoading()
                startActivity(Intent(this, HitboxConfigActivity::class.java))
            }
        }

        btnConfigKeyViewer.setOnClickListener {
            showLoading()
            if (OverlayService.isRunning) {
                stopService(Intent(this, OverlayService::class.java))
            }
            startActivity(Intent(this, KeyViewerConfigActivity::class.java))
        }

        btnSelectApps.setOnClickListener {
            checkAndShowXiaomiWarning {
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
        setupKeyModeDropdown()
        btnToggleLanguage.setOnClickListener {
            showLoading()
            val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            val currentLang = pref.getString("app_language", "en")
            val newLocale = if (currentLang == "en") "vi" else "en"
            
            pref.edit().putString("app_language", newLocale).apply()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLocale))
        }

        handleIncomingIntent(intent)

        // Kiểm tra lỗi crash từ phiên trước
        val crashPref = getSharedPreferences("CrashPrefs", Context.MODE_PRIVATE)
        val crashLog = crashPref.getString("CRASH_LOG", null)
        if (!crashLog.isNullOrEmpty()) {
            val displayLog = if (crashLog.length > 500) crashLog.substring(0, 500) + "..." else crashLog
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.crash_dialog_title))
                .setMessage(displayLog)
                .setPositiveButton(getString(R.string.crash_copy_code)) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", crashLog)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.crash_ignore)) { _, _ ->
                    crashPref.edit().remove("CRASH_LOG").apply()
                }
                .setCancelable(false)
                .show()
        }

        GitHubUpdateManager.checkForUpdate(this)

        findViewById<ImageButton>(R.id.btnManualCheckUpdate).setOnClickListener {
            Toast.makeText(this, getString(R.string.update_check_manual), Toast.LENGTH_SHORT).show()
            GitHubUpdateManager.checkForUpdate(this)
        }
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

    private fun updateAccessibilityStatusUI() {
        val isEnabled = isAccessibilityServiceEnabled(this, TouchRendererService::class.java)
        if (isEnabled) {
            tvAccessibilityStatus.text = getString(R.string.main_acc_status_granted)
            tvAccessibilityStatus.setTextColor(Color.GREEN)
        } else {
            tvAccessibilityStatus.text = getString(R.string.main_acc_status_pending)
            tvAccessibilityStatus.setTextColor(Color.RED)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        val componentName = android.content.ComponentName(context, service).flattenToString()
        while (colonSplitter.hasNext()) {
            val s = colonSplitter.next()
            if (s.equals(componentName, ignoreCase = true)) return true
        }
        return false
    }

    private fun setupKeyModeDropdown() {
        val modes = arrayOf("4 KEY", "6 KEY", "8 KEY", "10 KEY")

        // Tạo Adapter Custom ghi đè hoàn toàn bộ lọc (Filter)
        val noFilterAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, modes) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        // LUÔN LUÔN trả về toàn bộ danh sách gốc, bất chấp từ khóa tìm kiếm là gì
                        results.values = modes
                        results.count = modes.size
                        return results
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        // 1. GÁN ADAPTER TRƯỚC
        dropdownKeyMode.setAdapter(noFilterAdapter)

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val currentMode = pref.getInt("current_key_mode", 6)
        
        // 2. SỬ DỤNG LỆNH .POST ĐỂ TRÁNH LỖI LỌC DANH SÁCH LẦN ĐẦU MỞ APP
        dropdownKeyMode.post {
            dropdownKeyMode.setText("${currentMode} KEY", false)
        }

        dropdownKeyMode.setOnItemClickListener { _, _, position, _ ->
            val selected = modes[position]
            val modeNumber = try {
                selected.split(" ")[0].toInt()
            } catch (e: Exception) {
                6
            }
            pref.edit().putInt("current_key_mode", modeNumber).apply()
            
            // KIỂM TRA TRẠNG THÁI CÔNG TẮC ĐỂ RESTART SERVICE
            if (switchOverlay.isChecked) {
                // Tắt Service
                stopService(Intent(this, OverlayService::class.java))
                
                // Bật lại Service sau một khoảng trễ nhỏ để hệ thống giải phóng bộ nhớ
                Handler(Looper.getMainLooper()).postDelayed({
                    val intentStart = Intent(this, OverlayService::class.java).apply {
                        action = "com.quyetgd.keyvieweroverlay.ACTION_START_FOREGROUND"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intentStart)
                    } else {
                        startService(intentStart)
                    }
                    Toast.makeText(this, getString(R.string.main_toast_restarted, selected), Toast.LENGTH_SHORT).show()
                }, 300)
            } else {
                // Nếu chưa bật thì chỉ bắn cấu hình để màn hình Config (nếu đang mở) cập nhật
                val intent = Intent("com.quyetgd.keyvieweroverlay.UPDATE_OVERLAY_CONFIG")
                sendBroadcast(intent)
                Toast.makeText(this, getString(R.string.main_toast_saved_mode, selected), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showKeyMappingDialog() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val keyMode = pref.getInt("current_key_mode", 6)
        var waitingIndex = -1

        // Root Layout nguyên khối
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.parseColor("#1E1E1E"))
            }
            background = bg
        }

        // Tiêu đề thủ công
        val tvTitle = TextView(this).apply {
            text = getString(R.string.mapping_dialog_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dpToPx(16)
            layoutParams = params
        }
        root.addView(tvTitle)

        // Container cho danh sách phím
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(container)

        val rowViews = mutableListOf<Pair<TextView, TextView>>()

        for (i in 0 until keyMode) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                
                val bgItem = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor("#2A2A2A"))
                }
                background = bgItem
                
                val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                if (i > 0) params.topMargin = dpToPx(8)
                layoutParams = params
            }

            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = getString(R.string.mapping_row_key, i + 1)
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val savedKeyName = pref.getString("key_name_${keyMode}_$i", getString(R.string.mapping_status_none))
            val tvKey = TextView(this).apply {
                text = savedKeyName
                setTextColor(if (savedKeyName == getString(R.string.mapping_status_none)) Color.GRAY else Color.parseColor("#A78BFA"))
                textSize = 14f
            }

            row.addView(tvLabel)
            row.addView(tvKey)
            container.addView(row)
            rowViews.add(tvLabel to tvKey)

            row.setOnClickListener {
                waitingIndex = i
                rowViews.forEachIndexed { index, pair ->
                    if (index != i) {
                        val kn = pref.getString("key_name_${keyMode}_$index", getString(R.string.mapping_status_none))
                        pair.second.text = kn
                        pair.second.setTextColor(if (kn == getString(R.string.mapping_status_none)) Color.GRAY else Color.parseColor("#A78BFA"))
                    }
                }
                tvKey.text = getString(R.string.mapping_status_waiting)
                tvKey.setTextColor(Color.parseColor("#A78BFA"))
            }
        }

        // Nút XONG thủ công
        val btnDone = TextView(this).apply {
            text = getString(R.string.mapping_btn_done)
            setTextColor(Color.parseColor("#A78BFA"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END
                topMargin = dpToPx(24)
            }
            layoutParams = params
        }
        root.addView(btnDone)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(root)
            .create()

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (waitingIndex != -1 && event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                
                val pressedKeyName = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
                pref.edit().apply {
                    putInt("key_code_${keyMode}_$waitingIndex", keyCode)
                    putString("key_name_${keyMode}_$waitingIndex", pressedKeyName)
                }.apply()

                val tvKey = rowViews[waitingIndex].second
                tvKey.text = pressedKeyName
                tvKey.setTextColor(Color.parseColor("#A78BFA"))
                
                waitingIndex = -1
                true
            } else {
                false
            }
        }

        dialog.show()
        // XÓA PHÔNG NỀN LỆCH MÀU CỦA HỆ THỐNG
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Cập nhật intent mới
        handleIncomingIntent(intent)

        // Kiểm tra lỗi crash từ phiên trước
        val crashPref = getSharedPreferences("CrashPrefs", Context.MODE_PRIVATE)
        val crashLog = crashPref.getString("CRASH_LOG", null)
        if (!crashLog.isNullOrEmpty()) {
            val displayLog = if (crashLog.length > 500) crashLog.substring(0, 500) + "..." else crashLog
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.crash_dialog_title))
                .setMessage(displayLog)
                .setPositiveButton(getString(R.string.crash_copy_code)) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", crashLog)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.crash_ignore)) { _, _ ->
                    crashPref.edit().remove("CRASH_LOG").apply()
                }
                .setCancelable(false)
                .show()
        }

        GitHubUpdateManager.checkForUpdate(this)

        findViewById<ImageButton>(R.id.btnManualCheckUpdate).setOnClickListener {
            Toast.makeText(this, getString(R.string.update_check_manual), Toast.LENGTH_SHORT).show()
            GitHubUpdateManager.checkForUpdate(this)
        }
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

        updateSwitchEnableState()

        // LIÊN KẾT LOGIC BẬT/TẮT TÀI NGUYÊN
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val inputSource = pref.getString("input_source", "touch")
        if (inputSource == "touch") {
            startShizukuPolling()
        } else {
            stopShizukuPolling()
        }

        mainHandler.post(updateRunnable) // Bắt đầu vòng lặp cập nhật UI
    }

    private fun updateSwitchEnableState() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val inputSource = pref.getString("input_source", "touch")
        if (inputSource == "touch") {
            val isConnected = Shizuku.pingBinder()
            val hasPermission = if (isConnected) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            switchOverlay.isEnabled = isConnected && hasPermission
        } else {
            val hasAccessibility = isAccessibilityServiceEnabled(this, TouchRendererService::class.java)
            switchOverlay.isEnabled = hasAccessibility
        }
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
            } else {
                if (isConnected) {
                    tvShizukuStatus.text = getString(R.string.shizuku_not_connected_or_no_permission)
                } else {
                    tvShizukuStatus.text = getString(R.string.shizuku_not_running)
                }
                tvShizukuStatus.setTextColor(Color.RED)

                // Mất kết nối: Tắt công tắc, khóa nhấn
                if (switchOverlay.isChecked) {
                    switchOverlay.setOnCheckedChangeListener(null)
                    switchOverlay.isChecked = false
                    setupSwitchListener()
                }

                // Ép dừng Service nếu đang chạy ngầm
                if (OverlayService.isRunning) {
                    stopService(Intent(this, OverlayService::class.java))
                }
            }

            // Luôn cập nhật lại trạng thái enabled tổng quát
            updateSwitchEnableState()

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
            updateSwitchEnableState()

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

        val dialog = BottomSheetDialog(this, R.style.RoundedBottomSheetDialog)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
        }

        val title = TextView(this).apply {
            text = getString(R.string.dialog_select_apps_title)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dpToPx(16))
        }

        val rv = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        rv.adapter = AppAdapter(finalItems) { _, _ -> }

        val btnOk = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(56)).apply {
                topMargin = dpToPx(16)
            }
            text = getString(android.R.string.ok)
            cornerRadius = dpToPx(16)
            setOnClickListener {
                val selectedSet = finalItems.filter { !it.isHeader && it.isChecked }.map { it.packageName }.toSet()
                getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).edit().putStringSet("allowed_apps", selectedSet).apply()
                dialog.dismiss()
            }
        }

        root.addView(title)
        root.addView(rv)
        root.addView(btnOk)

        dialog.setContentView(root)
        
        // Cố định chiều cao khoảng 70% màn hình để dễ nhìn
        val dm = resources.displayMetrics
        root.layoutParams.height = (dm.heightPixels * 0.7).toInt()
        
        dialog.show()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val xiaomiBrands = listOf("xiaomi", "redmi", "poco", "blackshark")
        return xiaomiBrands.any { manufacturer.contains(it) || brand.contains(it) }
    }

    private fun checkAndShowXiaomiWarning(onProceed: () -> Unit) {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val isDismissed = pref.getBoolean("xiaomi_warning_dismissed", false)

        if (!isXiaomiDevice() || isDismissed) {
            onProceed()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_xiaomi_warning, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val btnSettings = dialogView.findViewById<Button>(R.id.btnXiaomiSettings)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnXiaomiCancel)
        val btnDone = dialogView.findViewById<Button>(R.id.btnXiaomiDone)

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDone.setOnClickListener {
            pref.edit().putBoolean("xiaomi_warning_dismissed", true).apply()
            dialog.dismiss()
            onProceed()
        }

        dialog.show()
    }

    private fun initDefaultLanguage() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        
        // Kiểm tra xem đã có key ngôn ngữ trong bộ nhớ chưa (chưa có nghĩa là lần mở app đầu tiên)
        if (!pref.contains("app_language")) {
            // Lấy mã ngôn ngữ hiện tại của hệ điều hành
            val systemLang = java.util.Locale.getDefault().language
            
            // Nếu là tiếng Việt thì dùng "vi", tất cả ngôn ngữ khác đều fallback về tiếng Anh "en"
            val defaultAppLang = if (systemLang == "vi") "vi" else "en"
            
            // Lưu lựa chọn này vào bộ nhớ để các lần mở app sau không bị ghi đè
            pref.edit().putString("app_language", defaultAppLang).apply()
            
            // Áp dụng ngôn ngữ cho ứng dụng ngay lần đầu
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(defaultAppLang))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER)
        Shizuku.removeRequestPermissionResultListener(this)
        mainHandler.removeCallbacksAndMessages(null)
    }
}
