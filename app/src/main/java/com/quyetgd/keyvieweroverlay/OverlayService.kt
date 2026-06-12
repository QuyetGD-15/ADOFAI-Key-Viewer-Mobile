package com.quyetgd.keyvieweroverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
        const val CHANNEL_ID = "overlay_service_channel"
        const val NOTIFICATION_ID = 1

        val SPACE_REGEX = "\\s+".toRegex()
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var wrapper: FrameLayout
    private lateinit var viewerContainer: View
    private lateinit var viewerParams: WindowManager.LayoutParams

    private val keyViews = arrayOfNulls<TextView>(6)
    private var isOverlayShowing = false
    private var isKeyViewerOn = false
    private var isShowTouchesOn = false

    private var isManualOverride = false
    private var lastForegroundApp = ""
    private var lastIsLandscape = false

    private fun getLatestForegroundApp(): String {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 5000, time)
        var latestApp = ""
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestApp = event.packageName ?: ""
            }
        }
        return latestApp
    }

    private fun checkIsLandscape(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation
            rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        } catch (e: Exception) {
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }

    private val autoShowRunnable = object : Runnable {
        override fun run() {
            try {
                var currentApp = getLatestForegroundApp()

                if (currentApp.isEmpty() || currentApp == "com.android.systemui") {
                    currentApp = lastForegroundApp
                }

                val isLandscape = checkIsLandscape()

                if (currentApp != lastForegroundApp) {
                    isManualOverride = false
                }

                if (currentApp != lastForegroundApp || isLandscape != lastIsLandscape) {
                    lastForegroundApp = currentApp
                    lastIsLandscape = isLandscape

                    if (!isManualOverride) {
                        val allowedApps = sharedPrefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
                        val shouldShow = allowedApps.contains(currentApp) && isLandscape

                        if (shouldShow && !isKeyViewerOn) {
                            isKeyViewerOn = true
                            mainHandler.post {
                                tryShowOverlay()
                                updateNotification()
                            }
                        } else if (!shouldShow && isKeyViewerOn) {
                            isKeyViewerOn = false
                            mainHandler.post {
                                hideOverlay()
                                updateNotification()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoShowDebug", "Lỗi vòng lặp Auto: ${e.message}")
            } finally {
                mainHandler.postDelayed(this, 1500)
            }
        }
    }

    private class TouchSlot(
        var trackingId: Int = -1,
        var x: Float = -1f,
        var y: Float = -1f,
        var lastHitLane: Int = -1,
        var isActive: Boolean = false
    )

    private val slots = Array(10) { TouchSlot() }
    private var currentSlot = 0

    private var maxRawX = 1f
    private var maxRawY = 1f

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
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        sharedPrefs = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        totalClicks = sharedPrefs.getInt("TOTAL_CLICKS", 0)

        setupViewerView()

        val metrics = resources.displayMetrics
        cachedScreenWidth = kotlin.math.max(metrics.widthPixels, metrics.heightPixels).toFloat()
        cachedScreenHeight = kotlin.math.min(metrics.widthPixels, metrics.heightPixels).toFloat()

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
                val currentTime = System.currentTimeMillis()
                while (kpsQueue.isNotEmpty() && currentTime - kpsQueue.first() > 1000) {
                    kpsQueue.removeFirst()
                }

                // ĐẢO NGƯỢC TỐI ƯU: Bỏ điều kiện `if (changed)`.
                // Ép hệ thống luôn cập nhật UI và lưu SharedPreferences mỗi 100ms
                // để chỉ số KPS giảm xuống mượt mà theo thời gian thực.
                updateKpsTotalUI(kpsQueue.size, totalClicks)
                sharedPrefs.edit().putInt("TOTAL_CLICKS", totalClicks).apply()

                mainHandler.postDelayed(this, 100)
            }
        })

        if (Shizuku.pingBinder()) {
            initHardwareAndStartReading()
        }

        mainHandler.post(autoShowRunnable)
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

            val cmd = arrayOf("sh", "-c", "getevent -p -l")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, cmd, null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var currentDevice: String? = null
            var hasX = false
            var hasY = false

            reader.forEachLine { line ->
                if (line.startsWith("add device")) {
                    if (currentDevice != null && hasX && hasY) return@forEachLine
                    currentDevice = line.substringAfter(": ").trim()
                    hasX = false
                    hasY = false
                }
                if (line.contains("ABS_MT_POSITION_X")) hasX = true
                if (line.contains("ABS_MT_POSITION_Y")) hasY = true
            }
            if (currentDevice != null && hasX && hasY) return currentDevice
        } catch (e: Exception) {
            // Error finding device
        }
        return null
    }

    private fun startReadingTouchEvents(devicePath: String) {
        isReadingEvents = true
        Log.d("KeyViewer_Debug", "Bắt đầu đọc Shizuku tại: $devicePath")

        val pref = getSharedPreferences("HardwarePrefs", Context.MODE_PRIVATE)
        val hwMaxX = pref.getInt("hardware_max_x", 10799).toFloat()
        val hwMaxY = pref.getInt("hardware_max_y", 24599).toFloat()

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
                    val parts = line.trim().split(SPACE_REGEX)
                    if (parts.size < 3) continue

                    val type = parts[parts.size - 3].removeSuffix(":")
                    val code = parts[parts.size - 2]
                    val value = try { parts.last().toLong(16).toInt() } catch (e: Exception) { 0 }

                    when (type) {
                        "0003" -> { // EV_ABS
                            when (code) {
                                "002f" -> currentSlot = value.coerceIn(0, 9)
                                "0039" -> slots[currentSlot].trackingId = value
                                "0035" -> slots[currentSlot].x = value.toFloat()
                                "0036" -> slots[currentSlot].y = value.toFloat()
                            }
                        }
                        "0000" -> { // EV_SYN
                            if (code == "0000") { // SYN_REPORT
                                processSync(hwMaxX, hwMaxY)
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
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun processSync(hwMaxX: Float, hwMaxY: Float) {
        val screenWidth = cachedScreenWidth
        val screenHeight = cachedScreenHeight

        for (i in 0 until 10) {
            val slot = slots[i]

            val mappedX = (slot.y / hwMaxY) * screenWidth
            val mappedY = ((hwMaxX - slot.x) / hwMaxX) * screenHeight

            if (slot.trackingId != -1) {
                if (!slot.isActive) {

                    var directHitLane = -1
                    for (j in 0 until 6) {
                        if (hitboxes[j].contains(mappedX, mappedY)) {
                            directHitLane = j
                            break
                        }
                    }

                    var finalLaneToActivate = -1

                    if (directHitLane != -1) {
                        var isLaneOccupied = false
                        for (k in 0 until 10) {
                            val s = slots[k]
                            if (s !== slot && s.isActive && s.lastHitLane == directHitLane) {
                                isLaneOccupied = true
                                break
                            }
                        }

                        if (!isLaneOccupied) {
                            finalLaneToActivate = directHitLane
                        } else {
                            var nearestEmptyLane = -1
                            var minDistanceSq = Float.MAX_VALUE // So sánh bình phương, không dùng sqrt

                            val startLane = if (directHitLane < 3) 0 else 3
                            val endLane = if (directHitLane < 3) 3 else 6

                            for (j in startLane until endLane) {
                                if (j == directHitLane) continue

                                var isJEmpty = true
                                for (k in 0 until 10) {
                                    val s = slots[k]
                                    if (s.isActive && s.lastHitLane == j) {
                                        isJEmpty = false
                                        break
                                    }
                                }

                                if (isJEmpty) {
                                    val hBox = hitboxes[j]
                                    if (hBox.width() <= 0 || hBox.height() <= 0) continue

                                    val centerX = hBox.centerX()
                                    val centerY = hBox.centerY()

                                    // Tính toán không dùng pow() và sqrt()
                                    val dx = mappedX - centerX
                                    val dy = mappedY - centerY
                                    val distSq = dx * dx + dy * dy

                                    if (distSq < minDistanceSq) {
                                        minDistanceSq = distSq
                                        nearestEmptyLane = j
                                    }
                                }
                            }

                            if (nearestEmptyLane != -1) {
                                Log.d("KeyViewer_Debug", "🔄 Auto-Correct Cụm ${if (directHitLane < 3) "TRÁI" else "PHẢI"}: Trượt từ $directHitLane sang Lane trống $nearestEmptyLane")
                                finalLaneToActivate = nearestEmptyLane
                            }
                        }
                    }

                    if (finalLaneToActivate != -1) {
                        slot.lastHitLane = finalLaneToActivate
                        slot.isActive = true
                        onKeyDown(finalLaneToActivate)

                        val currentTime = System.currentTimeMillis()
                        kpsQueue.addLast(currentTime)
                        totalClicks++

                        while (kpsQueue.isNotEmpty() && currentTime - kpsQueue.first() > 1000) {
                            kpsQueue.removeFirst()
                        }
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

        SharedTouchData.invalidateCallback?.invoke()
    }

    private fun onKeyDown(lane: Int) {
        mainHandler.post {
            if (activeTrails[lane] != null) return@post
            val tv = keyViews[lane] ?: return@post
            activeTrails[lane] = keyTrailView.addTrail(tv.x, tv.width.toFloat())
            tv.setBackgroundResource(R.drawable.bg_key_pressed)
            tv.setTextColor(Color.BLACK)
        }
    }

    private fun onKeyUp(lane: Int) {
        // Tối ưu .any{} thành for-loop chuẩn để tránh rác RAM
        var isStillOccupied = false
        for (i in 0 until 10) {
            val s = slots[i]
            if (s.isActive && s.lastHitLane == lane && s.trackingId != -1) {
                isStillOccupied = true
                break
            }
        }
        if (isStillOccupied) return

        mainHandler.post {
            activeTrails[lane]?.isReleased = true
            activeTrails[lane] = null
            val tv = keyViews[lane]
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

        for (i in 0 until 6) {
            val keyId = resources.getIdentifier("key${i + 1}", "id", packageName)
            keyViews[i] = viewerContainer.findViewById(keyId)
        }

        viewerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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
            val serviceChannel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startServiceAsForeground() {
        updateNotification()
    }

    private fun updateNotification() {
        val keyViewerText = if (isKeyViewerOn) getString(R.string.notif_toggle_on) else getString(R.string.notif_toggle_off)
        val showTouchesText = if (isShowTouchesOn) getString(R.string.notif_hide_touches) else getString(R.string.notif_show_touches)

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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_key)
            .setContentTitle(getString(R.string.notif_content_title))
            .setContentText(getString(R.string.notif_content_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, keyViewerText, pendingKeyViewer)
            .addAction(0, showTouchesText, pendingTouches)
            .addAction(0, getString(R.string.notif_open_app), pendingOpenApp)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun tryShowOverlay(): Boolean {
        if (AppState.isAppVisible) {
            Toast.makeText(this, getString(R.string.toast_enter_game), Toast.LENGTH_SHORT).show()
            return false
        }

        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, getString(R.string.toast_landscape_only), Toast.LENGTH_SHORT).show()
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
            ACTION_TOGGLE_KEY_VIEWER -> {
                isManualOverride = true
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
        mainHandler.removeCallbacks(autoShowRunnable)
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
                tvKps?.text = "${getString(R.string.kps_label)}\n$kps"
                tvTotal?.text = "${getString(R.string.total_label)}\n$total"
            } catch (e: Exception) {
                // UI update error
            }
        }
    }
}