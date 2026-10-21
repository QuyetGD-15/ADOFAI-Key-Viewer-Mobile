package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class HitboxConfigActivity : AppCompatActivity() {

    private lateinit var hitboxes: Array<HitboxView>
    private var keyMode: Int = 6
    private var dirty = false
    private var draftReady = false
    private var selectedIndex = 0
    private var isAutoMode = false
    private lateinit var autoPreview: AutoHitboxPreviewView
    private lateinit var gridOverlay: View

    companion object {
        private const val HITBOX_PREFS = "HitboxPrefs"
        private const val PREF_AUTO_MODE = "hitbox_auto_mode"
        private const val PREF_AUTO_DIVIDER = "hitbox_auto_divider_fraction"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Thiết lập giao diện tràn viền để lấy tọa độ chuẩn xác nhất
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Ẩn thanh trạng thái, điều hướng
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            .let { controller ->
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        setContentView(R.layout.activity_hitbox_config)

        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        keyMode = savedInstanceState?.getInt("draft_mode") ?: pref.getInt("current_key_mode", 6)

        setupHitboxesAndGrid()
        autoPreview = findViewById(R.id.auto_hitbox_preview)
        val hitboxPrefs = getSharedPreferences(HITBOX_PREFS, Context.MODE_PRIVATE)
        isAutoMode = hitboxPrefs.getBoolean(PREF_AUTO_MODE, false)
        autoPreview.dividerFraction = hitboxPrefs.getFloat(PREF_AUTO_DIVIDER, 0.5f)
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_auto).isChecked = isAutoMode
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_custom).isChecked = !isAutoMode
        autoPreview.onDividerChanged = {
            dirty = true
            hitboxPrefs.edit().putFloat(PREF_AUTO_DIVIDER, autoPreview.dividerFraction).apply()
            updateStatus()
        }
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_custom).setOnClickListener { setHitboxMode(false) }
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_auto).setOnClickListener { setHitboxMode(true) }
        setHitboxMode(isAutoMode)
        val toolbar = findViewById<View>(R.id.hitbox_toolbar)
        val margin = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val params = view.layoutParams as FrameLayout.LayoutParams
            params.setMargins(margin + safe.left, margin + safe.top, margin + safe.right, margin)
            view.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
        findViewById<Button>(R.id.btnSaveHitbox).isEnabled = false
        findViewById<Button>(R.id.btnResetHitbox).isEnabled = false
        findViewById<FrameLayout>(R.id.hitbox_root).doOnLayout {
            loadHitboxCoordinates()
            val geometry = savedInstanceState?.getFloatArray("draft_geometry")
            if (geometry != null && geometry.size == keyMode * 4) {
                hitboxes.forEachIndexed { index, view ->
                    view.x = geometry[index * 4]
                    view.y = geometry[index * 4 + 1]
                    view.layoutParams = view.layoutParams.apply {
                        width = geometry[index * 4 + 2].toInt()
                        height = geometry[index * 4 + 3].toInt()
                    }
                }
            }
            dirty = savedInstanceState?.getBoolean("draft_dirty") ?: false
            selectedIndex = (savedInstanceState?.getInt("draft_selected") ?: 0).coerceIn(hitboxes.indices)
            selectHitbox(hitboxes[selectedIndex])
            draftReady = true
            findViewById<Button>(R.id.btnSaveHitbox).isEnabled = true
            findViewById<Button>(R.id.btnResetHitbox).isEnabled = true
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestClose()
        })
        findViewById<Button>(R.id.btnCloseHitbox).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<Button>(R.id.btnSaveHitbox).setOnClickListener {
            saveHitboxCoordinates()
        }

        findViewById<Button>(R.id.btnResetHitbox).setOnClickListener {
            if (isAutoMode) {
                autoPreview.dividerFraction = 0.5f
                hitboxPrefs.edit().putFloat(PREF_AUTO_DIVIDER, 0.5f).apply()
                dirty = true
                updateStatus()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.hitbox_editor_reset_title)
                    .setMessage(R.string.hitbox_editor_reset_message)
                    .setNegativeButton(R.string.hitbox_editor_cancel, null)
                    .setPositiveButton(R.string.reset) { _, _ ->
                        resetHitboxesToDefault()
                        dirty = true
                        updateStatus()
                    }
                    .show()
            }
        }
        // Báo cho OverlayService biết để mở chế độ chỉnh sửa
        sendBroadcast(Intent(OverlayService.ACTION_START_EDIT).setPackage(packageName))
    }

    private fun setHitboxMode(auto: Boolean) {
        isAutoMode = auto
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_auto).isChecked = auto
        findViewById<android.widget.RadioButton>(R.id.radio_hitbox_custom).isChecked = !auto
        autoPreview.visibility = if (auto) View.VISIBLE else View.GONE
        hitboxes.forEach { it.visibility = if (auto) View.GONE else View.VISIBLE }
        gridOverlay.visibility = if (auto) View.GONE else View.VISIBLE
        getSharedPreferences(HITBOX_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_AUTO_MODE, auto)
            .apply()

        findViewById<TextView>(R.id.hitbox_status).text = getString(
            if (auto) R.string.hitbox_mode_auto_status else R.string.hitbox_mode_custom_status
        )
    }

    private fun setupHitboxesAndGrid() {
        val root = findViewById<FrameLayout>(R.id.hitbox_root)
        root.setBackgroundColor(Color.parseColor("#151515"))

        // Thêm Grid lines mờ làm nền
        gridOverlay = object : View(this) {
            private val paint = Paint().apply {
                color = Color.parseColor("#252525")
                strokeWidth = 2f
            }
            override fun onDraw(canvas: Canvas) {
                val step = width.toFloat() / keyMode
                for (i in 1 until keyMode) {
                    val x = i * step
                    canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                }
            }
        }
        root.addView(gridOverlay, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Khởi tạo danh sách Hitbox dựa theo keyMode
        // Nằm trên lưới nhưng dưới thanh công cụ cố định trong XML.
        hitboxes = Array(keyMode) { index ->
            HitboxView(this).apply {
                hitboxNumber = (index + 1).toString()
                layoutParams = FrameLayout.LayoutParams(100, 100)
                onSelected = { selectHitbox(this) }
                onGeometryChanged = {
                    dirty = true
                    updateStatus()
                }
            }.also { root.addView(it, index + 1) }
        }

    }

    private fun selectHitbox(selected: HitboxView) {
        selectedIndex = hitboxes.indexOf(selected)
        hitboxes.forEach { it.editingSelected = it === selected }
        updateStatus()
    }

    private fun updateStatus() {
        val selection = getString(R.string.hitbox_editor_status, hitboxes[selectedIndex].hitboxNumber)
        findViewById<TextView>(R.id.hitbox_status).text = if (dirty) {
            "$selection • ${getString(R.string.hitbox_editor_unsaved)}"
        } else selection
    }

    private fun requestClose() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hitbox_editor_discard_title)
            .setMessage(R.string.hitbox_editor_discard_message)
            .setNegativeButton(R.string.hitbox_editor_cancel, null)
            .setPositiveButton(R.string.hitbox_editor_discard) { _, _ -> finish() }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (draftReady) {
            outState.putInt("draft_mode", keyMode)
            outState.putBoolean("draft_dirty", dirty)
            outState.putInt("draft_selected", selectedIndex)
            outState.putFloatArray("draft_geometry", FloatArray(keyMode * 4).also { geometry ->
                hitboxes.forEachIndexed { index, view ->
                    geometry[index * 4] = view.x
                    geometry[index * 4 + 1] = view.y
                    geometry[index * 4 + 2] = view.layoutParams.width.toFloat()
                    geometry[index * 4 + 3] = view.layoutParams.height.toFloat()
                }
            })
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Ẩn Overlay khi vào màn hình cấu hình
        sendBroadcast(Intent("com.quyetgd.keyvieweroverlay.VISIBILITY_OVERLAY").apply {
            putExtra("isVisible", false)
            setPackage(packageName)
        })
    }

    override fun onPause() {
        super.onPause()
        // Hiện lại Overlay khi thoát màn hình cấu hình
        sendBroadcast(Intent("com.quyetgd.keyvieweroverlay.VISIBILITY_OVERLAY").apply {
            putExtra("isVisible", true)
            setPackage(packageName)
        })
    }

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun getHitboxKey(index: Int, type: String): String {
        val id = index + 1
        return if (keyMode == 6) {
            "hitbox_${id}_$type"
        } else {
            // Theo yêu cầu: "hitbox_left_" + keyMode + "_" + i
            val prefix = when(type) {
                "x" -> "hitbox_left_"
                "y" -> "hitbox_top_"
                "w" -> "hitbox_width_"
                "h" -> "hitbox_height_"
                else -> "hitbox_"
            }
            "$prefix${keyMode}_$index"
        }
    }

    private fun saveHitboxCoordinates() {
        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            val location = IntArray(2)
            hitboxes.forEachIndexed { index, view ->
                // Lấy tọa độ TUYỆT ĐỐI trên màn hình thay vì tọa độ cục bộ (view.x)
                view.getLocationOnScreen(location)
                val absoluteX = location[0].toFloat()
                val absoluteY = location[1].toFloat()

                putFloat(getHitboxKey(index, "x"), absoluteX)
                putFloat(getHitboxKey(index, "y"), absoluteY)
                putInt(getHitboxKey(index, "w"), view.layoutParams.width)
                putInt(getHitboxKey(index, "h"), view.layoutParams.height)
            }
            putBoolean(PREF_AUTO_MODE, isAutoMode)
            putFloat(PREF_AUTO_DIVIDER, autoPreview.dividerFraction)
            apply()
        }
        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()

        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
        finish()
    }

    private fun loadHitboxCoordinates() {
        val sharedPref = getSharedPreferences(HITBOX_PREFS, Context.MODE_PRIVATE)
        
        // KIỂM TRA LẦN ĐẦU CHO CHẾ ĐỘ NÀY
        val checkKey = getHitboxKey(0, "x")
        if (!sharedPref.contains(checkKey)) {
            resetHitboxesToDefault()
            return
        }

        val rootLocation = IntArray(2)

        hitboxes.forEachIndexed { index, view ->
            val savedAbsoluteX = sharedPref.getFloat(getHitboxKey(index, "x"), -1f)
            val savedAbsoluteY = sharedPref.getFloat(getHitboxKey(index, "y"), -1f)
            val w = sharedPref.getInt(getHitboxKey(index, "w"), -1)
            val h = sharedPref.getInt(getHitboxKey(index, "h"), -1)

            if (savedAbsoluteX != -1f && savedAbsoluteY != -1f && w != -1 && h != -1) {
                run {
                    val parentView = view.parent as? View
                    if (parentView != null) {
                        parentView.getLocationOnScreen(rootLocation)
                        val parentOffsetX = rootLocation[0].toFloat()
                        val parentOffsetY = rootLocation[1].toFloat()

                        view.x = savedAbsoluteX - parentOffsetX
                        view.y = savedAbsoluteY - parentOffsetY
                    } else {
                        view.x = savedAbsoluteX
                        view.y = savedAbsoluteY
                    }

                    val params = view.layoutParams
                    params.width = w
                    params.height = h
                    view.layoutParams = params
                }
            }
        }
    }

    private fun resetHitboxesToDefault() {
        val realMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(realMetrics)

        val screenWidth = kotlin.math.max(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
        val screenHeight = kotlin.math.min(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()

        val dotOffset = 20f

        if (keyMode == 6) {
            // [GIỮ NGUYÊN 100% CODE CŨ CỦA BẠN]
            val w1 = screenWidth * 0.10f
            val w2 = screenWidth * 0.15f
            val w3 = screenWidth * 0.25f
            val widths = floatArrayOf(w1, w2, w3, w3, w2, w1)

            var currentX = 0f
            hitboxes.forEachIndexed { index, view ->
                val startX = currentX.toInt()
                val endX = (currentX + widths[index]).toInt()
                val trueBoxWidth = endX - startX
                val trueBoxHeight = screenHeight.toInt()

                val viewX = startX.toFloat() - dotOffset
                val viewY = 0f - dotOffset
                val viewWidth = trueBoxWidth + (dotOffset * 2f)
                val viewHeight = trueBoxHeight + (dotOffset * 2f)

                view.x = viewX
                view.y = viewY
                val params = view.layoutParams
                params.width = viewWidth.toInt()
                params.height = viewHeight.toInt()
                view.layoutParams = params

                currentX += widths[index]
            }
        } else {
            // [THUẬT TOÁN TỰ ĐỘNG DÀN ĐỀU CHO CÁC CHẾ ĐỘ CÒN LẠI]
            val colWidth = screenWidth / keyMode
            hitboxes.forEachIndexed { index, view ->
                val startX = index * colWidth
                val trueBoxWidth = colWidth.toInt()
                val trueBoxHeight = screenHeight.toInt()

                val viewX = startX - dotOffset
                val viewY = 0f - dotOffset
                val viewWidth = trueBoxWidth + (dotOffset * 2f)
                val viewHeight = trueBoxHeight + (dotOffset * 2f)

                view.x = viewX
                view.y = viewY
                val params = view.layoutParams
                params.width = viewWidth.toInt()
                params.height = viewHeight.toInt()
                view.layoutParams = params


            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
    }
}
