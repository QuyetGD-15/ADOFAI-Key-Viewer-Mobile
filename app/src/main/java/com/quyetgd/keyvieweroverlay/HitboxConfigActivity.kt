package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class HitboxConfigActivity : AppCompatActivity() {

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

        val hitboxIds = arrayOf(
            R.id.hitbox1,
            R.id.hitbox2,
            R.id.hitbox3,
            R.id.hitbox4,
            R.id.hitbox5,
            R.id.hitbox6
        )
        val hitboxes = hitboxIds.mapIndexed { index, id ->
            findViewById<HitboxView>(id).apply {
                hitboxNumber = (index + 1).toString()
            }
        }.toTypedArray()

        // Đọc và khôi phục vị trí cũ
        loadHitboxCoordinates(hitboxes)

        findViewById<Button>(R.id.btnSaveHitbox).setOnClickListener {
            saveHitboxCoordinates(hitboxes)
        }

        findViewById<Button>(R.id.btnResetHitbox).setOnClickListener {
            resetHitboxesToDefault(hitboxes)
        }

        // Báo cho OverlayService biết để mở chế độ chỉnh sửa
        sendBroadcast(Intent(OverlayService.ACTION_START_EDIT).setPackage(packageName))
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

    private fun saveHitboxCoordinates(hitboxes: Array<HitboxView>) {
        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            hitboxes.forEachIndexed { index, view ->
                val id = index + 1
                // Sử dụng view.x và view.y trực tiếp vì Activity đã tràn viền (hệ quy chiếu 0,0)
                putFloat("hitbox_${id}_x", view.x)
                putFloat("hitbox_${id}_y", view.y)
                putInt("hitbox_${id}_w", view.width)
                putInt("hitbox_${id}_h", view.height)
            }
            apply()
        }
        Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()

        // Gửi tín hiệu dừng chỉnh sửa trước khi đóng
        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
        finish()
    }

    private fun loadHitboxCoordinates(hitboxes: Array<HitboxView>) {
        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        hitboxes.forEachIndexed { index, view ->
            val id = index + 1
            val x = sharedPref.getFloat("hitbox_${id}_x", -1f)
            val y = sharedPref.getFloat("hitbox_${id}_y", -1f)
            val w = sharedPref.getInt("hitbox_${id}_w", -1)
            val h = sharedPref.getInt("hitbox_${id}_h", -1)

            if (x != -1f && y != -1f && w != -1 && h != -1) {
                view.post {
                    view.x = x
                    view.y = y
                    val params = view.layoutParams
                    params.width = w
                    params.height = h
                    view.layoutParams = params
                }
            }
        }
    }

    private fun resetHitboxesToDefault(hitboxes: Array<HitboxView>) {
        val realMetrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(realMetrics)

        val screenWidth = kotlin.math.max(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()
        val screenHeight = kotlin.math.min(realMetrics.widthPixels, realMetrics.heightPixels).toFloat()

        // Chính là bán kính chấm đỏ lấy từ HitboxView
        val dotOffset = 20f

        val sharedPref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        hitboxes.forEachIndexed { index, view ->
            val id = index + 1

            // Tính toán kích thước KHUNG XANH THẬT
            val startX = (index * screenWidth / 6f).toInt()
            val endX = ((index + 1) * screenWidth / 6f).toInt()

            val trueBoxWidth = endX - startX
            val trueBoxHeight = screenHeight.toInt()

            // 💡 BÙ TRỪ: Đẩy View lùi lên trên và sang trái 20px, đồng thời phình to ra 40px
            // Để giấu gọn 4 chấm đỏ ra ngoài rìa khung xanh
            val viewX = startX.toFloat() - dotOffset
            val viewY = 0f - dotOffset
            val viewWidth = trueBoxWidth + (dotOffset * 2f)
            val viewHeight = trueBoxHeight + (dotOffset * 2f)

            // Cập nhật View
            view.x = viewX
            view.y = viewY
            val params = view.layoutParams
            params.width = viewWidth.toInt()
            params.height = viewHeight.toInt()
            view.layoutParams = params

            // Ghi đè vào SharedPreferences
            editor.putFloat("hitbox_${id}_x", viewX)
            editor.putFloat("hitbox_${id}_y", viewY)
            editor.putInt("hitbox_${id}_w", viewWidth.toInt())
            editor.putInt("hitbox_${id}_h", viewHeight.toInt())
        }
        editor.apply()
        Toast.makeText(this, getString(R.string.toast_hitbox_reset), Toast.LENGTH_SHORT).show()
    }
    override fun onDestroy() {
        super.onDestroy()
        // Đảm bảo OverlayService được khóa lại khi thoát màn hình này
        sendBroadcast(Intent(OverlayService.ACTION_STOP_EDIT).setPackage(packageName))
    }
}
