package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class FastCounterView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER // Luôn căn giữa
    }

    private var currentValue = -1
    private val digitChars = CharArray(16)
    private var charCount = 0
    private var isUnderline = false
    private var colorStateList: ColorStateList? = null
    private var cachedTextWidth = 0f
    private var cachedFontAscent = textPaint.ascent()
    private var cachedFontDescent = textPaint.descent()

    // Bỏ qua bộ đệm nháp GPU, tăng tốc độ render phần cứng
    override fun hasOverlappingRendering(): Boolean = false

    var formatWithComma = false

    fun setCount(value: Int) {
        if (this.currentValue == value) return
        this.currentValue = value
        rebuildDigits()
        invalidate()
    }

    private fun rebuildDigits() {
        var temp = currentValue
        charCount = 0

        if (temp == 0) {
            digitChars[0] = '0'
            charCount = 1
        } else {
            var digitIndex = 0
            while (temp > 0) {
                if (formatWithComma && digitIndex > 0 && digitIndex % 3 == 0) {
                    digitChars[charCount++] = ','
                }
                digitChars[charCount++] = (temp % 10 + '0'.code).toChar()
                temp /= 10
                digitIndex++
            }

            // Đảo ngược mảng
            for (i in 0 until charCount / 2) {
                val t = digitChars[i]
                digitChars[i] = digitChars[charCount - 1 - i]
                digitChars[charCount - 1 - i] = t
            }
        }
        refreshTextMetrics()
    }

    private fun refreshTextMetrics() {
        cachedTextWidth = if (charCount > 0) {
            textPaint.measureText(digitChars, 0, charCount)
        } else {
            0f
        }
        cachedFontAscent = textPaint.ascent()
        cachedFontDescent = textPaint.descent()
    }

    fun setTypeface(tf: Typeface) {
        if (textPaint.typeface == tf) return
        textPaint.typeface = tf
        refreshTextMetrics()
        invalidate()
    }

    // THÊM API NHẬN KÍCH CỠ CHỮ
    fun setTextSize(sizePx: Float) {
        if (textPaint.textSize == sizePx) return
        textPaint.textSize = sizePx
        refreshTextMetrics()
        invalidate()
    }

    fun setTextColor(colors: ColorStateList) {
        if (colorStateList === colors) return
        colorStateList = colors
        updateTextColor()
    }

    fun setUnderline(underline: Boolean) {
        if (this.isUnderline == underline) return
        this.isUnderline = underline
        textPaint.isUnderlineText = underline
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = cachedTextWidth.toInt()
        val desiredHeight = (cachedFontDescent - cachedFontAscent).toInt()
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateTextColor()
    }

    private fun updateTextColor() {
        colorStateList?.let {
            val color = it.getColorForState(drawableState, it.defaultColor)
            if (textPaint.color != color) {
                textPaint.color = color
                invalidate()
            }
        }
    }

    var textAlignment: Paint.Align = Paint.Align.CENTER
        set(value) {
            if (field == value) return
            field = value
            textPaint.textAlign = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (charCount == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 1. TÍNH TOÁN CHỐNG TRÀN CHỮ
        val textWidth = cachedTextWidth
        val maxWidth = viewWidth * 0.9f // Giữ lại 10% làm lề an toàn

        canvas.save()

        // 2. Dịch trục vẽ ra đúng lề
        val y = viewHeight / 2f - (cachedFontDescent + cachedFontAscent) / 2f
        val x = when (textAlignment) {
            Paint.Align.LEFT -> viewWidth * 0.05f
            Paint.Align.RIGHT -> viewWidth * 0.95f
            else -> viewWidth / 2f
        }
        canvas.translate(x, y)

        // 3. AUTO-FIT: Nếu chữ dài hơn ô vuông, tự động bóp tỉ lệ lại!
        if (textWidth > maxWidth && textWidth > 0) {
            val scale = maxWidth / textWidth
            canvas.scale(scale, scale) // Bóp đều cả ngang và dọc để chữ không bị méo
        }

        // Vẽ mảng byte nguyên thủy (Zero-Allocation)
        canvas.drawText(digitChars, 0, charCount, 0f, 0f, textPaint)
        canvas.restore()
    }
}