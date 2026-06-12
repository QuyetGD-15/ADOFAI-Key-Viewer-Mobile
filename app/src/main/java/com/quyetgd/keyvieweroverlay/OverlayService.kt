package com.quyetgd.keyvieweroverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayDeque

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val ACTION_START_FOREGROUND = "com.quyetgd.keyvieweroverlay.ACTION_START_FOREGROUND"
        const val ACTION_TOGGLE_KEY_VIEWER = "com.quyetgd.keyvieweroverlay.ACTION_TOGGLE_KEY_VIEWER"
        const val ACTION_TOGGLE_TOUCHES = "com.quyetgd.keyvieweroverlay.ACTION_TOGGLE_TOUCHES"
        const val ACTION_OPEN_APP = "com.quyetgd.keyvieweroverlay.ACTION_OPEN_APP"

        const val ACTION_START_EDIT = "com.quyetgd.keyvieweroverlay.ACTION_START_EDIT"
        const val ACTION_STOP_EDIT = "com.quyetgd.keyvieweroverlay.ACTION_STOP_EDIT"
        const val ACTION_UPDATE_CONFIG = "com.quyetgd.keyvieweroverlay.UPDATE_OVERLAY_CONFIG"
        const val ACTION_VISIBILITY = "com.quyetgd.keyvieweroverlay.VISIBILITY_OVERLAY"
        // THÊM: Action để lắng nghe sự kiện xóa thông báo
        const val ACTION_NOTIFICATION_DISMISSED = "com.quyetgd.keyvieweroverlay.ACTION_NOTIFICATION_DISMISSED"

        const val CHANNEL_ID = "overlay_service_channel"
        const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var wrapper: FrameLayout
    private lateinit var viewerContainer: View
    private lateinit var viewerParams: WindowManager.LayoutParams

    private var isOverlayShowing = false
    private var isKeyViewerOn = false
    private var isShowTouchesOn = false

    private class TouchSlot(
        var trackingId: Int = -1,
        var x: Float = -1f,
        var y: Float = -1f,
        var lastHitLane: Int = -1,
        var isActive: Boolean = false
    )

    private val slots = Array(10) { TouchSlot() }
    private var currentSlot = 0

    // Biến lưu thông số phần cứng động
    private var maxRawX = 1f
    private var maxRawY = 1f

    // Biến Cache để giảm tải CPU
    private var cachedScreenWidth = -1f
    private var cachedScreenHeight = -1f
    private var cachedRotation = -1

    private val hitboxes = Array(6) { RectF() }
    private lateinit var keyTrailView: KeyTrailView
    private val activeTrails = arrayOfNulls<KeyTrailView.Trail>(6)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val baseTextSize = 20f

    private var eventReaderThread: Thread? = null
    private var isReadingEvents = false
    private var shizukuProcess: Process? = null

    private val kpsQueue = ArrayDeque<Long>()
    private var totalClicks = 0
    private lateinit var sharedPrefs: SharedPreferences

    private val resetTotalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_RESET_TOTAL") {
                totalClicks = 0
                sharedPrefs.edit().putInt("TOTAL_CLICKS", 0).apply()
                updateKpsTotalUI(kpsQueue.size, totalClicks)
            }
        }
    }

    private val editReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_EDIT -> {}
                ACTION_STOP_EDIT -> {
                    mainHandler.post { loadKeyViewerSettings() }
                    loadHitboxesFromPrefs()
                }
                ACTION_UPDATE_CONFIG -> {
                    mainHandler.post { loadKeyViewerSettings() }
                }
                ACTION_VISIBILITY -> {
                    val isVisible = intent.getBooleanExtra("isVisible", true)
                    mainHandler.post {
                        wrapper.visibility = if (isVisible) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        sharedPrefs = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        totalClicks = sharedPrefs.getInt("TOTAL_CLICKS", 0)

        setupViewerView()

        mainHandler.post {
            loadKeyViewerSettings()
            loadHitboxesFromPrefs()
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_START_EDIT)
            addAction(ACTION_STOP_EDIT)
            addAction(ACTION_UPDATE_CONFIG)
            addAction(ACTION_VISIBILITY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(editReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(editReceiver, filter)
        }

        val resetFilter = IntentFilter("ACTION_RESET_TOTAL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resetTotalReceiver, resetFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(resetTotalReceiver, resetFilter)
        }

        mainHandler.post(object : Runnable {
            override fun run() {
                var changed = false
                val currentTime = System.currentTimeMillis()
                while (kpsQueue.isNotEmpty() && currentTime - kpsQueue.first() > 1000) {
                    kpsQueue.removeFirst()
                    changed = true
                }
                if (changed) updateKpsTotalUI(kpsQueue.size, totalClicks)
                mainHandler.postDelayed(this, 100)
            }
        })

        if (Shizuku.pingBinder()) {
            initHardwareAndStartReading()
        }
    }

    private fun initHardwareAndStartReading() {
        Thread {
            val device = calibrateHardwareAndFindDevice() ?: "/dev/input/event2"
            startReadingTouchEvents(device)
        }.start()
    }

    private fun calibrateHardwareAndFindDevice(): String? {
        try {
            val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
            for (id in inputManager.inputDeviceIds) {
                val device = inputManager.getInputDevice(id) ?: continue
                if (!device.isVirtual && (device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN)) {
                    val xRange = device.getMotionRange(MotionEvent.AXIS_X)
                    val yRange = device.getMotionRange(MotionEvent.AXIS_Y)
                    if (xRange != null && yRange != null && xRange.max > 100f) {
                        maxRawX = xRange.max
                        maxRawY = yRange.max
                        Log.d("KeyViewer_Debug", "Đã quét phần cứng: MaxX=$maxRawX, MaxY=$maxRawY")
                        break
                    }
                }
            }

            val cmd = arrayOf("sh", "-c", "getevent -il")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, cmd, null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var currentDevice: String? = null
            var hasTouch = false
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!
                if (currentLine.startsWith("add device")) {
                    if (currentDevice != null && hasTouch) {
                        process.destroy()
                        return currentDevice
                    }
                    currentDevice = currentLine.substringAfter(": ").trim()
                    hasTouch = false
                }
                if (currentLine.contains("ABS_MT_POSITION_X")) hasTouch = true
            }
            if (currentDevice != null && hasTouch) {
                process.destroy()
                return currentDevice
            }
        } catch (e: Exception) {
            Log.e("KeyViewer_Debug", "Lỗi quét thiết bị: ${e.message}")
        }
        return null
    }

    private fun startReadingTouchEvents(devicePath: String) {
        isReadingEvents = true
        Log.d("KeyViewer_Debug", "Bắt đầu đọc Shizuku tại: $devicePath")

        eventReaderThread = Thread {
            try {
                val cmd = arrayOf("sh", "-c", "getevent $devicePath")
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, cmd, null, null) as Process
                shizukuProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isReadingEvents) {
                    line = reader.readLine() ?: break
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size < 3) continue

                    val type = parts[parts.size - 3].removeSuffix(":")
                    val code = parts[parts.size - 2]
                    val value = try { parts.last().toLong(16).toInt() } catch (e: Exception) { 0 }

                    when (type) {
                        "0003" -> {
                            when (code) {
                                "002f" -> currentSlot = value.coerceIn(0, 9)
                                "0039" -> slots[currentSlot].trackingId = value
                                "0035" -> slots[currentSlot].x = value.toFloat()
                                "0036" -> slots[currentSlot].y = value.toFloat()
                            }
                        }
                        "0000" -> {
                            if (code == "0000") {
                                processSync()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Reader error
            } finally {
                stopReadingTouchEvents()
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun processSync() {
        if (cachedRotation == -1) {
            val metrics = resources.displayMetrics
            cachedScreenWidth = kotlin.math.max(metrics.widthPixels, metrics.heightPixels).toFloat()
            cachedScreenHeight = kotlin.math.min(metrics.widthPixels, metrics.heightPixels).toFloat()
            @Suppress("DEPRECATION")
            cachedRotation = windowManager.defaultDisplay.rotation
        }

        for (i in 0 until 10) {
            val slot = slots[i]

            var safeMaxX = if (maxRawX > 10f) maxRawX else 10799f
            var safeMaxY = if (maxRawY > 10f) maxRawY else 24599f

            if (slot.x > safeMaxX * 2 || slot.y > safeMaxY * 2) {
                safeMaxX = kotlin.math.max(safeMaxX * 10f, 10799f)
                safeMaxY = kotlin.math.max(safeMaxY * 10f, 24599f)
            }

            val normX = slot.x / safeMaxX
            val normY = slot.y / safeMaxY

            var mappedX = 0f
            var mappedY = 0f

            when (cachedRotation) {
                Surface.ROTATION_0 -> {
                    mappedX = normX * cachedScreenWidth
                    mappedY = normY * cachedScreenHeight
                }
                Surface.ROTATION_90 -> {
                    mappedX = normY * cachedScreenWidth
                    mappedY = (1f - normX) * cachedScreenHeight
                }
                Surface.ROTATION_180 -> {
                    mappedX = (1f - normX) * cachedScreenWidth
                    mappedY = (1f - normY) * cachedScreenHeight
                }
                Surface.ROTATION_270 -> {
                    mappedX = (1f - normY) * cachedScreenWidth
                    mappedY = normX * cachedScreenHeight
                }
            }

            if (slot.trackingId != -1) {
                if (!slot.isActive) {
                    var hitLane = -1
                    for (j in 0 until 6) {
                        if (hitboxes[j].contains(mappedX, mappedY)) {
                            hitLane = j
                            break
                        }
                    }

                    if (hitLane != -1) {
                        slot.lastHitLane = hitLane
                        slot.isActive = true
                        onKeyDown(hitLane)

                        val currentTime = System.currentTimeMillis()
                        kpsQueue.addLast(currentTime)
                        totalClicks++

                        while (kpsQueue.isNotEmpty() && currentTime - kpsQueue.first() > 1000) {
                            kpsQueue.removeFirst()
                        }

                        sharedPrefs.edit().putInt("TOTAL_CLICKS", totalClicks).apply()
                        updateKpsTotalUI(kpsQueue.size, totalClicks)
                    }
                }
            } else {
                if (slot.isActive && slot.lastHitLane != -1) {
                    onKeyUp(slot.lastHitLane)
                    slot.lastHitLane = -1
                    slot.isActive = false
                }
            }

            val sharedPt = SharedTouchData.points[i]
            if (isShowTouchesOn && slot.isActive && slot.trackingId != -1) {
                sharedPt.x = mappedX
                sharedPt.y = mappedY
                sharedPt.isActive = true
            } else {
                sharedPt.isActive = false
            }
        }

        if (isShowTouchesOn) {
            SharedTouchData.invalidateCallback?.invoke()
        }
    }

    private fun onKeyDown(lane: Int) {
        mainHandler.post {
            if (activeTrails[lane] != null) return@post
            val keyId = resources.getIdentifier("key${lane + 1}", "id", packageName)
            val tv = viewerContainer.findViewById<TextView>(keyId) ?: return@post
            activeTrails[lane] = keyTrailView.addTrail(tv.x, tv.width.toFloat())
            tv.setBackgroundResource(R.drawable.bg_key_pressed)
            tv.setTextColor(Color.BLACK)
        }
    }

    private fun onKeyUp(lane: Int) {
        val isStillOccupied = slots.any { it.isActive && it.lastHitLane == lane && it.trackingId != -1 }
        if (isStillOccupied) return

        mainHandler.post {
            activeTrails[lane]?.isReleased = true
            activeTrails[lane] = null
            val keyId = resources.getIdentifier("key${lane + 1}", "id", packageName)
            val tv = viewerContainer.findViewById<TextView>(keyId)
            tv?.setBackgroundResource(R.drawable.bg_key_normal)
            tv?.setTextColor(Color.WHITE)
        }
    }

    private fun stopReadingTouchEvents() {
        isReadingEvents = false
        shizukuProcess?.destroy()
        shizukuProcess = null
        eventReaderThread = null
    }

    private fun setupViewerView() {
        wrapper = FrameLayout(this)
        wrapper.visibility = View.VISIBLE

        viewerContainer = LayoutInflater.from(this).inflate(R.layout.overlay_view, wrapper, false)
        keyTrailView = viewerContainer.findViewById(R.id.keyTrailView)

        keyTrailView.setBackgroundColor(Color.TRANSPARENT)

        wrapper.addView(viewerContainer)

        viewerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun loadKeyViewerSettings() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        val x = pref.getFloat("viewer_x", 100f)
        val y = pref.getFloat("viewer_y", 100f)
        val scale = pref.getFloat("viewer_scale", 1.0f)
        val speed = pref.getFloat("trail_speed", 1.0f)
        val limitPx = pref.getInt("trail_limit_px", 300)

        val keyWidth = pref.getInt("key_width", 60)
        val keyHeight = pref.getInt("key_height", 60)
        val keySpacing = pref.getInt("key_spacing", 0)

        val keysContainer = viewerContainer.findViewById<LinearLayout>(R.id.keysContainer)

        viewerContainer.post {
            viewerContainer.pivotX = 0f
            viewerContainer.pivotY = 0f

            viewerContainer.translationX = 0f
            viewerContainer.translationY = 0f
            viewerContainer.x = x
            viewerContainer.y = y

            viewerContainer.scaleX = scale
            viewerContainer.scaleY = scale

            for (i in 0 until keysContainer.childCount) {
                val keyView = keysContainer.getChildAt(i)
                val params = keyView.layoutParams as ViewGroup.MarginLayoutParams
                params.width = (keyWidth * resources.displayMetrics.density).toInt()
                params.height = (keyHeight * resources.displayMetrics.density).toInt()
                if (i > 0) {
                    params.leftMargin = (keySpacing * resources.displayMetrics.density).toInt()
                }
                keyView.layoutParams = params

                if (keyView is TextView) {
                    keyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTextSize * scale)
                }
            }

            val countersContainer = viewerContainer.findViewById<LinearLayout>(R.id.bottomCountersContainer)
            val tvKps = viewerContainer.findViewById<TextView>(R.id.tvKps)

            val counterParams = countersContainer.layoutParams as ViewGroup.MarginLayoutParams
            counterParams.topMargin = (keySpacing * resources.displayMetrics.density).toInt()
            countersContainer.layoutParams = counterParams

            val kpsParams = tvKps.layoutParams as ViewGroup.MarginLayoutParams
            kpsParams.rightMargin = (keySpacing * resources.displayMetrics.density).toInt()
            tvKps.layoutParams = kpsParams

            val trailParams = keyTrailView.layoutParams
            trailParams.height = (limitPx * resources.displayMetrics.density).toInt()
            keyTrailView.layoutParams = trailParams
            keyTrailView.setParameters(speed, limitPx.toFloat())

            viewerContainer.requestLayout()
        }
    }

    private fun loadHitboxesFromPrefs() {
        val pref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        for (i in 0 until 6) {
            val id = i + 1
            val x = pref.getFloat("hitbox_${id}_x", 0f)
            val y = pref.getFloat("hitbox_${id}_y", 0f)
            val w = pref.getInt("hitbox_${id}_w", 0)
            val h = pref.getInt("hitbox_${id}_h", 0)
            hitboxes[i].set(x, y, x + w, y + h)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Overlay Service Channel", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startServiceAsForeground() {
        updateNotification()
    }

    private fun updateNotification() {
        val keyViewerText = if (isKeyViewerOn) "TẮT KEY VIEWER" else "BẬT KEY VIEWER"
        val showTouchesText = if (isShowTouchesOn) "ẨN NHẤN" else "HIỆN NHẤN"

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val intentKeyViewer = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_KEY_VIEWER }
        val pendingKeyViewer = PendingIntent.getService(this, 1, intentKeyViewer, flag)

        val hasAccessibility = SharedTouchData.invalidateCallback != null
        val pendingTouches = if (hasAccessibility) {
            val intentTouches = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_TOUCHES }
            PendingIntent.getService(this, 2, intentTouches, flag)
        } else {
            val intentPrompt = Intent(this, MainActivity::class.java).apply {
                action = "ACTION_PROMPT_ACCESSIBILITY"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_SHOW_ACC_PROMPT", true)
            }
            PendingIntent.getActivity(this, 2, intentPrompt, flag)
        }

        val intentOpenApp = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingOpenApp = PendingIntent.getActivity(this, 3, intentOpenApp, flag)

        // THÊM: Intent lắng nghe sự kiện xóa
        val intentDismiss = Intent(this, OverlayService::class.java).apply { action = ACTION_NOTIFICATION_DISMISSED }
        val pendingDismiss = PendingIntent.getService(this, 4, intentDismiss, flag)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_key)
            .setContentTitle("Key Viewer Overlay")
            .setContentText("Sử dụng các nút bên dưới để điều khiển.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(pendingDismiss) // THÊM: Gắn bùa hồi sinh
            .addAction(0, keyViewerText, pendingKeyViewer)
            .addAction(0, showTouchesText, pendingTouches)
            .addAction(0, "MỞ APP", pendingOpenApp)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun tryShowOverlay(): Boolean {
        if (AppState.isAppVisible) {
            Toast.makeText(this, "Hãy vào game rồi mở keyviewer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "Keyviewer overlay chỉ hoạt động ở màn hình ngang", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!isOverlayShowing) {
            mainHandler.post {
                try {
                    if (wrapper.parent == null) windowManager.addView(wrapper, viewerParams)
                    isOverlayShowing = true
                } catch (e: Exception) {
                    // Overlay show error
                }
            }
        }
        return true
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            mainHandler.post {
                try {
                    if (wrapper.parent != null) windowManager.removeView(wrapper)
                    isOverlayShowing = false
                } catch (e: Exception) {
                    // Overlay hide error
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cachedRotation = -1

        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isKeyViewerOn) {
                hideOverlay()
                isKeyViewerOn = false
            }
            if (isShowTouchesOn) {
                isShowTouchesOn = false
                SharedTouchData.invalidateCallback?.invoke()
            }
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                startServiceAsForeground()
            }
            // THÊM: Xử lý sự kiện hồi sinh thông báo
            ACTION_NOTIFICATION_DISMISSED -> {
                if (isRunning) {
                    updateNotification()
                }
            }
            ACTION_TOGGLE_KEY_VIEWER -> {
                if (isKeyViewerOn) {
                    hideOverlay()
                    isKeyViewerOn = false
                } else {
                    if (tryShowOverlay()) {
                        isKeyViewerOn = true
                    }
                }
                updateNotification()
            }
            ACTION_TOGGLE_TOUCHES -> {
                if (isShowTouchesOn) {
                    isShowTouchesOn = false
                    SharedTouchData.invalidateCallback?.invoke()
                } else {
                    if (SharedTouchData.invalidateCallback != null) {
                        isShowTouchesOn = true
                    }
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopReadingTouchEvents()
        try { unregisterReceiver(editReceiver) } catch (e: Exception) { }
        try { unregisterReceiver(resetTotalReceiver) } catch (e: Exception) { }
        hideOverlay()
    }

    private fun updateKpsTotalUI(kps: Int, total: Int) {
        mainHandler.post {
            try {
                val tvKps = viewerContainer.findViewById<TextView>(R.id.tvKps)
                val tvTotal = viewerContainer.findViewById<TextView>(R.id.tvTotal)
                tvKps?.text = "KPS\n$kps"
                tvTotal?.text = "Total\n$total"
            } catch (e: Exception) {
                // UI update error
            }
        }
    }
}