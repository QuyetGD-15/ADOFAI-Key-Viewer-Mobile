package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
    private lateinit var tvBorderWidthLabel: TextView
    private lateinit var tvCornerRadiusLabel: TextView

    private lateinit var tvSection1Header: TextView
    private lateinit var tvSection2Header: TextView
    private lateinit var tvThemeHeader: TextView

    private lateinit var seekPosX: Slider
    private lateinit var seekPosY: Slider
    private lateinit var seekScale: Slider
    private lateinit var seekSpeed: Slider
    private lateinit var seekLimit: Slider
    private lateinit var seekKeyWidth: Slider
    private lateinit var seekKeyHeight: Slider
    private lateinit var seekKeySpacing: Slider
    private lateinit var seekBorderWidth: Slider
    private lateinit var seekCornerRadius: Slider

    private lateinit var swEnableKeyRain: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var layoutThemeContent: LinearLayout
    private lateinit var swShowKeyCounters: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var cbBold: CheckBox
    private lateinit var cbItalic: CheckBox
    private lateinit var cbUnderline: CheckBox
    private lateinit var tvThemeTextSizeLabel: TextView
    private lateinit var seekThemeTextSize: Slider
    private lateinit var etTextColorHex: EditText
    private lateinit var etTextColorPressedHex: EditText
    private lateinit var etBgNormalHex: EditText
    private lateinit var etBgPressedHex: EditText
    private lateinit var etBorderNormalHex: EditText
    private lateinit var etBorderPressedHex: EditText
    private lateinit var etRainColorHex: EditText
    private lateinit var etRainShadowHex: EditText

    private lateinit var viewTextColorPreview: MaterialCardView
    private lateinit var viewTextColorPressedPreview: MaterialCardView
    private lateinit var viewBgNormalPreview: MaterialCardView
    private lateinit var viewBgPressedPreview: MaterialCardView
    private lateinit var viewBorderNormalPreview: MaterialCardView
    private lateinit var viewBorderPressedPreview: MaterialCardView
    private lateinit var viewRainColorPreview: MaterialCardView
    private lateinit var viewRainShadowPreview: MaterialCardView

    private var currentScale = 1.0f
    private var currentSpeed = 1.0f
    private var currentLimit = 200 // Mặc định 200dp
    private var currentKeyWidth = 60 // Mặc định 60dp
    private var currentKeyHeight = 60 // Mặc định 60dp
    private var currentKeySpacing = 0

    private var currentX = 0f
    private var currentY = 0f

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
        initDefaultThemeOnFirstLaunch()
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

        // KIỂM TRA TỰ ĐỘNG NẠP MẶC ĐỊNH LẦN ĐẦU
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val isKeyViewerConfigured = pref.getBoolean("is_keyviewer_configured", false)

        if (!isKeyViewerConfigured) {
            // KHÓA CỜ NGAY LẬP TỨC trước khi gọi hàm Reset để tránh Activity Recreate gọi Reset 2 lần làm mất tọa độ.
            pref.edit().putBoolean("is_keyviewer_configured", true).apply()

            // Gọi hàm load mặc định (đặt view ra giữa màn hình)
            resetToDefault()
        } else {
            // Chỉ nạp tọa độ từ bộ nhớ nếu đã configured
            loadPreferences()
        }

        setupListeners()

        // Cố định Pivot 0,0 để đồng bộ hệ tọa độ
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        // Cập nhật giao diện ban đầu
        viewerContainer.post {
            renderKeyPreview()
            updateLivePreview()
        }
    }

    private fun renderKeyPreview() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val keyMode = pref.getInt("current_key_mode", 6)
        val showCounters = swShowKeyCounters.isChecked

        keysContainer.removeAllViews()
        for (i in 1..keyMode) {
            val container = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (currentKeyWidth * resources.displayMetrics.density).toInt(),
                    (currentKeyHeight * resources.displayMetrics.density).toInt()
                )
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                id = View.generateViewId()
            }

            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = i.toString()
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvCount = TextView(this).apply {
                val density = resources.displayMetrics.density
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4 * density).toInt()
                }
                gravity = Gravity.CENTER
                text = "0"
                maxLines = 1
                includeFontPadding = false
                visibility = if (showCounters) View.VISIBLE else View.GONE
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 6, 14, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }

            container.addView(tvLabel)
            container.addView(tvCount)
            keysContainer.addView(container)
        }
    }

    private fun initViews() {
        viewerContainer = findViewById(R.id.viewerContainer)
        keysContainer = findViewById(R.id.keysContainer)
        keyTrailView = findViewById(R.id.keyTrailView)
        bottomCountersContainer = findViewById(R.id.bottomCountersContainer)

        layoutThemeContent = findViewById(R.id.layoutThemeContent)
        swShowKeyCounters = findViewById(R.id.swShowKeyCounters)
        swEnableKeyRain = findViewById(R.id.swEnableKeyRain)
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
        tvBorderWidthLabel = findViewById(R.id.tvBorderWidthLabel)
        tvCornerRadiusLabel = findViewById(R.id.tvCornerRadiusLabel)

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
        seekBorderWidth = findViewById(R.id.seekBorderWidth)
        seekCornerRadius = findViewById(R.id.seekCornerRadius)

        // Thiết lập max cho Pos Sliders dựa trên màn hình (Dải gấp đôi để hỗ trợ âm/dương)
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels
        seekPosX.valueTo = (centerX * 2).toFloat()
        seekPosY.valueTo = (centerY * 2).toFloat()

        seekKeyHeight.valueTo = 70f
        seekKeySpacing.valueTo = 20f

        // Tâm scale ở góc trên trái (Pivot 0,0)
        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        // Áp dụng Accordion cho Mục 1, Mục 2 và Mục 3
        setupCollapsibleSection(tvSection1Header, tvSection1Header, section1ContentLayout, defaultExpanded = false)
        setupCollapsibleSection(tvSection2Header, tvSection2Header, section2ContentLayout, defaultExpanded = false)
        setupCollapsibleSection(tvThemeHeader, tvThemeHeader, layoutThemeContent, defaultExpanded = false)

        setupPresetUI()
    }

    private fun setupPresetUI() {
        val presetDropdown = findViewById<android.widget.AutoCompleteTextView>(R.id.presetDropdown)
        val btnSavePreset = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSavePreset)

        // 1. Khởi tạo Adapter cho AutoCompleteTextView (Material 3 Dropdown)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, presetNames)
        presetDropdown.setAdapter(adapter)

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        // Lấy lại vị trí Mẫu chủ đề cuối cùng người dùng đã chọn (Mặc định là 0)
        val savedPresetIndex = pref.getInt("saved_preset_index", 0)
        // Cập nhật vị trí hiển thị mặc định
        if (savedPresetIndex < presetNames.size) {
            presetDropdown.setText(adapter.getItem(savedPresetIndex), false)
        }

        // Xử lý sự kiện khi chọn mẫu
        presetDropdown.setOnItemClickListener { parent, view, position, id ->
            if (!isUserInteractingWithSpinner) {
                return@setOnItemClickListener // Từ chối thực thi nếu không phải người dùng tự tay vuốt chạm
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
                    bgNormal = "#338E3CFF",
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

            // Lưu lại chỉ số preset đang chọn
            pref.edit().putInt("saved_preset_index", position).apply()
        }

        // Xử lý sự kiện nút Lưu để ghi đè
        btnSavePreset.setOnClickListener {
            val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            val editor = pref.edit()

            // Tìm position hiện tại từ Text của Dropdown
            val currentText = presetDropdown.text.toString()
            val selectedPosition = presetNames.indexOf(currentText)

            if (selectedPosition < 2) {
                Toast.makeText(this, getString(R.string.toast_cannot_overwrite_system), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. ĐỌC TẤT CẢ GIÁ TRỊ HIỆN TẠI TRÊN GIAO DIỆN UI
            val textSize = (seekThemeTextSize.value + 10)
            val textColor = etTextColorHex.text.toString()
            val textColorPressed = etTextColorPressedHex.text.toString()
            val bgNormal = etBgNormalHex.text.toString()
            val borderNormal = etBorderNormalHex.text.toString()
            val bgPressed = etBgPressedHex.text.toString()
            val borderPressed = etBorderPressedHex.text.toString()
            val rainColor = etRainColorHex.text.toString()
            val rainShadow = etRainShadowHex.text.toString()
            val isBold = cbBold.isChecked
            val isItalic = cbItalic.isChecked
            val isUnderline = cbUnderline.isChecked

            // 2. LUÔN LUÔN LƯU VÀO CẤU HÌNH CHẠY CHÍNH (Để OverlayService đọc được ngay lập tức)
            editor.putFloat("theme_text_size", textSize)
            editor.putString("theme_text_color", textColor)
            editor.putString("theme_text_color_pressed", textColorPressed)
            editor.putString("theme_bg_normal", bgNormal)
            editor.putString("theme_bg_pressed", bgPressed)
            editor.putString("theme_border_normal", borderNormal)
            editor.putString("theme_border_pressed", borderPressed)
            editor.putString("theme_rain_color", rainColor)
            editor.putString("theme_rain_shadow", rainShadow)
            editor.putBoolean("theme_text_bold", isBold)
            editor.putBoolean("theme_text_italic", isItalic)
            editor.putBoolean("theme_text_underline", isUnderline)

            // 3. LOGIC GHI ĐÈ BẢO VỆ PRESET: Nếu vị trí đang chọn >= 2 (tức là Tùy chỉnh 1, 2, 3...) thì ghi đè vào Slot đó
            val suffix = "_preset_$selectedPosition"
            editor.putFloat("theme_text_size$suffix", textSize)
            editor.putString("theme_text_color$suffix", textColor)
            editor.putString("theme_text_color_pressed$suffix", textColorPressed)
            editor.putString("theme_bg_normal$suffix", bgNormal)
            editor.putString("theme_bg_pressed$suffix", bgPressed)
            editor.putString("theme_border_normal$suffix", borderNormal)
            editor.putString("theme_border_pressed$suffix", borderPressed)
            editor.putString("theme_rain_color$suffix", rainColor)
            editor.putString("theme_rain_shadow$suffix", rainShadow)
            editor.putBoolean("theme_text_bold$suffix", isBold)
            editor.putBoolean("theme_text_italic$suffix", isItalic)
            editor.putBoolean("theme_text_underline$suffix", isUnderline)

            // Lưu lại chỉ số preset đang chọn để không bị giật lùi giao diện
            editor.putInt("saved_preset_index", selectedPosition)
            editor.apply()

            // 4. BẮN BROADCAST ĐỂ OVERLAY CẬP NHẬT NGAY LẬP TỨC
            triggerOverlayRefresh()

            Toast.makeText(this, getString(R.string.toast_preset_saved, currentText), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCollapsibleSection(headerTitle: android.widget.TextView, clickableView: android.view.View, contentLayout: android.view.View, defaultExpanded: Boolean = false) {
        // Lọc bỏ tam giác cũ nếu có để tránh bị lặp ký tự
        val originalText = headerTitle.text.toString().replace("▼ ", "").replace("▶ ", "").trim()
        
        val updateUI = { isExpanded: Boolean ->
            contentLayout.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            headerTitle.text = if (isExpanded) "▼ $originalText" else "▶ $originalText"
        }

        var isExpanded = defaultExpanded
        updateUI(isExpanded) // Áp dụng trạng thái ban đầu

        clickableView.setOnClickListener {
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

        seekThemeTextSize.value = (textSize - 10).coerceIn(seekThemeTextSize.valueFrom, seekThemeTextSize.valueTo)
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
        currentSpeed = sharedPref.getFloat("trail_speed", 0.8f)
        currentLimit = sharedPref.getInt("trail_limit_px", 200)
        currentKeyWidth = sharedPref.getInt("key_width", 60)
        currentKeyHeight = sharedPref.getInt("key_height", 60)
        currentKeySpacing = sharedPref.getInt("key_spacing", 7)

        currentX = x
        currentY = y

        viewerContainer.x = x
        viewerContainer.y = y
        viewerContainer.scaleX = currentScale
        viewerContainer.scaleY = currentScale

        seekPosX.value = (x + centerX).coerceIn(seekPosX.valueFrom, seekPosX.valueTo)
        seekPosY.value = (y + centerY).coerceIn(seekPosY.valueFrom, seekPosY.valueTo)
        seekScale.value = (currentScale * 100).coerceIn(seekScale.valueFrom, seekScale.valueTo)
        seekSpeed.value = (currentSpeed * 100).coerceIn(seekSpeed.valueFrom, seekSpeed.valueTo)
        seekLimit.value = (currentLimit - 70).toFloat().coerceIn(seekLimit.valueFrom, seekLimit.valueTo)
        seekKeyWidth.value = (currentKeyWidth - 30).toFloat().coerceIn(seekKeyWidth.valueFrom, seekKeyWidth.valueTo)
        seekKeyHeight.value = (currentKeyHeight - 30).toFloat().coerceIn(seekKeyHeight.valueFrom, seekKeyHeight.valueTo)
        seekKeySpacing.value = currentKeySpacing.toFloat().coerceIn(seekKeySpacing.valueFrom, seekKeySpacing.valueTo)
        seekBorderWidth.value = sharedPref.getInt("theme_border_width", 2).toFloat().coerceIn(seekBorderWidth.valueFrom, seekBorderWidth.valueTo)
        seekCornerRadius.value = sharedPref.getInt("theme_corner_radius", 6).toFloat().coerceIn(seekCornerRadius.valueFrom, seekCornerRadius.valueTo)
        swEnableKeyRain.isChecked = sharedPref.getBoolean("theme_keyrain_enabled", true)
        keyTrailView.visibility = if (swEnableKeyRain.isChecked) View.VISIBLE else View.GONE

        // Load THEME settings
        swShowKeyCounters.isChecked = sharedPref.getBoolean("show_key_counters", false)
        cbBold.isChecked = sharedPref.getBoolean("theme_text_bold", false)
        cbItalic.isChecked = sharedPref.getBoolean("theme_text_italic", false)
        cbUnderline.isChecked = sharedPref.getBoolean("theme_text_underline", false)

        val textSize = try { sharedPref.getFloat("theme_text_size", 20f) } catch (e: Exception) {
            try { sharedPref.getInt("theme_text_size", 20).toFloat() } catch (e2: Exception) { 20f }
        }
        seekThemeTextSize.value = (textSize - 10).coerceIn(seekThemeTextSize.valueFrom, seekThemeTextSize.valueTo)

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

    private fun syncColorPreview(et: EditText, preview: MaterialCardView) {
        try {
            preview.setCardBackgroundColor(Color.parseColor(et.text.toString()))
        } catch (e: Exception) {}
    }

    private fun setupListeners() {
        // Color Picker dialog listeners
        val colorClick = View.OnClickListener { v ->
            val et: EditText
            val title: String
            when (v.id) {
                R.id.viewTextColorPreview -> { et = etTextColorHex; title = getString(R.string.theme_color_text_normal) }
                R.id.viewTextColorPressedPreview -> { et = etTextColorPressedHex; title = getString(R.string.theme_color_text_pressed) }
                R.id.viewBgNormalPreview -> { et = etBgNormalHex; title = getString(R.string.theme_color_bg_normal) }
                R.id.viewBgPressedPreview -> { et = etBgPressedHex; title = getString(R.string.theme_color_bg_pressed) }
                R.id.viewBorderNormalPreview -> { et = etBorderNormalHex; title = getString(R.string.theme_color_border_normal) }
                R.id.viewBorderPressedPreview -> { et = etBorderPressedHex; title = getString(R.string.theme_color_border_pressed) }
                R.id.viewRainColorPreview -> { et = etRainColorHex; title = getString(R.string.theme_color_rain) }
                R.id.viewRainShadowPreview -> { et = etRainShadowHex; title = getString(R.string.theme_color_rain_shadow) }
                else -> return@OnClickListener
            }
            var currentColor = Color.WHITE
            try { currentColor = Color.parseColor(et.text.toString()) } catch (e: Exception) {}

            showColorPickerDialog(title, currentColor) { selectedColor ->
                val hex = String.format("#%08X", (0xFFFFFFFF and selectedColor.toLong()))
                et.setText(hex)
                if (v is MaterialCardView) {
                    v.setCardBackgroundColor(selectedColor)
                }
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

        swEnableKeyRain.setOnCheckedChangeListener { _, isChecked ->
            keyTrailView.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateLivePreview()
        }

        // CheckBoxes and SeekBar
        swShowKeyCounters.setOnCheckedChangeListener { _, _ ->
            renderKeyPreview()
            updateLivePreview()
        }
        cbBold.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbItalic.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbUnderline.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        seekThemeTextSize.addOnChangeListener { _, value, fromUser ->
            tvThemeTextSizeLabel.text = getString(R.string.theme_text_size, value.toInt() + 10)
            if (fromUser) updateLivePreview()
        }

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

                    currentX = view.x
                    currentY = view.y

                    // Cập nhật ngược lại Slider (Cộng thêm Center Offset)
                    val dm = resources.displayMetrics
                    seekPosX.value = (view.x.toInt() + dm.widthPixels).toFloat().coerceIn(seekPosX.valueFrom, seekPosX.valueTo)
                    seekPosY.value = (view.y.toInt() + dm.heightPixels).toFloat().coerceIn(seekPosY.valueFrom, seekPosY.valueTo)
                    updateLabels()
                    true
                }
                else -> false
            }
        }

        // Slider Limit
        seekLimit.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentLimit = value.toInt() + 70
            updateLivePreview()
        }

        // Các Slider khác
        seekScale.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentScale = value / 100f
            if (currentScale < 0.1f) currentScale = 0.1f
            viewerContainer.scaleX = currentScale
            viewerContainer.scaleY = currentScale
            updateLivePreview()
        }
        seekSpeed.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentSpeed = value / 100f
            updateLivePreview()
        }
        seekKeyWidth.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentKeyWidth = value.toInt() + 30
            updateLivePreview()
        }
        seekKeyHeight.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentKeyHeight = value.toInt() + 30
            updateLivePreview()
        }
        seekKeySpacing.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentKeySpacing = value.toInt()
            updateLivePreview()
        }
        seekBorderWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateLivePreview()
        }
        seekCornerRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateLivePreview()
        }

        // Listeners cho PosX và PosY
        seekPosX.addOnChangeListener { _, value, fromUser ->
            val dm = resources.displayMetrics
            val centerX = dm.widthPixels
            val actualX = value - centerX
            if (fromUser) {
                viewerContainer.x = actualX
                currentX = actualX
            }
            updateLabels()
        }
        seekPosY.addOnChangeListener { _, value, fromUser ->
            val dm = resources.displayMetrics
            val centerY = dm.heightPixels
            val actualY = value - centerY
            if (fromUser) {
                viewerContainer.y = actualY
                currentY = actualY
            }
            updateLabels()
        }

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
        // --- ĐỒNG BỘ SỐ HIỂN TRÊN BONG BÓNG (TOOLTIP) CỦA SLIDER ---
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels

        val floatFormatter = object : com.google.android.material.slider.LabelFormatter {
            override fun getFormattedValue(value: Float): String {
                return String.format(java.util.Locale.US, "%.1f", value / 10f)
            }
        }

        val intFormatter = object : com.google.android.material.slider.LabelFormatter {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }

        seekScale.setLabelFormatter(floatFormatter)
        seekSpeed.setLabelFormatter(floatFormatter)

        seekPosX.setLabelFormatter(intFormatter)
        seekPosY.setLabelFormatter(intFormatter)
        seekLimit.setLabelFormatter(intFormatter)
        seekKeyWidth.setLabelFormatter(intFormatter)
        seekKeyHeight.setLabelFormatter(intFormatter)
        seekKeySpacing.setLabelFormatter(intFormatter)
        seekThemeTextSize.setLabelFormatter(intFormatter)

        // Thêm formatter cho 2 thanh trượt mới nếu Agent đã tạo
        try {
            findViewById<com.google.android.material.slider.Slider>(R.id.seekBorderWidth)?.setLabelFormatter(intFormatter)
            findViewById<com.google.android.material.slider.Slider>(R.id.seekCornerRadius)?.setLabelFormatter(intFormatter)
        } catch (e: Exception) {}

        // 1. Tọa độ (Trừ đi tâm màn hình)
        seekPosX.setLabelFormatter { value -> (value - centerX).toInt().toString() }
        seekPosY.setLabelFormatter { value -> (value - centerY).toInt().toString() }

        // 2. Tỷ lệ & Tốc độ (Chia 100 để ra số thập phân)
        seekScale.setLabelFormatter { value ->
            String.format(java.util.Locale.US, "%.2f", value / 100f)
        }
        seekSpeed.setLabelFormatter { value ->
            String.format(java.util.Locale.US, "%.2f", value / 100f)
        }

        // 3. Kích thước (Cộng thêm offset như cấu hình gốc)
        seekLimit.setLabelFormatter { value -> (value + 70).toInt().toString() }
        seekKeyWidth.setLabelFormatter { value -> (value + 30).toInt().toString() }
        seekKeyHeight.setLabelFormatter { value -> (value + 30).toInt().toString() }

        // 4. Khoảng cách (Giữ nguyên)
        seekKeySpacing.setLabelFormatter(intFormatter)
        seekBorderWidth.setLabelFormatter(intFormatter)
        seekCornerRadius.setLabelFormatter(intFormatter)

        // 5. Cỡ chữ Theme (Cộng thêm 10 offset)
        seekThemeTextSize.setLabelFormatter { value -> (value + 10).toInt().toString() }
    }

    private fun updateLivePreview() {
        val density = resources.displayMetrics.density
        val widthPx = (currentKeyWidth * density).toInt()
        val heightPx = (currentKeyHeight * density).toInt()
        val spacingPx = (currentKeySpacing * density).toInt()
        val limitPx = (currentLimit * density).toInt()
        val borderPx = (seekBorderWidth.value.toInt() * density).toInt()
        val radiusPx = seekCornerRadius.value * density

        val isBold = cbBold.isChecked
        val isItalic = cbItalic.isChecked
        val isUnderline = cbUnderline.isChecked
        val textSize = seekThemeTextSize.value.toInt() + 10

        // TỐI ƯU 1: Tính toán Font 1 lần duy nhất
        val typeface = when {
            isBold && isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD_ITALIC)
            isBold -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD)
            isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.ITALIC)
            else -> getAdofaiBaseFont()
        }

        // TỐI ƯU 2: Tính toán Màu 1 lần duy nhất
        val textColor = try { Color.parseColor(etTextColorHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val bgNormal = try { Color.parseColor(etBgNormalHex.text.toString()) } catch (e: Exception) { Color.BLACK }
        val borderNormal = try { Color.parseColor(etBorderNormalHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val bgPressed = try { Color.parseColor(etBgPressedHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val borderPressed = try { Color.parseColor(etBorderPressedHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val rainColor = try { Color.parseColor(etRainColorHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val rainShadow = try { Color.parseColor(etRainShadowHex.text.toString()) } catch (e: Exception) { Color.CYAN }

        // TỐI ƯU 3: Cache Drawable (Sử dụng createAlphaSelector để đồng nhất với OverlayService)
        val alphaSelector = createAlphaSelector(bgNormal, borderNormal, bgPressed, borderPressed, borderPx, radiusPx)
        val constantState = alphaSelector.constantState

        val showCounters = swShowKeyCounters.isChecked

        // Vòng lặp gán UI siêu nhẹ
        for (i in 0 until keysContainer.childCount) {
            val container = keysContainer.getChildAt(i) as? LinearLayout ?: continue
            val params = container.layoutParams as ViewGroup.MarginLayoutParams
            params.width = widthPx
            params.height = heightPx
            if (i > 0) params.leftMargin = spacingPx
            container.layoutParams = params

            container.background = constantState?.newDrawable()?.mutate() ?: alphaSelector

            (container.getChildAt(0) as? TextView)?.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                setTextColor(textColor)
                this.typeface = typeface
                paintFlags = if (isUnderline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }

            (container.getChildAt(1) as? TextView)?.apply {
                setTextColor(textColor)
                this.typeface = typeface
                visibility = if (showCounters) View.VISIBLE else View.GONE
            }
        }

        // Cập nhật bộ đếm đáy
        val counterParams = bottomCountersContainer.layoutParams as ViewGroup.MarginLayoutParams
        counterParams.topMargin = spacingPx
        bottomCountersContainer.layoutParams = counterParams

        val localKpsContainer = viewerContainer.findViewById<View>(resources.getIdentifier("kpsContainer", "id", packageName))
        val localTotalContainer = viewerContainer.findViewById<View>(resources.getIdentifier("totalContainer", "id", packageName))

        if (localKpsContainer != null && localTotalContainer != null) {
            localKpsContainer.background = constantState?.newDrawable()?.mutate() ?: alphaSelector
            localTotalContainer.background = constantState?.newDrawable()?.mutate() ?: alphaSelector

            (localKpsContainer.layoutParams as ViewGroup.MarginLayoutParams).rightMargin = spacingPx

            val applyToTextView = { idName: String ->
                viewerContainer.findViewById<TextView>(resources.getIdentifier(idName, "id", packageName))?.apply {
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                    this.typeface = typeface
                    paintFlags = if (isUnderline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
            }
            applyToTextView("tvKpsLabel")
            applyToTextView("tvKpsValue")
            applyToTextView("tvTotalLabel")
            applyToTextView("tvTotalValue")
        }

        // Cập nhật RainKey
        keyTrailView.layoutParams.height = limitPx
        keyTrailView.setParameters(currentSpeed, currentLimit.toFloat())
        keyTrailView.setThemeColors(rainColor, rainShadow)

        updateLabels()
    }

    private fun showColorPickerDialog(title: String, currentColor: Int, onColorSelected: (Int) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val previewBox = dialogView.findViewById<View>(R.id.viewColorPreview)
        val seekA = dialogView.findViewById<Slider>(R.id.seekA)
        val seekR = dialogView.findViewById<Slider>(R.id.seekR)
        val seekG = dialogView.findViewById<Slider>(R.id.seekG)
        val seekB = dialogView.findViewById<Slider>(R.id.seekB)
        
        val etA = dialogView.findViewById<EditText>(R.id.etA)
        val etR = dialogView.findViewById<EditText>(R.id.etR)
        val etG = dialogView.findViewById<EditText>(R.id.etG)
        val etB = dialogView.findViewById<EditText>(R.id.etB)

        var a = Color.alpha(currentColor) / 255f
        var r = Color.red(currentColor) / 255f
        var g = Color.green(currentColor) / 255f
        var b = Color.blue(currentColor) / 255f

        var isUpdating = false

        val updateAll = {
            if (!isUpdating) {
                isUpdating = true
                seekA.value = a
                seekR.value = r
                seekG.value = g
                seekB.value = b
                etA.setText(String.format("%.2f", a))
                etR.setText(String.format("%.2f", r))
                etG.setText(String.format("%.2f", g))
                etB.setText(String.format("%.2f", b))
                previewBox.setBackgroundColor(Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt()))
                isUpdating = false
            }
        }

        updateAll()

        val setupTwoWay = { slider: Slider, editText: EditText, updateVal: (Float) -> Unit ->
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser && !isUpdating) {
                    isUpdating = true
                    updateVal(value)
                    editText.setText(String.format("%.2f", value))
                    previewBox.setBackgroundColor(Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt()))
                    isUpdating = false
                }
            }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!isUpdating) {
                        val v = s.toString().toFloatOrNull()
                        if (v != null && v in 0f..1f) {
                            isUpdating = true
                            updateVal(v)
                            slider.value = v
                            previewBox.setBackgroundColor(Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt()))
                            isUpdating = false
                        }
                    }
                }
            })
        }

        setupTwoWay(seekR, etR) { r = it }
        setupTwoWay(seekG, etG) { g = it }
        setupTwoWay(seekB, etB) { b = it }
        setupTwoWay(seekA, etA) { a = it }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.color_ok)) { _, _ ->
                onColorSelected(Color.argb((a * 255).toInt(), (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt()))
            }
            .setNegativeButton(getString(R.string.color_cancel), null)
            .show()
    }

    private fun createBoxDrawable(bgColor: Int, borderColor: Int, strokeWidthPx: Int, cornerRadiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(bgColor)
            setStroke(strokeWidthPx, borderColor)
        }
    }

    private fun createAlphaSelector(
        bgNormal: Int,
        borderNormal: Int,
        bgPressed: Int,
        borderPressed: Int,
        strokePx: Int,
        radiusPx: Float
    ): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createBoxDrawable(bgPressed, borderPressed, strokePx, radiusPx)
            )
            addState(intArrayOf(), createBoxDrawable(bgNormal, borderNormal, strokePx, radiusPx))
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
        tvBorderWidthLabel.text = getString(R.string.border_width_label, seekBorderWidth.value.toInt())
        tvCornerRadiusLabel.text = getString(R.string.corner_radius_label, seekCornerRadius.value.toInt())
    }

    private fun saveAndExit() {
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)

        sharedPref.edit().apply {
            putFloat("viewer_x", currentX)
            putFloat("viewer_y", currentY)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)
            putInt("key_width", currentKeyWidth)
            putInt("key_height", currentKeyHeight)
            putInt("key_spacing", currentKeySpacing)
            putInt("theme_border_width", seekBorderWidth.value.toInt())
            putInt("theme_corner_radius", seekCornerRadius.value.toInt())
            putBoolean("theme_keyrain_enabled", swEnableKeyRain.isChecked)

            // Save THEME settings
            putBoolean("show_key_counters", swShowKeyCounters.isChecked)
            putBoolean("theme_text_bold", cbBold.isChecked)
            putBoolean("theme_text_italic", cbItalic.isChecked)
            putBoolean("theme_text_underline", cbUnderline.isChecked)
            putFloat("theme_text_size", (seekThemeTextSize.value + 10))
            putString("theme_text_color", etTextColorHex.text.toString())
            putString("theme_text_color_pressed", etTextColorPressedHex.text.toString())
            putString("theme_bg_normal", etBgNormalHex.text.toString())
            putString("theme_bg_pressed", etBgPressedHex.text.toString())
            putString("theme_border_normal", etBorderNormalHex.text.toString())
            putString("theme_border_pressed", etBorderPressedHex.text.toString())
            putString("theme_rain_color", etRainColorHex.text.toString())
            putString("theme_rain_shadow", etRainShadowHex.text.toString())

            // Khóa vĩnh viễn trạng thái đã setup, đảm bảo không bị reset ở lần mở sau
            putBoolean("is_keyviewer_configured", true)
            apply()
        }

        // Gửi tín hiệu để Service cập nhật ngay lập tức (nếu đang chạy ngầm)
        triggerOverlayRefresh()

        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetToDefault() {
        // GIỮ NGUYÊN BẢN GIÁ TRỊ RESET CỦA BẠN
        currentScale = 0.5f
        currentSpeed = 0.8f
        currentLimit = 280
        currentKeyWidth = 55
        currentKeyHeight = 60
        currentKeySpacing = 7
        currentX = 0f
        currentY = 0f

        // Lưu SharedPreferences (Lưu mặc định toàn bộ bao gồm Theme)
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putFloat("viewer_x", currentX)
            putFloat("viewer_y", currentY)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)
            putInt("key_width", currentKeyWidth)
            putInt("key_height", currentKeyHeight)
            putInt("key_spacing", currentKeySpacing)
            putInt("theme_border_width", 2)
            putInt("theme_corner_radius", 6)

            // BỔ SUNG THEME MẶC ĐỊNH
            putString("theme_bg_normal", "#000000")
            putString("theme_border_normal", "#FFFFFF")
            putString("theme_bg_pressed", "#FFFFFF")
            putString("theme_border_pressed", "#FFFFFF")
            putString("theme_text_color", "#FFFFFF")
            putString("theme_text_color_pressed", "#000000")
            putString("theme_rain_color", "#FFFFFF")
            putString("theme_rain_shadow", "#000000")
            putFloat("theme_text_size", 20f)
            putBoolean("theme_text_bold", true)
            putBoolean("theme_text_italic", false)
            putBoolean("theme_text_underline", false)

            putBoolean("is_keyviewer_configured", true)
            apply()
        }

        // Nạp lại toàn bộ giao diện từ Prefs để cập nhật Sliders, EditTexts, Màu sắc
        loadPreferences()

        // Vẽ lại giao diện Preview
        renderKeyPreview()
        updateLivePreview()

        Toast.makeText(this, getString(R.string.toast_reset_default), Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun initDefaultThemeOnFirstLaunch() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)

        // Kiểm tra cờ đánh dấu lần đầu setup theme
        if (!pref.contains("is_first_theme_setup")) {
            val editor = pref.edit()
            editor.putBoolean("is_first_theme_setup", true)

            // Chọn sẵn Spinner ở Mẫu 0 (Mặc định)
            editor.putInt("saved_preset_index", 0)

            // Ghi cứng toàn bộ thông số chuẩn của Mẫu Mặc Định vào cấu hình chính
            editor.putString("theme_text_color", "#FFFFFF")
            editor.putString("theme_text_color_pressed", "#FF000000")
            editor.putString("theme_bg_normal", "#000000")
            editor.putString("theme_bg_pressed", "#FFFFFF")
            editor.putString("theme_border_normal", "#FFFFFF")
            editor.putString("theme_border_pressed", "#FFFFFF")
            editor.putString("theme_rain_color", "#FFFFFF")
            editor.putString("theme_rain_shadow", "#FF000000")
            editor.putFloat("theme_text_size", 20f)
            editor.putBoolean("theme_text_bold", false)
            editor.putBoolean("theme_text_italic", false)
            editor.putBoolean("theme_text_underline", false)
            editor.putInt("theme_border_width", 2)
            editor.putInt("theme_corner_radius", 6)

            editor.apply()
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