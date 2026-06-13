package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class KeyViewerConfigActivity : AppCompatActivity() {

    private lateinit var viewerContainer: FrameLayout
    private lateinit var keysContainer: LinearLayout
    private lateinit var keyTrailView: KeyTrailView
    private lateinit var bottomCountersContainer: LinearLayout

    private lateinit var tvPosXLabel: TextView
    private lateinit var tvPosYLabel: TextView
    private lateinit var tvScaleLabel: TextView
    private lateinit var tvSpeedLabel: TextView
    private lateinit var tvLimitLabel: TextView
    private lateinit var tvKeyWidthLabel: TextView
    private lateinit var tvKeyHeightLabel: TextView
    private lateinit var tvKeySpacingLabel: TextView

    private lateinit var seekPosX: SeekBar
    private lateinit var seekPosY: SeekBar
    private lateinit var seekScale: SeekBar
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekLimit: SeekBar
    private lateinit var seekKeyWidth: SeekBar
    private lateinit var seekKeyHeight: SeekBar
    private lateinit var seekKeySpacing: SeekBar

    private var currentScale = 1.0f
    private var currentSpeed = 1.0f
    private var currentLimit = 200 // Mặc định 200dp
    private var currentKeyWidth = 60 // Mặc định 60dp
    private var currentKeyHeight = 60 // Mặc định 60dp
    private var currentKeySpacing = 0

    private var lastX = 0f
    private var lastY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Thiết lập giao diện tràn viền để lấy tọa độ chuẩn xác nhất
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Ẩn thanh trạng thái, điều hướng
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_key_viewer_config)

        // 1. Tắt OverlayService khi bắt đầu cấu hình
        stopService(Intent(this, OverlayService::class.java))

        initViews()
        loadPreferences()
        setupListeners()
        
        // Cố định Pivot 0,0 để đồng bộ hệ tọa độ
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        // Cập nhật giao diện ban đầu
        viewerContainer.post { updateLivePreview() }
    }

    private fun initViews() {
        viewerContainer = findViewById(R.id.viewerContainer)
        keysContainer = findViewById(R.id.keysContainer)
        keyTrailView = findViewById(R.id.keyTrailView)
        bottomCountersContainer = findViewById(R.id.bottomCountersContainer)

        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        tvLimitLabel = findViewById(R.id.tvLimitLabel)
        tvKeyWidthLabel = findViewById(R.id.tvKeyWidthLabel)
        tvKeyHeightLabel = findViewById(R.id.tvKeyHeightLabel)
        tvKeySpacingLabel = findViewById(R.id.tvKeySpacingLabel)

        seekPosX = findViewById(R.id.seekPosX)
        seekPosY = findViewById(R.id.seekPosY)
        seekScale = findViewById(R.id.seekScale)
        seekSpeed = findViewById(R.id.seekSpeed)
        seekLimit = findViewById(R.id.seekLimit)
        seekKeyWidth = findViewById(R.id.seekKeyWidth)
        seekKeyHeight = findViewById(R.id.seekKeyHeight)
        seekKeySpacing = findViewById(R.id.seekKeySpacing)

        // Thiết lập max cho Pos SeekBars dựa trên màn hình (Dải gấp đôi để hỗ trợ âm/dương)
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels
        seekPosX.max = centerX * 2
        seekPosY.max = centerY * 2

        // Tâm scale ở góc trên trái (Pivot 0,0)
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f
    }

    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels

        val x = sharedPref.getFloat("viewer_x", 0f)
        val y = sharedPref.getFloat("viewer_y", 0f)
        currentScale = sharedPref.getFloat("viewer_scale", 1.0f)
        currentSpeed = sharedPref.getFloat("trail_speed", 1.0f)
        currentLimit = sharedPref.getInt("trail_limit_px", 200)
        currentKeyWidth = sharedPref.getInt("key_width", 60)
        currentKeyHeight = sharedPref.getInt("key_height", 60)
        currentKeySpacing = sharedPref.getInt("key_spacing", 0)

        viewerContainer.x = x
        viewerContainer.y = y
        viewerContainer.scaleX = currentScale
        viewerContainer.scaleY = currentScale

        seekPosX.progress = x.toInt() + centerX
        seekPosY.progress = y.toInt() + centerY
        seekScale.progress = (currentScale * 100).toInt()
        seekSpeed.progress = (currentSpeed * 100).toInt()
        seekLimit.progress = currentLimit - 70
        seekKeyWidth.progress = currentKeyWidth - 30
        seekKeyHeight.progress = currentKeyHeight - 30
        seekKeySpacing.progress = currentKeySpacing
    }

    private fun setupListeners() {
        // Kéo thả toàn bộ cụm
        viewerContainer.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x += (event.rawX - lastX)
                    view.y += (event.rawY - lastY)
                    lastX = event.rawX
                    lastY = event.rawY
                    
                    // Cập nhật ngược lại SeekBar (Cộng thêm Center Offset)
                    val dm = resources.displayMetrics
                    seekPosX.progress = view.x.toInt() + dm.widthPixels
                    seekPosY.progress = view.y.toInt() + dm.heightPixels
                    updateLabels()
                    true
                }
                else -> false
            }
        }

        // SeekBar Limit
        seekLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentLimit = progress + 70
                updateLivePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Các SeekBar khác
        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                when (seekBar?.id) {
                    R.id.seekScale -> {
                        currentScale = progress / 100f
                        if (currentScale < 0.1f) currentScale = 0.1f
                        viewerContainer.scaleX = currentScale
                        viewerContainer.scaleY = currentScale
                    }
                    R.id.seekSpeed -> currentSpeed = progress / 100f
                    R.id.seekKeyWidth -> currentKeyWidth = progress + 30
                    R.id.seekKeyHeight -> currentKeyHeight = progress + 30
                    R.id.seekKeySpacing -> currentKeySpacing = progress
                }
                updateLivePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekScale.setOnSeekBarChangeListener(commonListener)
        seekSpeed.setOnSeekBarChangeListener(commonListener)
        seekKeyWidth.setOnSeekBarChangeListener(commonListener)
        seekKeyHeight.setOnSeekBarChangeListener(commonListener)
        seekKeySpacing.setOnSeekBarChangeListener(commonListener)

        // Listeners cho PosX và PosY
        val posListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dm = resources.displayMetrics
                val centerX = dm.widthPixels
                val centerY = dm.heightPixels
                
                val actualX = seekPosX.progress - centerX
                val actualY = seekPosY.progress - centerY
                
                if (fromUser) {
                    when (seekBar?.id) {
                        R.id.seekPosX -> viewerContainer.x = actualX.toFloat()
                        R.id.seekPosY -> viewerContainer.y = actualY.toFloat()
                    }
                }
                updateLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        seekPosX.setOnSeekBarChangeListener(posListener)
        seekPosY.setOnSeekBarChangeListener(posListener)

        findViewById<Button>(R.id.btnSaveViewer).setOnClickListener {
            saveAndExit()
        }

        findViewById<Button>(R.id.btnResetViewer).setOnClickListener {
            resetToDefault()
        }

        findViewById<Button>(R.id.btnResetTotal)?.setOnClickListener {
            getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).edit().putInt("TOTAL_CLICKS", 0).apply()
            sendBroadcast(Intent("ACTION_RESET_TOTAL"))
            Toast.makeText(this, getString(R.string.toast_total_reset), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLivePreview() {
        val widthPx = dpToPx(currentKeyWidth.toFloat())
        val heightPx = dpToPx(currentKeyHeight.toFloat())
        val spacingPx = dpToPx(currentKeySpacing.toFloat())
        val limitPx = dpToPx(currentLimit.toFloat())

        // Cập nhật phím
        for (i in 0 until keysContainer.childCount) {
            val keyView = keysContainer.getChildAt(i)
            val params = keyView.layoutParams as ViewGroup.MarginLayoutParams
            params.width = widthPx
            params.height = heightPx
            if (i > 0) {
                params.leftMargin = spacingPx
            }
            keyView.layoutParams = params
        }

        // Spacing cho bộ đếm
        val counterParams = bottomCountersContainer.layoutParams as ViewGroup.MarginLayoutParams
        counterParams.topMargin = spacingPx
        bottomCountersContainer.layoutParams = counterParams

        val tvKps = findViewById<TextView>(R.id.tvKps)
        val tvTotal = findViewById<TextView>(R.id.tvTotal)
        tvKps?.text = "${getString(R.string.kps_label)}\n0"
        tvTotal?.text = "${getString(R.string.total_label)}\n0"

        val kpsParams = tvKps.layoutParams as ViewGroup.MarginLayoutParams
        kpsParams.rightMargin = spacingPx
        tvKps.layoutParams = kpsParams

        // Chiều cao RainKey
        val trailParams = keyTrailView.layoutParams
        trailParams.height = limitPx
        keyTrailView.layoutParams = trailParams

        // Cố định Pivot 0,0
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        updateLabels()
    }

    private fun anchorContainer() {
        val screenH = resources.displayMetrics.heightPixels
        val anchorY = screenH * 0.8f
        // Giữ đáy của container (sau khi scale) tại vị trí anchorY
        viewerContainer.y = anchorY - (viewerContainer.height * viewerContainer.scaleY)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun updateLabels() {
        tvPosXLabel.text = getString(R.string.pos_x_label, viewerContainer.x.toInt())
        tvPosYLabel.text = getString(R.string.pos_y_label, viewerContainer.y.toInt())
        tvScaleLabel.text = getString(R.string.scale_label, currentScale)
        tvSpeedLabel.text = getString(R.string.speed_label, currentSpeed)
        tvLimitLabel.text = getString(R.string.limit_label, currentLimit)
        tvKeyWidthLabel.text = getString(R.string.key_width_label, currentKeyWidth)
        tvKeyHeightLabel.text = getString(R.string.key_height_label, currentKeyHeight)
        tvKeySpacingLabel.text = getString(R.string.spacing_label, currentKeySpacing)
    }

    private fun saveAndExit() {
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        
        // Sử dụng x, y trực tiếp từ View vì Activity đã tràn viền (no limits)
        val savedX = viewerContainer.x
        val savedY = viewerContainer.y

        sharedPref.edit().apply {
            putFloat("viewer_x", savedX)
            putFloat("viewer_y", savedY)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)
            putInt("key_width", currentKeyWidth)
            putInt("key_height", currentKeyHeight)
            putInt("key_spacing", currentKeySpacing)
            apply()
        }
        
        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetToDefault() {
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels

        // 1. Thiết lập lại các giá trị biến (giá trị thực)
        currentScale = 0.5f
        currentSpeed = 0.7f
        currentLimit = 280
        currentKeyWidth = 55
        currentKeyHeight = 60
        currentKeySpacing = 5

        // 2. Cập nhật thanh trượt
        seekPosX.progress = centerX
        seekPosY.progress = centerY
        seekScale.progress = (currentScale * 100).toInt()
        seekSpeed.progress = (currentSpeed * 100).toInt()
        seekLimit.progress = currentLimit - 70 
        seekKeyWidth.progress = currentKeyWidth - 30
        seekKeyHeight.progress = currentKeyHeight - 30
        seekKeySpacing.progress = currentKeySpacing
        
        // 3. Cập nhật UI Preview và Tọa độ View
        viewerContainer.x = 0f
        viewerContainer.y = 0f
        viewerContainer.scaleX = currentScale
        viewerContainer.scaleY = currentScale
        
        updateLivePreview()

        // 4. Lưu SharedPreferences
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putFloat("viewer_x", 0f)
            putFloat("viewer_y", 0f)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)
            putInt("key_width", currentKeyWidth)
            putInt("key_height", currentKeyHeight)
            putInt("key_spacing", currentKeySpacing)
            apply()
        }

        Toast.makeText(this, getString(R.string.toast_reset_default), Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.isAppVisible = true
        hideSystemUI()
        // Đảm bảo OverlayService được tắt khi quay lại màn hình này
        stopService(Intent(this, OverlayService::class.java))
    }

    override fun onPause() {
        super.onPause()
        AppState.isAppVisible = false
    }
}
