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
import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import java.lang.ref.WeakReference
import android.content.Context
import android.content.SharedPreferences

class TouchRendererService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        // Published only after Android connects the service and supplies its overlay token.
        private var connectedRef: WeakReference<TouchRendererService>? = null
        val connectedInstance: TouchRendererService? get() = connectedRef?.get()

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, TouchRendererService::class.java)
            // Read enabled state separately from connection state: Android can take
            // time to bind an enabled service (or reconnect it after process death).
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
                    enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
            } catch (_: SecurityException) {
                false
            }
        }

        fun isReady(context: Context): Boolean = connectedInstance != null && isEnabled(context)
    }

    private var keyViewerView: View? = null
    private var pointerCanvasView: View? = null
    private lateinit var windowManager: WindowManager
    private val currentKeyMap = HashMap<Int, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (connectedInstance === this) return
        connectedInstance?.disconnect()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        connectedRef = WeakReference(this)
        try {
            setupOverlay()
        } catch (e: Exception) {
            AppLogger.e(this, "AccessibilityOverlay", "Cannot attach touch renderer", e)
        }
        
        val pref = getSharedPreferences("KeyViewerPrefs", MODE_PRIVATE)
        pref.registerOnSharedPreferenceChangeListener(this)
        updateKeyMap(pref)
        OverlayService.instance?.onAccessibilityHostConnected()
    }

    // All window operations and lifecycle callbacks run on the main thread.
    fun showKeyViewer(view: View, params: WindowManager.LayoutParams): Boolean {
        if (connectedInstance !== this) return false
        require(params.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        if (keyViewerView != null && keyViewerView !== view) removeKeyViewer(keyViewerView!!)
        return try {
            if (keyViewerView === view && view.parent != null) {
                // Key-mode rebuilds replace LayoutParams; retain this connection's token.
                params.token = (view.layoutParams as? WindowManager.LayoutParams)?.token
                windowManager.updateViewLayout(view, params)
            } else {
                // addView supplies the current service token. Never reuse a previous one.
                params.token = null
                windowManager.addView(view, params)
                keyViewerView = view
            }
            true
        } catch (e: Exception) {
            AppLogger.e(this, "AccessibilityOverlay", "Cannot attach/update KeyViewer", e)
            false
        }
    }

    fun removeKeyViewer(view: View) {
        if (keyViewerView !== view) return
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: Exception) {
            AppLogger.e(this, "AccessibilityOverlay", "Cannot remove KeyViewer", e)
        } finally {
            keyViewerView = null
        }
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
    
    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    private fun disconnect() {
        val wasConnected = connectedInstance === this
        if (wasConnected) connectedRef = null
        getSharedPreferences("KeyViewerPrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        keyViewerView?.let { removeKeyViewer(it) }
        pointerCanvasView?.let {
            if (::windowManager.isInitialized) {
                try {
                    windowManager.removeViewImmediate(it)
                } catch (e: Exception) {
                    AppLogger.e(this, "AccessibilityOverlay", "Cannot remove touch renderer", e)
                }
            }
        }
        pointerCanvasView = null
        currentKeyMap.clear()
        if (wasConnected) {
            SharedTouchData.invalidateCallback = null
            OverlayService.instance?.onAccessibilityHostDisconnected()
        }
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
