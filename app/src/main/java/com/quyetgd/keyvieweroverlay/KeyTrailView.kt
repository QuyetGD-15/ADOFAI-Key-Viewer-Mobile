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

    // --- BƯỚC 1: CẤU TRÚC OBJECT POOL (CHỐNG RÁC BỘ NHỚ) ---
    class Trail {
        var startY: Float = 0f
        var bottomY: Float = 0f
        var leftX: Float = 0f
        var rightX: Float = 0f
        var isReleased: Boolean = false
        var alpha: Float = 255f // Đổi sang Float để trừ mịn màng theo DeltaTime
        var isActive: Boolean = false

        // Hàm "đánh thức" vệt sáng tái sử dụng
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

    // Giới hạn an toàn, 150 vệt sáng cùng lúc là dư sức cho 30-40 KPS mà không sụt FPS
    private val MAX_TRAILS = 150
    private val trailPool = Array(MAX_TRAILS) { Trail() }

    // --- BƯỚC 2: HỆ THỐNG PAINT GIA TỐC PHẦN CỨNG ---
    private val corePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        // Sử dụng BlurMaskFilter cho GPU xử lý, thay vì setShadowLayer ép CPU chạy
        maskFilter = android.graphics.BlurMaskFilter(16f, android.graphics.BlurMaskFilter.Blur.OUTER)
    }

    private var rainColor = Color.WHITE
    private var rainShadowColor = Color.CYAN
    private var isShadowEnabled = true

    // Vận tốc nay tính theo GIÂY thay vì FRAME (25px/frame ở 60fps = ~1500px/giây)
    private val baseSpeedPerSecond = 1500f
    private val baseMaxHeight = 1000f
    private val fadeSpeedPerSecond = 1500f // Tốc độ mờ alpha theo giây

    var trailSpeed: Float = 1.0f
    var trailLimit: Float = 1.0f

    private var isRunning = false
    private var lastFrameTimeNanos: Long = 0L

    // --- BƯỚC 3: GAME LOOP CHUẨN VẬT LÝ ---
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isRunning) {
                if (lastFrameTimeNanos != 0L) {
                    // Tính DeltaTime (Thời gian trôi qua giữa 2 khung hình, tính bằng giây)
                    val deltaTime = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    updateLogic(deltaTime)
                    invalidate()
                }
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        // LOẠI BỎ HOÀN TOÀN LAYER_TYPE_SOFTWARE: Bật Hardware Acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRunning = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
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

        // Vòng lặp For nguyên thủy (Không sinh Iterator = Không kích hoạt Garbage Collection)
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive) continue

            if (!trail.isReleased) {
                trail.startY -= actualSpeed
            } else {
                trail.startY -= actualSpeed
                trail.bottomY -= actualSpeed
            }

            // Xử lý mờ dần khi chạm giới hạn bay
            if (trail.startY < limitY) {
                trail.alpha -= alphaDecrease
            }

            // Trả vệt sáng về giấc ngủ (Pool) khi hết Alpha hoặc bay khỏi màn hình
            if (trail.alpha <= 0f || trail.bottomY < -100f) {
                trail.isActive = false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Tối ưu Draw Calls
        for (i in 0 until MAX_TRAILS) {
            val trail = trailPool[i]
            if (!trail.isActive) continue

            val currentAlpha = trail.alpha.toInt().coerceIn(0, 255)

            // 1. Vẽ lớp Glow (Hiệu ứng phát sáng bằng GPU)
            if (isShadowEnabled) {
                glowPaint.color = rainShadowColor
                glowPaint.alpha = (currentAlpha * 0.7f).toInt() // Bóng đổ hơi mờ hơn lõi để tạo chiều sâu
                canvas.drawRect(trail.leftX, trail.startY, trail.rightX, trail.bottomY, glowPaint)
            }

            // 2. Vẽ lớp lõi (Core)
            corePaint.color = rainColor
            corePaint.alpha = currentAlpha
            canvas.drawRect(trail.leftX, trail.startY, trail.rightX, trail.bottomY, corePaint)
        }
    }

    fun setThemeColors(color: Int, shadowColor: Int) {
        this.rainColor = color
        this.rainShadowColor = shadowColor
        this.isShadowEnabled = Color.alpha(shadowColor) > 0
        invalidate()
    }

    // Không cần hàm synchronized do luồng vẽ và logic đều chạy trên MainThread
    fun addTrail(x: Float, width: Float): Trail? {
        val bottomY = height.toFloat()
        // Kỹ thuật Pool: Quét tìm 1 object rảnh rỗi và tái sử dụng
        for (i in 0 until MAX_TRAILS) {
            if (!trailPool[i].isActive) {
                trailPool[i].reset(x, width, bottomY)
                return trailPool[i] // Trả về con trỏ để Service gọi lệnh release() sau này
            }
        }
        // Fallback: Nếu spam quá nhanh hết 150 vệt, bỏ qua lệnh vẽ để giữ vững khung hình
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