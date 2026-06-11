package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class KeyTrailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        // Cho phép vẽ Shadow bằng phần mềm để đảm bảo tương thích
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    data class Trail(
        var startY: Float,
        var bottomY: Float,
        val leftX: Float,
        val rightX: Float,
        var isReleased: Boolean,
        var alpha: Int = 255
    )

    private val trails = mutableListOf<Trail>()
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        // Thêm Đổ bóng: Bán kính tỏa ra 12px, không lệch X/Y, màu Đen đậm (70% alpha)
        setShadowLayer(12f, 0f, 0f, Color.parseColor("#B3000000"))
    }
    
    private val baseSpeed = 25f
    private val baseMaxHeight = 1000f

    var trailSpeed: Float = 1.0f
    var trailLimit: Float = 1.0f

    private var isRunning = false

    /**
     * Vòng lặp Choreographer: Đảm bảo hoạt ảnh (animation) của RainKey 
     * mượt mà nhất có thể theo tần số quét của màn hình (60/90/120Hz).
     */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isRunning) {
                updateLogic()
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun updateLogic() {
        synchronized(trails) {
            val iterator = trails.iterator()
            val actualSpeed = baseSpeed * trailSpeed
            val limitY = height.toFloat() - (baseMaxHeight * trailLimit)

            while (iterator.hasNext()) {
                val trail = iterator.next()
                
                if (!trail.isReleased) {
                    trail.startY -= actualSpeed
                } else {
                    trail.startY -= actualSpeed
                    trail.bottomY -= actualSpeed
                }

                // Xử lý mờ dần khi chạm giới hạn bay
                if (trail.startY < limitY) {
                    trail.alpha -= 25 // Giảm alpha nhanh
                    if (trail.alpha < 0) trail.alpha = 0
                }

                // Xóa khi đã hoàn toàn biến mất hoặc bay quá xa
                if (trail.alpha <= 0 || trail.bottomY < -100) {
                    iterator.remove()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(trails) {
            trails.forEach { trail ->
                paint.alpha = trail.alpha
                canvas.drawRect(trail.leftX, trail.startY, trail.rightX, trail.bottomY, paint)
            }
        }
    }

    fun addTrail(x: Float, width: Float): Trail {
        val bottomY = height.toFloat()
        val newTrail = Trail(
            startY = bottomY,
            bottomY = bottomY,
            leftX = x,
            rightX = x + width,
            isReleased = false
        )
        synchronized(trails) {
            trails.add(newTrail)
        }
        return newTrail
    }

    fun releaseAll() {
        synchronized(trails) {
            trails.forEach { it.isReleased = true }
        }
    }
    
    fun setParameters(speed: Float, limit: Float) {
        this.trailSpeed = speed
        this.trailLimit = limit
    }
}
