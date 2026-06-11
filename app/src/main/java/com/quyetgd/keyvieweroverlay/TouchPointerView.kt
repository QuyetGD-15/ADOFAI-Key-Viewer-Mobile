package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TouchPointerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintOuterBlack = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = dpToPx(6f)
        alpha = 204 // ~0.8
        isAntiAlias = true
    }

    private val paintInnerWhite = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dpToPx(3f)
        alpha = 204
        isAntiAlias = true
    }

    private val radius = dpToPx(20f)
    private var activeTouches = listOf<Pair<Float, Float>>()

    fun updateTouches(touches: List<Pair<Float, Float>>) {
        activeTouches = touches
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        activeTouches.forEach { (x, y) ->
            canvas.drawCircle(x, y, radius, paintOuterBlack)
            canvas.drawCircle(x, y, radius, paintInnerWhite)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
