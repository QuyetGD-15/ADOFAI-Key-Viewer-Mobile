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

    override fun hasOverlappingRendering(): Boolean = false

    private val MAX_TRAILS = 150
    private val trailPool = Array(MAX_TRAILS) { Trail() }

    // ================= TỐI ƯU 2 CÂY CỌ VẼ TÁCH BIỆT =================
    private val trailPaintRow1 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val trailPaintRow2 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    // Màu sắc Hàng 1
    private var rainColor1 = Color.WHITE
    private var rainShadowColor1 = Color.CYAN
    private var isShadowEnabled1 = true

    // Màu sắc Hàng 2 (Tạm gán mặc định màu Tím để bạn test Z-Index)
    private var rainColor2 = Color.parseColor("#A78BFA")
    private var rainShadowColor2 = Color.parseColor("#7C3AED")
    private var isShadowEnabled2 = true
    // ================================================================

    private val baseSpeedPerMs = 1.5f

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
                isRendering = false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

        val currentTimeMs = SystemClock.uptimeMillis()
        val actualSpeedPerMs = baseSpeedPerMs * trailSpeed
        val screenHeight = height.toFloat()

        // PASS 1: Lọc và vẽ toàn bộ phím Hàng 1 (lane 0 -> 7) trước
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive || trail.laneIndex >= 8) continue
            drawSingleTrail(canvas, trail, trailPaintRow1, currentTimeMs, actualSpeedPerMs, screenHeight)
        }

        // PASS 2: Lọc và vẽ các phím Hàng 2 (lane 8, 9) sau cùng để luôn nằm đè lên trên
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive || trail.laneIndex < 8) continue
            drawSingleTrail(canvas, trail, trailPaintRow2, currentTimeMs, actualSpeedPerMs, screenHeight)
        }

        canvas.restore()
    }

    // Tách riêng thuật toán vẽ để tái sử dụng cho 2 cọ
    private fun drawSingleTrail(canvas: Canvas, trail: Trail, paint: Paint, currentTimeMs: Long, actualSpeedPerMs: Float, screenHeight: Float) {
        val timeSincePressed = currentTimeMs - trail.timePressed
        val bottomY = if (trail.timeReleased == 0L) {
            screenHeight
        } else {
            val timeSinceReleased = currentTimeMs - trail.timeReleased
            screenHeight - (timeSinceReleased * actualSpeedPerMs)
        }

        val topY = screenHeight - (timeSincePressed * actualSpeedPerMs)

        // TỐI ƯU TUYỆT ĐỐI: Dọn dẹp RAM ngay khi toàn bộ cục Keyrain (bottomY) vượt qua đỉnh View
        if (bottomY <= 0f) {
            if (trail.isActive) {
                trail.isActive = false
                activeTrailCount--
            }
            return
        }

        // Vẽ 100% rõ nét, phần thừa bay ra ngoài sẽ do Android Canvas tự động cắt bỏ
        paint.alpha = 255
        canvas.drawRect(trail.x, topY, trail.x + trail.width, bottomY, paint)
    }

    // Hàm nhận màu cho Hàng 1
    fun setThemeColors(color: Int, shadowColor: Int) {
        this.rainColor1 = color
        this.rainShadowColor1 = shadowColor
        this.isShadowEnabled1 = Color.alpha(shadowColor) > 0

        val rainR = Color.red(color)
        val rainG = Color.green(color)
        val rainB = Color.blue(color)
        trailPaintRow1.color = Color.rgb(rainR, rainG, rainB)

        if (isShadowEnabled1) {
            val shadowR = Color.red(shadowColor)
            val shadowG = Color.green(shadowColor)
            val shadowB = Color.blue(shadowColor)
            trailPaintRow1.setShadowLayer(20f, 0f, 0f, Color.argb((255 * 0.7f).toInt(), shadowR, shadowG, shadowB))
        } else {
            trailPaintRow1.clearShadowLayer()
        }
        invalidate()
    }

    // Hàm mới: Nhận màu cho Hàng 2 (Sẽ kết nối với SharedPreferences ở bước sau)
    fun setRow2ThemeColors(color: Int, shadowColor: Int) {
        this.rainColor2 = color
        this.rainShadowColor2 = shadowColor
        this.isShadowEnabled2 = Color.alpha(shadowColor) > 0

        val rainR = Color.red(color)
        val rainG = Color.green(color)
        val rainB = Color.blue(color)
        trailPaintRow2.color = Color.rgb(rainR, rainG, rainB)

        if (isShadowEnabled2) {
            val shadowR = Color.red(shadowColor)
            val shadowG = Color.green(shadowColor)
            val shadowB = Color.blue(shadowColor)
            trailPaintRow2.setShadowLayer(20f, 0f, 0f, Color.argb((255 * 0.7f).toInt(), shadowR, shadowG, shadowB))
        } else {
            trailPaintRow2.clearShadowLayer()
        }
        invalidate()
    }

    fun addTrail(laneIndex: Int, x: Float, width: Float, pressTime: Long): Trail? {
        for (i in 0 until MAX_TRAILS) {
            if (!trailPool[i].isActive) {
                trailPool[i].reset(laneIndex, x, width, pressTime)
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
        // Không cần tính toán bất cứ thứ gì ở đây nữa, trả lại tài nguyên cho CPU!
    }

}