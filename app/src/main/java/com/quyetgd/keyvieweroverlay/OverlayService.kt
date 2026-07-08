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
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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

class OverlayService : Service() {

    companion object {
        var isRunning = false
        var instance: OverlayService? = null
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
    }

    private var currentInputSource = "touch"
    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var wrapper: FrameLayout
    private lateinit var viewerContainer: View
    private lateinit var viewerParams: WindowManager.LayoutParams

    private var kpsContainer: View? = null
    private var tvKpsLabel: TextView? = null
    private var tvKpsValue: TextView? = null
    private var totalContainer: View? = null
    private var tvTotalLabel: TextView? = null
    private var tvTotalValue: TextView? = null
    private var lastRenderedKps = -1
    private var lastRenderedTotal = -1

    private var cachedPhysWidth = 0f
    private var cachedPhysHeight = 0f
    private var cachedOffsetX = 0f
    private var cachedOffsetY = 0f
    private var absMaxScreenDim = 0f
    private var absMinScreenDim = 0f

    @Volatile
    private var currentHardwareRotation = Surface.ROTATION_0

    // TỐI ƯU 1: Bộ Cache dùng chung toàn cục
    private val decimalFormatter = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
    private val realMetrics = android.util.DisplayMetrics()

    // TỐI ƯU 2: Biến mảng O(1) siêu tốc
    private var keyMode = 6
    private lateinit var laneOccupants: IntArray
    private lateinit var hitboxCentersX: FloatArray
    private lateinit var hitboxCentersY: FloatArray

    private lateinit var keyContainers: Array<LinearLayout?>
    private lateinit var keyLabels: Array<TextView?>
    private lateinit var keyCountersTv: Array<TextView?>
    private var isOverlayShowing = false
    private var isKeyViewerOn = false
    private var isShowTouchesOn = false

    private var isManualOverride = false
    private var lastForegroundApp = ""
    private var lastIsLandscape = false

    // Theme Caches
    private var themeTextSizeSp = 20f
    private var themeTypeface: Typeface = Typeface.DEFAULT
    private var themeIsUnderline = false
    private var themeTextColor = Color.WHITE
    private var themeTextColorPressed = Color.WHITE

    private val kpsTimestamps = LongArray(256)
    private var kpsHead = 0
    private var kpsTail = 0
    private val kpsLock = Any()

