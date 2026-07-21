package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup

class KeyTrailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // KẾ THỪA THUẬT TOÁN ASYNC: LƯU THỜI GIAN THỰC TẾ
    class Trail {
        var laneIndex: Int = -1
        var x: Float = 0f
        var width: Float = 0f
        var timePressed: Long = 0L
        var timeReleased: Long = 0L
        var isActive: Boolean = false

        fun reset(lane: Int, startX: Float, w: Float, pressTime: Long) {
            this.laneIndex = lane
            this.x = startX
            this.width = w
            this.timePressed = pressTime
            this.timeReleased = 0L
            this.isActive = true
        }
    }

    // Bỏ qua bộ đệm nháp GPU, giải phóng sức mạnh kết xuất bóng đổ
    override fun hasOverlappingRendering(): Boolean = false

    private val MAX_TRAILS = 150
    private val trailPool = Array(MAX_TRAILS) { Trail() }

    // TỐI ƯU RAM CỰC HẠN: Chỉ dùng đúng 1 cây cọ cho toàn bộ ứng dụng
    private val trailPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var rainColor = Color.WHITE
    private var rainShadowColor = Color.CYAN
    private var isShadowEnabled = true

    // Đổi sang tốc độ trên mili-giây cho phù hợp toán học mới
    private val baseSpeedPerMs = 1.5f
    private var maxTravelDistance = 1000f

    var trailSpeed: Float = 1.0f
    var trailLimit: Float = 1.0f

    @Volatile private var activeTrailCount = 0
    private var isRendering = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (activeTrailCount > 0) {
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                // HẾT VỆT SÁNG -> GPU ĐI NGỦ! (0% CPU/GPU Usage)
                isRendering = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Không gọi postFrameCallback ở đây nữa, chờ có vệt sáng mới gọi
        post {
            var currentParent = parent as? ViewGroup
            while (currentParent != null) {
                currentParent.clipChildren = false
                currentParent.clipToPadding = false
                currentParent = currentParent.parent as? ViewGroup
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRendering = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(-200f, 0f, width.toFloat() + 200f, height.toFloat())

        // ĐOẠN PHÉP THUẬT: TÍNH TỌA ĐỘ THEO THỜI GIAN CHUẨN XÁC DÙ GAME CÓ LAG
        val currentTimeMs = SystemClock.uptimeMillis()
        val actualSpeedPerMs = baseSpeedPerMs * trailSpeed
        val screenHeight = height.toFloat()

        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive) continue

            val timeSincePressed = currentTimeMs - trail.timePressed

            // Đáy vệt sáng
            val bottomY = if (trail.timeReleased == 0L) {
                screenHeight
            } else {
                val timeSinceReleased = currentTimeMs - trail.timeReleased
                screenHeight - (timeSinceReleased * actualSpeedPerMs)
            }

            // Đỉnh vệt sáng
            val topY = screenHeight - (timeSincePressed * actualSpeedPerMs)
            val distanceTraveled = screenHeight - topY
            var alpha = 255

            // Mờ dần khi hết giới hạn
            if (distanceTraveled > maxTravelDistance) {
                val overshoot = distanceTraveled - maxTravelDistance

                // 255 / 200 = 1.275f. Dùng hằng số nhân trực tiếp, tiết kiệm 9000 phép chia mỗi giây!
                alpha = 255 - (overshoot * 1.275f).toInt()
            }

            if (alpha <= 0 || bottomY < -100f) {
                if (trail.isActive) {
                    trail.isActive = false
                    activeTrailCount-- // Trừ biến đếm khi vệt sáng chết
                }
                continue
            }

            alpha = alpha.coerceIn(0, 255)
            // Thay đổi độ mờ trực tiếp vào cây cọ duy nhất (Tốn 0 RAM, 0 Object Allocation)
            trailPaint.alpha = alpha

            canvas.drawRect(trail.x, topY, trail.x + trail.width, bottomY, trailPaint)
        }
        canvas.restore()
    }

    fun setThemeColors(color: Int, shadowColor: Int) {
        this.rainColor = color
        this.rainShadowColor = shadowColor
        this.isShadowEnabled = Color.alpha(shadowColor) > 0

        val rainR = Color.red(color)
        val rainG = Color.green(color)
        val rainB = Color.blue(color)

        // Cài đặt màu gốc (Không cần Alpha)
        trailPaint.color = Color.rgb(rainR, rainG, rainB)

        if (isShadowEnabled) {
            val shadowR = Color.red(shadowColor)
            val shadowG = Color.green(shadowColor)
            val shadowB = Color.blue(shadowColor)

            // Cài đặt bóng đổ 1 lần duy nhất
            trailPaint.setShadowLayer(20f, 0f, 0f, Color.argb((255 * 0.7f).toInt(), shadowR, shadowG, shadowB))
        } else {
            trailPaint.clearShadowLayer()
        }

        // Buộc hệ thống vẽ lại với màu mới
        invalidate()
    }

    fun addTrail(laneIndex: Int, x: Float, width: Float, pressTime: Long): Trail? {
        for (i in 0 until MAX_TRAILS) {
            if (!trailPool[i].isActive) {
                trailPool[i].reset(laneIndex, x, width, pressTime)

                // THỨC DẬY NGAY LẬP TỨC KHI CÓ TOUCH!
                activeTrailCount++
                if (!isRendering) {
                    isRendering = true
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }

                return trailPool[i]
            }
        }
        return null
    }

    fun releaseLane(laneIndex: Int, releaseTime: Long) {
        for (i in 0 until MAX_TRAILS) {
            if (trailPool[i].isActive && trailPool[i].laneIndex == laneIndex && trailPool[i].timeReleased == 0L) {
                // Ghi nhận mốc thời gian thả tay cực chuẩn
                trailPool[i].timeReleased = releaseTime
            }
        }
    }

    fun releaseAll() {
        val releaseTime = SystemClock.uptimeMillis()
        for (i in 0 until MAX_TRAILS) {
            if (trailPool[i].isActive && trailPool[i].timeReleased == 0L) {
                trailPool[i].timeReleased = releaseTime
            }
        }
    }

    fun setParameters(speed: Float, limit: Float) {
        this.trailSpeed = speed
        this.trailLimit = limit
        if (height > 0) {
            maxTravelDistance = height.toFloat() * trailLimit
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxTravelDistance = h.toFloat() * trailLimit
    }
}