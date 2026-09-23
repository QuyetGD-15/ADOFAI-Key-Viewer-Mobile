package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

class ThemeEditorActivity : AppCompatActivity() {
    private lateinit var preview: ThemePreviewView
    private lateinit var host: LinearLayout
    private lateinit var editorScroll: ScrollView
    private var targetListScroll: HorizontalScrollView? = null
    private lateinit var tabs: TabLayout
    private var keyMode = 6
    private var mode = ThemeColorStore.BASIC
    private var selectedTarget = "key_0"
    private val drafts = LinkedHashMap<String, ThemeColorSet>()
    private var basicDraft: ThemeColorSet? = null
    private var basicTrail2: String? = null
    private var basicShadow2: String? = null
    private var systemDraft: SystemThemePreset? = null
    private val selectedPresets = mutableMapOf<String, Int>()
    private var draftBold = false
    private var draftItalic = false
    private var draftUnderline = false
    private var draftShowCounters = false
    private var draftPerformanceShadow = false
    private var draftSubmitted = false
    private val pref by lazy { getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_theme_editor)
        val shared = ThemeDraftBridge.draft
        keyMode = pref.getInt("current_key_mode", 6)
        mode = shared?.mode ?: ThemeColorStore.mode(pref, keyMode)
        if (shared != null) {
            basicDraft = shared.basic
            basicTrail2 = shared.trail2
            basicShadow2 = shared.shadow2
            drafts.putAll(shared.advanced)
            draftBold = shared.bold; draftItalic = shared.italic; draftUnderline = shared.underline
            draftShowCounters = shared.showCounters; draftPerformanceShadow = shared.performanceShadow
        } else {
            draftBold = pref.getBoolean("theme_text_bold", false)
            draftItalic = pref.getBoolean("theme_text_italic", false)
            draftUnderline = pref.getBoolean("theme_text_underline", false)
            draftShowCounters = pref.getBoolean("show_key_counters", false)
            draftPerformanceShadow = pref.getBoolean("theme_performance_shadow", false)
        }
        host = findViewById(R.id.themeEditorHost)
        editorScroll = findViewById(R.id.themeEditorScroll)
        editorScroll.isFocusableInTouchMode = true
        editorScroll.requestFocus()
        tabs = findViewById(R.id.themeTabs)
        preview = findViewById(R.id.themePreview)
        preview.keyMode = keyMode
        preview.autoPreview = true
        findViewById<View>(R.id.btnThemeBack).setOnClickListener { cancelEditing() }
        findViewById<View>(R.id.btnThemeSave).setOnClickListener { save(); finish() }
        tabs.setBackground(GradientDrawable().apply { setColor(0xFF1D1D1D.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFF414141.toInt()) })
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4))
        tabs.addTab(tabs.newTab().setText(R.string.theme_mode_basic)); tabs.addTab(tabs.newTab().setText(R.string.theme_mode_advanced))
        tabs.getTabAt(if (mode == ThemeColorStore.ADVANCED) 1 else 0)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { mode = if (tab.position == 1) ThemeColorStore.ADVANCED else ThemeColorStore.BASIC; buildEditor() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        buildEditor()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun presetSelection() = selectedPresets[mode] ?: pref.getInt("theme_editor_preset_${keyMode}_$mode", 0).coerceIn(0, 7)
    private fun targets(): List<String> = buildList { repeat(keyMode) { add("key_$it") }; add("kps"); add("total") }
    private fun label(target: String) = when (target) { "kps" -> getString(R.string.kps_label); "total" -> getString(R.string.total_label); else -> getString(R.string.theme_target_key, target.substringAfter('_').toInt() + 1) }
    private fun basic() = basicDraft ?: ThemeColorStore.basic(pref)
    private fun trail2() = basicTrail2 ?: ThemeColorStore.basicTrail2(pref).first
    private fun shadow2() = basicShadow2 ?: ThemeColorStore.basicTrail2(pref).second
    private fun current(target: String): ThemeColorSet = if (systemDraft != null) {
        val preset = systemDraft!!
        when (target) { "kps" -> preset.kps; "total" -> preset.total; else -> preset.keys.getOrElse(target.substringAfter('_').toIntOrNull() ?: 0) { basic() } }
    } else if (mode == ThemeColorStore.ADVANCED) {
        drafts.getOrPut(target) { ThemeColorStore.advanced(pref, keyMode, target, basic()) }
    } else basic()

    private fun buildEditor(preserveScroll: Boolean = false) {
        val previousY = if (preserveScroll) editorScroll.scrollY else 0
        val previousX = if (preserveScroll) targetListScroll?.scrollX ?: 0 else 0
        host.removeAllViews()
        targetListScroll = null
        addPresetBar()
        if (mode == ThemeColorStore.ADVANCED || systemDraft != null) addTargetList(previousX)
        else addDescription(getString(R.string.theme_editor_basic_desc))
        addStyleControls()
        addSectionTitle(getString(R.string.theme_editor_colors))
        val c = current(selectedTarget)
        addColor(getString(R.string.theme_editor_text), c.textNormal) { update(c.copy(textNormal = it)) }
        addColor(getString(R.string.theme_editor_background), c.bgNormal) { update(c.copy(bgNormal = it)) }
        addColor(getString(R.string.theme_editor_border), c.borderNormal) { update(c.copy(borderNormal = it)) }
        if (mode == ThemeColorStore.BASIC) {
            addSectionTitle(getString(R.string.theme_editor_rain_row1))
            addColor(getString(R.string.theme_editor_rain_color_row1), c.trail) { update(c.copy(trail = it)) }
            addColor(getString(R.string.theme_editor_rain_shadow_row1), c.shadow) { update(c.copy(shadow = it)) }
            if (keyMode > 8) {
                addSectionTitle(getString(R.string.theme_editor_rain_row2))
                addColor(getString(R.string.theme_rain_color_row2), trail2()) { basicTrail2 = it; refreshPreview() }
                addColor(getString(R.string.theme_rain_shadow_row2), shadow2()) { basicShadow2 = it; refreshPreview() }
            }
        } else if (selectedTarget.startsWith("key_")) {
            addSectionTitle(getString(R.string.theme_editor_pressed))
            addColor(getString(R.string.theme_editor_text_pressed), c.textPressed) { update(c.copy(textPressed = it)) }
            addColor(getString(R.string.theme_editor_background_pressed), c.bgPressed) { update(c.copy(bgPressed = it)) }
            addColor(getString(R.string.theme_editor_border_pressed), c.borderPressed) { update(c.copy(borderPressed = it)) }
            addSectionTitle(getString(R.string.theme_editor_rain))
            addColor(getString(R.string.theme_color_rain), c.trail) { update(c.copy(trail = it)) }
            addColor(getString(R.string.theme_color_rain_shadow), c.shadow) { update(c.copy(shadow = it)) }
        }
        refreshPreview()
        editorScroll.post { editorScroll.scrollTo(0, previousY) }
    }

    private fun addStyleControls() {
        val surface = GradientDrawable().apply { setColor(0xFF2B2B2B.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0xFF414141.toInt()) }
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = surface; setPadding(dp(8), 0, dp(8), 0) }
        fun styleCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val cb = CheckBox(this).apply { this.text = text; isChecked = checked; setTextColor(Color.WHITE); minHeight = dp(52); setOnCheckedChangeListener { _, value -> onChange(value) } }
            styleRow.addView(cb, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        styleCheck(getString(R.string.theme_bold), draftBold) { draftBold = it; refreshPreview() }
        styleCheck(getString(R.string.theme_italic), draftItalic) { draftItalic = it; refreshPreview() }
        styleCheck(getString(R.string.theme_underline), draftUnderline) { draftUnderline = it; refreshPreview() }
        host.addView(styleRow, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })
        val counter = SwitchMaterial(this).apply {
            text = getString(R.string.theme_show_counters)
            isChecked = draftShowCounters
            setTextColor(Color.WHITE)
            background = surface.constantState?.newDrawable()?.mutate()
            setPadding(dp(12), 0, dp(12), 0)
            setOnCheckedChangeListener { _, value -> draftShowCounters = value; refreshPreview() }
        }
        host.addView(counter, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(8) })
        val shadow = SwitchMaterial(this).apply {
            text = getString(R.string.pref_performance_shadow)
            isChecked = draftPerformanceShadow
            setTextColor(Color.WHITE)
            background = surface.constantState?.newDrawable()?.mutate()
            setPadding(dp(12), 0, dp(12), 0)
            setOnCheckedChangeListener { _, value -> draftPerformanceShadow = value; refreshPreview() }
        }
        host.addView(shadow, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(8) })
    }

    private fun addTargetList(previousX: Int = 0) {
        addDescription(getString(R.string.theme_editor_target_hint))
        val title = TextView(this).apply {
            text = getString(R.string.theme_editor_editing_target, label(selectedTarget))
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 4, 0, 8)
        }
        host.addView(title)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        targets().forEach { target ->
            val item = MaterialButton(this).apply {
                text = label(target)
                isAllCaps = false
                minimumWidth = dp(92)
                minimumHeight = dp(48)
                val selected = target == selectedTarget
                backgroundTintList = ColorStateList.valueOf(if (selected) Color.WHITE else 0xFF363636.toInt())
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                alpha = 1f
                setOnClickListener { selectedTarget = target; buildEditor(preserveScroll = true) }
            }
            list.addView(item, LinearLayout.LayoutParams(-2, dp(60)).apply { rightMargin = dp(8) })
        }
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            background = GradientDrawable().apply { setColor(0xFF2B2B2B.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(1), 0xFF414141.toInt()) }
            setPadding(dp(8), 0, dp(8), 0)
            addView(list, ViewGroup.LayoutParams(-2, dp(64)))
        }
        targetListScroll = horizontal
        host.addView(horizontal)
        horizontal.post { horizontal.scrollTo(previousX, 0) }
    }

    private fun addPresetBar() {
        val names = listOf(getString(R.string.preset_default), getString(R.string.preset_jipper)) + (0 until ThemeColorStore.SYSTEM_PRESET_COUNT).map {
            ThemeColorStore.systemPreset(keyMode, it, basic()).name
        } + if (mode == ThemeColorStore.BASIC) (1..3).map { getString(R.string.theme_editor_custom_preset, it) }
        else (4..6).map { getString(R.string.theme_editor_custom_preset, it) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val selectorSurface = GradientDrawable().apply { setColor(0xFF2B2B2B.toInt()); cornerRadius = dp(10).toFloat(); setStroke(dp(1), 0xFF414141.toInt()) }
        val spinner = Spinner(this).apply {
            isFocusableInTouchMode = false
            background = selectorSurface
            setPadding(dp(10), 0, dp(10), 0)
            setPopupBackgroundDrawable(GradientDrawable().apply { setColor(0xFF303030.toInt()) })
            adapter = ArrayAdapter(this@ThemeEditorActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(presetSelection())
        }
        val caption = TextView(this).apply { text = getString(R.string.theme_editor_preset_mode, keyMode); setTextColor(Color.WHITE); textSize = 14f }
        val saveButton = MaterialButton(this).apply { text = getString(R.string.theme_editor_save_preset); isAllCaps = false }
        row.addView(caption, LinearLayout.LayoutParams(dp(110), dp(52)))
        row.addView(spinner, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(saveButton, LinearLayout.LayoutParams(-2, dp(52)))
        host.addView(row)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != presetSelection()) {
                    selectedPresets[mode] = position
                    loadPreset(position)
                    buildEditor(preserveScroll = true)
                }
            }
        }
        saveButton.setOnClickListener {
            val slot = spinner.selectedItemPosition
            if (slot < 5) Toast.makeText(this, R.string.toast_cannot_overwrite_system, Toast.LENGTH_SHORT).show()
            else { savePreset(slot); Toast.makeText(this, getString(R.string.theme_editor_preset_saved, names[slot]), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun loadPreset(slot: Int) {
        systemDraft = null
        val default = if (slot == 1) ThemeColorSet("#FFFFFFFF", "#FF000000", "#338E3CFF", "#FFFFFFFF", "#FF8C3EFF", "#FFFFFFFF", "#FF8C3EFF", "#FF000000") else ThemeColorSet()
        when {
            slot in 2..4 -> {
                systemDraft = ThemeColorStore.systemPreset(keyMode, slot - 2, basic())
                basicDraft = systemDraft!!.keys.first()
                drafts.clear()
                targets().forEach { target -> drafts[target] = current(target) }
            }
            mode == ThemeColorStore.BASIC -> {
                basicDraft = if (slot < 2) default else ThemeColorStore.advancedPreset(pref, keyMode, slot - 4, "basic", basic())
                if (slot >= 5) {
                    basicTrail2 = pref.getString("theme_basic_preset_${keyMode}_${slot - 4}_trail2", trail2())
                    basicShadow2 = pref.getString("theme_basic_preset_${keyMode}_${slot - 4}_shadow2", shadow2())
                }
            }
            else -> targets().forEach { target ->
                drafts[target] = if (slot < 2) default else ThemeColorStore.advancedPreset(pref, keyMode, slot - 1, target, basic())
            }
        }
    }

    private fun savePreset(slot: Int) {
        val editor = pref.edit()
        if (mode == ThemeColorStore.BASIC) {
            ThemeColorStore.writeAdvancedPreset(editor, keyMode, slot - 4, "basic", basic())
            editor.putString("theme_basic_preset_${keyMode}_${slot - 4}_trail2", trail2())
            editor.putString("theme_basic_preset_${keyMode}_${slot - 4}_shadow2", shadow2())
        } else targets().forEach { target -> ThemeColorStore.writeAdvancedPreset(editor, keyMode, slot - 1, target, current(target)) }
        editor.apply()
    }

    private fun addDescription(text: String) { TextView(this).apply { this.text = text; setTextColor(0x99FFFFFF.toInt()); textSize = 14f; setPadding(0, dp(12), 0, dp(14)); host.addView(this) } }
    private fun addSectionTitle(title: String) {
        TextView(this).apply {
            text = title
            setTextColor(0xFFD7C7FF.toInt())
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(10))
            host.addView(this)
        }
    }
    private fun addColor(title: String, value: String, onChange: (String) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)); background = GradientDrawable().apply { setColor(0xFF303030.toInt()); cornerRadius = dp(10).toFloat(); setStroke(dp(1), 0xFF414141.toInt()) } }
        val name = TextView(this).apply { text = title; textSize = 16f; setTextColor(Color.WHITE) }
        val hex = EditText(this).apply {
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(value); textSize = 14f; setTextColor(Color.WHITE); setSelectAllOnFocus(true); imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val swatch = View(this).apply { background = GradientDrawable().apply { setColor(parse(value)); cornerRadius = dp(8).toFloat() } }
        fun commit(raw: String) {
            val normalized = try { if (raw.trim().matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) ThemeColorSet.normalize(raw.trim()) else null } catch (_: Exception) { null }
            if (normalized != null) {
                onChange(normalized)
                hex.setText(normalized)
                swatch.background = GradientDrawable().apply { setColor(parse(normalized)); cornerRadius = dp(8).toFloat() }
            } else hex.error = getString(R.string.theme_editor_invalid_hex)
        }
        hex.setOnEditorActionListener { _, _, _ -> commit(hex.text.toString()); true }
        hex.setOnFocusChangeListener { _, focus -> if (!focus && hex.text.toString() != value) commit(hex.text.toString()) }
        swatch.setOnClickListener { showColorPickerDialog(title, parse(hex.text.toString())) { color -> commit(String.format("#%08X", color.toLong() and 0xffffffffL)) } }
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(hex, LinearLayout.LayoutParams(dp(116), -2).apply { rightMargin = dp(8) })
        row.addView(swatch, LinearLayout.LayoutParams(dp(50), dp(42)))
        host.addView(row, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(10) })
    }
    private fun update(value: ThemeColorSet) {
        val system = systemDraft
        if (system != null) {
            val target = if (mode == ThemeColorStore.BASIC) "key_0" else selectedTarget
            systemDraft = when (target) {
                "kps" -> system.copy(kps = value)
                "total" -> system.copy(total = value)
                else -> {
                    val keys = system.keys.copyOf()
                    val index = target.substringAfter('_').toIntOrNull() ?: 0
                    if (index in keys.indices) keys[index] = value
                    system.copy(keys = keys)
                }
            }
        } else if (mode == ThemeColorStore.ADVANCED) drafts[selectedTarget] = value
        else basicDraft = value
        refreshPreview()
    }
    private fun refreshPreview() {
        val fallback = basic()
        val system = systemDraft
        val sets = Array(keyMode) { i -> when {
            system != null -> system.keys[i]
            mode == ThemeColorStore.ADVANCED -> drafts["key_$i"] ?: ThemeColorStore.advanced(pref, keyMode, "key_$i", fallback)
            else -> fallback
        } }
        preview.usePerKeyTrails = system != null || mode == ThemeColorStore.ADVANCED
        preview.row2Trail = trail2()
        preview.row2Shadow = shadow2()
        preview.colors = sets
        preview.kpsColors = system?.kps ?: if (mode == ThemeColorStore.ADVANCED) drafts["kps"] ?: fallback else fallback
        preview.totalColors = system?.total ?: if (mode == ThemeColorStore.ADVANCED) drafts["total"] ?: fallback else fallback
        preview.applyStyle(draftBold, draftItalic, draftUnderline, draftShowCounters)
        val draft = ThemeDraftBridge.draft
        preview.applyPreviewConfig(
            draft?.keySpacing ?: pref.getInt("key_spacing", 7),
            draft?.borderWidth ?: pref.getInt("theme_border_width", 2),
            draft?.cornerRadius ?: pref.getInt("theme_corner_radius", 6),
            draft?.keyRainEnabled ?: pref.getBoolean("theme_keyrain_enabled", true),
            draft?.trailSpeed ?: pref.getFloat("trail_speed", .8f),
            draft?.trailLimitPx ?: pref.getInt("trail_limit_px", 300),
            draft?.shadowEnabled ?: pref.getBoolean("theme_shadow_enabled", true),
            draftPerformanceShadow
        )
    }
    private fun parse(v: String) = try { Color.parseColor(v) } catch (_: Exception) { Color.WHITE }
    private fun save() {
        val effectiveMode = if (systemDraft != null) ThemeColorStore.ADVANCED else mode
        val advanced = LinkedHashMap(ThemeDraftBridge.draft?.advanced ?: drafts)
        if (systemDraft != null) {
            systemDraft!!.keys.forEachIndexed { i, colors -> advanced["key_$i"] = colors }
            advanced["kps"] = systemDraft!!.kps; advanced["total"] = systemDraft!!.total
        }
        val previous = ThemeDraftBridge.draft
        ThemeDraftBridge.draft = ThemeDraft(
            effectiveMode, basic(), trail2(), shadow2(), advanced,
            draftBold, draftItalic, draftUnderline, draftShowCounters, draftPerformanceShadow,
            previous?.keySpacing ?: pref.getInt("key_spacing", 7),
            previous?.borderWidth ?: pref.getInt("theme_border_width", 2),
            previous?.cornerRadius ?: pref.getInt("theme_corner_radius", 6),
            previous?.keyRainEnabled ?: pref.getBoolean("theme_keyrain_enabled", true),
            previous?.trailSpeed ?: pref.getFloat("trail_speed", .8f),
            previous?.trailLimitPx ?: pref.getInt("trail_limit_px", 300),
            previous?.shadowEnabled ?: pref.getBoolean("theme_shadow_enabled", true),
            previous?.presetColors ?: LinkedHashMap(), previous?.presetTrail2 ?: LinkedHashMap()
        )
        setResult(RESULT_OK)
        draftSubmitted = true
    }

    private fun cancelEditing() {
        ThemeDraftBridge.draft = null
        draftSubmitted = false
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) {
            preview.releaseResources()
            targetListScroll = null
            if (!draftSubmitted) ThemeDraftBridge.draft = null
            drafts.clear()
            selectedPresets.clear()
            basicDraft = null
            basicTrail2 = null
            basicShadow2 = null
            systemDraft = null
        }
        super.onDestroy()
    }

    private fun showColorPickerDialog(title: String, color: Int, onSelected: (Int) -> Unit) {
        val v = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val a = v.findViewById<Slider>(R.id.seekA)
        val r = v.findViewById<Slider>(R.id.seekR)
        val g = v.findViewById<Slider>(R.id.seekG)
        val b = v.findViewById<Slider>(R.id.seekB)
        val previewColor = v.findViewById<View>(R.id.viewColorPreview)
        val values = listOf(
            r to v.findViewById<EditText>(R.id.etR),
            g to v.findViewById<EditText>(R.id.etG),
            b to v.findViewById<EditText>(R.id.etB),
            a to v.findViewById<EditText>(R.id.etA)
        )
        a.value = Color.alpha(color) / 255f
        r.value = Color.red(color) / 255f
        g.value = Color.green(color) / 255f
        b.value = Color.blue(color) / 255f
        fun selectedColor() = Color.argb(
            (a.value * 255).toInt(), (r.value * 255).toInt(),
            (g.value * 255).toInt(), (b.value * 255).toInt()
        )
        fun refreshDialogPreview() {
            previewColor.setBackgroundColor(selectedColor())
            values.forEach { (slider, field) ->
                if (!field.hasFocus()) field.setText((slider.value * 255).toInt().toString())
            }
        }
        values.forEach { (slider, field) ->
            slider.addOnChangeListener { _, _, fromUser -> if (fromUser) refreshDialogPreview() }
            field.setOnFocusChangeListener { _, focused ->
                if (!focused) {
                    field.text.toString().toIntOrNull()?.coerceIn(0, 255)?.let { slider.value = it / 255f }
                    refreshDialogPreview()
                }
            }
        }
        refreshDialogPreview()
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(v)
            .setPositiveButton(R.string.color_ok) { _, _ -> onSelected(selectedColor()) }
            .setNegativeButton(R.string.color_cancel, null)
            .show()
    }
}