    @Volatile
    private var totalClicks = 0
    private lateinit var keyCounters: IntArray
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var keyDownRunnables: Array<Runnable>
    private lateinit var keyUpRunnables: Array<Runnable>

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun dpToPxInt(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun getAdofaiBaseFont(): Typeface {
        return try {
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.adofai_font) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun reloadThemeConfig(pref: SharedPreferences) {
        themeTextSizeSp = try { pref.getFloat("theme_text_size", 20f) } catch (e: Exception) { 20f }
        val isBold = pref.getBoolean("theme_text_bold", false)
        val isItalic = pref.getBoolean("theme_text_italic", false)
        themeIsUnderline = pref.getBoolean("theme_text_underline", false)
        themeTextColor = try { Color.parseColor(pref.getString("theme_text_color", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }
        themeTextColorPressed = try { Color.parseColor(pref.getString("theme_text_color_pressed", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }

        val style = if (isBold && isItalic) Typeface.BOLD_ITALIC
        else if (isBold) Typeface.BOLD
        else if (isItalic) Typeface.ITALIC
        else Typeface.NORMAL

        themeTypeface = Typeface.create(getAdofaiBaseFont(), style)
    }

    private fun applyThemeToTextView(tv: TextView?) {
        if (tv == null) return
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, themeTextSizeSp)
        tv.setTextColor(themeTextColor)
        tv.setTypeface(themeTypeface, themeTypeface.style)
        if (themeIsUnderline) {
            tv.paintFlags = tv.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        } else {
            tv.paintFlags = tv.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
        }
    }

    private fun createTextColorStateList(normalColor: Int, pressedColor: Int): android.content.res.ColorStateList {
        val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf())
        val colors = intArrayOf(pressedColor, normalColor)
        return android.content.res.ColorStateList(states, colors)
    }

    private fun getLatestForegroundApp(): String {
        return try {
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
            latestApp
        } catch (e: SecurityException) {
            // Tối ưu: Bọc lót Crash khi bị người dùng rút quyền đột ngột
            ""
        }
    }

    private fun checkIsLandscape(): Boolean {
        val rotation = currentHardwareRotation
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private val autoShowRunnable = object : Runnable {
        override fun run() {
            try {
                var currentApp = getLatestForegroundApp()
                if (currentApp.isEmpty() || currentApp == "com.android.systemui") {
                    currentApp = lastForegroundApp
                }

                val isLandscape = checkIsLandscape()

                if (currentApp != lastForegroundApp) isManualOverride = false

                if (currentApp != lastForegroundApp || isLandscape != lastIsLandscape) {
                    lastForegroundApp = currentApp
                    lastIsLandscape = isLandscape

                    if (!isManualOverride) {
                        val allowedApps = sharedPrefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
                        val shouldShow = allowedApps.contains(currentApp) && isLandscape

                        if (shouldShow && !isKeyViewerOn) {
                            isKeyViewerOn = true
                            mainHandler.post { tryShowOverlay(); updateNotification() }
                        } else if (!shouldShow && isKeyViewerOn) {
                            isKeyViewerOn = false
                            mainHandler.post { hideOverlay(); updateNotification() }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.log(this@OverlayService, "AutoShow ERROR: ${e.message}")
            } finally {
                mainHandler.postDelayed(this, 1500)
            }
        }
    }

    private class TouchSlot(var trackingId: Int = -1, var x: Float = -1f, var y: Float = -1f, var lastHitLane: Int = -1, var isActive: Boolean = false)

    private val slots = Array(10) { TouchSlot() }
    private var currentSlot = 0
    private var maxRawX = 1f
    private var maxRawY = 1f
    private lateinit var hitboxes: Array<RectF>
    private lateinit var keyTrailView: KeyTrailView
    private lateinit var activeTrails: Array<KeyTrailView.Trail?> // Cập nhật kiểu dữ liệu khớp với Gói 1
    private val mainHandler = Handler(Looper.getMainLooper())

    private var eventReaderThread: Thread? = null
    @Volatile private var isReadingEvents = false
    private var shizukuProcess: Process? = null

    private val resetTotalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_RESET_TOTAL") {
                totalClicks = 0
                keyCounters.fill(0)
                mainHandler.post {
                    for (i in 0 until keyMode) keyCountersTv[i]?.text = "0"
                    updateKpsTotalUI(lastRenderedKps, totalClicks, force = true)
                }
                sharedPrefs.edit().putInt("TOTAL_CLICKS", 0).apply()
            }
        }
    }

    private val editReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_EDIT -> {
                    mainHandler.post { loadKeyViewerSettings() }
                    loadHitboxesFromPrefs()
                }
                ACTION_UPDATE_CONFIG -> {
                    mainHandler.post {
                        val newSource = sharedPrefs.getString("input_source", "touch") ?: "touch"
                        if (newSource != currentInputSource) {
                            currentInputSource = newSource
                            if (currentInputSource == "keyboard") {
                                stopReadingTouchEvents()
                                isShowTouchesOn = false
                                SharedTouchData.invalidateCallback?.invoke()
                            } else if (currentInputSource == "touch" && Shizuku.pingBinder()) {
                                initHardwareAndStartReading()
                            }
                        }

                        val newMode = sharedPrefs.getInt("current_key_mode", 6)
                        if (newMode != keyMode) {
                            keyMode = newMode
                            reinitializeArrays()
                            setupViewerView()
                            if (isOverlayShowing && wrapper.parent != null) {
                                try { windowManager.updateViewLayout(wrapper, viewerParams) } catch (e: Exception) {}
                            }
                        }
                        loadKeyViewerSettings()
                        loadHitboxesFromPrefs()
                    }
                }
                ACTION_VISIBILITY -> {
                    val isVisible = intent.getBooleanExtra("isVisible", true)
                    mainHandler.post {
                        wrapper.visibility = if (isVisible) View.VISIBLE else View.GONE
                        if (isVisible) wrapper.post { updateDisplayMetricsCache() }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        sharedPrefs = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        currentInputSource = sharedPrefs.getString("input_source", "touch") ?: "touch"
        totalClicks = sharedPrefs.getInt("TOTAL_CLICKS", 0)
        keyMode = sharedPrefs.getInt("current_key_mode", 6)

        reinitializeArrays()
        setupViewerView()
        updateDisplayMetricsCache()

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
        val resetFilter = IntentFilter("ACTION_RESET_TOTAL")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(editReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(resetTotalReceiver, resetFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(editReceiver, filter)
            registerReceiver(resetTotalReceiver, resetFilter)
        }

        mainHandler.post(object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                var currentKps = 0
                synchronized(kpsLock) {
                    while (kpsTail != kpsHead && currentTime - kpsTimestamps[kpsTail] > 1000) {
                        kpsTail = (kpsTail + 1) and 255
                    }
                    currentKps = if (kpsHead >= kpsTail) kpsHead - kpsTail else kpsHead + 256 - kpsTail
                }
                updateKpsTotalUI(currentKps, totalClicks)
                mainHandler.postDelayed(this, 100)
            }
        })

        if (currentInputSource == "touch" && Shizuku.pingBinder()) {
            initHardwareAndStartReading()
        }

        mainHandler.post(autoShowRunnable)
    }

    private fun reinitializeArrays() {
        laneOccupants = IntArray(keyMode)
        keyCounters = IntArray(keyMode)
        hitboxCentersX = FloatArray(keyMode)
        hitboxCentersY = FloatArray(keyMode)
        keyContainers = arrayOfNulls(keyMode)
        keyLabels = arrayOfNulls(keyMode)
        keyCountersTv = arrayOfNulls(keyMode)
        hitboxes = Array(keyMode) { RectF() }
        activeTrails = arrayOfNulls(keyMode)

        keyDownRunnables = Array(keyMode) { lane ->
            Runnable {
                if (activeTrails[lane] != null) return@Runnable
                val container = keyContainers[lane] ?: return@Runnable
                // Tích hợp kiến trúc Pool của Gói 1
                activeTrails[lane] = keyTrailView.addTrail(container.x, container.width.toFloat())
                container.isPressed = true
                keyCounters[lane]++
                keyCountersTv[lane]?.text = keyCounters[lane].toString()
            }
        }

        keyUpRunnables = Array(keyMode) { lane ->
            Runnable {
                activeTrails[lane]?.isReleased = true
                activeTrails[lane] = null
                val container = keyContainers[lane] ?: return@Runnable
                container.isPressed = false
            }
        }
    }

    private fun updateDisplayMetricsCache() {
        try {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            val w = realMetrics.widthPixels.toFloat()
            val h = realMetrics.heightPixels.toFloat()
            absMaxScreenDim = maxOf(w, h)
            absMinScreenDim = minOf(w, h)

            @Suppress("DEPRECATION")
            cachedHardwareRotation = windowManager.defaultDisplay.rotation
            currentHardwareRotation = cachedHardwareRotation

            if (isOverlayShowing && wrapper.parent != null) {
                val windowLocation = IntArray(2)
                wrapper.getLocationOnScreen(windowLocation)
                cachedOffsetX = windowLocation[0].toFloat()
                cachedOffsetY = windowLocation[1].toFloat()
            } else {
                cachedOffsetX = 0f
                cachedOffsetY = 0f
            }
        } catch (e: Exception) { }
    }
    private var cachedHardwareRotation = Surface.ROTATION_0

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
            var hasX = false; var hasY = false
            var tempMaxX = 1f; var tempMaxY = 1f
            val maxRegex = "max\\s+(\\d+)".toRegex()

            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("add device")) {
                    if (currentDevice != null && hasX && hasY) {
                        maxRawX = tempMaxX; maxRawY = tempMaxY
                        return currentDevice
                    }
                    currentDevice = line.substringAfter(": ").trim()
                    hasX = false; hasY = false
                }
                if (line.contains("ABS_MT_POSITION_X")) {
                    hasX = true
                    maxRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { tempMaxX = it }
                }
                if (line.contains("ABS_MT_POSITION_Y")) {
                    hasY = true
                    maxRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { tempMaxY = it }
                }
                line = reader.readLine()
            }
            if (currentDevice != null && hasX && hasY) {
                maxRawX = tempMaxX; maxRawY = tempMaxY
                return currentDevice
            }
        } catch (e: Exception) { }
        return null
    }

    private fun parseHexInline(s: String, start: Int, end: Int): Int {
        var res = 0
        for (i in start until end) {
            val c = s[i]
            val v = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> return res
            }
            res = (res shl 4) or v
        }
        return res
    }

