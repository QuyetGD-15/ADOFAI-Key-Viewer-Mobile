package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

class HitboxView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var hitboxNumber: String = "1"
        set(value) {
            field = value
            invalidate()
        }

    private val borderPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#330000FF")
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val handleRadius = 20f

    // Các biến Neo (Anchor) để chống sai số tích lũy
    private var initialX = 0f
    private var initialY = 0f
    private var initialWidth = 0f
    private var initialHeight = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activeHandle = -1 // -1: Drag, 0: TL, 1: TR, 2: BL, 3: BR

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(handleRadius, handleRadius, w - handleRadius, h - handleRadius, bgPaint)
        canvas.drawRect(handleRadius, handleRadius, w - handleRadius, h - handleRadius, borderPaint)

        canvas.drawCircle(handleRadius, handleRadius, handleRadius, handlePaint)
        canvas.drawCircle(w - handleRadius, handleRadius, handleRadius, handlePaint)
        canvas.drawCircle(handleRadius, h - handleRadius, handleRadius, handlePaint)
        canvas.drawCircle(w - handleRadius, h - handleRadius, handleRadius, handlePaint)

        val textY = (h / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(hitboxNumber, w / 2, textY, textPaint)
    }

    /**
     * Xử lý sự kiện chạm để di chuyển hoặc thay đổi kích thước hitbox.
     * Sử dụng cơ chế Delta tuyệt đối để loại bỏ giật lag và sai số.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Chốt (Neo) tất cả các thông số tại thời điểm chạm
                initialX = x
                initialY = y
                initialWidth = width.toFloat()
                initialHeight = height.toFloat()
                initialTouchX = rawX
                initialTouchY = rawY

                activeHandle = getTouchedHandle(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                // Tính khoảng cách Delta tuyệt đối so với điểm chạm đầu tiên
                val deltaX = rawX - initialTouchX
                val deltaY = rawY - initialTouchY

                val params = layoutParams as ViewGroup.MarginLayoutParams
                val minSize = 100f // Giới hạn kích thước nhỏ nhất để không bị lật ngược View

                if (activeHandle == -1) {
                    // Di chuyển toàn bộ: Dùng tọa độ gốc + Delta tuyệt đối
                    x = initialX + deltaX
                    y = initialY + deltaY
                } else {
                    when (activeHandle) {
                        0 -> { // Top-Left (Góc trên trái)
                            var newW = initialWidth - deltaX
                            var newH = initialHeight - deltaY
                            var moveX = deltaX
                            var moveY = deltaY

                            // Khóa giới hạn
                            if (newW < minSize) { newW = minSize; moveX = initialWidth - minSize }
                            if (newH < minSize) { newH = minSize; moveY = initialHeight - minSize }

                            x = initialX + moveX
                            y = initialY + moveY
                            params.width = newW.toInt()
                            params.height = newH.toInt()
                        }
                        1 -> { // Top-Right (Góc trên phải)
                            var newW = initialWidth + deltaX
                            var newH = initialHeight - deltaY
                            var moveY = deltaY

                            if (newW < minSize) { newW = minSize }
                            if (newH < minSize) { newH = minSize; moveY = initialHeight - minSize }

                            y = initialY + moveY
                            params.width = newW.toInt()
                            params.height = newH.toInt()
                        }
                        2 -> { // Bottom-Left (Góc dưới trái)
                            var newW = initialWidth - deltaX
                            var newH = initialHeight + deltaY
                            var moveX = deltaX

                            if (newW < minSize) { newW = minSize; moveX = initialWidth - minSize }
                            if (newH < minSize) { newH = minSize }

                            x = initialX + moveX
                            params.width = newW.toInt()
                            params.height = newH.toInt()
                        }
                        3 -> { // Bottom-Right (Góc dưới phải)
                            var newW = initialWidth + deltaX
                            var newH = initialHeight + deltaY

                            if (newW < minSize) newW = minSize
                            if (newH < minSize) newH = minSize

                            params.width = newW.toInt()
                            params.height = newH.toInt()
                        }
                    }
                    layoutParams = params
                }
            }
        }
        return true
    }

    private fun getTouchedHandle(x: Float, y: Float): Int {
        val touchArea = handleRadius * 3
        if (x < touchArea && y < touchArea) return 0
        if (x > width - touchArea && y < touchArea) return 1
        if (x < touchArea && y > height - touchArea) return 2
        if (x > width - touchArea && y > height - touchArea) return 3
        return -1
    }
}