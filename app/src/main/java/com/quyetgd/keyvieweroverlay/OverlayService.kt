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
import android.os.SystemClock

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

    private var cachedOffsetX = 0f
    private var cachedOffsetY = 0f
    private var absMaxScreenDim = 0f
    private var absMinScreenDim = 0f
    private var cachedPhysWidth = 0f
    private var cachedPhysHeight = 0f
    private var invHwMaxX = 1f
    private var invHwMaxY = 1f

    @Volatile
    private var currentHardwareRotation = Surface.ROTATION_0

    private val decimalFormatter = java.text.NumberFormat.getInstance(java.util.Locale.getDefault())
    private val realMetrics = android.util.DisplayMetrics()

    private var keyMode = 6
    private lateinit var laneOccupants: IntArray
    private lateinit var hitboxCentersX: FloatArray
    private lateinit var hitboxCentersY: FloatArray

    private lateinit var keyContainers: Array<LinearLayout?>
    private lateinit var keyLabels: Array<TextView?>
    private lateinit var keyCountersTv: Array<FastCounterView?>
    private var isOverlayShowing = false
    private var isKeyViewerOn = false
    private var isShowTouchesOn = false

    private var isManualOverride = false
    private var lastForegroundApp = ""
    private var lastIsLandscape = false

    private var themeTextSizeSp = 20f
    private var themeTypeface: Typeface = Typeface.DEFAULT
    private var themeIsUnderline = false
    private var themeTextColor = Color.WHITE
    private var themeTextColorPressed = Color.WHITE

    private val kpsTimestamps = LongArray(256)
    @Volatile private var kpsHead = 0
    @Volatile private var kpsTail = 0
    // XÓA BỎ DÒNG: private val kpsLock = Any()

    @Volatile
    private var totalClicks = 0
    private lateinit var keyCounters: IntArray
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var keyDownRunnables: Array<Runnable>
    private lateinit var keyUpRunnables: Array<Runnable>
    private lateinit var pendingKeyDownTimes: LongArray
    private lateinit var pendingKeyUpTimes: LongArray

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

    // 3. CHỐNG RÁC: Khởi tạo vỏ bọc Event cố định
    private val sharedUsageEvent = UsageEvents.Event()

    private fun getLatestForegroundApp(): String {
        return try {
            val time = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(time - 5000, time)
            var latestApp = ""

            while (events.hasNextEvent()) {
                // Đổ dữ liệu vào vỏ bọc cũ, không đẻ ra Object mới
                events.getNextEvent(sharedUsageEvent)
                if (sharedUsageEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestApp = sharedUsageEvent.packageName ?: ""
                }
            }
            latestApp
        } catch (e: SecurityException) {
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

                if (currentApp != lastForegroundApp) {
                    isManualOverride = false
                }

                // Chỉ ghi log khi có sự thay đổi (chuyển app hoặc xoay màn hình) để tránh spam log
                if (currentApp != lastForegroundApp || isLandscape != lastIsLandscape) {
                    AppLogger.i(this@OverlayService, "AutoShow", "Trạng thái đổi -> App: $currentApp | Cửa sổ ngang: $isLandscape | ManualOverride: $isManualOverride")

                    lastForegroundApp = currentApp
                    lastIsLandscape = isLandscape

                    if (!isManualOverride) {
                        val allowedApps = sharedPrefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
                        val shouldShow = allowedApps.contains(currentApp) && isLandscape

                        if (shouldShow && !isKeyViewerOn) {
                            AppLogger.i(this@OverlayService, "AutoShow", "Kích hoạt tự động hiện Overlay cho Game")
                            isKeyViewerOn = true
                            mainHandler.post { tryShowOverlay(); updateNotification() }
                        } else if (!shouldShow && isKeyViewerOn) {
                            AppLogger.i(this@OverlayService, "AutoShow", "Tự động ẩn Overlay do thoát Game")
                            isKeyViewerOn = false
                            mainHandler.post { hideOverlay(); updateNotification() }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(this@OverlayService, "OverlayService", "Lỗi AutoShowRunnable", e)
            } finally {
                mainHandler.postDelayed(this, 1500)
            }
        }
    }
    // Chỉ giữ 1 class TouchSlot duy nhất, với biến mappedLane
    private class TouchSlot {
        var trackingId = -1
        var x = -1f
        var y = -1f
        var mappedLane = -1
        var isActive = false
    }

    // MỞ RỘNG MẢNG LÊN 32 ĐỂ KHÔNG BỊ TRÀN KHE CẮM
    private val slots = Array(32) { TouchSlot() }
    private var currentSlot = 0
    private var maxRawX = 1f
    private var maxRawY = 1f
    private var diagnosticClickCount = 0
    // TỐI ƯU CỰC HẠN: Rã Object thành các mảng cơ sở (Primitive Array)
    private lateinit var hitboxLeft: FloatArray
    private lateinit var hitboxRight: FloatArray
    private lateinit var hitboxTop: FloatArray
    private lateinit var hitboxBottom: FloatArray
    private lateinit var keyTrailView: KeyTrailView
    private lateinit var activeTrails: Array<KeyTrailView.Trail?>
    private val mainHandler = Handler(Looper.getMainLooper())

    private var eventReaderThread: Thread? = null
    @Volatile private var isReadingEvents = false
    private var shizukuProcess: Process? = null

    private val resetTotalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_RESET_TOTAL") {
                AppLogger.i(context!!, "OverlayService", "Người dùng yêu cầu Reset Total Clicks")
                totalClicks = 0
                keyCounters.fill(0)
                mainHandler.post {
                    for (i in 0 until keyMode) keyCountersTv[i]?.setCount(0)
                    updateKpsTotalUI(lastRenderedKps, totalClicks, force = true)
                }

                // Xóa trắng trong bộ nhớ
                val editor = sharedPrefs.edit()
                editor.putInt("TOTAL_CLICKS", 0)
                for (i in 0 until keyMode) editor.putInt("KEY_COUNT_${keyMode}_$i", 0)
                editor.apply()
            }
        }
    }

    private val editReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_EDIT -> {
                    AppLogger.i(context!!, "OverlayService", "Chế độ Edit dừng lại, tải lại cấu hình")
                    mainHandler.post { loadKeyViewerSettings() }
                    loadHitboxesFromPrefs()
                }
                ACTION_UPDATE_CONFIG -> {
                    AppLogger.i(context!!, "OverlayService", "Nhận lệnh UPDATE_CONFIG từ màn hình Cài đặt")
                    mainHandler.post {
                        val newSource = sharedPrefs.getString("input_source", "touch") ?: "touch"
                        if (newSource != currentInputSource) {
                            AppLogger.i(context, "OverlayService", "Đổi nguồn Input: $currentInputSource -> $newSource")
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
                            AppLogger.i(context, "OverlayService", "Đổi số phím KeyMode: $keyMode -> $newMode")
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
                    AppLogger.d(context!!, "OverlayService", "Lệnh đổi Visibility wrapper: $isVisible")
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
        // Trục xuất Main Thread của Overlay sang lõi xử lý Đồ họa VIP nhất, né lõi của Game
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        super.onCreate()
        AppLogger.i(this, "OverlayService", "=== BẮT ĐẦU KHỞI TẠO SERVICE ===")
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

        AppLogger.i(this, "OverlayService", "Thông số ban đầu: Input=$currentInputSource, Mode=$keyMode, TotalClicks=$totalClicks")

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
                val currentTime = SystemClock.uptimeMillis()
                var currentKps = 0

                    while (kpsTail != kpsHead && currentTime - kpsTimestamps[kpsTail] > 1000) {
                        kpsTail = (kpsTail + 1) and 255
                    }
                    currentKps = if (kpsHead >= kpsTail) kpsHead - kpsTail else kpsHead + 256 - kpsTail

                updateKpsTotalUI(currentKps, totalClicks)
                mainHandler.postDelayed(this, 100)
            }
        })

        if (currentInputSource == "touch" && Shizuku.pingBinder()) {
            AppLogger.i(this, "OverlayService", "Đã cấp Shizuku, tiến hành gắn hook cảm ứng")
            initHardwareAndStartReading()
        } else if (currentInputSource == "touch") {
            AppLogger.w(this, "OverlayService", "Input là Touch nhưng không có Shizuku Ping Binder!")
        }

        mainHandler.post(autoShowRunnable)
    }

    private fun reinitializeArrays() {
        // 1. Phục hồi toàn bộ các khởi tạo gốc đã bị mất
        laneOccupants = IntArray(keyMode)
        hitboxCentersX = FloatArray(keyMode)
        hitboxCentersY = FloatArray(keyMode)
        keyContainers = arrayOfNulls(keyMode)
        keyLabels = arrayOfNulls(keyMode)
        keyCountersTv = arrayOfNulls(keyMode)
        hitboxLeft = FloatArray(keyMode)
        hitboxRight = FloatArray(keyMode)
        hitboxTop = FloatArray(keyMode)
        hitboxBottom = FloatArray(keyMode)
        activeTrails = arrayOfNulls(keyMode)

        // 2. Khởi tạo mảng đếm và nạp lại số liệu cũ từ bộ nhớ
        keyCounters = IntArray(keyMode)
        for (i in 0 until keyMode) {
            keyCounters[i] = sharedPrefs.getInt("KEY_COUNT_${keyMode}_$i", 0)
        }

        // 3. Khởi tạo 2 mảng thời gian Async
        pendingKeyDownTimes = LongArray(keyMode)
        pendingKeyUpTimes = LongArray(keyMode)

        keyDownRunnables = Array(keyMode) { lane ->
            Runnable {
                val container = keyContainers[lane] ?: return@Runnable
                // Lấy thời gian gốc đã chụp được từ luồng cảm ứng
                val pressTime = pendingKeyDownTimes[lane]

                if (activeTrails[lane] != null) {
                    // Nếu kẹt phím, ép nhả bằng thời gian gốc mới
                    keyTrailView.releaseLane(lane, pressTime)
                }

                // Truyền pressTime sang cho KeyTrailView
                activeTrails[lane] = keyTrailView.addTrail(lane, container.x, container.width.toFloat(), pressTime)

                container.isPressed = true
                keyCounters[lane]++

                val tv = keyCountersTv[lane]
                if (tv != null && tv.visibility == View.VISIBLE) {
                    tv.setCount(keyCounters[lane])
                }
            }
        }

        keyUpRunnables = Array(keyMode) { lane ->
            Runnable {
                keyContainers[lane]?.isPressed = false
                // Lấy thời gian nhả phím gốc
                val releaseTime = pendingKeyUpTimes[lane]
                // Truyền releaseTime sang cho KeyTrailView
                keyTrailView.releaseLane(lane, releaseTime)
                activeTrails[lane] = null
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
            val rotation = cachedHardwareRotation
            cachedPhysWidth = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) absMaxScreenDim else absMinScreenDim
            cachedPhysHeight = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) absMinScreenDim else absMaxScreenDim
        } catch (e: Exception) {
            AppLogger.e(this, "OverlayService", "Lỗi updateDisplayMetricsCache", e)
        }
    }

    private var cachedHardwareRotation = Surface.ROTATION_0

    private fun initHardwareAndStartReading() {
        Thread {
            AppLogger.d(this, "TouchReader", "Bắt đầu tìm thiết bị /dev/input...")
            val device = calibrateHardwareAndFindDevice() ?: "/dev/input/event2"
            AppLogger.i(this, "TouchReader", "Chốt thiết bị đọc cảm ứng: $device (MaxX=$maxRawX, MaxY=$maxRawY)")
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
                        AppLogger.d(this, "TouchReader", "Thông số màn hình thực tế qua InputManager: $maxRawX x $maxRawY")
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
            var currentName = ""
            var hasX = false; var hasY = false
            var tempMaxX = 1f; var tempMaxY = 1f
            val maxRegex = "max\\s+(\\d+)".toRegex()

            class DeviceInfo(val path: String, val name: String, val maxX: Float, val maxY: Float)
            val candidates = mutableListOf<DeviceInfo>()

            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("add device")) {
                    if (currentDevice != null && hasX && hasY) {
                        candidates.add(DeviceInfo(currentDevice, currentName, tempMaxX, tempMaxY))
                    }
                    currentDevice = line.substringAfter(": ").trim()
                    currentName = ""
                    hasX = false; hasY = false
                } else if (line.contains("name:")) {
                    currentName = line.substringAfter("name:").replace("\"", "").trim().lowercase()
                } else if (line.contains("ABS_MT_POSITION_X")) {
                    hasX = true
                    maxRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { tempMaxX = it }
                } else if (line.contains("ABS_MT_POSITION_Y")) {
                    hasY = true
                    maxRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let { tempMaxY = it }
                }
                line = reader.readLine()
            }
            if (currentDevice != null && hasX && hasY) {
                candidates.add(DeviceInfo(currentDevice, currentName, tempMaxX, tempMaxY))
            }

            AppLogger.d(this, "TouchReader", "Đã tìm thấy ${candidates.size} thiết bị cảm ứng.")
            var bestDevice: DeviceInfo? = null

            for (candidate in candidates) {
                AppLogger.d(this, "TouchReader", "- Ứng viên: ${candidate.path} | Name: '${candidate.name}' | Max: ${candidate.maxX}x${candidate.maxY}")

                // Ưu tiên 1: Tên màn hình thật của hãng (Samsung, Goodix, Synaptics...)
                if (candidate.name.contains("sec_touchscreen") ||
                    candidate.name.contains("fts") ||
                    candidate.name.contains("goodix") ||
                    candidate.name.contains("synaptics")) {
                    bestDevice = candidate
                    break
                }
            }

            if (bestDevice == null) {
                // Ưu tiên 2: Tên có chữ touch nhưng loại trừ triệt để thiết bị ảo (virtual)
                bestDevice = candidates.firstOrNull { it.name.contains("touch") && !it.name.contains("virtual") }
            }

            if (bestDevice == null && candidates.isNotEmpty()) {
                // Ưu tiên 3: Nếu tên lạ, luôn lấy thiết bị có MaxX lớn nhất
                // (Màn hình vật lý thật luôn có Raw phân giải siêu cao, 100% lớn hơn hàng ảo)
                bestDevice = candidates.maxByOrNull { it.maxX }
            }

            if (bestDevice != null) {
                maxRawX = bestDevice.maxX
                maxRawY = bestDevice.maxY
                invHwMaxX = 1f / maxRawX
                invHwMaxY = 1f / maxRawY
                AppLogger.i(this, "TouchReader", "=> THIẾT BỊ CHUẨN ĐƯỢC CHỌN: ${bestDevice.path}")
                return bestDevice.path
            }
        } catch (e: Exception) {
            AppLogger.e(this, "TouchReader", "Lỗi trong quá trình calibrate Hardware", e)
        }
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
            // Ép Kernel cấp quyền ưu tiên ngang với phần cứng xử lý Âm thanh (Cao nhất, độ trễ thấp nhất)
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            var inputStream: java.io.InputStream? = null
            try {
                val cmd = arrayOf("sh", "-c", "getevent $devicePath")
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(null, cmd, null, null) as Process
                shizukuProcess = process

                // ZERO-ALLOCATION I/O READING: Cỗ máy đọc Byte thô không sinh rác
                inputStream = process.inputStream
                val buffer = ByteArray(4096)
                var bytesRead: Int

                var type = 0
                var code = 0
                var value = 0
                var hexTemp = 0
                var tokenCount = 0
                var inToken = false

                AppLogger.i(this, "TouchReader", "Bắt đầu stream sự kiện bằng Mảng Byte (Zero-Allocation 100%)")

                while (isReadingEvents && !Thread.currentThread().isInterrupted) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    for (i in 0 until bytesRead) {
                        val b = buffer[i]
                        val char = b.toInt().toChar()

                        if (char == ' ' || char == '\t') {
                            if (inToken) {
                                when (tokenCount) {
                                    0 -> type = hexTemp
                                    1 -> code = hexTemp
                                }
                                tokenCount++
                                hexTemp = 0
                                inToken = false
                            }
                        } else if (char == '\n' || char == '\r') {
                            if (inToken) {
                                value = hexTemp // Cột cuối cùng là Value
                                tokenCount++
                            }

                            // Nếu đã thu thập đủ 3 cột (Type, Code, Value) -> Xử lý chạm!
                            if (tokenCount >= 3) {
                                when (type) {
                                    0x0003 -> { // EV_ABS
                                        when (code) {
                                            0x002f -> currentSlot = value.coerceIn(0, 31)
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

                            // Reset bộ nhớ cho dòng event tiếp theo
                            tokenCount = 0
                            hexTemp = 0
                            inToken = false
                            type = 0; code = 0; value = 0

                        } else {
                            // Dịch bit nhị phân trực tiếp từ Hex sang Int
                            val digit = when (char) {
                                in '0'..'9' -> char - '0'
                                in 'a'..'f' -> char - 'a' + 10
                                in 'A'..'F' -> char - 'A' + 10
                                else -> -1
                            }
                            if (digit != -1) {
                                hexTemp = (hexTemp shl 4) or digit
                                inToken = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(this, "TouchReader", "Lỗi đọc luồng getevent Byte", e)
            } finally {
                try { inputStream?.close() } catch (e: Exception) {}
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

        // CẤM THUẬT: Tính trước nghịch đảo 1 lần duy nhất để biến mọi phép Chia thành phép Nhân
        val invHwMaxX = 1f / hwMaxX
        val invHwMaxY = 1f / hwMaxY

        for (i in 0 until 32) {
            val slot = slots[i]
            var finalMappedX = -1f
            var finalMappedY = -1f

            if (slot.trackingId != -1) {
                var rawMappedX = 0f
                var rawMappedY = 0f

                // CPU chạy mượt mà tuyệt đối vì chỉ còn phép Nhân (*)
                // Bỏ qua mọi phép tính hằng số, lao thẳng vào xử lý bit
                when (currentHardwareRotation) {
                    Surface.ROTATION_0 -> { rawMappedX = slot.x * invHwMaxX * cachedPhysWidth; rawMappedY = slot.y * invHwMaxY * cachedPhysHeight }
                    Surface.ROTATION_90 -> { rawMappedX = slot.y * invHwMaxY * cachedPhysWidth; rawMappedY = (hwMaxX - slot.x) * invHwMaxX * cachedPhysHeight }
                    Surface.ROTATION_270 -> { rawMappedX = (hwMaxY - slot.y) * invHwMaxY * cachedPhysWidth; rawMappedY = slot.x * invHwMaxX * cachedPhysHeight }
                    Surface.ROTATION_180 -> { rawMappedX = (hwMaxX - slot.x) * invHwMaxX * cachedPhysWidth; rawMappedY = (hwMaxY - slot.y) * invHwMaxY * cachedPhysHeight }
                }
                finalMappedX = rawMappedX - cachedOffsetX
                finalMappedY = rawMappedY - cachedOffsetY

                if (!slot.isActive) {
                    var directHitLane = -1
                    for (j in 0 until keyMode) {
                        // CPU tính toán thuần túy bằng Register, không tốn chi phí gọi hàm!
                        if (finalMappedX >= hitboxLeft[j] && finalMappedX < hitboxRight[j] &&
                            finalMappedY >= hitboxTop[j] && finalMappedY < hitboxBottom[j]) {
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
                                    // Thay vì gọi hBox.width(), tính trực tiếp
                                    if (hitboxRight[j] <= hitboxLeft[j] || hitboxBottom[j] <= hitboxTop[j]) continue
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
                        // SỬ DỤNG mappedLane THAY VÌ lastHitLane
                        slot.mappedLane = finalLaneToActivate
                        slot.isActive = true
                        laneOccupants[finalLaneToActivate]++
                        pendingKeyDownTimes[finalLaneToActivate] = SystemClock.uptimeMillis()
                        onKeyDown(finalLaneToActivate)

                        val currentTime = SystemClock.uptimeMillis()

                            kpsTimestamps[kpsHead] = currentTime
                            kpsHead = (kpsHead + 1) and 255
                            if (kpsHead == kpsTail) kpsTail = (kpsTail + 1) and 255

                        totalClicks++
                    }
                }
            } else {
                // SỬ DỤNG mappedLane ĐỂ TRÁNH QUÊN PHÍM KHI NHẢ
                if (slot.isActive && slot.mappedLane != -1) {
                    laneOccupants[slot.mappedLane]--
                    if (laneOccupants[slot.mappedLane] < 0) laneOccupants[slot.mappedLane] = 0
                    pendingKeyUpTimes[slot.mappedLane] = SystemClock.uptimeMillis()
                    onKeyUp(slot.mappedLane)
                    slot.mappedLane = -1
                    slot.isActive = false
                }
            }

            // BƯỚC 3 (BÊN DƯỚI) SẼ SỬA ĐOẠN NÀY ĐỂ TRÁNH CRASH APP
            if (isShowTouchesOn && i < SharedTouchData.points.size) {
                val sharedPt = SharedTouchData.points[i]
                if (slot.isActive && slot.trackingId != -1) {
                    sharedPt.x = finalMappedX
                    sharedPt.y = finalMappedY
                    sharedPt.isActive = true
                } else {
                    sharedPt.isActive = false
                }
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
                val currentTime = SystemClock.uptimeMillis()

                    kpsTimestamps[kpsHead] = currentTime
                    kpsHead = (kpsHead + 1) and 255
                    if (kpsHead == kpsTail) kpsTail = (kpsTail + 1) and 255

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
        AppLogger.i(this, "TouchReader", "Nhận lệnh dừng chủ động luồng đọc chạm")
        isReadingEvents = false
        eventReaderThread?.interrupt()
        shizukuProcess?.destroy()
        shizukuProcess = null
        eventReaderThread = null
    }

    private fun setupViewerView() {
        AppLogger.d(this, "OverlayService", "Tiến hành nạp layout UI cho KeyViewer")
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

            val tvCount = FastCounterView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPxInt(20) // Gắn cứng height để khỏi lo layout
                ).apply {
                    bottomMargin = dpToPxInt(4)
                }
                setCount(keyCounters[i])
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
        AppLogger.d(this, "OverlayService", "Đọc cấu hình Theme và Tọa độ từ SharedPreferences")
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
        val isKeyRainEnabled = pref.getBoolean("theme_keyrain_enabled", true)
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
                    tvCount.setTypeface(themeTypeface)
                    tvCount.setUnderline(themeIsUnderline)
                    tvCount.setTextColor(createTextColorStateList(themeTextColor, themeTextColorPressed))

                    // --- THÊM 2 DÒNG NÀY ---
                    // Cài đặt size của số đếm bằng 65% size của phím gốc để nhìn hài hòa
                    val countSizePx = (themeTextSizeSp * 0.65f) * resources.displayMetrics.scaledDensity
                    tvCount.setTextSize(countSizePx)
                    // -----------------------

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

            keyTrailView.visibility = if (isKeyRainEnabled) View.VISIBLE else View.GONE
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
            "SPACE" -> "␣"
            "ENTER" -> "↵"
            "TAB" -> "⇥"
            "BACKSPACE" -> "⌫"
            "DEL", "FORWARD_DEL" -> "⌦"
            "ESCAPE" -> "ESC"
            "CAPS_LOCK" -> "⇪"
            "NUM_LOCK" -> "NUM"
            "SCROLL_LOCK" -> "SCRLK"
            "INSERT" -> "INS"
            "PRINT_SCREEN", "SYSRQ" -> "PRTSC"
            "PAUSE", "BREAK" -> "PAUSE"
            "MENU", "APP_SWITCH" -> "☰","SHIFT_LEFT" -> "L⇧"
            "SHIFT_RIGHT" -> "R⇧"
            "CTRL_LEFT" -> "LCTL"
            "CTRL_RIGHT" -> "RCTL"
            "ALT_LEFT" -> "LALT"
            "ALT_RIGHT" -> "RALT"
            "META_LEFT", "WINDOWS_LEFT" -> "LWIN"
            "META_RIGHT", "WINDOWS_RIGHT" -> "RWIN","UP" -> "↑"
            "DOWN" -> "↓"
            "LEFT" -> "←"
            "RIGHT" -> "→"
            "DPAD_UP" -> "D↑"
            "DPAD_DOWN" -> "D↓"
            "DPAD_LEFT" -> "D←"
            "DPAD_RIGHT" -> "D→"
            "PAGE_UP" -> "PG↑"
            "PAGE_DOWN" -> "PG↓"
            "HOME", "MOVE_HOME" -> "⇱"
            "END", "MOVE_END" -> "⇲","NUMPAD_0" -> "N0"
            "NUMPAD_1" -> "N1"
            "NUMPAD_2" -> "N2"
            "NUMPAD_3" -> "N3"
            "NUMPAD_4" -> "N4"
            "NUMPAD_5" -> "N5"
            "NUMPAD_6" -> "N6"
            "NUMPAD_7" -> "N7"
            "NUMPAD_8" -> "N8"
            "NUMPAD_9" -> "N9"
            "NUMPAD_ENTER" -> "N↵"
            "NUMPAD_ADD" -> "N+"
            "NUMPAD_SUBTRACT" -> "N-"
            "NUMPAD_MULTIPLY" -> "N*"
            "NUMPAD_DIVIDE" -> "N/"
            "NUMPAD_DOT" -> "N."
            "NUMPAD_COMMA" -> "N,"
            "NUMPAD_EQUALS" -> "N=","MINUS" -> "-"
            "EQUALS" -> "="
            "PLUS" -> "+"
            "GRAVE" -> "`"
            "BACKSLASH" -> "\"
            "COMMA" -> ","
            "PERIOD" -> "."
            "SLASH" -> "/"
            "LEFT_BRACKET" -> "["
            "RIGHT_BRACKET" -> "]"
            "SEMICOLON" -> ";"
            "APOSTROPHE" -> "'"
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
            AppLogger.d(this, "Hitbox", "Hitbox chưa được thiết lập, tiến hành sinh tự động")
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

            // Ép thẳng vào mảng cơ sở, không sinh Object
            hitboxLeft[i] = x
            hitboxRight[i] = x + w
            hitboxTop[i] = y
            hitboxBottom[i] = y + h

            hitboxCentersX[i] = x + (w / 2f)
            hitboxCentersY[i] = y + (h / 2f)
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
        } catch (e: Exception) {
            AppLogger.e(this, "OverlayService", "Lỗi startForeground notification", e)
        }
    }

    private fun tryShowOverlay(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            AppLogger.w(this, "Overlay", "Từ chối hiện Overlay do chưa cấp quyền SYSTEM_ALERT_WINDOW")
            mainHandler.post { Toast.makeText(this, getString(R.string.toast_overlay_permission_required), Toast.LENGTH_LONG).show() }
            return false
        }
        if (AppState.isAppVisible) {
            mainHandler.post { Toast.makeText(this, getString(R.string.toast_enter_game), Toast.LENGTH_SHORT).show() }
            return false
        }
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            AppLogger.w(this, "Overlay", "Từ chối hiện Overlay do màn hình đang nằm dọc (Portrait)")
            return false
        }
        if (!isOverlayShowing) {
            mainHandler.post {
                try {
                    if (wrapper.parent == null) {
                        windowManager.addView(wrapper, viewerParams)
                        isOverlayShowing = true
                        AppLogger.i(this, "Overlay", "Hiển thị khung KeyViewer thành công")
                        wrapper.post { updateDisplayMetricsCache() }
                    }
                } catch (e: Exception) {
                    AppLogger.e(this, "Overlay", "Lỗi addView khung KeyViewer", e)
                }
            }
        }
        return true
    }
    private fun hideOverlay() {
        if (isOverlayShowing) {
            AppLogger.i(this, "Overlay", "Lệnh ẩn khung KeyViewer được gọi")

            // Lưu tổng số và từng phím
            val editor = sharedPrefs.edit()
            editor.putInt("TOTAL_CLICKS", totalClicks)
            for (i in 0 until keyMode) editor.putInt("KEY_COUNT_${keyMode}_$i", keyCounters[i])
            editor.apply()

            mainHandler.post {
                try {
                    if (wrapper.parent != null) windowManager.removeView(wrapper)
                    isOverlayShowing = false
                } catch (e: Exception) {
                    AppLogger.e(this, "Overlay", "Lỗi removeView khung KeyViewer", e)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLogger.d(this, "OverlayService", "Cấu hình thiết bị thay đổi (Orientation: ${newConfig.orientation})")
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isKeyViewerOn) { hideOverlay(); isKeyViewerOn = false }
            if (isShowTouchesOn) { isShowTouchesOn = false; SharedTouchData.invalidateCallback?.invoke() }
            updateNotification()
        }
        if (isOverlayShowing) wrapper.post { updateDisplayMetricsCache() }

        try {
            @Suppress("DEPRECATION") val newRotation = windowManager.defaultDisplay.rotation
            if (newRotation != currentHardwareRotation) {
                AppLogger.d(this, "OverlayService", "Màn hình xoay vật lý từ $currentHardwareRotation sang $newRotation")
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
            ACTION_START_FOREGROUND -> {
                AppLogger.i(this, "OverlayService", "Lệnh start Foreground Service")
                updateNotification()
            }
            ACTION_TOGGLE_KEY_VIEWER -> {
                AppLogger.i(this, "OverlayService", "Nút gạt Bật/Tắt Overlay trên thông báo được ấn")
                isManualOverride = true
                if (isKeyViewerOn) { hideOverlay(); isKeyViewerOn = false }
                else if (tryShowOverlay()) isKeyViewerOn = true
                updateNotification()
            }
            ACTION_TOGGLE_TOUCHES -> {
                AppLogger.i(this, "OverlayService", "Nút gạt Bật/Tắt Hiển thị Chạm trên thông báo được ấn")
                isShowTouchesOn = if (isShowTouchesOn) { SharedTouchData.invalidateCallback?.invoke(); false }
                else SharedTouchData.invalidateCallback != null
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(this, "OverlayService", "=== DỪNG VÀ HỦY SERVICE ===")
        val editor = sharedPrefs.edit()
        editor.putInt("TOTAL_CLICKS", totalClicks)
        for (i in 0 until keyMode) editor.putInt("KEY_COUNT_${keyMode}_$i", keyCounters[i])
        editor.apply()
        isRunning = false
        instance = null
        sharedPrefs.edit().putInt("TOTAL_CLICKS", totalClicks).apply()
        mainHandler.removeCallbacks(autoShowRunnable)
        stopReadingTouchEvents()
        try { unregisterReceiver(editReceiver); unregisterReceiver(resetTotalReceiver) } catch (e: Exception) {}
        hideOverlay()
    }

    // 1. CHỐNG RÁC: Khởi tạo biến Runnable CỐ ĐỊNH 1 lần duy nhất thay vì tạo mới liên tục
    private val kpsUiUpdateRunnable = Runnable {
        try {
            // (Nếu được, ở tương lai ta sẽ đổi 2 TextView này thành FastCounterView để hết rác 100%)
            tvKpsValue?.text = lastRenderedKps.toString()
            tvTotalValue?.text = decimalFormatter.format(lastRenderedTotal)
        } catch (e: Exception) {}
    }

    private fun updateKpsTotalUI(kps: Int, total: Int, force: Boolean = false) {
        if (!force && kps == lastRenderedKps && total == lastRenderedTotal) return
        lastRenderedKps = kps; lastRenderedTotal = total

        // 2. Tái sử dụng Runnable cố định, tuyệt đối không dùng { ... } Lambda ở đây nữa
        mainHandler.removeCallbacks(kpsUiUpdateRunnable)
        mainHandler.post(kpsUiUpdateRunnable)
    }
}