    private fun startReadingTouchEvents(devicePath: String) {
        isReadingEvents = true
        val hwMaxX = maxRawX
        val hwMaxY = maxRawY

        eventReaderThread = Thread {
            var reader: BufferedReader? = null
            try {
                val cmd = arrayOf("sh", "-c", "getevent $devicePath")
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, cmd, null, null) as Process
                shizukuProcess = process

                reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isReadingEvents && !Thread.currentThread().isInterrupted) {
                    line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val len = line.length
                    var p3 = len
                    while (p3 > 0 && line[p3 - 1] <= ' ') p3--
                    if (p3 == 0) continue

                    var p2 = p3 - 1
                    while (p2 > 0 && line[p2] > ' ') p2--
                    val valStart = p2 + 1

                    var p1 = p2 - 1
                    while (p1 > 0 && line[p1] <= ' ') p1--
                    val codeEnd = p1 + 1
                    while (p1 > 0 && line[p1] > ' ') p1--
                    val codeStart = p1 + 1

                    var p0 = p1 - 1
                    while (p0 > 0 && line[p0] <= ' ') p0--
                    val typeEnd = p0 + 1
                    while (p0 > 0 && line[p0] > ' ') p0--
                    val typeStart = p0 + 1

                    val type = parseHexInline(line, typeStart, typeEnd)
                    val code = parseHexInline(line, codeStart, codeEnd)
                    val value = parseHexInline(line, valStart, p3)

                    when (type) {
                        0x0003 -> { // EV_ABS
                            when (code) {
                                // KHÓA AN TOÀN: Ép cứng Slot từ 0 đến 9, ngăn IndexOutOfBoundsException
                                0x002f -> currentSlot = value.coerceIn(0, 9)
                                0x0039 -> slots[currentSlot].trackingId = value
                                0x0035 -> slots[currentSlot].x = value.toFloat()
                                0x0036 -> slots[currentSlot].y = value.toFloat()
                            }
                        }
                        0x0000 -> { // EV_SYN
                            if (code == 0x0000) processSync(hwMaxX, hwMaxY)
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                // Tối ưu giải phóng I/O và Thread
                try { reader?.close() } catch (e: Exception) {}
                shizukuProcess?.destroy()
                isReadingEvents = false
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun processSync(hwMaxX: Float, hwMaxY: Float) {
        val rotation = currentHardwareRotation
        val physScreenWidth = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) absMaxScreenDim else absMinScreenDim
        val physScreenHeight = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) absMinScreenDim else absMaxScreenDim
        val offsetX = cachedOffsetX
        val offsetY = cachedOffsetY

        for (i in 0 until 10) {
            val slot = slots[i]
            var finalMappedX = -1f
            var finalMappedY = -1f

            if (slot.trackingId != -1) {
                var rawMappedX = 0f
                var rawMappedY = 0f

                when (rotation) {
                    Surface.ROTATION_0 -> { rawMappedX = (slot.x / hwMaxX) * physScreenWidth; rawMappedY = (slot.y / hwMaxY) * physScreenHeight }
                    Surface.ROTATION_90 -> { rawMappedX = (slot.y / hwMaxY) * physScreenWidth; rawMappedY = ((hwMaxX - slot.x) / hwMaxX) * physScreenHeight }
                    Surface.ROTATION_270 -> { rawMappedX = ((hwMaxY - slot.y) / hwMaxY) * physScreenWidth; rawMappedY = (slot.x / hwMaxX) * physScreenHeight }
                    Surface.ROTATION_180 -> { rawMappedX = ((hwMaxX - slot.x) / hwMaxX) * physScreenWidth; rawMappedY = ((hwMaxY - slot.y) / hwMaxY) * physScreenHeight }
                }

                finalMappedX = rawMappedX - offsetX
                finalMappedY = rawMappedY - offsetY

                if (!slot.isActive) {
                    var directHitLane = -1
                    for (j in 0 until keyMode) {
                        if (hitboxes[j].contains(finalMappedX, finalMappedY)) {
                            directHitLane = j
                            break
                        }
                    }

                    var finalLaneToActivate = -1
                    if (directHitLane != -1) {
                        if (laneOccupants[directHitLane] == 0) {
                            finalLaneToActivate = directHitLane
                        } else {
                            var nearestEmptyLane = -1
                            var minDistanceSq = Float.MAX_VALUE
                            val half = keyMode / 2
                            val startLane = if (directHitLane < half) 0 else half
                            val endLane = if (directHitLane < half) half else keyMode

                            for (j in startLane until endLane) {
                                if (j == directHitLane) continue
                                if (laneOccupants[j] == 0) {
                                    val hBox = hitboxes[j]
                                    if (hBox.width() <= 0 || hBox.height() <= 0) continue
                                    val dx = finalMappedX - hitboxCentersX[j]
                                    val dy = finalMappedY - hitboxCentersY[j]
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < minDistanceSq) {
                                        minDistanceSq = distSq
                                        nearestEmptyLane = j
                                    }
                                }
                            }
                            if (nearestEmptyLane != -1) finalLaneToActivate = nearestEmptyLane
                        }
                    }

                    if (finalLaneToActivate != -1) {
                        slot.lastHitLane = finalLaneToActivate
                        slot.isActive = true
                        laneOccupants[finalLaneToActivate]++
                        onKeyDown(finalLaneToActivate)

                        val currentTime = System.currentTimeMillis()
                        synchronized(kpsLock) {
                            kpsTimestamps[kpsHead] = currentTime
                            kpsHead = (kpsHead + 1) and 255
                            if (kpsHead == kpsTail) kpsTail = (kpsTail + 1) and 255
                        }
                        totalClicks++
                    }
                }
            } else {
                if (slot.isActive && slot.lastHitLane != -1) {
                    laneOccupants[slot.lastHitLane]--
                    if (laneOccupants[slot.lastHitLane] < 0) laneOccupants[slot.lastHitLane] = 0
                    onKeyUp(slot.lastHitLane)
                    slot.lastHitLane = -1
                    slot.isActive = false
                }
            }

            val sharedPt = SharedTouchData.points[i]
            if (isShowTouchesOn && slot.isActive && slot.trackingId != -1) {
                sharedPt.x = finalMappedX
                sharedPt.y = finalMappedY
                sharedPt.isActive = true
            } else {
                sharedPt.isActive = false
            }
        }
        SharedTouchData.invalidateCallback?.invoke()
    }

    private fun onKeyDown(lane: Int) = mainHandler.post(keyDownRunnables[lane])

    private fun onKeyUp(lane: Int) {
        if (laneOccupants[lane] > 0) return
        mainHandler.post(keyUpRunnables[lane])
    }

    fun triggerKeyPressFromKeyboard(lane: Int, isDown: Boolean) {
        if (currentInputSource != "keyboard" || lane !in 0 until keyMode) return

        val executeAction = {
            if (isDown) {
                laneOccupants[lane]++
                keyDownRunnables[lane].run()
                val currentTime = System.currentTimeMillis()
                synchronized(kpsLock) {
                    kpsTimestamps[kpsHead] = currentTime
                    kpsHead = (kpsHead + 1) and 255
                    if (kpsHead == kpsTail) kpsTail = (kpsTail + 1) and 255
                }
                totalClicks++
                updateKpsTotalUI(lastRenderedKps, totalClicks)
            } else {
                laneOccupants[lane]--
                if (laneOccupants[lane] < 0) laneOccupants[lane] = 0
                if (laneOccupants[lane] == 0) keyUpRunnables[lane].run()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) executeAction() else mainHandler.post(executeAction)
    }

    private fun stopReadingTouchEvents() {
        isReadingEvents = false
        eventReaderThread?.interrupt() // Cắt đứt luồng đang treo
        shizukuProcess?.destroy()
        shizukuProcess = null
        eventReaderThread = null
    }

    private fun setupViewerView() {
        if (!::wrapper.isInitialized) {
            wrapper = FrameLayout(this).apply {
                visibility = View.VISIBLE
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        if (::viewerContainer.isInitialized) wrapper.removeView(viewerContainer)

        viewerContainer = LayoutInflater.from(this).inflate(R.layout.overlay_view, wrapper, false).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        keyTrailView = viewerContainer.findViewById(R.id.keyTrailView)
        keyTrailView.setBackgroundColor(Color.TRANSPARENT)
        wrapper.addView(viewerContainer)

        val keysContainer = viewerContainer.findViewById<LinearLayout>(R.id.keysContainer)
        keysContainer.removeAllViews()

        // Tối ưu Hardcode UI: Sinh phím động chuẩn độ phân giải thay vì 60px cứng
        val defaultSizePx = dpToPxInt(60)
        for (i in 0 until keyMode) {
            val container = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(defaultSizePx, defaultSizePx)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val tvLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = if (currentInputSource == "keyboard") getAbbreviatedKeyName(sharedPrefs.getString("key_name_${keyMode}_$i", null)) else (i + 1).toString()
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }

            val tvCount = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPxInt(4)
                }
                gravity = Gravity.CENTER
                text = keyCounters[i].toString()
                maxLines = 1
                includeFontPadding = false
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 6, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            }

            container.addView(tvLabel)
            container.addView(tvCount)
            keysContainer.addView(container)

            keyContainers[i] = container
            keyLabels[i] = tvLabel
            keyCountersTv[i] = tvCount
        }

        kpsContainer = viewerContainer.findViewById(resources.getIdentifier("kpsContainer", "id", packageName))
        tvKpsLabel = viewerContainer.findViewById(resources.getIdentifier("tvKpsLabel", "id", packageName))
        tvKpsValue = viewerContainer.findViewById(resources.getIdentifier("tvKpsValue", "id", packageName))
        totalContainer = viewerContainer.findViewById(resources.getIdentifier("totalContainer", "id", packageName))
        tvTotalLabel = viewerContainer.findViewById(resources.getIdentifier("tvTotalLabel", "id", packageName))
        tvTotalValue = viewerContainer.findViewById(resources.getIdentifier("tvTotalValue", "id", packageName))

        tvTotalLabel?.visibility = if (keyMode == 4) View.GONE else View.VISIBLE
        totalContainer?.visibility = View.VISIBLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wrapper.isForceDarkAllowed = false
            viewerContainer.isForceDarkAllowed = false
            keyTrailView.isForceDarkAllowed = false
        }

        viewerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun loadKeyViewerSettings() {
        val pref = getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
        reloadThemeConfig(pref)

        val x = pref.getFloat("viewer_x", 100f)
        val y = pref.getFloat("viewer_y", 100f)
        val scale = pref.getFloat("viewer_scale", 1.0f)
        val speed = pref.getFloat("trail_speed", 0.8f)
        val limitPx = pref.getInt("trail_limit_px", 300)
        val keyWidth = pref.getInt("key_width", 60)
        val keyHeight = pref.getInt("key_height", 60)
        val keySpacing = pref.getInt("key_spacing", 7)
        val borderWidthDp = pref.getInt("theme_border_width", 2)
        val cornerRadiusDp = pref.getInt("theme_corner_radius", 6)
        val borderPx = (borderWidthDp * resources.displayMetrics.density).toInt()
        val radiusPx = cornerRadiusDp * resources.displayMetrics.density

        val bgNormal = try { Color.parseColor(pref.getString("theme_bg_normal", "#000000")) } catch (e: Exception) { Color.BLACK }
        val borderNormal = try { Color.parseColor(pref.getString("theme_border_normal", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }
        val bgPressed = try { Color.parseColor(pref.getString("theme_bg_pressed", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }
        val borderPressed = try { Color.parseColor(pref.getString("theme_border_pressed", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }
        val rainColor = try { Color.parseColor(pref.getString("theme_rain_color", "#FFFFFF")) } catch (e: Exception) { Color.WHITE }
        val shadowColor = try { Color.parseColor(pref.getString("theme_rain_shadow", "#00FFFF")) } catch (e: Exception) { Color.CYAN }

        val keysContainer = viewerContainer.findViewById<LinearLayout>(R.id.keysContainer)
        val showCounters = pref.getBoolean("show_key_counters", false)

        viewerContainer.post {
            viewerContainer.apply { pivotX = 0f; pivotY = 0f; this.x = x; this.y = y; scaleX = scale; scaleY = scale }

            for (i in 0 until keysContainer.childCount) {
                val container = keysContainer.getChildAt(i) as? LinearLayout ?: continue
                val params = container.layoutParams as ViewGroup.MarginLayoutParams
                params.width = dpToPxInt(keyWidth)
                params.height = dpToPxInt(keyHeight)
                if (i > 0) params.leftMargin = dpToPxInt(keySpacing)
                container.layoutParams = params

                val tvLabel = keyLabels[i]
                val tvCount = keyCountersTv[i]

                if (currentInputSource == "keyboard") tvLabel?.text = getAbbreviatedKeyName(pref.getString("key_name_${keyMode}_$i", null))
                else tvLabel?.text = (i + 1).toString()

                applyThemeToTextView(tvLabel)
                tvLabel?.setTextColor(createTextColorStateList(themeTextColor, themeTextColorPressed))

                if (tvCount != null) {
                    tvCount.typeface = themeTypeface
                    if (themeIsUnderline) tvCount.paintFlags = tvCount.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    else tvCount.paintFlags = tvCount.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
                    tvCount.setTextColor(createTextColorStateList(themeTextColor, themeTextColorPressed))
                    tvCount.visibility = if (showCounters) View.VISIBLE else View.GONE
                }
                container.background = createAlphaSelector(bgNormal, borderNormal, bgPressed, borderPressed, borderPx, radiusPx)
            }

            kpsContainer?.background = createAlphaSelector(bgNormal, borderNormal, bgPressed, borderPressed, borderPx, radiusPx)
            totalContainer?.background = createAlphaSelector(bgNormal, borderNormal, bgPressed, borderPressed, borderPx, radiusPx)
            kpsContainer?.let { (it.layoutParams as ViewGroup.MarginLayoutParams).rightMargin = dpToPxInt(keySpacing) }
            viewerContainer.findViewById<View>(R.id.bottomCountersContainer)?.let { (it.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dpToPxInt(keySpacing) }

            applyThemeToTextView(tvKpsLabel)
            applyThemeToTextView(tvKpsValue)
            applyThemeToTextView(tvTotalLabel)
            applyThemeToTextView(tvTotalValue)
            updateKpsTotalUI(lastRenderedKps, lastRenderedTotal, force = true)

            keyTrailView.layoutParams.height = dpToPxInt(limitPx)
            keyTrailView.setParameters(speed, limitPx.toFloat())
            keyTrailView.setThemeColors(rainColor, shadowColor)
            viewerContainer.requestLayout()
        }
    }

    private fun createBoxDrawable(bgColor: Int, borderColor: Int, strokeWidthPx: Int, cornerRadiusPx: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(bgColor)
            setStroke(strokeWidthPx, borderColor)
        }
    }

    private fun createAlphaSelector(
        bgNormal: Int,
        borderNormal: Int,
        bgPressed: Int,
        borderPressed: Int,
        strokePx: Int,
        radiusPx: Float
    ): android.graphics.drawable.StateListDrawable {
        return android.graphics.drawable.StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createBoxDrawable(bgPressed, borderPressed, strokePx, radiusPx)
            )
            addState(intArrayOf(), createBoxDrawable(bgNormal, borderNormal, strokePx, radiusPx))
        }
    }

    private fun getAbbreviatedKeyName(keyName: String?): String {
        if (keyName.isNullOrBlank()) return "NULL"
        return when (keyName.uppercase()) {
            "SPACE" -> "␣"; "ENTER", "NUMPAD_ENTER" -> "↵"; "DEL", "FORWARD_DEL", "BACKSPACE" -> "⌫"
            "DPAD_UP", "UP" -> "↑"; "DPAD_DOWN", "DOWN" -> "↓"; "DPAD_LEFT", "LEFT" -> "←"; "DPAD_RIGHT", "RIGHT" -> "→"
            "ESCAPE" -> "ESC"; "PAGE_UP" -> "PG↑"; "PAGE_DOWN" -> "PG↓"; "SHIFT_LEFT", "SHIFT_RIGHT" -> "⇧"
            "CTRL_LEFT", "CTRL_RIGHT" -> "CTL"; "ALT_LEFT", "ALT_RIGHT" -> "ALT"; "TAB" -> "⇥"; "MINUS" -> "-"
            "EQUALS" -> "="; "PLUS" -> "+"; "GRAVE" -> "`"; "BACKSLASH" -> "\\"; "COMMA" -> ","; "PERIOD" -> "."
            "SLASH" -> "/"; "LEFT_BRACKET" -> "["; "RIGHT_BRACKET" -> "]"; "SEMICOLON" -> ";"; "APOSTROPHE" -> "'"
            else -> if (keyName.length > 3) keyName.substring(0, 3) else keyName
        }
    }

    private fun getHitboxKey(index: Int, type: String): String {
        val id = index + 1
        return if (keyMode == 6) "hitbox_${id}_$type"
        else "hitbox_" + (when(type) { "x" -> "left_"; "y" -> "top_"; "w" -> "width_"; "h" -> "height_"; else -> "" }) + "${keyMode}_$index"
    }

    private fun loadHitboxesFromPrefs() {
        val pref = getSharedPreferences("HitboxPrefs", Context.MODE_PRIVATE)
        val checkKey = getHitboxKey(0, "x")

        if (!pref.contains(checkKey)) {
            val editor = pref.edit()
            val screenWidth = absMaxScreenDim
            val screenHeight = absMinScreenDim
            val offsetPx = dpToPx(20f)
            val paddingPx = dpToPx(40f)

            if (keyMode == 6) {
                val w1 = screenWidth * 0.10f; val w2 = screenWidth * 0.15f; val w3 = screenWidth * 0.25f
                val widths = floatArrayOf(w1, w2, w3, w3, w2, w1)
                var currentX = 0f
                for (i in 0 until 6) {
                    val w = widths[i]
                    editor.putFloat("hitbox_${i+1}_x", currentX - offsetPx)
                    editor.putFloat("hitbox_${i+1}_y", -offsetPx)
                    editor.putInt("hitbox_${i+1}_w", (w + paddingPx).toInt())
                    editor.putInt("hitbox_${i+1}_h", (screenHeight + paddingPx).toInt())
                    currentX += w
                }
            } else {
                val colWidth = screenWidth / keyMode
                for (i in 0 until keyMode) {
                    editor.putFloat(getHitboxKey(i, "x"), (i * colWidth) - offsetPx)
                    editor.putFloat(getHitboxKey(i, "y"), -offsetPx)
                    editor.putInt(getHitboxKey(i, "w"), (colWidth + paddingPx).toInt())
                    editor.putInt(getHitboxKey(i, "h"), (screenHeight + paddingPx).toInt())
                }
            }
            editor.apply()
        }

        for (i in 0 until keyMode) {
            val x = pref.getFloat(getHitboxKey(i, "x"), 0f)
            val y = pref.getFloat(getHitboxKey(i, "y"), 0f)
            val w = pref.getInt(getHitboxKey(i, "w"), 0)
            val h = pref.getInt(getHitboxKey(i, "h"), 0)
            hitboxes[i].set(x, y, x + w, y + h)
            hitboxCentersX[i] = hitboxes[i].centerX()
            hitboxCentersY[i] = hitboxes[i].centerY()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun updateNotification() {
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingKeyViewer = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_KEY_VIEWER }, flag)
        val pendingTouches = if (SharedTouchData.invalidateCallback != null) {
            PendingIntent.getService(this, 2, Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_TOUCHES }, flag)
        } else {
            PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).apply {
                action = "ACTION_PROMPT_ACCESSIBILITY"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_SHOW_ACC_PROMPT", true)
            }, flag)
        }
        val pendingOpenApp = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }, flag)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_key)
            .setContentTitle(getString(R.string.notif_content_title))
            .setContentText(getString(R.string.notif_content_text))
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (isKeyViewerOn) getString(R.string.notif_toggle_on) else getString(R.string.notif_toggle_off), pendingKeyViewer)

        if (currentInputSource == "touch") notificationBuilder.addAction(0, if (isShowTouchesOn) getString(R.string.notif_hide_touches) else getString(R.string.notif_show_touches), pendingTouches)
        notificationBuilder.addAction(0, getString(R.string.notif_open_app), pendingOpenApp)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
        } catch (e: Exception) { }
    }

    private fun tryShowOverlay(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            mainHandler.post { Toast.makeText(this, getString(R.string.toast_overlay_permission_required), Toast.LENGTH_LONG).show() }
            return false
        }
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
                    if (wrapper.parent == null) {
                        windowManager.addView(wrapper, viewerParams)
                        isOverlayShowing = true
                        wrapper.post { updateDisplayMetricsCache() }
                    }
                } catch (e: Exception) { }
            }
        }
        return true
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            sharedPrefs.edit().putInt("TOTAL_CLICKS", totalClicks).apply()
            mainHandler.post { try { if (wrapper.parent != null) windowManager.removeView(wrapper); isOverlayShowing = false } catch (e: Exception) {} }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isKeyViewerOn) { hideOverlay(); isKeyViewerOn = false }
            if (isShowTouchesOn) { isShowTouchesOn = false; SharedTouchData.invalidateCallback?.invoke() }
            updateNotification()
        }
        if (isOverlayShowing) wrapper.post { updateDisplayMetricsCache() }

        try {
            @Suppress("DEPRECATION") val newRotation = windowManager.defaultDisplay.rotation
            if (newRotation != currentHardwareRotation) {
                currentHardwareRotation = newRotation
                cachedHardwareRotation = newRotation
                updateDisplayMetricsCache()
                loadKeyViewerSettings()
                if (isOverlayShowing && wrapper.parent != null) windowManager.updateViewLayout(wrapper, viewerParams)
            }
        } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> updateNotification()
            ACTION_TOGGLE_KEY_VIEWER -> {
                isManualOverride = true
                if (isKeyViewerOn) { hideOverlay(); isKeyViewerOn = false }
                else if (tryShowOverlay()) isKeyViewerOn = true
                updateNotification()
            }
            ACTION_TOGGLE_TOUCHES -> {
                isShowTouchesOn = if (isShowTouchesOn) { SharedTouchData.invalidateCallback?.invoke(); false }
                else SharedTouchData.invalidateCallback != null
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        sharedPrefs.edit().putInt("TOTAL_CLICKS", totalClicks).apply()
        mainHandler.removeCallbacks(autoShowRunnable)
        stopReadingTouchEvents()
        try { unregisterReceiver(editReceiver); unregisterReceiver(resetTotalReceiver) } catch (e: Exception) {}
        hideOverlay()
    }

    private fun updateKpsTotalUI(kps: Int, total: Int, force: Boolean = false) {
        if (!force && kps == lastRenderedKps && total == lastRenderedTotal) return
        lastRenderedKps = kps; lastRenderedTotal = total
        mainHandler.post {
            try {
                tvKpsValue?.text = kps.toString()
                tvTotalValue?.text = decimalFormatter.format(total)
            } catch (e: Exception) {}
        }
    }
}