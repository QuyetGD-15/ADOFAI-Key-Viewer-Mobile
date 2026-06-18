package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class KeyViewerConfigActivity : AppCompatActivity() {

    private lateinit var viewerContainer: FrameLayout
    private lateinit var keysContainer: LinearLayout
    private lateinit var keyTrailView: KeyTrailView
    private lateinit var bottomCountersContainer: LinearLayout
    private lateinit var section1ContentLayout: LinearLayout
    private lateinit var section2ContentLayout: LinearLayout

    private lateinit var tvPosXLabel: TextView
    private lateinit var tvPosYLabel: TextView
    private lateinit var tvScaleLabel: TextView
    private lateinit var tvSpeedLabel: TextView
    private lateinit var tvLimitLabel: TextView
    private lateinit var tvKeyWidthLabel: TextView
    private lateinit var tvKeyHeightLabel: TextView
    private lateinit var tvKeySpacingLabel: TextView

    private lateinit var tvSection1Header: TextView
    private lateinit var tvSection2Header: TextView
    private lateinit var tvThemeHeader: TextView

    private lateinit var seekPosX: SeekBar
    private lateinit var seekPosY: SeekBar
    private lateinit var seekScale: SeekBar
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekLimit: SeekBar
    private lateinit var seekKeyWidth: SeekBar
    private lateinit var seekKeyHeight: SeekBar
    private lateinit var seekKeySpacing: SeekBar

    private lateinit var layoutThemeContent: LinearLayout
    private lateinit var cbBold: CheckBox
    private lateinit var cbItalic: CheckBox
    private lateinit var cbUnderline: CheckBox
    private lateinit var tvThemeTextSizeLabel: TextView
    private lateinit var seekThemeTextSize: SeekBar
    private lateinit var etTextColorHex: EditText
    private lateinit var etTextColorPressedHex: EditText
    private lateinit var etBgNormalHex: EditText
    private lateinit var etBgPressedHex: EditText
    private lateinit var etBorderNormalHex: EditText
    private lateinit var etBorderPressedHex: EditText
    private lateinit var etRainColorHex: EditText
    private lateinit var etRainShadowHex: EditText

    private lateinit var viewTextColorPreview: View
    private lateinit var viewTextColorPressedPreview: View
    private lateinit var viewBgNormalPreview: View
    private lateinit var viewBgPressedPreview: View
    private lateinit var viewBorderNormalPreview: View
    private lateinit var viewBorderPressedPreview: View
    private lateinit var viewRainColorPreview: View
    private lateinit var viewRainShadowPreview: View

    private var currentScale = 1.0f
    private var currentSpeed = 1.0f
    private var currentLimit = 200 // Mặc định 200dp
    private var currentKeyWidth = 60 // Mặc định 60dp
    private var currentKeyHeight = 60 // Mặc định 60dp
    private var currentKeySpacing = 0

    private var lastX = 0f
    private var lastY = 0f

    private var currentSelectedPresetIndex = 0
    private val presetNames by lazy {
        arrayOf(
            getString(R.string.preset_default),
            getString(R.string.preset_jipper),
            getString(R.string.preset_custom_1),
            getString(R.string.preset_custom_2),
            getString(R.string.preset_custom_3)
        )
    }
    private var isUserInteractingWithSpinner = false

    override fun onUserInteraction() {
        super.onUserInteraction()
        isUserInteractingWithSpinner = true
    }

    private fun getAdofaiBaseFont(): android.graphics.Typeface {
        return try {
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.adofai_font) ?: android.graphics.Typeface.DEFAULT
        } catch (e: Exception) {
            android.graphics.Typeface.DEFAULT
        }
    }

    // Thêm biến gốc để tính toán scale chữ cho chuẩn với OverlayService
    private val baseTextSize = 20f

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

        layoutThemeContent = findViewById(R.id.layoutThemeContent)
        tvThemeHeader = findViewById(R.id.tvThemeHeader)
        cbBold = findViewById(R.id.cbBold)
        cbItalic = findViewById(R.id.cbItalic)
        cbUnderline = findViewById(R.id.cbUnderline)
        tvThemeTextSizeLabel = findViewById(R.id.tvThemeTextSizeLabel)
        seekThemeTextSize = findViewById(R.id.seekThemeTextSize)
        etTextColorHex = findViewById(R.id.etTextColorHex)
        etTextColorPressedHex = findViewById(R.id.etTextColorPressedHex)
        etBgNormalHex = findViewById(R.id.etBgNormalHex)
        etBgPressedHex = findViewById(R.id.etBgPressedHex)
        etBorderNormalHex = findViewById(R.id.etBorderNormalHex)
        etBorderPressedHex = findViewById(R.id.etBorderPressedHex)
        etRainColorHex = findViewById(R.id.etRainColorHex)
        etRainShadowHex = findViewById(R.id.etRainShadowHex)

        viewTextColorPreview = findViewById(R.id.viewTextColorPreview)
        viewTextColorPressedPreview = findViewById(R.id.viewTextColorPressedPreview)
        viewBgNormalPreview = findViewById(R.id.viewBgNormalPreview)
        viewBgPressedPreview = findViewById(R.id.viewBgPressedPreview)
        viewBorderNormalPreview = findViewById(R.id.viewBorderNormalPreview)
        viewBorderPressedPreview = findViewById(R.id.viewBorderPressedPreview)
        viewRainColorPreview = findViewById(R.id.viewRainColorPreview)
        viewRainShadowPreview = findViewById(R.id.viewRainShadowPreview)

        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        tvLimitLabel = findViewById(R.id.tvLimitLabel)
        tvKeyWidthLabel = findViewById(R.id.tvKeyWidthLabel)
        tvKeyHeightLabel = findViewById(R.id.tvKeyHeightLabel)
        tvKeySpacingLabel = findViewById(R.id.tvKeySpacingLabel)

        tvSection1Header = findViewById(R.id.tvSection1Header)
        section1ContentLayout = findViewById(R.id.section1ContentLayout)
        tvSection2Header = findViewById(R.id.tvSection2Header)
        section2ContentLayout = findViewById(R.id.section2ContentLayout)

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

        seekKeyHeight.max = 70
        seekKeySpacing.max = 20

        // Tâm scale ở góc trên trái (Pivot 0,0)
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        // Áp dụng Accordion cho Mục 1, Mục 2 và Mục 3
        setupCollapsibleSection(tvSection1Header, section1ContentLayout, defaultExpanded = false)
        setupCollapsibleSection(tvSection2Header, section2ContentLayout, defaultExpanded = false)
        setupCollapsibleSection(tvThemeHeader, layoutThemeContent, defaultExpanded = false)

        setupPresetUI()
    }

    private fun setupPresetUI() {
        val presetRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 20)
        }

        // 1. Khởi tạo Spinner xổ ra 5 mẫu
        val presetSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val adapter = ArrayAdapter(this@KeyViewerConfigActivity, android.R.layout.simple_spinner_item, presetNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
        }

        // 2. Khởi tạo nút Lưu bên cạnh
        val btnSavePreset = Button(this).apply {
            text = getString(R.string.save_preset)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 20
            }
        }

        presetRowLayout.addView(presetSpinner)
        presetRowLayout.addView(btnSavePreset)

        // Thêm hàng này vào Layout tổng của phần Theme
        layoutThemeContent.addView(presetRowLayout, 0)

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        // Lấy lại vị trí Mẫu chủ đề cuối cùng người dùng đã chọn (Mặc định là 0)
        val savedPresetIndex = pref.getInt("saved_preset_index", 0)
        // Gán lại vị trí cho Spinner MÀ KHÔNG kích hoạt animation
        presetSpinner.setSelection(savedPresetIndex, false)

        // Xử lý sự kiện khi chọn mẫu
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isUserInteractingWithSpinner) {
                    return // Từ chối thực thi nếu không phải người dùng tự tay vuốt chạm
                }
                
                currentSelectedPresetIndex = position
                val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
                
                if (position == 0) {
                    // MẪU 0: MẶC ĐỊNH
                    applyValuesToUIControls(
                        textColorNormal = "#FFFFFF",
                        textColorPressed = "#FF000000",
                        bgNormal = "#000000",
                        bgPressed = "#FFFFFF",
                        borderNormal = "#FFFFFF",
                        borderPressed = "#FFFFFF",
                        rainColor = "#FFFFFF",
                        rainShadow = "#FF000000",
                        textSize = 20f,
                        isBold = false,
                        isItalic = false,
                        isUnderline = false
                    )
                } else if (position == 1) {
                    // MẪU 1: JIPPER
                    applyValuesToUIControls(
                        textColorNormal = "#FFFFFF",
                        textColorPressed = "#FF000000",
                        bgNormal = "#4D903CFF",
                        bgPressed = "#FFFFFFFF",
                        borderNormal = "#FF8C3EFF",
                        borderPressed = "#FFFFFF",
                        rainColor = "#FF8C3EFF",
                        rainShadow = "#FF000000",
                        textSize = 20f,
                        isBold = false,
                        isItalic = false,
                        isUnderline = false
                    )
                } else {
                    // MẪU 2, 3, 4 (TÙY CHỈNH): Đọc từ SharedPreferences. 
                    // CHÚ Ý: Giá trị mặc định (Fallback) được gán giống hệt MẪU MẶC ĐỊNH ở trên.
                    val suffix = "_preset_$position"
                    val textColorNormal = pref.getString("theme_text_color$suffix", "#FFFFFF") ?: "#FFFFFF"
                    val textColorPressed = pref.getString("theme_text_color_pressed$suffix", "#FF000000") ?: "#FF000000"
                    val bgNormal = pref.getString("theme_bg_normal$suffix", "#000000") ?: "#000000"
                    val bgPressed = pref.getString("theme_bg_pressed$suffix", "#FFFFFF") ?: "#FFFFFF"
                    val borderNormal = pref.getString("theme_border_normal$suffix", "#FFFFFF") ?: "#FFFFFF"
                    val borderPressed = pref.getString("theme_border_pressed$suffix", "#FFFFFF") ?: "#FFFFFF"
                    val rainColor = pref.getString("theme_rain_color$suffix", "#FFFFFF") ?: "#FFFFFF"
                    val rainShadow = pref.getString("theme_rain_shadow$suffix", "#FF000000") ?: "#FF000000"
                    
                    val textSize = try { pref.getFloat("theme_text_size$suffix", 20f) } catch (e: Exception) {
                        try { pref.getInt("theme_text_size$suffix", 20).toFloat() } catch (e2: Exception) { 20f }
                    }

                    val isBold = pref.getBoolean("theme_text_bold$suffix", false)
                    val isItalic = pref.getBoolean("theme_text_italic$suffix", false)
                    val isUnderline = pref.getBoolean("theme_text_underline$suffix", false)

                    applyValuesToUIControls(
                        textColorNormal, textColorPressed, bgNormal, bgPressed, 
                        borderNormal, borderPressed, rainColor, rainShadow, 
                        textSize, isBold, isItalic, isUnderline
                    )
                }

                // Lưu lại vị trí Mẫu chủ đề cuối cùng người dùng đã chọn
                val editor = pref.edit()
                editor.putInt("saved_preset_index", position)
                editor.apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Xử lý sự kiện nút Lưu để ghi đè
        btnSavePreset.setOnClickListener {
            if (currentSelectedPresetIndex < 2) {
                Toast.makeText(this, getString(R.string.toast_cannot_overwrite_system), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            val editor = pref.edit()
            val suffix = "_preset_$currentSelectedPresetIndex"
            
            val textColor = etTextColorHex.text.toString()
            val textColorPressed = etTextColorPressedHex.text.toString()
            val bgNormal = etBgNormalHex.text.toString()
            val borderNormal = etBorderNormalHex.text.toString()
            val bgPressed = etBgPressedHex.text.toString()
            val borderPressed = etBorderPressedHex.text.toString()
            val rainColor = etRainColorHex.text.toString()
            val rainShadow = etRainShadowHex.text.toString()
            val textSize = (seekThemeTextSize.progress + 10).toFloat()

            // Save to Preset Slot
            editor.putString("theme_text_color$suffix", textColor)
            editor.putString("theme_text_color_pressed$suffix", textColorPressed)
            editor.putString("theme_bg_normal$suffix", bgNormal)
            editor.putString("theme_border_normal$suffix", borderNormal)
            editor.putString("theme_bg_pressed$suffix", bgPressed)
            editor.putString("theme_border_pressed$suffix", borderPressed)
            editor.putString("theme_rain_color$suffix", rainColor)
            editor.putString("theme_rain_shadow$suffix", rainShadow)
            editor.putFloat("theme_text_size$suffix", textSize)
            editor.putBoolean("theme_text_bold$suffix", cbBold.isChecked)
            editor.putBoolean("theme_text_italic$suffix", cbItalic.isChecked)
            editor.putBoolean("theme_text_underline$suffix", cbUnderline.isChecked)
            
            // Apply to Main Config
            editor.putString("theme_text_color", textColor)
            editor.putString("theme_text_color_pressed", textColorPressed)
            editor.putString("theme_bg_normal", bgNormal)
            editor.putString("theme_border_normal", borderNormal)
            editor.putString("theme_bg_pressed", bgPressed)
            editor.putString("theme_border_pressed", borderPressed)
            editor.putString("theme_rain_color", rainColor)
            editor.putString("theme_rain_shadow", rainShadow)
            editor.putFloat("theme_text_size", textSize)
            editor.putBoolean("theme_text_bold", cbBold.isChecked)
            editor.putBoolean("theme_text_italic", cbItalic.isChecked)
            editor.putBoolean("theme_text_underline", cbUnderline.isChecked)
            
            editor.apply()
            Toast.makeText(this, getString(R.string.toast_preset_saved, presetNames[currentSelectedPresetIndex]), Toast.LENGTH_SHORT).show()
            
            triggerOverlayRefresh()
        }
    }

    private fun setupCollapsibleSection(headerTitle: android.widget.TextView, contentLayout: android.view.View, defaultExpanded: Boolean = false) {
        // Lọc bỏ tam giác cũ nếu có để tránh bị lặp ký tự
        val originalText = headerTitle.text.toString().replace("▼ ", "").replace("▶ ", "").trim()
        
        val updateUI = { isExpanded: Boolean ->
            contentLayout.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            headerTitle.text = if (isExpanded) "▼ $originalText" else "▶ $originalText"
        }

        var isExpanded = defaultExpanded
        updateUI(isExpanded) // Áp dụng trạng thái ban đầu

        // Mở rộng vùng bấm (Click area) cho thân thiện với ngón tay
        headerTitle.setPadding(0, 20, 0, 20)
        
        headerTitle.setOnClickListener {
            isExpanded = !isExpanded
            updateUI(isExpanded)
        }
    }

    private fun applyValuesToUIControls(
        textColorNormal: String,
        textColorPressed: String,
        bgNormal: String,
        bgPressed: String,
        borderNormal: String,
        borderPressed: String,
        rainColor: String,
        rainShadow: String,
        textSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean
    ) {
        etTextColorHex.setText(textColorNormal)
        etTextColorPressedHex.setText(textColorPressed)
        etBgNormalHex.setText(bgNormal)
        etBgPressedHex.setText(bgPressed)
        etBorderNormalHex.setText(borderNormal)
        etBorderPressedHex.setText(borderPressed)
        etRainColorHex.setText(rainColor)
        etRainShadowHex.setText(rainShadow)
        
        seekThemeTextSize.progress = (textSize - 10).toInt().coerceAtLeast(0)
        cbBold.isChecked = isBold
        cbItalic.isChecked = isItalic
        cbUnderline.isChecked = isUnderline
        
        updateLivePreview()
    }

    private fun triggerOverlayRefresh() {
        sendBroadcast(Intent("com.quyetgd.keyvieweroverlay.UPDATE_OVERLAY_CONFIG"))
    }

    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels

        // GIỮ NGUYÊN BẢN CÁC GIÁ TRI CỦA BẠN
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

        // Load THEME settings
        cbBold.isChecked = sharedPref.getBoolean("theme_text_bold", false)
        cbItalic.isChecked = sharedPref.getBoolean("theme_text_italic", false)
        cbUnderline.isChecked = sharedPref.getBoolean("theme_text_underline", false)
        
        val textSize = try { sharedPref.getFloat("theme_text_size", 20f) } catch (e: Exception) {
            try { sharedPref.getInt("theme_text_size", 20).toFloat() } catch (e2: Exception) { 20f }
        }
        seekThemeTextSize.progress = textSize.toInt() - 10
        
        val textColor = sharedPref.getString("theme_text_color", "#FFFFFF") ?: "#FFFFFF"
        val textColorPressed = sharedPref.getString("theme_text_color_pressed", "#FFFFFF") ?: "#FFFFFF"
        val bgNormal = sharedPref.getString("theme_bg_normal", "#000000") ?: "#000000"
        val bgPressed = sharedPref.getString("theme_bg_pressed", "#44FFFFFF") ?: "#44FFFFFF"
        val borderNormal = sharedPref.getString("theme_border_normal", "#FFFFFF") ?: "#FFFFFF"
        val borderPressed = sharedPref.getString("theme_border_pressed", "#FFEB3B") ?: "#FFEB3B"
        val rainColor = sharedPref.getString("theme_rain_color", "#FFFFFF") ?: "#FFFFFF"
        val rainShadow = sharedPref.getString("theme_rain_shadow", "#00FFFF") ?: "#00FFFF"

        etTextColorHex.setText(textColor)
        etTextColorPressedHex.setText(textColorPressed)
        etBgNormalHex.setText(bgNormal)
        etBgPressedHex.setText(bgPressed)
        etBorderNormalHex.setText(borderNormal)
        etBorderPressedHex.setText(borderPressed)
        etRainColorHex.setText(rainColor)
        etRainShadowHex.setText(rainShadow)

        syncColorPreview(etTextColorHex, viewTextColorPreview)
        syncColorPreview(etTextColorPressedHex, viewTextColorPressedPreview)
        syncColorPreview(etBgNormalHex, viewBgNormalPreview)
        syncColorPreview(etBgPressedHex, viewBgPressedPreview)
        syncColorPreview(etBorderNormalHex, viewBorderNormalPreview)
        syncColorPreview(etBorderPressedHex, viewBorderPressedPreview)
        syncColorPreview(etRainColorHex, viewRainColorPreview)
        syncColorPreview(etRainShadowHex, viewRainShadowPreview)
    }

    private fun syncColorPreview(et: EditText, preview: View) {
        try {
            preview.setBackgroundColor(Color.parseColor(et.text.toString()))
        } catch (e: Exception) {}
    }

    private fun setupListeners() {
        // Color Picker dialog listeners
        val colorClick = View.OnClickListener { v ->
            val et = when (v.id) {
                R.id.viewTextColorPreview -> etTextColorHex
                R.id.viewTextColorPressedPreview -> etTextColorPressedHex
                R.id.viewBgNormalPreview -> etBgNormalHex
                R.id.viewBgPressedPreview -> etBgPressedHex
                R.id.viewBorderNormalPreview -> etBorderNormalHex
                R.id.viewBorderPressedPreview -> etBorderPressedHex
                R.id.viewRainColorPreview -> etRainColorHex
                R.id.viewRainShadowPreview -> etRainShadowHex
                else -> return@OnClickListener
            }
            var currentColor = Color.WHITE
            try { currentColor = Color.parseColor(et.text.toString()) } catch (e: Exception) {}
            
            showColorPickerDialog(currentColor) { selectedColor ->
                val hex = String.format("#%08X", (0xFFFFFFFF and selectedColor.toLong()))
                et.setText(hex)
                v.setBackgroundColor(selectedColor)
                updateLivePreview()
            }
        }

        viewTextColorPreview.setOnClickListener(colorClick)
        viewTextColorPressedPreview.setOnClickListener(colorClick)
        viewBgNormalPreview.setOnClickListener(colorClick)
        viewBgPressedPreview.setOnClickListener(colorClick)
        viewBorderNormalPreview.setOnClickListener(colorClick)
        viewBorderPressedPreview.setOnClickListener(colorClick)
        viewRainColorPreview.setOnClickListener(colorClick)
        viewRainShadowPreview.setOnClickListener(colorClick)

        // TextWatcher for HEX EditTexts
        val themeTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                syncColorPreview(etTextColorHex, viewTextColorPreview)
                syncColorPreview(etTextColorPressedHex, viewTextColorPressedPreview)
                syncColorPreview(etBgNormalHex, viewBgNormalPreview)
                syncColorPreview(etBgPressedHex, viewBgPressedPreview)
                syncColorPreview(etBorderNormalHex, viewBorderNormalPreview)
                syncColorPreview(etBorderPressedHex, viewBorderPressedPreview)
                syncColorPreview(etRainColorHex, viewRainColorPreview)
                syncColorPreview(etRainShadowHex, viewRainShadowPreview)
                updateLivePreview()
            }
        }
        etTextColorHex.addTextChangedListener(themeTextWatcher)
        etTextColorPressedHex.addTextChangedListener(themeTextWatcher)
        etBgNormalHex.addTextChangedListener(themeTextWatcher)
        etBgPressedHex.addTextChangedListener(themeTextWatcher)
        etBorderNormalHex.addTextChangedListener(themeTextWatcher)
        etBorderPressedHex.addTextChangedListener(themeTextWatcher)
        etRainColorHex.addTextChangedListener(themeTextWatcher)
        etRainShadowHex.addTextChangedListener(themeTextWatcher)

        // CheckBoxes and SeekBar
        cbBold.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbItalic.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbUnderline.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        seekThemeTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThemeTextSizeLabel.text = getString(R.string.theme_text_size, progress + 10)
                if (fromUser) updateLivePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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
        val widthPx = (currentKeyWidth * resources.displayMetrics.density).toInt()
        val heightPx = (currentKeyHeight * resources.displayMetrics.density).toInt()
        val spacingPx = (currentKeySpacing * resources.displayMetrics.density).toInt()
        val limitPx = (currentLimit * resources.displayMetrics.density).toInt()

        // Get Theme values
        val isBold = cbBold.isChecked
        val isItalic = cbItalic.isChecked
        val isUnderline = cbUnderline.isChecked
        val textSize = seekThemeTextSize.progress + 10
        val textColorHex = etTextColorHex.text.toString()
        val bgNormalHex = etBgNormalHex.text.toString()
        val borderNormalHex = etBorderNormalHex.text.toString()
        val rainColorHex = etRainColorHex.text.toString()
        val rainShadowHex = etRainShadowHex.text.toString()

        val typeface = when {
            isBold && isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD_ITALIC)
            isBold -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD)
            isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.ITALIC)
            else -> getAdofaiBaseFont()
        }

        var textColor = Color.WHITE
        var bgNormal = Color.BLACK
        var borderNormal = Color.WHITE
        var rainColor = Color.WHITE
        var rainShadow = Color.CYAN

        try { textColor = Color.parseColor(textColorHex) } catch (e: Exception) {}
        try { bgNormal = Color.parseColor(bgNormalHex) } catch (e: Exception) {}
        try { borderNormal = Color.parseColor(borderNormalHex) } catch (e: Exception) {}
        try { rainColor = Color.parseColor(rainColorHex) } catch (e: Exception) {}
        try { rainShadow = Color.parseColor(rainShadowHex) } catch (e: Exception) {}

        val normalDrawable = createBoxDrawable(bgNormal, borderNormal, 5)

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

            if (keyView is TextView) {
                keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                keyView.setTextColor(textColor)
                keyView.typeface = typeface
                if (isUnderline) {
                    keyView.paintFlags = keyView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                } else {
                    keyView.paintFlags = keyView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
                keyView.background = normalDrawable.constantState?.newDrawable()?.mutate() ?: normalDrawable
            }
        }

        // Spacing cho bộ đếm
        val counterParams = bottomCountersContainer.layoutParams as ViewGroup.MarginLayoutParams
        counterParams.topMargin = spacingPx
        bottomCountersContainer.layoutParams = counterParams

        val localKpsContainer = viewerContainer.findViewById<View>(resources.getIdentifier("kpsContainer", "id", packageName))
        val localTotalContainer = viewerContainer.findViewById<View>(resources.getIdentifier("totalContainer", "id", packageName))
        val localKpsLabel = viewerContainer.findViewById<TextView>(resources.getIdentifier("tvKpsLabel", "id", packageName))
        val localKpsValue = viewerContainer.findViewById<TextView>(resources.getIdentifier("tvKpsValue", "id", packageName))
        val localTotalLabel = viewerContainer.findViewById<TextView>(resources.getIdentifier("tvTotalLabel", "id", packageName))
        val localTotalValue = viewerContainer.findViewById<TextView>(resources.getIdentifier("tvTotalValue", "id", packageName))
        
        var kpsLabelStr = "KPS"
        var totalLabelStr = "Total"
        try {
            kpsLabelStr = getString(R.string.kps_label)
            totalLabelStr = getString(R.string.total_label)
        } catch (e: Exception) {}

        if (localKpsContainer != null && localTotalContainer != null) {
            localKpsLabel?.text = kpsLabelStr
            localKpsValue?.text = "0"
            localTotalLabel?.text = totalLabelStr
            localTotalValue?.text = "0"

            localKpsContainer.background = normalDrawable.constantState?.newDrawable()?.mutate() ?: normalDrawable
            localTotalContainer.background = normalDrawable.constantState?.newDrawable()?.mutate() ?: normalDrawable

            val kpsParams = localKpsContainer.layoutParams as ViewGroup.MarginLayoutParams
            kpsParams.rightMargin = spacingPx
            localKpsContainer.layoutParams = kpsParams

            val applyToTextView = { tv: TextView? ->
                if (tv != null) {
                    tv.setTextColor(textColor)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                    tv.typeface = typeface
                    if (isUnderline) {
                        tv.paintFlags = tv.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    } else {
                        tv.paintFlags = tv.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                    }
                }
            }
            applyToTextView(localKpsLabel)
            applyToTextView(localKpsValue)
            applyToTextView(localTotalLabel)
            applyToTextView(localTotalValue)
        }

        // Chiều cao RainKey
        val trailParams = keyTrailView.layoutParams
        trailParams.height = limitPx
        keyTrailView.layoutParams = trailParams

        // FIX ĐỒNG BỘ 2: Setup thông số cho nét vẽ Trail (Tốc độ rơi và chiều cao tối đa)
        keyTrailView.setParameters(currentSpeed, currentLimit.toFloat())
        keyTrailView.setThemeColors(rainColor, rainShadow)

        // Cố định Pivot 0,0
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        updateLabels()
    }

    private fun showColorPickerDialog(currentColor: Int, onColorSelected: (Int) -> Unit) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.color_picker_title))

        // Khung chứa gốc (Root Layout)
        val rootLayout = android.widget.LinearLayout(this)
        rootLayout.orientation = android.widget.LinearLayout.VERTICAL
        rootLayout.setPadding(60, 40, 60, 40)

        // Ô vuông xem trước màu (Cố định ở trên cùng)
        val previewBox = android.view.View(this)
        previewBox.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            150
        ).apply { bottomMargin = 40 }
        previewBox.setBackgroundColor(currentColor)
        rootLayout.addView(previewBox)

        // ScrollView để cuộn các thanh trượt
        val scrollView = android.widget.ScrollView(this)
        val scrollParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f // Ép ScrollView chiếm toàn bộ không gian còn lại
        )
        scrollView.layoutParams = scrollParams

        // Khung chứa các thanh trượt (Nằm trong ScrollView)
        val slidersLayout = android.widget.LinearLayout(this)
        slidersLayout.orientation = android.widget.LinearLayout.VERTICAL
        scrollView.addView(slidersLayout)

        rootLayout.addView(scrollView)

        var a = android.graphics.Color.alpha(currentColor)
        var r = android.graphics.Color.red(currentColor)
        var g = android.graphics.Color.green(currentColor)
        var b = android.graphics.Color.blue(currentColor)

        fun createRow(label: String, initialValue: Int, onChange: (Int) -> Unit) {
            val row = android.widget.LinearLayout(this)
            row.orientation = android.widget.LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, 15, 0, 15)
            
            val tvLabel = android.widget.TextView(this).apply { 
                text = label
                textSize = 16f
                width = 80
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val edtValue = android.widget.EditText(this).apply {
                setText(String.format(java.util.Locale.US, "%.2f", initialValue / 255f))
                textSize = 16f
                width = 150
                gravity = android.view.Gravity.CENTER
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }

            val seekBar = android.widget.SeekBar(this).apply {
                max = 255
                progress = initialValue
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            var isUpdating = false

            seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        isUpdating = true
                        edtValue.setText(String.format(java.util.Locale.US, "%.2f", progress / 255f))
                        edtValue.setSelection(edtValue.text.length)
                        isUpdating = false

                        onChange(progress)
                        previewBox.setBackgroundColor(android.graphics.Color.argb(a, r, g, b))
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })

            edtValue.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (isUpdating) return
                    val input = s.toString().toFloatOrNull()
                    if (input != null) {
                        val floatVal = input.coerceIn(0f, 1f)
                        val progress = (floatVal * 255).toInt()
                        
                        isUpdating = true
                        seekBar.progress = progress
                        isUpdating = false

                        onChange(progress)
                        previewBox.setBackgroundColor(android.graphics.Color.argb(a, r, g, b))
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            row.addView(tvLabel)
            row.addView(seekBar)
            row.addView(edtValue)
            // QUAN TRỌNG: Add vào slidersLayout (nằm trong ScrollView) thay vì layout gốc
            slidersLayout.addView(row)
        }

        createRow(getString(R.string.color_a), a) { a = it }
        createRow(getString(R.string.color_r), r) { r = it }
        createRow(getString(R.string.color_g), g) { g = it }
        createRow(getString(R.string.color_b), b) { b = it }

        builder.setView(rootLayout)
        builder.setPositiveButton(getString(R.string.color_ok)) { _, _ ->
            val finalColor = android.graphics.Color.argb(a, r, g, b)
            onColorSelected(finalColor)
        }
        builder.setNegativeButton(getString(R.string.color_cancel), null)
        builder.show()
    }

    private fun createBoxDrawable(bgColor: Int, borderColor: Int, strokeWidthPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(bgColor)
            setStroke(strokeWidthPx, borderColor)
        }
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

            // Save THEME settings
            putBoolean("theme_text_bold", cbBold.isChecked)
            putBoolean("theme_text_italic", cbItalic.isChecked)
            putBoolean("theme_text_underline", cbUnderline.isChecked)
            putFloat("theme_text_size", (seekThemeTextSize.progress + 10).toFloat())
            putString("theme_text_color", etTextColorHex.text.toString())
            putString("theme_text_color_pressed", etTextColorPressedHex.text.toString())
            putString("theme_bg_normal", etBgNormalHex.text.toString())
            putString("theme_bg_pressed", etBgPressedHex.text.toString())
            putString("theme_border_normal", etBorderNormalHex.text.toString())
            putString("theme_border_pressed", etBorderPressedHex.text.toString())
            putString("theme_rain_color", etRainColorHex.text.toString())
            putString("theme_rain_shadow", etRainShadowHex.text.toString())
            apply()
        }

        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetToDefault() {
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels

        // GIỮ NGUYÊN BẢN GIÁ TRỊ RESET CỦA BẠN
        currentScale = 0.5f
        currentSpeed = 0.7f
        currentLimit = 280
        currentKeyWidth = 55
        currentKeyHeight = 60
        currentKeySpacing = 5

        // Cập nhật thanh trượt
        seekPosX.progress = centerX
        seekPosY.progress = centerY
        seekScale.progress = (currentScale * 100).toInt()
        seekSpeed.progress = (currentSpeed * 100).toInt()
        seekLimit.progress = currentLimit - 70
        seekKeyWidth.progress = currentKeyWidth - 30
        seekKeyHeight.progress = currentKeyHeight - 30
        seekKeySpacing.progress = currentKeySpacing

        // Cập nhật UI Preview và Tọa độ View (Về 0f)
        viewerContainer.x = 0f
        viewerContainer.y = 0f
        viewerContainer.scaleX = currentScale
        viewerContainer.scaleY = currentScale

        updateLivePreview()

        // Lưu SharedPreferences (Lưu 0f)
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