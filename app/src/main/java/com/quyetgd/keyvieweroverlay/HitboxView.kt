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
    private var lastX = 0f
    private var lastY = 0f
    private var initialX = 0f
    private var initialY = 0f
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
     * activeHandle xác định hành động: -1 là di chuyển, 0-3 là kéo các góc để resize.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = rawX
                lastY = rawY
                initialX = x
                initialY = y
                initialTouchX = rawX
                initialTouchY = rawY
                activeHandle = getTouchedHandle(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - lastX
                val dy = rawY - lastY

                val params = layoutParams as ViewGroup.MarginLayoutParams
                
                if (activeHandle == -1) {
                    x = initialX + (rawX - initialTouchX)
                    y = initialY + (rawY - initialTouchY)
                } else {
                    when (activeHandle) {
                        0 -> { // TL
                            val newW = width - dx
                            val newH = height - dy
                            if (newW > 100 && newH > 100) {
                                x += dx
                                y += dy
                                params.width = newW.toInt()
                                params.height = newH.toInt()
                            }
                        }
                        1 -> { // TR
                            val newW = width + dx
                            val newH = height - dy
                            if (newW > 100 && newH > 100) {
                                y += dy
                                params.width = newW.toInt()
                                params.height = newH.toInt()
                            }
                        }
                        2 -> { // BL
                            val newW = width - dx
                            val newH = height + dy
                            if (newW > 100 && newH > 100) {
                                x += dx
                                params.width = newW.toInt()
                                params.height = newH.toInt()
                            }
                        }
                        3 -> { // BR
                            val newW = width + dx
                            val newH = height + dy
                            if (newW > 100 && newH > 100) {
                                params.width = newW.toInt()
                                params.height = newH.toInt()
                            }
                        }
                    }
                    layoutParams = params
                }
                lastX = rawX
                lastY = rawY
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
