package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup

class KeyTrailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- BƯỚC 1: CẤU TRÚC OBJECT POOL (CHỐNG RÁC BỘ NHỚ) ---
    class Trail {
        var startY: Float = 0f
        var bottomY: Float = 0f
        var leftX: Float = 0f
        var rightX: Float = 0f
        var isReleased: Boolean = false
        var alpha: Float = 255f
        var isActive: Boolean = false

        fun reset(x: Float, width: Float, startBottomY: Float) {
            this.leftX = x
            this.rightX = x + width
            this.startY = startBottomY
            this.bottomY = startBottomY
            this.isReleased = false
            this.alpha = 255f
            this.isActive = true
        }
    }

    private val MAX_TRAILS = 150
    private val trailPool = Array(MAX_TRAILS) { Trail() }

    // --- BƯỚC 2: HỆ THỐNG PAINT ---
    private val corePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        maskFilter = android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.OUTER)
    }

    private var rainColor = Color.WHITE
    private var rainShadowColor = Color.CYAN
    private var isShadowEnabled = true

    private val baseSpeedPerSecond = 1500f
    private val baseMaxHeight = 1000f
    private val fadeSpeedPerSecond = 1500f

    var trailSpeed: Float = 1.0f
    var trailLimit: Float = 1.0f

    private var isRunning = false
    private var lastFrameTimeNanos: Long = 0L

    // --- BƯỚC 3: GAME LOOP ---
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isRunning) {
                if (lastFrameTimeNanos != 0L) {
                    val deltaTime = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    updateLogic(deltaTime)
                    invalidate()
                }
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)

        // Phá tường giới hạn của các View Cha để cho phép bóng đổ tràn ra ngoài
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
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun updateLogic(deltaTime: Float) {
        val actualSpeed = baseSpeedPerSecond * trailSpeed * deltaTime
        val limitY = height.toFloat() - (baseMaxHeight * trailLimit)
        val alphaDecrease = fadeSpeedPerSecond * deltaTime

        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive) continue

            if (!trail.isReleased) {
                trail.startY -= actualSpeed
            } else {
                trail.startY -= actualSpeed
                trail.bottomY -= actualSpeed
            }

            if (trail.startY < limitY) {
                trail.alpha -= alphaDecrease
            }

            if (trail.alpha <= 0f || trail.bottomY < -100f) {
                trail.isActive = false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // --- PHÉP THUẬT LƯỚI LỌC (CLIP RECT) ---
        // Lưu trạng thái bạt vẽ
        canvas.save()

        // Mở cửa Trái (-200f) và Phải (width + 200f) để bóng đổ bung lụa.
        // Khóa cửa Trên (0f) và Dưới (height) để vệt sáng bị cắt đứt đúng giới hạn Limit bay!
        canvas.clipRect(-200f, 0f, width.toFloat() + 200f, height.toFloat())

        super.onDraw(canvas)

        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive) continue

            val currentAlpha = trail.alpha.toInt().coerceIn(0, 255)

            // 1. Vẽ lớp Glow
            if (isShadowEnabled) {
                glowPaint.color = rainShadowColor
                glowPaint.alpha = (currentAlpha * 0.7f).toInt()
                canvas.drawRect(trail.leftX, trail.startY, trail.rightX, trail.bottomY, glowPaint)
            }

            // 2. Vẽ lớp Lõi (Core)
            corePaint.color = rainColor
            corePaint.alpha = currentAlpha
            canvas.drawRect(trail.leftX, trail.startY, trail.rightX, trail.bottomY, corePaint)
        }

        // Khôi phục bạt vẽ để không ảnh hưởng đến các UI khác
        canvas.restore()
    }

    fun setThemeColors(color: Int, shadowColor: Int) {
        this.rainColor = color
        this.rainShadowColor = shadowColor
        this.isShadowEnabled = Color.alpha(shadowColor) > 0
        invalidate()
    }

    fun addTrail(x: Float, width: Float): Trail? {
        val bottomY = height.toFloat()
        // Kỹ thuật Pool
        for (i in 0 until MAX_TRAILS) {
            if (!trailPool[i].isActive) {
                trailPool[i].reset(x, width, bottomY)
                return trailPool[i]
            }
        }
        return null
    }

    fun releaseAll() {
        for (i in 0 until MAX_TRAILS) {
            if (trailPool[i].isActive) {
                trailPool[i].isReleased = true
            }
        }
    }

    fun setParameters(speed: Float, limit: Float) {
        this.trailSpeed = speed
        this.trailLimit = limit
    }
}