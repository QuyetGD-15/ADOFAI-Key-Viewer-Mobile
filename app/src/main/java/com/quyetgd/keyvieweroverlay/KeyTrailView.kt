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
    private var advancedColors: Array<ThemeColorSet>? = null
    private var advancedTrailColors: IntArray? = null
    private var advancedShadowColors: IntArray? = null

    // ================= TỐI ƯU CỌ VẼ (HỖ TRỢ CẢ 3 CHẾ ĐỘ ĐỔ BÓNG) =================
    private val trailPaintRow1 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val shadowPaintRow1 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val trailPaintRow2 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val shadowPaintRow2 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    // Trạng thái bật tắt từ Cài đặt
    private var isGlobalShadowEnabled = true
    private var isPerformanceShadow = false
    private val GLOW_SPREAD = 12f // Độ tràn viền 4 hướng

    // Màu sắc Hàng 1
    private var rainColor1 = Color.WHITE
    private var rainShadowColor1 = Color.CYAN
    private var isShadowEnabled1 = true

    // Màu sắc Hàng 2
    private var rainColor2 = Color.parseColor("#A78BFA")
    private var rainShadowColor2 = Color.parseColor("#7C3AED")
    private var isShadowEnabled2 = true
    // ==============================================================================

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
        stopRendering()
        super.onDetachedFromWindow()
    }

    /** Stops frame callbacks and drops preview-only lane colors when a view is no longer used. */
    fun releaseResources() {
        stopRendering()
        advancedColors = null
        advancedTrailColors = null
        advancedShadowColors = null
    }

    private fun stopRendering() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        isRendering = false
        activeTrailCount = 0
        for (trail in trailPool) {
            trail.isActive = false
            trail.timeReleased = 0L
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(-200f, 0f, width.toFloat() + 200f, height.toFloat())

        val currentTimeMs = SystemClock.uptimeMillis()
        val actualSpeedPerMs = baseSpeedPerMs * trailSpeed
        val screenHeight = height.toFloat()
        val perLaneTrail = advancedTrailColors
        val perLaneShadow = advancedShadowColors

        // PASS 1: Vẽ Hàng 1
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive || trail.laneIndex >= 8) continue
            val shadowPaint = if (isGlobalShadowEnabled && isPerformanceShadow && isShadowEnabled1) shadowPaintRow1 else null
            val laneTrail = perLaneTrail?.getOrNull(trail.laneIndex)
            if (laneTrail != null) trailPaintRow1.color = laneTrail
            val laneShadow = perLaneShadow?.getOrNull(trail.laneIndex) ?: rainShadowColor1
            if (isGlobalShadowEnabled && isPerformanceShadow && isShadowEnabled1) {
                shadowPaintRow1.color = Color.argb(100, Color.red(laneShadow), Color.green(laneShadow), Color.blue(laneShadow))
            }
            configureLanePaint(trailPaintRow1, laneShadow, isShadowEnabled1)
            drawSingleTrail(canvas, trail, trailPaintRow1, shadowPaint, currentTimeMs, actualSpeedPerMs, screenHeight)
        }

        // PASS 2: Vẽ Hàng 2 (Đè lên trên)
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive || trail.laneIndex < 8) continue
            val shadowPaint = if (isGlobalShadowEnabled && isPerformanceShadow && isShadowEnabled2) shadowPaintRow2 else null
            val laneTrail = perLaneTrail?.getOrNull(trail.laneIndex)
            if (laneTrail != null) trailPaintRow2.color = laneTrail
            val laneShadow = perLaneShadow?.getOrNull(trail.laneIndex) ?: rainShadowColor2
            if (isGlobalShadowEnabled && isPerformanceShadow && isShadowEnabled2) {
                shadowPaintRow2.color = Color.argb(100, Color.red(laneShadow), Color.green(laneShadow), Color.blue(laneShadow))
            }
            configureLanePaint(trailPaintRow2, laneShadow, isShadowEnabled2)
            drawSingleTrail(canvas, trail, trailPaintRow2, shadowPaint, currentTimeMs, actualSpeedPerMs, screenHeight)
        }

        canvas.restore()
    }

    private fun configureLanePaint(paint: Paint, shadowColor: Int, rowShadowEnabled: Boolean) {
        if (isGlobalShadowEnabled && !isPerformanceShadow && rowShadowEnabled && Color.alpha(shadowColor) > 0) {
            paint.setShadowLayer(20f, 0f, 0f, Color.argb(
                (Color.alpha(shadowColor) * .7f).toInt(),
                Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor)
            ))
        } else {
            paint.clearShadowLayer()
        }
    }

    private fun drawSingleTrail(
        canvas: Canvas, trail: Trail, paint: Paint, shadowPaint: Paint?,
        currentTimeMs: Long, actualSpeedPerMs: Float, screenHeight: Float
    ) {
        val timeSincePressed = currentTimeMs - trail.timePressed
        val bottomY = if (trail.timeReleased == 0L) {
            screenHeight
        } else {
            val timeSinceReleased = currentTimeMs - trail.timeReleased
            screenHeight - (timeSinceReleased * actualSpeedPerMs)
        }

        val topY = screenHeight - (timeSincePressed * actualSpeedPerMs)

        if (bottomY <= 0f) {
            if (trail.isActive) {
                trail.isActive = false
                activeTrailCount--
            }
            return
        }

        // Đổ bóng giả tràn 4 hướng (chỉ vẽ khi chế độ nhẹ máy được bật)
        if (shadowPaint != null) {
            canvas.drawRect(
                trail.x - GLOW_SPREAD,
                topY - GLOW_SPREAD,
                trail.x + trail.width + GLOW_SPREAD,
                bottomY + GLOW_SPREAD,
                shadowPaint
            )
        }

        // Preserve the alpha supplied by the theme's rain color.
        canvas.drawRect(trail.x, topY, trail.x + trail.width, bottomY, paint)
    }

    // --- CÁC HÀM CẬP NHẬT MÀU VÀ ĐỔ BÓNG TỪ CONFIG ---

    fun setShadowConfig(enableShadow: Boolean, performanceShadow: Boolean) {
        this.isGlobalShadowEnabled = enableShadow
        this.isPerformanceShadow = performanceShadow
        updatePaintsRow1()
        updatePaintsRow2()
        invalidate()
    }

    fun setThemeColors(color: Int, shadowColor: Int) {
        this.rainColor1 = color
        this.rainShadowColor1 = shadowColor
        this.isShadowEnabled1 = Color.alpha(shadowColor) > 0
        updatePaintsRow1()
        invalidate()
    }

    fun setAdvancedColors(colors: Array<ThemeColorSet>?) {
        advancedColors = colors
        advancedTrailColors = colors?.map { Color.parseColor(it.trail) }?.toIntArray()
        advancedShadowColors = colors?.map { Color.parseColor(it.shadow) }?.toIntArray()
        invalidate()
    }

    fun setRow2ThemeColors(color: Int, shadowColor: Int) {
        this.rainColor2 = color
        this.rainShadowColor2 = shadowColor
        this.isShadowEnabled2 = Color.alpha(shadowColor) > 0
        updatePaintsRow2()
        invalidate()
    }

    private fun updatePaintsRow1() {
        trailPaintRow1.color = Color.rgb(Color.red(rainColor1), Color.green(rainColor1), Color.blue(rainColor1))

        if (isGlobalShadowEnabled && isShadowEnabled1) {
            if (isPerformanceShadow) {
                // Bóng nhẹ (Fake Glow): Tắt ShadowLayer nặng nề, lấy shadowPaint ra dùng
                trailPaintRow1.clearShadowLayer()
                shadowPaintRow1.color = Color.argb(100, Color.red(rainShadowColor1), Color.green(rainShadowColor1), Color.blue(rainShadowColor1))
            } else {
                // Bóng Max Setting (ShadowLayer cũ): Bật ShadowLayer
                trailPaintRow1.setShadowLayer(20f, 0f, 0f, Color.argb((255 * 0.7f).toInt(), Color.red(rainShadowColor1), Color.green(rainShadowColor1), Color.blue(rainShadowColor1)))
            }
        } else {
            // Tắt hoàn toàn bóng
            trailPaintRow1.clearShadowLayer()
        }
    }

    private fun updatePaintsRow2() {
        trailPaintRow2.color = Color.rgb(Color.red(rainColor2), Color.green(rainColor2), Color.blue(rainColor2))

        if (isGlobalShadowEnabled && isShadowEnabled2) {
            if (isPerformanceShadow) {
                trailPaintRow2.clearShadowLayer()
                shadowPaintRow2.color = Color.argb(100, Color.red(rainShadowColor2), Color.green(rainShadowColor2), Color.blue(rainShadowColor2))
            } else {
                trailPaintRow2.setShadowLayer(20f, 0f, 0f, Color.argb((255 * 0.7f).toInt(), Color.red(rainShadowColor2), Color.green(rainShadowColor2), Color.blue(rainShadowColor2)))
            }
        } else {
            trailPaintRow2.clearShadowLayer()
        }
    }

    // --- CÁC HÀM CƠ BẢN CÒN LẠI ---

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

    fun releaseTrail(trail: Trail?, releaseTime: Long) {
        if (trail != null && trail.isActive && trail.timeReleased == 0L) {
            trail.timeReleased = releaseTime
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
    }
}