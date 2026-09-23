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
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
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
    // --- THÊM 6 BIẾN NÀY ĐỂ QUẢN LÝ KPS/TOTAL ---
    private var kpsContainer: View? = null
    private var tvKpsLabel: TextView? = null
    private var tvKpsValue: TextView? = null
    private var totalContainer: View? = null
    private var tvTotalLabel: TextView? = null
    private var tvTotalValue: TextView? = null
    // --------------------------------------------

    private lateinit var tvPosXLabel: TextView
    private lateinit var tvPosYLabel: TextView
    private lateinit var tvScaleLabel: TextView
    private lateinit var tvSpeedLabel: TextView
    private lateinit var tvLimitLabel: TextView
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
    private lateinit var seekKeySpacing: Slider
    private lateinit var seekBorderWidth: Slider
    private lateinit var seekCornerRadius: Slider

    private lateinit var swEnableKeyRain: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var layoutThemeContent: LinearLayout
    private lateinit var swShowKeyCounters: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var swEnableShadow: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var swPerformanceShadow: com.google.android.material.switchmaterial.SwitchMaterial
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

    // BIẾN CHO HÀNG 2
    private lateinit var etRainColor2Hex: EditText
    private lateinit var etRainShadow2Hex: EditText

    private lateinit var viewTextColorPreview: MaterialCardView
    private lateinit var viewTextColorPressedPreview: MaterialCardView
    private lateinit var viewBgNormalPreview: MaterialCardView
    private lateinit var viewBgPressedPreview: MaterialCardView
    private lateinit var viewBorderNormalPreview: MaterialCardView
    private lateinit var viewBorderPressedPreview: MaterialCardView
    private lateinit var viewRainColorPreview: MaterialCardView
    private lateinit var viewRainShadowPreview: MaterialCardView

    // PREVIEW CHO HÀNG 2
    private lateinit var viewRainColor2Preview: MaterialCardView
    private lateinit var viewRainShadow2Preview: MaterialCardView

    private var currentScale = 1.0f
    private var currentSpeed = 1.0f
    private var currentLimit = 200
    private val currentKeyWidth = 55
    private val currentKeyHeight = 60
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
    private var colorMode = ThemeColorStore.BASIC
    private var selectedColorTarget = "key_0"
    private var suppressColorEditorEvents = false
    private val themeEditorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) ThemeDraftBridge.draft?.let { applyThemeDraft(it) }
    }
    private val advancedDraft = LinkedHashMap<String, ThemeColorSet>()
    private lateinit var colorModeGroup: MaterialButtonToggleGroup
    private lateinit var advancedTargetDropdown: android.widget.AutoCompleteTextView
    private lateinit var advancedTargetLayout: TextInputLayout

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

    private val baseTextSize = 20f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDefaultThemeOnFirstLaunch()
        supportActionBar?.hide()

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContentView(R.layout.activity_key_viewer_config)

        stopService(Intent(this, OverlayService::class.java))

        initViews()

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val isKeyViewerConfigured = pref.getBoolean("is_keyviewer_configured", false)

        if (!isKeyViewerConfigured) {
            pref.edit().putBoolean("is_keyviewer_configured", true).apply()
            resetToDefault()
        } else {
            loadPreferences()
        }

        setupListeners()

        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

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

        // Ẩn vĩnh viễn khay XML cũ cho TẤT CẢ các mode
        val bottomCounters = viewerContainer.findViewById<View>(R.id.bottomCountersContainer)
        bottomCounters?.visibility = View.GONE

        // Tạo khung chứa tổng
        val frameWorkspace = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            tag = "WORKSPACE_${keyMode}K"
        }
        keysContainer.addView(frameWorkspace)

        val density = resources.displayMetrics.density

        for (i in 0 until keyMode) {
            val container = LinearLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams((currentKeyWidth * density).toInt(), (currentKeyHeight * density).toInt())
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = (i + 1).toString()
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }
            val tvCount = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
            frameWorkspace.addView(container)
        }

        // ================= TẠO KPS/TOTAL ẢO CHO MỌI MODE =================
        val dpToPx = { dp: Int -> (dp * density).toInt() }
        val isHorizontal = (keyMode == 4 || keyMode == 6 || keyMode == 8 || keyMode == 16)

        val labelParams = if (isHorizontal) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val valueParams = if (isHorizontal) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val newKpsContainer = LinearLayout(this).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            if (isHorizontal) setPadding(dpToPx(8), 0, dpToPx(12), 0)
        }
        val newTvKpsLabel = TextView(this).apply {
            text = getString(R.string.kps_label) // <-- Sửa ở đây
            gravity = if (isHorizontal) (Gravity.START or Gravity.CENTER_VERTICAL) else Gravity.CENTER
            includeFontPadding = false
        }
        val newTvKpsValue = TextView(this).apply {
            text = "0"
            gravity = if (isHorizontal) (Gravity.END or Gravity.CENTER_VERTICAL) else Gravity.CENTER
            includeFontPadding = false
        }
        newKpsContainer.addView(newTvKpsLabel, labelParams)
        newKpsContainer.addView(newTvKpsValue, valueParams)

        val newTotalContainer = LinearLayout(this).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            if (isHorizontal) setPadding(dpToPx(12), 0, dpToPx(8), 0)
        }

        // 4K Không có chữ Total
        val is4K = (keyMode == 4)
        val newTvTotalLabel = TextView(this).apply {
            text = getString(R.string.total_label) // <-- Sửa ở đây
            gravity = if (isHorizontal) (Gravity.START or Gravity.CENTER_VERTICAL) else Gravity.CENTER
            includeFontPadding = false
            visibility = if (is4K) View.GONE else View.VISIBLE
        }

        val totalValueParams = if (is4K) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) else valueParams

        val newTvTotalValue = TextView(this).apply {
            text = "0"
            gravity = if (isHorizontal || is4K) (Gravity.END or Gravity.CENTER_VERTICAL) else Gravity.CENTER
            includeFontPadding = false
        }
        newTotalContainer.addView(newTvTotalLabel, labelParams)
        newTotalContainer.addView(newTvTotalValue, totalValueParams)

        kpsContainer = newKpsContainer; tvKpsLabel = newTvKpsLabel; tvKpsValue = newTvKpsValue
        totalContainer = newTotalContainer; tvTotalLabel = newTvTotalLabel; tvTotalValue = newTvTotalValue

        frameWorkspace.addView(newKpsContainer)
        frameWorkspace.addView(newTotalContainer)
    }

    private fun initViews() {
        viewerContainer = findViewById(R.id.viewerContainer)
        keysContainer = findViewById(R.id.keysContainer)
        keyTrailView = findViewById(R.id.keyTrailView)
        keyTrailView.setBackgroundColor(Color.parseColor("#33FFFFFF"))
        bottomCountersContainer = findViewById(R.id.bottomCountersContainer)

        layoutThemeContent = findViewById(R.id.layoutThemeContent)
        swShowKeyCounters = findViewById(R.id.swShowKeyCounters)
        swEnableShadow = findViewById(R.id.swEnableShadow)
        swPerformanceShadow = findViewById(R.id.swPerformanceShadow)
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

        etRainColor2Hex = findViewById(R.id.etRainColor2Hex)
        etRainShadow2Hex = findViewById(R.id.etRainShadow2Hex)

        viewTextColorPreview = findViewById(R.id.viewTextColorPreview)
        viewTextColorPressedPreview = findViewById(R.id.viewTextColorPressedPreview)
        viewBgNormalPreview = findViewById(R.id.viewBgNormalPreview)
        viewBgPressedPreview = findViewById(R.id.viewBgPressedPreview)
        viewBorderNormalPreview = findViewById(R.id.viewBorderNormalPreview)
        viewBorderPressedPreview = findViewById(R.id.viewBorderPressedPreview)
        viewRainColorPreview = findViewById(R.id.viewRainColorPreview)
        viewRainShadowPreview = findViewById(R.id.viewRainShadowPreview)

        viewRainColor2Preview = findViewById(R.id.viewRainColor2Preview)
        viewRainShadow2Preview = findViewById(R.id.viewRainShadow2Preview)

        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        tvSpeedLabel = findViewById(R.id.tvSpeedLabel)
        tvLimitLabel = findViewById(R.id.tvLimitLabel)
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
        seekKeySpacing = findViewById(R.id.seekKeySpacing)
        seekBorderWidth = findViewById(R.id.seekBorderWidth)
        seekCornerRadius = findViewById(R.id.seekCornerRadius)

        val dm = resources.displayMetrics
        val centerX = dm.widthPixels
        val centerY = dm.heightPixels
        seekPosX.valueTo = (centerX * 2).toFloat()
        seekPosY.valueTo = (centerY * 2).toFloat()
        seekKeySpacing.valueTo = 20f

        viewerContainer.pivotX = 0f
        viewerContainer.pivotY = 0f

        setupCollapsibleSection(tvSection1Header, tvSection1Header, section1ContentLayout, defaultExpanded = false)
        setupCollapsibleSection(tvSection2Header, tvSection2Header, section2ContentLayout, defaultExpanded = false)
        tvThemeHeader.setOnClickListener {
            val pref = getSharedPreferences("KeyViewerPrefs", MODE_PRIVATE)
            val fallback = readEditorColors()
            val advanced = LinkedHashMap<String, ThemeColorSet>()
            for (target in allColorTargets()) {
                advanced[target] = if (target == selectedColorTarget && colorMode == ThemeColorStore.ADVANCED) readEditorColors()
                else advancedDraft[target] ?: ThemeColorStore.advanced(pref, currentKeyMode(), target, fallback)
            }
            ThemeDraftBridge.draft = ThemeDraft(
                colorMode, fallback,
                etRainColor2Hex.text.toString(),
                etRainShadow2Hex.text.toString(),
                advanced,
                cbBold.isChecked, cbItalic.isChecked, cbUnderline.isChecked,
                swShowKeyCounters.isChecked, swPerformanceShadow.isChecked,
                seekKeySpacing.value.toInt(), seekBorderWidth.value.toInt(), seekCornerRadius.value.toInt(),
                swEnableKeyRain.isChecked, currentSpeed, currentLimit, swEnableShadow.isChecked
            )
            themeEditorLauncher.launch(Intent(this, ThemeEditorActivity::class.java))
        }
        layoutThemeContent.visibility = View.GONE
        setupPresetUI()
    }

    private fun currentKeyMode(): Int = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).getInt("current_key_mode", 6)

    private fun allColorTargets(): List<String> = buildList {
        repeat(currentKeyMode()) { add("key_$it") }
        add("kps")
        add("total")
    }

    private fun targetLabel(target: String): String = when (target) {
        "kps" -> "KPS"
        "total" -> "Total"
        else -> getString(R.string.theme_target_key, target.substringAfter('_').toIntOrNull()?.plus(1) ?: 1)
    }

    private fun readEditorColors() = ThemeColorSet(
        etTextColorHex.text.toString(), etTextColorPressedHex.text.toString(),
        etBgNormalHex.text.toString(), etBgPressedHex.text.toString(),
        etBorderNormalHex.text.toString(), etBorderPressedHex.text.toString(),
        etRainColorHex.text.toString(), etRainShadowHex.text.toString()
    ).normalized()

    private fun writeEditorColors(colors: ThemeColorSet) {
        suppressColorEditorEvents = true
        etTextColorHex.setText(colors.textNormal); etTextColorPressedHex.setText(colors.textPressed)
        etBgNormalHex.setText(colors.bgNormal); etBgPressedHex.setText(colors.bgPressed)
        etBorderNormalHex.setText(colors.borderNormal); etBorderPressedHex.setText(colors.borderPressed)
        etRainColorHex.setText(colors.trail); etRainShadowHex.setText(colors.shadow)
        suppressColorEditorEvents = false
        updateLivePreview()
    }

    private fun ensureAdvancedDraft() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val fallback = ThemeColorStore.basic(pref)
        for (target in allColorTargets()) advancedDraft.putIfAbsent(
            target, ThemeColorStore.advanced(pref, currentKeyMode(), target, fallback)
        )
    }

    private fun setupColorModeUI() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        colorMode = ThemeColorStore.mode(pref, currentKeyMode())
        ensureAdvancedDraft()

        val host = layoutThemeContent
        colorModeGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val basicButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId(); text = getString(R.string.theme_mode_basic); isCheckable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val advancedButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId(); text = getString(R.string.theme_mode_advanced); isCheckable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        colorModeGroup.addView(basicButton); colorModeGroup.addView(advancedButton)
        host.addView(colorModeGroup, 0)

        advancedTargetDropdown = android.widget.AutoCompleteTextView(this).apply {
            inputType = android.text.InputType.TYPE_NULL
            setTextColor(Color.WHITE)
        }
        advancedTargetLayout = TextInputLayout(this).apply {
            hint = getString(R.string.theme_edit_target)
            setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE)
            addView(
                advancedTargetDropdown,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        host.addView(advancedTargetLayout, 1)
        val targetPairs = allColorTargets().map { it to targetLabel(it) }
        advancedTargetDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, targetPairs.map { it.second }))
        advancedTargetDropdown.setText(targetPairs.first().second, false)
        advancedTargetDropdown.setOnItemClickListener { _, _, position, _ ->
            advancedDraft[selectedColorTarget] = readEditorColors()
            selectedColorTarget = targetPairs[position].first
            writeEditorColors(advancedDraft.getValue(selectedColorTarget))
        }

        colorModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (colorMode == ThemeColorStore.ADVANCED) advancedDraft[selectedColorTarget] = readEditorColors()
            colorMode = if (checkedId == advancedButton.id) ThemeColorStore.ADVANCED else ThemeColorStore.BASIC
            val modeEditor = pref.edit()
            ThemeColorStore.setMode(modeEditor, currentKeyMode(), colorMode)
            modeEditor.apply()
            advancedTargetLayout.visibility = if (colorMode == ThemeColorStore.ADVANCED) View.VISIBLE else View.GONE
            if (colorMode == ThemeColorStore.ADVANCED) writeEditorColors(advancedDraft.getValue(selectedColorTarget))
            else writeEditorColors(ThemeColorStore.basic(pref))
            findViewById<View>(R.id.etRainColor2Hex)?.parent?.let { (it as? View)?.visibility = if (colorMode == ThemeColorStore.BASIC) View.VISIBLE else View.GONE }
        }
        colorModeGroup.check(if (colorMode == ThemeColorStore.ADVANCED) advancedButton.id else basicButton.id)
    }

    private fun saveAdvancedColors(editor: android.content.SharedPreferences.Editor) {
        advancedDraft[selectedColorTarget] = readEditorColors()
        for ((target, colors) in advancedDraft) ThemeColorStore.writeAdvanced(editor, currentKeyMode(), target, colors)
        ThemeColorStore.setMode(editor, currentKeyMode(), colorMode)
    }

    private fun setupPresetUI() {
        val presetDropdown = findViewById<android.widget.AutoCompleteTextView>(R.id.presetDropdown)
        val btnSavePreset = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSavePreset)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, presetNames)
        presetDropdown.setAdapter(adapter)

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val savedPresetIndex = pref.getInt("saved_preset_index_${currentKeyMode()}_$colorMode", 0)

        if (savedPresetIndex < presetNames.size) {
            presetDropdown.setText(adapter.getItem(savedPresetIndex), false)
        }

        presetDropdown.setOnItemClickListener { parent, view, position, id ->
            if (!isUserInteractingWithSpinner) {
                return@setOnItemClickListener
            }

            currentSelectedPresetIndex = position
            val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)

            if (position == 0) {
                applyValuesToUIControls(
                    textColorNormal = "#FFFFFF",
                    textColorPressed = "#FF000000",
                    bgNormal = "#000000",
                    bgPressed = "#FFFFFF",
                    borderNormal = "#FFFFFF",
                    borderPressed = "#FFFFFF",
                    rainColor = "#FFFFFF",
                    rainShadow = "#FF000000",
                    rainColor2 = "#FF999898",
                    rainShadow2 = "#FFFFFF",
                    textSize = 20f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false
                )
            } else if (position == 1) {
                applyValuesToUIControls(
                    textColorNormal = "#FFFFFF",
                    textColorPressed = "#FF000000",
                    bgNormal = "#338E3CFF",
                    bgPressed = "#FFFFFFFF",
                    borderNormal = "#FF8C3EFF",
                    borderPressed = "#FFFFFF",
                    rainColor = "#FF8C3EFF",
                    rainShadow = "#FF000000",
                    rainColor2 = "#FFFFFFFF",
                    rainShadow2 = "#FF000000",
                    textSize = 20f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false
                )
            } else if (colorMode == ThemeColorStore.ADVANCED) {
                advancedDraft[selectedColorTarget] = readEditorColors()
                for (target in allColorTargets()) {
                    val fallback = advancedDraft[target] ?: ThemeColorStore.basic(pref)
                    advancedDraft[target] = ThemeColorStore.advancedPreset(pref, currentKeyMode(), position, target, fallback)
                }
                writeEditorColors(advancedDraft.getValue(selectedColorTarget))
            } else {
                val suffix = "_preset_${currentKeyMode()}_${colorMode}_$position"
                val textColorNormal = pref.getString("theme_text_color$suffix", "#FFFFFF") ?: "#FFFFFF"
                val textColorPressed = pref.getString("theme_text_color_pressed$suffix", "#FF000000") ?: "#FF000000"
                val bgNormal = pref.getString("theme_bg_normal$suffix", "#000000") ?: "#000000"
                val bgPressed = pref.getString("theme_bg_pressed$suffix", "#FFFFFF") ?: "#FFFFFF"
                val borderNormal = pref.getString("theme_border_normal$suffix", "#FFFFFF") ?: "#FFFFFF"
                val borderPressed = pref.getString("theme_border_pressed$suffix", "#FFFFFF") ?: "#FFFFFF"
                val rainColor = pref.getString("theme_rain_color$suffix", "#FFFFFF") ?: "#FFFFFF"
                val rainShadow = pref.getString("theme_rain_shadow$suffix", "#FF000000") ?: "#FF000000"

                val rainColor2 = pref.getString("theme_rain_color_2$suffix", "#A78BFA") ?: "#A78BFA"
                val rainShadow2 = pref.getString("theme_rain_shadow_2$suffix", "#7C3AED") ?: "#7C3AED"

                val textSize = try { pref.getFloat("theme_text_size$suffix", 20f) } catch (e: Exception) {
                    try { pref.getInt("theme_text_size$suffix", 20).toFloat() } catch (e2: Exception) { 20f }
                }

                val isBold = pref.getBoolean("theme_text_bold$suffix", false)
                val isItalic = pref.getBoolean("theme_text_italic$suffix", false)
                val isUnderline = pref.getBoolean("theme_text_underline$suffix", false)

                applyValuesToUIControls(
                    textColorNormal, textColorPressed, bgNormal, bgPressed,
                    borderNormal, borderPressed, rainColor, rainShadow,
                    rainColor2, rainShadow2,
                    textSize, isBold, isItalic, isUnderline
                )
            }

            pref.edit().putInt("saved_preset_index", position).apply()
        }

        btnSavePreset.setOnClickListener {
            val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            val editor = pref.edit()

            val currentText = presetDropdown.text.toString()
            val selectedPosition = presetNames.indexOf(currentText)

            if (selectedPosition < 2) {
                Toast.makeText(this, getString(R.string.toast_cannot_overwrite_system), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val textSize = (seekThemeTextSize.value + 10)
            val textColor = etTextColorHex.text.toString()
            val textColorPressed = etTextColorPressedHex.text.toString()
            val bgNormal = etBgNormalHex.text.toString()
            val borderNormal = etBorderNormalHex.text.toString()
            val bgPressed = etBgPressedHex.text.toString()
            val borderPressed = etBorderPressedHex.text.toString()
            val rainColor = etRainColorHex.text.toString()
            val rainShadow = etRainShadowHex.text.toString()
            val rainColor2 = etRainColor2Hex.text.toString()
            val rainShadow2 = etRainShadow2Hex.text.toString()
            val isBold = cbBold.isChecked
            val isItalic = cbItalic.isChecked
            val isUnderline = cbUnderline.isChecked

            editor.putFloat("theme_text_size", textSize)
            editor.putString("theme_text_color", textColor)
            editor.putString("theme_text_color_pressed", textColorPressed)
            editor.putString("theme_bg_normal", bgNormal)
            editor.putString("theme_bg_pressed", bgPressed)
            editor.putString("theme_border_normal", borderNormal)
            editor.putString("theme_border_pressed", borderPressed)
            editor.putString("theme_rain_color", rainColor)
            editor.putString("theme_rain_shadow", rainShadow)
            editor.putString("theme_rain_color_2", rainColor2)
            editor.putString("theme_rain_shadow_2", rainShadow2)
            editor.putBoolean("theme_text_bold", isBold)
            editor.putBoolean("theme_text_italic", isItalic)
            editor.putBoolean("theme_text_underline", isUnderline)

            val suffix = "_preset_${currentKeyMode()}_${colorMode}_$selectedPosition"
            if (colorMode == ThemeColorStore.ADVANCED) {
                advancedDraft[selectedColorTarget] = readEditorColors()
                for ((target, colors) in advancedDraft) {
                    ThemeColorStore.writeAdvancedPreset(editor, currentKeyMode(), selectedPosition, target, colors)
                }
            }
            editor.putFloat("theme_text_size$suffix", textSize)
            editor.putString("theme_text_color$suffix", textColor)
            editor.putString("theme_text_color_pressed$suffix", textColorPressed)
            editor.putString("theme_bg_normal$suffix", bgNormal)
            editor.putString("theme_bg_pressed$suffix", bgPressed)
            editor.putString("theme_border_normal$suffix", borderNormal)
            editor.putString("theme_border_pressed$suffix", borderPressed)
            editor.putString("theme_rain_color$suffix", rainColor)
            editor.putString("theme_rain_shadow$suffix", rainShadow)
            editor.putString("theme_rain_color_2$suffix", rainColor2)
            editor.putString("theme_rain_shadow_2$suffix", rainShadow2)
            editor.putBoolean("theme_text_bold$suffix", isBold)
            editor.putBoolean("theme_text_italic$suffix", isItalic)
            editor.putBoolean("theme_text_underline$suffix", isUnderline)

            editor.putInt("saved_preset_index_${currentKeyMode()}_$colorMode", selectedPosition)
            editor.apply()

            triggerOverlayRefresh()

            Toast.makeText(this, getString(R.string.toast_preset_saved, currentText), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCollapsibleSection(headerTitle: android.widget.TextView, clickableView: android.view.View, contentLayout: android.view.View, defaultExpanded: Boolean = false) {
        val originalText = headerTitle.text.toString().replace("▼ ", "").replace("▶ ", "").trim()

        val updateUI = { isExpanded: Boolean ->
            contentLayout.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            headerTitle.text = if (isExpanded) "▼ $originalText" else "▶ $originalText"
        }

        var isExpanded = defaultExpanded
        updateUI(isExpanded)

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
        rainColor2: String,
        rainShadow2: String,
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
        etRainColor2Hex.setText(rainColor2)
        etRainShadow2Hex.setText(rainShadow2)

        seekThemeTextSize.value = (textSize - 10).coerceIn(seekThemeTextSize.valueFrom, seekThemeTextSize.valueTo)
        cbBold.isChecked = isBold
        cbItalic.isChecked = isItalic
        cbUnderline.isChecked = isUnderline

        updateLivePreview()
    }

    private fun applyThemeDraft(draft: ThemeDraft) {
        colorMode = draft.mode
        advancedDraft.clear(); advancedDraft.putAll(draft.advanced)
        suppressColorEditorEvents = true
        writeEditorColors(if (colorMode == ThemeColorStore.ADVANCED) advancedDraft[selectedColorTarget] ?: draft.basic else draft.basic)
        etRainColor2Hex.setText(draft.trail2); etRainShadow2Hex.setText(draft.shadow2)
        cbBold.isChecked = draft.bold; cbItalic.isChecked = draft.italic; cbUnderline.isChecked = draft.underline
        swShowKeyCounters.isChecked = draft.showCounters; swPerformanceShadow.isChecked = draft.performanceShadow
        suppressColorEditorEvents = false
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

        val x = sharedPref.getFloat("viewer_x", 0f)
        val y = sharedPref.getFloat("viewer_y", 0f)
        currentScale = sharedPref.getFloat("viewer_scale", 1.0f)
        currentSpeed = sharedPref.getFloat("trail_speed", 0.8f)
        currentLimit = sharedPref.getInt("trail_limit_px", 200)
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
        seekKeySpacing.value = currentKeySpacing.toFloat().coerceIn(seekKeySpacing.valueFrom, seekKeySpacing.valueTo)
        seekBorderWidth.value = sharedPref.getInt("theme_border_width", 2).toFloat().coerceIn(seekBorderWidth.valueFrom, seekBorderWidth.valueTo)
        seekCornerRadius.value = sharedPref.getInt("theme_corner_radius", 6).toFloat().coerceIn(seekCornerRadius.valueFrom, seekCornerRadius.valueTo)
        swEnableKeyRain.isChecked = sharedPref.getBoolean("theme_keyrain_enabled", true)
        keyTrailView.visibility = if (swEnableKeyRain.isChecked) View.VISIBLE else View.GONE

        swShowKeyCounters.isChecked = sharedPref.getBoolean("show_key_counters", false)
        swEnableShadow.isChecked = sharedPref.getBoolean("theme_shadow_enabled", true)
        swPerformanceShadow.isChecked = sharedPref.getBoolean("theme_performance_shadow", false)
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
        val rainColor2 = sharedPref.getString("theme_rain_color_2", "#A78BFA") ?: "#A78BFA"
        val rainShadow2 = sharedPref.getString("theme_rain_shadow_2", "#7C3AED") ?: "#7C3AED"

        etTextColorHex.setText(textColor)
        etTextColorPressedHex.setText(textColorPressed)
        etBgNormalHex.setText(bgNormal)
        etBgPressedHex.setText(bgPressed)
        etBorderNormalHex.setText(borderNormal)
        etBorderPressedHex.setText(borderPressed)
        etRainColorHex.setText(rainColor)
        etRainShadowHex.setText(rainShadow)
        etRainColor2Hex.setText(rainColor2)
        etRainShadow2Hex.setText(rainShadow2)

        syncColorPreview(etTextColorHex, viewTextColorPreview)
        syncColorPreview(etTextColorPressedHex, viewTextColorPressedPreview)
        syncColorPreview(etBgNormalHex, viewBgNormalPreview)
        syncColorPreview(etBgPressedHex, viewBgPressedPreview)
        syncColorPreview(etBorderNormalHex, viewBorderNormalPreview)
        syncColorPreview(etBorderPressedHex, viewBorderPressedPreview)
        syncColorPreview(etRainColorHex, viewRainColorPreview)
        syncColorPreview(etRainShadowHex, viewRainShadowPreview)
        syncColorPreview(etRainColor2Hex, viewRainColor2Preview)
        syncColorPreview(etRainShadow2Hex, viewRainShadow2Preview)
    }

    private fun syncColorPreview(et: EditText, preview: MaterialCardView) {
        try {
            preview.setCardBackgroundColor(Color.parseColor(et.text.toString()))
        } catch (e: Exception) {}
    }

    private fun setupListeners() {
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
                R.id.viewRainColor2Preview -> { et = etRainColor2Hex; title = getString(R.string.theme_rain_color_row2) }
                R.id.viewRainShadow2Preview -> { et = etRainShadow2Hex; title = getString(R.string.theme_rain_shadow_row2) }
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
        viewRainColor2Preview.setOnClickListener(colorClick)
        viewRainShadow2Preview.setOnClickListener(colorClick)

        val themeTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressColorEditorEvents) return
                if (colorMode == ThemeColorStore.ADVANCED && ::advancedTargetDropdown.isInitialized) advancedDraft[selectedColorTarget] = readEditorColors()
                syncColorPreview(etTextColorHex, viewTextColorPreview)
                syncColorPreview(etTextColorPressedHex, viewTextColorPressedPreview)
                syncColorPreview(etBgNormalHex, viewBgNormalPreview)
                syncColorPreview(etBgPressedHex, viewBgPressedPreview)
                syncColorPreview(etBorderNormalHex, viewBorderNormalPreview)
                syncColorPreview(etBorderPressedHex, viewBorderPressedPreview)
                syncColorPreview(etRainColorHex, viewRainColorPreview)
                syncColorPreview(etRainShadowHex, viewRainShadowPreview)
                syncColorPreview(etRainColor2Hex, viewRainColor2Preview)
                syncColorPreview(etRainShadow2Hex, viewRainShadow2Preview)
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
        etRainColor2Hex.addTextChangedListener(themeTextWatcher)
        etRainShadow2Hex.addTextChangedListener(themeTextWatcher)

        swEnableKeyRain.setOnCheckedChangeListener { _, isChecked ->
            keyTrailView.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateLivePreview()
        }

        swShowKeyCounters.setOnCheckedChangeListener { _, _ ->
            renderKeyPreview()
            updateLivePreview()
        }
        swEnableShadow.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        swPerformanceShadow.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbBold.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbItalic.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        cbUnderline.setOnCheckedChangeListener { _, _ -> updateLivePreview() }
        seekThemeTextSize.addOnChangeListener { _, value, fromUser ->
            tvThemeTextSizeLabel.text = getString(R.string.theme_text_size, value.toInt() + 10)
            if (fromUser) updateLivePreview()
        }

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

                    val dm = resources.displayMetrics
                    seekPosX.value = (view.x.toInt() + dm.widthPixels).toFloat().coerceIn(seekPosX.valueFrom, seekPosX.valueTo)
                    seekPosY.value = (view.y.toInt() + dm.heightPixels).toFloat().coerceIn(seekPosY.valueFrom, seekPosY.valueTo)
                    updateLabels()
                    true
                }
                else -> false
            }
        }

        seekLimit.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentLimit = value.toInt() + 70
            updateLivePreview()
        }

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
        seekKeySpacing.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            currentKeySpacing = value.toInt()
            renderKeyPreview()
            updateLivePreview()
        }
        seekBorderWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateLivePreview()
        }
        seekCornerRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) updateLivePreview()
        }

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

        val dm2 = resources.displayMetrics
        val centerX2 = dm2.widthPixels
        val centerY2 = dm2.heightPixels

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
        seekKeySpacing.setLabelFormatter(intFormatter)
        seekThemeTextSize.setLabelFormatter(intFormatter)

        try {
            findViewById<com.google.android.material.slider.Slider>(R.id.seekBorderWidth)?.setLabelFormatter(intFormatter)
            findViewById<com.google.android.material.slider.Slider>(R.id.seekCornerRadius)?.setLabelFormatter(intFormatter)
        } catch (e: Exception) {}

        seekPosX.setLabelFormatter { value -> (value - centerX2).toInt().toString() }
        seekPosY.setLabelFormatter { value -> (value - centerY2).toInt().toString() }

        seekScale.setLabelFormatter { value ->
            String.format(java.util.Locale.US, "%.2f", value / 100f)
        }
        seekSpeed.setLabelFormatter { value ->
            String.format(java.util.Locale.US, "%.2f", value / 100f)
        }

        seekLimit.setLabelFormatter { value -> (value + 70).toInt().toString() }

        seekKeySpacing.setLabelFormatter(intFormatter)
        seekBorderWidth.setLabelFormatter(intFormatter)
        seekCornerRadius.setLabelFormatter(intFormatter)

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

        val typeface = when {
            isBold && isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD_ITALIC)
            isBold -> Typeface.create(getAdofaiBaseFont(), Typeface.BOLD)
            isItalic -> Typeface.create(getAdofaiBaseFont(), Typeface.ITALIC)
            else -> getAdofaiBaseFont()
        }

        val textColor = try { Color.parseColor(etTextColorHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val bgNormal = try { Color.parseColor(etBgNormalHex.text.toString()) } catch (e: Exception) { Color.BLACK }
        val borderNormal = try { Color.parseColor(etBorderNormalHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val bgPressed = try { Color.parseColor(etBgPressedHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val borderPressed = try { Color.parseColor(etBorderPressedHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val rainColor = try { Color.parseColor(etRainColorHex.text.toString()) } catch (e: Exception) { Color.WHITE }
        val rainShadow = try { Color.parseColor(etRainShadowHex.text.toString()) } catch (e: Exception) { Color.CYAN }
        val rainColor2 = try { Color.parseColor(etRainColor2Hex.text.toString()) } catch (e: Exception) { Color.parseColor("#A78BFA") }
        val rainShadow2 = try { Color.parseColor(etRainShadow2Hex.text.toString()) } catch (e: Exception) { Color.parseColor("#7C3AED") }

        val alphaSelector = createAlphaSelector(bgNormal, borderNormal, bgPressed, borderPressed, borderPx, radiusPx)
        val constantState = alphaSelector.constantState

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val keyMode = pref.getInt("current_key_mode", 6)
        val showCounters = swShowKeyCounters.isChecked
        if (colorMode == ThemeColorStore.ADVANCED) advancedDraft[selectedColorTarget] = readEditorColors()
        val basicSet = readEditorColors()
        fun colorsFor(target: String) = if (colorMode == ThemeColorStore.ADVANCED) advancedDraft[target] ?: basicSet else basicSet
        val kpsSet = colorsFor("kps")
        val totalSet = colorsFor("total")

        kpsContainer?.background = createAlphaSelector(Color.parseColor(kpsSet.bgNormal), Color.parseColor(kpsSet.borderNormal), Color.parseColor(kpsSet.bgPressed), Color.parseColor(kpsSet.borderPressed), borderPx, radiusPx)
        totalContainer?.background = createAlphaSelector(Color.parseColor(totalSet.bgNormal), Color.parseColor(totalSet.borderNormal), Color.parseColor(totalSet.bgPressed), Color.parseColor(totalSet.borderPressed), borderPx, radiusPx)

        /* legacy selector fallback */
        val applyToTextView = { tv: TextView? ->
            tv?.apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                this.typeface = typeface
                paintFlags = if (isUnderline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        applyToTextView(tvKpsLabel)
        applyToTextView(tvKpsValue)
        applyToTextView(tvTotalLabel)
        applyToTextView(tvTotalValue)

        // ================= KÍCH HOẠT LAYOUT ENGINE CHO TẤT CẢ CÁC MODE =================
        val frameWorkspace = keysContainer.findViewWithTag<FrameLayout>("WORKSPACE_${keyMode}K")
        if (frameWorkspace != null) {
            val keyContainersArr = arrayOfNulls<LinearLayout>(keyMode)

            for (i in 0 until keyMode) {
                val container = frameWorkspace.getChildAt(i) as? LinearLayout ?: continue
                keyContainersArr[i] = container

                val keySet = colorsFor("key_$i")
                container.background = createAlphaSelector(Color.parseColor(keySet.bgNormal), Color.parseColor(keySet.borderNormal), Color.parseColor(keySet.bgPressed), Color.parseColor(keySet.borderPressed), borderPx, radiusPx)
                (container.getChildAt(0) as? TextView)?.apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
                    setTextColor(Color.parseColor(keySet.textNormal))
                    this.typeface = typeface
                    paintFlags = if (isUnderline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
                (container.getChildAt(1) as? TextView)?.apply {
                    setTextColor(Color.parseColor(keySet.textNormal))
                    this.typeface = typeface
                    visibility = if (showCounters) View.VISIBLE else View.GONE
                }
            }

            // Gọi Engine tính toán tọa độ
            if (keyMode == 4 || keyMode == 6 || keyMode == 8) {
                LayoutEngine.applyStandardLayout(frameWorkspace, keyContainersArr, widthPx, heightPx, spacingPx, kpsContainer, totalContainer, keyMode)
            } else if (keyMode == 10) {
                LayoutEngine.apply10KJipperLayout(frameWorkspace, keyContainersArr, widthPx, heightPx, spacingPx, kpsContainer, totalContainer)
            } else if (keyMode == 12) {
                LayoutEngine.apply12KLayout(frameWorkspace, keyContainersArr, widthPx, heightPx, spacingPx, kpsContainer, totalContainer)
            } else if (keyMode == 16) {
                LayoutEngine.apply16KLayout(frameWorkspace, keyContainersArr, widthPx, heightPx, spacingPx, kpsContainer, totalContainer)
            }

            // ĐỒNG BỘ ĐỘ LỚN FONT CHỮ KPS/TOTAL VÀ AUTOSIZE
            val kpsTextSizeSp = if (keyMode == 10 || keyMode == 12) (textSize * 0.7f) else textSize.toFloat()

            tvKpsLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, kpsTextSizeSp)
            tvTotalLabel?.setTextSize(TypedValue.COMPLEX_UNIT_SP, kpsTextSizeSp)

            tvKpsValue?.setTextSize(TypedValue.COMPLEX_UNIT_SP, kpsTextSizeSp)
            tvTotalValue?.setTextSize(TypedValue.COMPLEX_UNIT_SP, kpsTextSizeSp)

            val minSizeSp = 10
            val maxSizeSp = kpsTextSizeSp.toInt().coerceAtLeast(minSizeSp + 2)

            tvKpsValue?.apply {
                maxLines = 1
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, minSizeSp, maxSizeSp, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
            tvTotalValue?.apply {
                maxLines = 1
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, minSizeSp, maxSizeSp, 1, TypedValue.COMPLEX_UNIT_SP
                )
            }
        }

        keyTrailView.layoutParams.height = limitPx
        keyTrailView.setParameters(currentSpeed, currentLimit.toFloat())
        keyTrailView.setThemeColors(rainColor, rainShadow)
        keyTrailView.setAdvancedColors(if (colorMode == ThemeColorStore.ADVANCED) Array(keyMode) { colorsFor("key_$it") } else null)
        keyTrailView.setRow2ThemeColors(rainColor2, rainShadow2)
        // Gửi trạng thái bật/tắt bóng xuống cho KeyTrailView
        keyTrailView.setShadowConfig(swEnableShadow.isChecked, swPerformanceShadow.isChecked)

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

        tvKeySpacingLabel.text = getString(R.string.spacing_label, currentKeySpacing)
        tvBorderWidthLabel.text = getString(R.string.border_width_label, seekBorderWidth.value.toInt())
        tvCornerRadiusLabel.text = getString(R.string.corner_radius_label, seekCornerRadius.value.toInt())
    }

    private fun saveAndExit() {
        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val themeDraft = ThemeDraftBridge.draft
        sharedPref.edit().apply {
            putFloat("viewer_x", currentX)
            putFloat("viewer_y", currentY)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)

            putInt("key_spacing", currentKeySpacing)
            putInt("theme_border_width", seekBorderWidth.value.toInt())
            putInt("theme_corner_radius", seekCornerRadius.value.toInt())
            putBoolean("theme_keyrain_enabled", swEnableKeyRain.isChecked)

            putBoolean("show_key_counters", swShowKeyCounters.isChecked)
            putBoolean("theme_shadow_enabled", swEnableShadow.isChecked)
            putBoolean("theme_performance_shadow", swPerformanceShadow.isChecked)
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
            putString("theme_rain_color_2", etRainColor2Hex.text.toString())
            putString("theme_rain_shadow_2", etRainShadow2Hex.text.toString())
            val effectiveColors = themeDraft
            effectiveColors?.let {
                putString("theme_rain_color_2", it.trail2)
                putString("theme_rain_shadow_2", it.shadow2)
                putBoolean("theme_performance_shadow", it.performanceShadow)
                putBoolean("theme_text_bold", it.bold)
                putBoolean("theme_text_italic", it.italic)
                putBoolean("theme_text_underline", it.underline)
                putBoolean("show_key_counters", it.showCounters)
                ThemeColorStore.setMode(this, currentKeyMode(), it.mode)
                if (it.mode == ThemeColorStore.BASIC) ThemeColorStore.writeBasic(this, it.basic, it.trail2, it.shadow2)
                it.advanced.forEach { (target, colors) -> ThemeColorStore.writeAdvanced(this, currentKeyMode(), target, colors) }
            }
            putBoolean("is_keyviewer_configured", true)
            apply()
        }

        triggerOverlayRefresh()
        ThemeDraftBridge.draft = null
        advancedDraft.clear()
        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun resetToDefault() {
        currentScale = 0.35f
        currentSpeed = 0.8f
        currentLimit = 280
        currentKeySpacing = 7
        currentX = 0f
        currentY = 0f

        val sharedPref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putFloat("viewer_x", currentX)
            putFloat("viewer_y", currentY)
            putFloat("viewer_scale", currentScale)
            putFloat("trail_speed", currentSpeed)
            putInt("trail_limit_px", currentLimit)
            putInt("key_spacing", currentKeySpacing)
            putInt("theme_border_width", 2)
            putInt("theme_corner_radius", 6)

            putString("theme_bg_normal", "#000000")
            putString("theme_border_normal", "#FFFFFF")
            putString("theme_bg_pressed", "#FFFFFF")
            putString("theme_border_pressed", "#FFFFFF")
            putString("theme_text_color", "#FFFFFF")
            putString("theme_text_color_pressed", "#000000")
            putString("theme_rain_color", "#FFFFFF")
            putString("theme_rain_shadow", "#000000")
            putString("theme_rain_color_2", "#FF999898")
            putString("theme_rain_shadow_2", "#FFFFFF")
            putFloat("theme_text_size", 20f)
            putBoolean("theme_text_bold", true)
            putBoolean("theme_text_italic", false)
            putBoolean("theme_text_underline", false)

            putBoolean("is_keyviewer_configured", true)
            apply()
        }

        loadPreferences()
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

        if (!pref.contains("is_first_theme_setup")) {
            val editor = pref.edit()
            editor.putBoolean("is_first_theme_setup", true)
            editor.putInt("saved_preset_index_${currentKeyMode()}_$colorMode", 0)

            editor.putString("theme_text_color", "#FFFFFF")
            editor.putString("theme_text_color_pressed", "#FF000000")
            editor.putString("theme_bg_normal", "#000000")
            editor.putString("theme_bg_pressed", "#FFFFFF")
            editor.putString("theme_border_normal", "#FFFFFF")
            editor.putString("theme_border_pressed", "#FFFFFF")
            editor.putString("theme_rain_color", "#FFFFFF")
            editor.putString("theme_rain_shadow", "#FF000000")
            editor.putString("theme_rain_color_2", "#FF999898")
            editor.putString("theme_rain_shadow_2", "#FFFFFF")
            editor.putFloat("theme_text_size", 20f)
            editor.putBoolean("theme_text_bold", false)
            editor.putBoolean("theme_text_italic", false)
            editor.putBoolean("theme_text_underline", false)
            editor.putInt("theme_border_width", 2)
            editor.putInt("theme_corner_radius", 6)

            editor.apply()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            ThemeDraftBridge.draft = null
            advancedDraft.clear()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        AppState.isAppVisible = true
        hideSystemUI()
        stopService(Intent(this, OverlayService::class.java))
    }

    override fun onPause() {
        super.onPause()
        AppState.isAppVisible = false
    }
}