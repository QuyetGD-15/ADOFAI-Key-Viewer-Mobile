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

    // Bỏ qua bộ đệm nháp GPU, tăng tốc độ render phần cứng
    override fun hasOverlappingRendering(): Boolean = false

    fun setCount(value: Int) {
        if (this.currentValue == value) return
        this.currentValue = value

        var temp = value
        charCount = 0

        if (temp == 0) {
            digitChars[0] = '0'
            charCount = 1
        } else {
            while (temp > 0) {
                // Tách số thuần túy, loại bỏ hoàn toàn logic dấu chấm
                digitChars[charCount++] = (temp % 10 + '0'.code).toChar()
                temp /= 10
            }

            // Đảo ngược mảng
            for (i in 0 until charCount / 2) {
                val t = digitChars[i]
                digitChars[i] = digitChars[charCount - 1 - i]
                digitChars[charCount - 1 - i] = t
            }
        }
        invalidate()
    }

    fun setTypeface(tf: Typeface) {
        textPaint.typeface = tf
        invalidate()
    }

    // THÊM API NHẬN KÍCH CỠ CHỮ
    fun setTextSize(sizePx: Float) {
        textPaint.textSize = sizePx
        invalidate()
    }

    fun setTextColor(colors: ColorStateList) {
        colorStateList = colors
        updateTextColor()
    }

    fun setUnderline(underline: Boolean) {
        this.isUnderline = underline
        textPaint.isUnderlineText = underline
        invalidate()
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

    override fun onDraw(canvas: Canvas) {
        if (charCount == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 1. TÍNH TOÁN CHỐNG TRÀN CHỮ
        val textWidth = textPaint.measureText(digitChars, 0, charCount)
        val maxWidth = viewWidth * 0.9f // Giữ lại 10% làm lề an toàn

        canvas.save()

        // 2. Dịch trục vẽ ra giữa View để số đếm luôn nằm giữa 100%
        canvas.translate(viewWidth / 2f, viewHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f)

        // 3. AUTO-FIT: Nếu 5, 6 số làm chữ dài hơn ô vuông, tự động bóp tỉ lệ lại!
        if (textWidth > maxWidth && textWidth > 0) {
            val scale = maxWidth / textWidth
            canvas.scale(scale, scale) // Bóp đều cả ngang và dọc để chữ không bị méo
        }

        // Vẽ mảng byte nguyên thủy (Zero-Allocation)
        canvas.drawText(digitChars, 0, charCount, 0f, 0f, textPaint)
        canvas.restore()
    }
}