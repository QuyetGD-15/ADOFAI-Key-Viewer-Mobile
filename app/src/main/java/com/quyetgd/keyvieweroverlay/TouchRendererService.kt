package com.quyetgd.keyvieweroverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper

class TouchRendererService : AccessibilityService() {
    private var pointerCanvasView: View? = null
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
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

        /**
         * Kiến trúc vẽ xuyên thấu: Sử dụng TYPE_ACCESSIBILITY_OVERLAY.
         * Đây là loại Window đặc biệt của AccessibilityService có quyền ưu tiên cao nhất,
         * cho phép vẽ đè lên tất cả các ứng dụng khác, bao gồm cả các lớp phủ hệ thống (như Game Turbo).
         */
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )
        windowManager.addView(pointerCanvasView, params)

        // Nhận lệnh vẽ lại từ Shizuku
        SharedTouchData.invalidateCallback = {
            mainHandler.post { pointerCanvasView?.invalidate() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    
    override fun onDestroy() {
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
