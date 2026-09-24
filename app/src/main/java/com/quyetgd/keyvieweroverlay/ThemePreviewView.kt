package com.quyetgd.keyvieweroverlay

import com.quyetgd.keyvieweroverlay.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/**
 * Read-only preview built from the same views used by OverlayService.
 * The inner overlay keeps its real dimensions and is only scaled as one unit to fit this viewport.
 */
class ThemePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val pref = context.getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
    private val overlayRoot = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        pivotX = 0f
        pivotY = 0f
    }
    private val trailView = KeyTrailView(context).apply { setBackgroundColor(Color.TRANSPARENT) }
    private val workspace = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private var keyViews: Array<LinearLayout?> = emptyArray()
    private var keyLabels: Array<TextView?> = emptyArray()
    private var keyCounters: Array<FastCounterView?> = emptyArray()
    private var kpsContainer: LinearLayout? = null
    private var totalContainer: LinearLayout? = null
    private var kpsLabel: TextView? = null
    private var totalLabel: TextView? = null
    private var kpsValue: FastCounterView? = null
    private var totalValue: FastCounterView? = null
    private var activeTrail: KeyTrailView.Trail? = null
    private var activeKey = -1
    private var naturalWidth = 1
    private var naturalHeight = 1
    private val keyCounts = IntArray(16) { it * 7 }
    private var showCounters = false
    private var textSizeSp = 20f
    private var textTypeface: Typeface = Typeface.DEFAULT
    private var underline = false
    private var borderWidthDp = 2
    private var cornerRadiusDp = 6f
    private var shadowEnabled = true
    private var performanceShadow = false
    private var keySpacing = pref.getInt("key_spacing", 7)
    private var keyRainEnabled = pref.getBoolean("theme_keyrain_enabled", true)
    private var trailSpeedValue = pref.getFloat("trail_speed", .8f)
    private var trailLimitPx = pref.getInt("trail_limit_px", 300)

    var keyMode: Int = 6
        set(value) {
            val next = value.coerceIn(4, 16)
            if (field != next || keyViews.isEmpty()) { field = next; buildOverlay() }
        }
    var colors: Array<ThemeColorSet> = emptyArray()
        set(value) { if (!field.contentEquals(value)) { field = value; applyTheme() } }
    var kpsColors: ThemeColorSet = ThemeColorSet()
        set(value) { if (field != value) { field = value; applyTheme() } }
    var totalColors: ThemeColorSet = ThemeColorSet()
        set(value) { if (field != value) { field = value; applyTheme() } }
    var usePerKeyTrails = false
        set(value) { if (field != value) { field = value; applyTheme() } }
    var row2Trail: String = pref.getString("theme_rain_color_2", "#FFA78BFA") ?: "#FFA78BFA"
        set(value) { if (field != value) { field = value; applyTheme() } }
    var row2Shadow: String = pref.getString("theme_rain_shadow_2", "#FF7C3AED") ?: "#FF7C3AED"
        set(value) { if (field != value) { field = value; applyTheme() } }

    fun applyShadowConfig(enabled: Boolean, lightweight: Boolean) {
        shadowEnabled = enabled
        performanceShadow = lightweight
        trailView.setShadowConfig(enabled, lightweight)
        applyTheme()
    }

    fun applyPreviewConfig(
        spacing: Int, borderWidth: Int, cornerRadius: Int, rainEnabled: Boolean,
        speed: Float, limitPx: Int, shadow: Boolean, lightweightShadow: Boolean
    ) {
        val safeSpacing = spacing.coerceAtLeast(0)
        val safeLimit = limitPx.coerceAtLeast(1)
        val rebuild = keySpacing != safeSpacing || trailLimitPx != safeLimit
        val appearanceChanged = borderWidthDp != borderWidth || cornerRadiusDp != cornerRadius.toFloat()
        val trailChanged = keyRainEnabled != rainEnabled || trailSpeedValue != speed || shadowEnabled != shadow || performanceShadow != lightweightShadow
        keySpacing = safeSpacing
        trailLimitPx = safeLimit
        keyRainEnabled = rainEnabled
        trailSpeedValue = speed
        borderWidthDp = borderWidth.coerceAtLeast(0)
        cornerRadiusDp = cornerRadius.coerceAtLeast(0).toFloat()
        shadowEnabled = shadow
        performanceShadow = lightweightShadow
        if (rebuild) buildOverlay() else {
            if (trailChanged) configureTrail(dp(trailLimitPx))
            if (appearanceChanged) applyTheme()
        }
        trailView.visibility = if (keyRainEnabled) VISIBLE else INVISIBLE
    }

    fun applyStyle(bold: Boolean, italic: Boolean, underlined: Boolean, counters: Boolean) {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val base = try { ResourcesCompat.getFont(context, R.font.adofai_font) ?: Typeface.DEFAULT }
        catch (_: Exception) { Typeface.DEFAULT }
        textTypeface = Typeface.create(base, style)
        underline = underlined
        showCounters = counters
        applyTheme()
    }

    fun applyOverlayMetrics(borderWidth: Int, cornerRadius: Float) {
        borderWidthDp = borderWidth.coerceAtLeast(0)
        cornerRadiusDp = cornerRadius.coerceAtLeast(0f)
        applyTheme()
    }

    var autoPreview = true
        set(value) {
            field = value
            if (value) scheduleNext() else {
                removeCallbacks(previewStep)
                removeCallbacks(releasePreviewTrail)
                releaseCurrent()
            }
        }

    private val releasePreviewTrail = Runnable { releaseCurrent() }

    private val previewStep = object : Runnable {
        override fun run() {
            if (!autoPreview || !isAttachedToWindow || keyViews.isEmpty()) return
            releaseCurrent()
            activeKey = (activeKey + 1) % keyMode
            press(activeKey)
            removeCallbacks(releasePreviewTrail)
            postDelayed(releasePreviewTrail, 230L)
            postDelayed(this, 650L)
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = true
        clipToPadding = true
        addView(overlayRoot)
        buildOverlay()
    }

    private fun buildOverlay() {
        removeCallbacks(previewStep)
        removeCallbacks(releasePreviewTrail)
        releaseCurrent()
        overlayRoot.removeAllViews()
        workspace.removeAllViews()
        keyViews = arrayOfNulls(keyMode)
        keyLabels = arrayOfNulls(keyMode)
        keyCounters = arrayOfNulls(keyMode)

        val keyW = dp(55)
        val keyH = dp(60)
        val spacing = dp(keySpacing)
        val trailLimit = dp(trailLimitPx)
        val inputSource = pref.getString("input_source", "touch") ?: "touch"

        overlayRoot.addView(trailView, LinearLayout.LayoutParams(1, trailLimit))
        overlayRoot.addView(workspace, LinearLayout.LayoutParams(1, 1))
        repeat(keyMode) { i ->
            val key = LinearLayout(context).apply {
                layoutParams = LayoutParams(keyW, keyH)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val label = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                text = if (inputSource == "keyboard") abbreviated(pref.getString("key_name_${keyMode}_$i", null)) else (i + 1).toString()
                textSize = textSizeSp
                typeface = textTypeface
                paintFlags = if (underline) Paint.UNDERLINE_TEXT_FLAG else 0
            }
            val counter = FastCounterView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(20)).apply { bottomMargin = dp(4) }
                setTextSize(sp(textSizeSp * .65f))
                visibility = if (showCounters) VISIBLE else GONE
                setCount(keyCounts[i])
            }
            key.addView(label)
            key.addView(counter)
            workspace.addView(key)
            keyViews[i] = key
            keyLabels[i] = label
            keyCounters[i] = counter
        }
        createCounters(keyH)
        when (keyMode) {
            4, 6, 8 -> LayoutEngine.applyStandardLayout(workspace, keyViews, keyW, keyH, spacing, kpsContainer, totalContainer, keyMode)
            10 -> LayoutEngine.apply10KJipperLayout(workspace, keyViews, keyW, keyH, spacing, kpsContainer, totalContainer)
            12 -> LayoutEngine.apply12KLayout(workspace, keyViews, keyW, keyH, spacing, kpsContainer, totalContainer)
            16 -> LayoutEngine.apply16KLayout(workspace, keyViews, keyW, keyH, spacing, kpsContainer, totalContainer)
        }
        val workspaceParams = workspace.layoutParams as LinearLayout.LayoutParams
        naturalWidth = workspaceParams.width
        naturalHeight = trailLimit + workspaceParams.height
        trailView.layoutParams = LinearLayout.LayoutParams(naturalWidth, trailLimit)
        workspace.layoutParams = LinearLayout.LayoutParams(naturalWidth, workspaceParams.height)
        overlayRoot.layoutParams = LayoutParams(naturalWidth, naturalHeight)
        configureTrail(trailLimit)
        applyTheme()
        requestLayout()
        if (autoPreview && isAttachedToWindow) scheduleNext()
    }

    private fun createCounters(keyH: Int) {
        val horizontal = keyMode == 4 || keyMode == 6 || keyMode == 8 || keyMode == 16
        fun counter(labelText: String, value: Int, total: Boolean): Triple<LinearLayout, TextView, FastCounterView> {
            val box = LinearLayout(context).apply {
                orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                if (horizontal) setPadding(dp(8), 0, dp(8), 0)
                layoutParams = LayoutParams(1, keyH)
            }
            val label = TextView(context).apply {
                text = labelText
                gravity = if (horizontal) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
                includeFontPadding = false
                textSize = if (keyMode == 10 || keyMode == 12) 14f else 20f
                visibility = if (total && keyMode == 4) GONE else VISIBLE
            }
            val count = FastCounterView(context).apply {
                formatWithComma = total
                textAlignment = if (horizontal) Paint.Align.RIGHT else Paint.Align.CENTER
                setTextSize(sp(if (keyMode == 10 || keyMode == 12) 14f else 20f))
                setCount(value)
            }
            if (horizontal) {
                box.addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                box.addView(count, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            } else {
                box.addView(label, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                box.addView(count, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
            return Triple(box, label, count)
        }
        counter(context.getString(R.string.kps_label), 12, false).also { (box, label, value) -> kpsContainer = box; kpsLabel = label; kpsValue = value; workspace.addView(box) }
        counter(context.getString(R.string.total_label), 128, true).also { (box, label, value) -> totalContainer = box; totalLabel = label; totalValue = value; workspace.addView(box) }
    }

    private fun configureTrail(limit: Int) {
        trailView.setParameters(trailSpeedValue, limit.toFloat())
        trailView.setShadowConfig(shadowEnabled, performanceShadow)
        trailView.visibility = if (keyRainEnabled) VISIBLE else INVISIBLE
    }

    private fun applyTheme() {
        if (keyViews.isEmpty()) return
        keyViews.forEachIndexed { i, key ->
            val c = colors.getOrNull(i) ?: ThemeColorSet()
            key?.background = selector(c)
            keyLabels[i]?.apply {
                setTextColor(textSelector(c))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                typeface = textTypeface
                paintFlags = if (underline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            keyCounters[i]?.apply {
                setTextColor(textSelector(c))
                setTextSize(sp(textSizeSp * .65f))
                setTypeface(textTypeface)
                setUnderline(underline)
                visibility = if (showCounters) VISIBLE else GONE
            }
        }
        applyCounterTheme(kpsContainer, kpsLabel, kpsValue, kpsColors)
        applyCounterTheme(totalContainer, totalLabel, totalValue, totalColors)
        val fallback = colors.firstOrNull() ?: ThemeColorSet()
        val perKey = if (usePerKeyTrails) Array(keyMode) { colors.getOrNull(it) ?: fallback } else null
        trailView.setThemeColors(parse(fallback.trail), parse(fallback.shadow))
        trailView.setRow2ThemeColors(parse(row2Trail), parse(row2Shadow))
        trailView.setAdvancedColors(perKey)
    }

    private fun applyCounterTheme(box: View?, label: TextView?, value: FastCounterView?, c: ThemeColorSet) {
        box?.background = selector(c)
        val counterSizeSp = if (keyMode == 10 || keyMode == 12) textSizeSp * .7f else textSizeSp
        label?.apply {
            setTextColor(parse(c.textNormal))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, counterSizeSp)
            typeface = textTypeface
            paintFlags = if (underline) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        }
        value?.apply {
            setTextColor(textSelector(c))
            setTextSize(sp(counterSizeSp))
            setTypeface(textTypeface)
            setUnderline(underline)
            textAlignment = if (keyMode == 10 || keyMode == 12) Paint.Align.CENTER else Paint.Align.RIGHT
        }
    }

    private fun press(lane: Int) {
        val key = keyViews.getOrNull(lane) ?: return
        key.isPressed = true
        keyCounts[lane]++
        keyCounters[lane]?.setCount(keyCounts[lane])
        val (x, w) = trailGeometry(lane, key)
        activeTrail = trailView.addTrail(lane, x, w, SystemClock.uptimeMillis())
    }

    private fun trailGeometry(lane: Int, key: View): Pair<Float, Float> {
        var source = key
        val mapped = when (keyMode) {
            10 -> if (lane == 8) 3 else if (lane == 9) 4 else -1
            12 -> when (lane) { 8 -> 2; 9 -> 3; 10 -> 4; 11 -> 5; else -> -1 }
            16 -> if (lane >= 8) lane - 8 else -1
            else -> -1
        }
        if (mapped >= 0) source = keyViews[mapped] ?: key
        return if (mapped >= 0) source.x + source.width * .15f to source.width * .70f
        else source.x to source.width.toFloat()
    }

    private fun releaseCurrent() {
        if (activeKey >= 0) keyViews.getOrNull(activeKey)?.isPressed = false
        trailView.releaseTrail(activeTrail, SystemClock.uptimeMillis())
        activeTrail = null
    }

    private fun scheduleNext() {
        removeCallbacks(previewStep)
        postDelayed(previewStep, 250L)
    }

    override fun onDetachedFromWindow() {
        releaseResources()
        super.onDetachedFromWindow()
    }

    fun releaseResources() {
        removeCallbacks(previewStep)
        removeCallbacks(releasePreviewTrail)
        releaseCurrent()
        trailView.releaseResources()
        overlayRoot.removeAllViews()
        workspace.removeAllViews()
        keyViews = emptyArray()
        keyLabels = emptyArray()
        keyCounters = emptyArray()
        kpsContainer = null; totalContainer = null
        kpsLabel = null; totalLabel = null
        kpsValue = null; totalValue = null
        activeTrail = null
        activeKey = -1
        naturalWidth = 1; naturalHeight = 1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        overlayRoot.measure(MeasureSpec.makeMeasureSpec(naturalWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(naturalHeight, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        overlayRoot.layout(0, 0, naturalWidth, naturalHeight)
        val scale = minOf((width - dp(8)).toFloat() / naturalWidth, (height - dp(8)).toFloat() / naturalHeight).coerceAtLeast(.05f)
        overlayRoot.scaleX = scale
        overlayRoot.scaleY = scale
        overlayRoot.translationX = (width - naturalWidth * scale) / 2f
        overlayRoot.translationY = (height - naturalHeight * scale) / 2f
    }

    private fun selector(c: ThemeColorSet): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), box(parse(c.bgPressed), parse(c.borderPressed)))
        addState(intArrayOf(), box(parse(c.bgNormal), parse(c.borderNormal)))
    }
    private fun textSelector(c: ThemeColorSet) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(parse(c.textPressed), parse(c.textNormal)))
    private fun box(bg: Int, border: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(bg); setStroke(dp(borderWidthDp), border); cornerRadius = dp(cornerRadiusDp.toInt()).toFloat() }
    private fun abbreviated(value: String?): String = value?.takeIf { it.isNotBlank() }?.take(4) ?: "?"
    private fun parse(value: String): Int = try { Color.parseColor(value) } catch (_: Exception) { Color.WHITE }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
