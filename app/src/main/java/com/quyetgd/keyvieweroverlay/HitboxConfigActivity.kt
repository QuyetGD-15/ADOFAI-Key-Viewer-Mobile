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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class HitboxConfigActivity : AppCompatActivity() {

    private lateinit var hitboxes: Array<HitboxView>
    private var keyMode: Int = 6

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
        keyMode = pref.getInt("current_key_mode", 6)

        setupHitboxesAndGrid()

        findViewById<Button>(R.id.btnSaveHitbox).setOnClickListener {
            saveHitboxCoordinates()
        }

        findViewById<Button>(R.id.btnResetHitbox).setOnClickListener {
            resetHitboxesToDefault()
        }

        // Báo cho OverlayService biết để mở chế độ chỉnh sửa
        sendBroadcast(Intent(OverlayService.ACTION_START_EDIT).setPackage(packageName))
    }

    private fun setupHitboxesAndGrid() {
        val root = findViewById<FrameLayout>(R.id.hitbox_root)

        // Thêm Grid lines mờ làm nền
        val gridOverlay = object : View(this) {
            private val paint = Paint().apply {
                color = Color.parseColor("#33FFFFFF")
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

        // Khởi tạo danh sách HitboxView dựa theo keyMode
        // Thêm vào vị trí index 1 để nằm trên GridOverlay (index 0) nhưng dưới các nút điều khiển trong XML
        hitboxes = Array(keyMode) { index ->
            HitboxView(this).apply {
                hitboxNumber = (index + 1).toString()
                layoutParams = FrameLayout.LayoutParams(100, 100)
            }.also { root.addView(it, index + 1) }
        }

        // Đọc và khôi phục vị trí cũ
        loadHitboxCoordinates()
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
                putInt(getHitboxKey(index, "w"), view.width)
                putInt(getHitboxKey(index, "h"), view.height)
            }
            apply()
        }
        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()

        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
        finish()
    }

    private fun loadHitboxCoordinates() {
        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        
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
                view.post {
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
        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        if (keyMode == 6) {
            // [GIỮ NGUYÊN 100% CODE CŨ CỦA BẠN]
            val w1 = screenWidth * 0.10f
            val w2 = screenWidth * 0.15f
            val w3 = screenWidth * 0.25f
            val widths = floatArrayOf(w1, w2, w3, w3, w2, w1)

            var currentX = 0f
            hitboxes.forEachIndexed { index, view ->
                val id = index + 1
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

                editor.putFloat("hitbox_${id}_x", viewX)
                editor.putFloat("hitbox_${id}_y", viewY)
                editor.putInt("hitbox_${id}_w", viewWidth.toInt())
                editor.putInt("hitbox_${id}_h", viewHeight.toInt())
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

                editor.putFloat(getHitboxKey(index, "x"), viewX)
                editor.putFloat(getHitboxKey(index, "y"), viewY)
                editor.putInt(getHitboxKey(index, "w"), viewWidth.toInt())
                editor.putInt(getHitboxKey(index, "h"), viewHeight.toInt())
            }
        }
        editor.apply()
        Toast.makeText(this, getString(R.string.toast_hitbox_reset), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
    }
}
