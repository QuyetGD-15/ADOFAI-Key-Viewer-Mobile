package com.quyetgd.keyvieweroverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.SharedPreferences

class TouchRendererService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var pointerCanvasView: View? = null
    private lateinit var windowManager: WindowManager
    private val currentKeyMap = HashMap<Int, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        pref.registerOnSharedPreferenceChangeListener(this)
        updateKeyMap(pref)
    }

    private fun updateKeyMap(pref: SharedPreferences) {
        currentKeyMap.clear()
        val keyMode = pref.getInt("current_key_mode", 6)
        for (i in 0 until keyMode) {
            val savedKeyCode = pref.getInt("key_code_${keyMode}_$i", -1)
            if (savedKeyCode != -1) {
                currentKeyMap[savedKeyCode] = i
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != null && (key.startsWith("key_code_") || key == "current_key_mode")) {
            sharedPreferences?.let { updateKeyMap(it) }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyIndex = currentKeyMap[event.keyCode]
        
        if (keyIndex != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) { // Tránh bị lặp phím khi giữ
                    OverlayService.instance?.triggerKeyPressFromKeyboard(keyIndex, true)
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                OverlayService.instance?.triggerKeyPressFromKeyboard(keyIndex, false)
            }
        }
        
        return false // Xuyên qua để vào game
    }

    private fun setupOverlay() {
        pointerCanvasView = object : View(this) {
            private val blackPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 10f; alpha = 204; isAntiAlias = true }
            private val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f; alpha = 204; isAntiAlias = true }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                for (pt in SharedTouchData.points) {
                    if (pt.isActive) {
                        // Vẽ viền đen ngoài, viền trắng trong
                        canvas.drawCircle(pt.x, pt.y, 40f, blackPaint)
                        canvas.drawCircle(pt.x, pt.y, 40f, whitePaint)
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply {
            // ĐỒNG BỘ 1: Ép điểm gốc (0,0) lên góc trên cùng bên trái
            gravity = android.view.Gravity.TOP or android.view.Gravity.START

            // ĐỒNG BỘ 2: Xin quyền đâm xuyên tai thỏ giống hệt OverlayService
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowManager.addView(pointerCanvasView, params)

        // Nhận lệnh vẽ lại từ Shizuku
        SharedTouchData.invalidateCallback = {
            pointerCanvasView?.postInvalidate()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        pointerCanvasView?.let { 
            if (it.parent != null || it.windowToken != null) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {}
            }
        }
        SharedTouchData.invalidateCallback = null
        super.onDestroy()
    }
}
