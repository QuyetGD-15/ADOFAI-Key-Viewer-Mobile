package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs

class HitboxView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var hitboxNumber: String = "1"
        set(value) {
            field = value
            contentDescription = "Hitbox $value"
            invalidate()
        }

    /** True while this hitbox is the one being edited by the host UI. */
    var editingSelected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Called when the user touches this hitbox so the host can select it. */
    var onSelected: (() -> Unit)? = null

    /** Called after a drag or resize changes the hitbox geometry. */
    var onGeometryChanged: (() -> Unit)? = null

    // The overlay contract uses a literal 20 physical-pixel content inset.
    private val contentInset = 20f
    private val density = resources.displayMetrics.density
    private val handleRadius = 12f * density
    private val handleTouchRadius = 24f * density
    private val minSize = 64f * density

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 7f * density), 0f)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics
        )
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Anchor values prevent accumulated rounding error during a gesture.
    private var initialX = 0f
    private var initialY = 0f
    private var initialWidth = 0f
    private var initialHeight = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activeHandle = -1 // -1: move, 0: TL, 1: TR, 2: BL, 3: BR
    private var gestureChanged = false
    private var tracking = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Hitbox $hitboxNumber"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val selected = editingSelected

        // Keep these coordinates at exactly 20 physical px for OverlayService.
        val left = contentInset
        val top = contentInset
        val right = w - contentInset
        val bottom = h - contentInset

        borderPaint.color = Color.parseColor(if (selected) "#66F5E9" else "#26B9B0")
        borderPaint.strokeWidth = if (selected) 3f * density else 2f * density
        bgPaint.color = Color.parseColor(if (selected) "#3326B9B0" else "#2226B9B0")
        handlePaint.color = Color.parseColor(if (selected) "#00BFA5" else "#168F8A")

        canvas.drawRect(left, top, right, bottom, bgPaint)
        canvas.drawRect(left, top, right, bottom, borderPaint)
        canvas.drawCircle(left, top, handleRadius, handlePaint)
        canvas.drawCircle(right, top, handleRadius, handlePaint)
        canvas.drawCircle(left, bottom, handleRadius, handlePaint)
        canvas.drawCircle(right, bottom, handleRadius, handlePaint)

        val textY = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(hitboxNumber, w / 2f, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = x
                initialY = y
                initialWidth = width.toFloat()
                initialHeight = height.toFloat()
                initialTouchX = rawX
                initialTouchY = rawY
                activeHandle = getTouchedHandle(event.x, event.y)
                tracking = true
                gestureChanged = false
                editingSelected = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onSelected?.invoke()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val deltaX = rawX - initialTouchX
                val deltaY = rawY - initialTouchY
                val oldX = x
                val oldY = y
                val params = layoutParams ?: return true
                val oldWidth = params.width.takeIf { it > 0 } ?: width
                val oldHeight = params.height.takeIf { it > 0 } ?: height
                // Existing smaller stored boxes stay unchanged until explicitly enlarged.
                val minWidth = minOf(minSize, initialWidth)
                val minHeight = minOf(minSize, initialHeight)
                var newX = initialX
                var newY = initialY
                var newWidth = initialWidth
                var newHeight = initialHeight

                when (activeHandle) {
                    -1 -> {
                        newX = initialX + deltaX
                        newY = initialY + deltaY
                    }
                    0 -> {
                        newWidth = maxOf(minWidth, initialWidth - deltaX)
                        newHeight = maxOf(minHeight, initialHeight - deltaY)
                        newX = initialX + (initialWidth - newWidth)
                        newY = initialY + (initialHeight - newHeight)
                    }
                    1 -> {
                        newWidth = maxOf(minWidth, initialWidth + deltaX)
                        newHeight = maxOf(minHeight, initialHeight - deltaY)
                        newY = initialY + (initialHeight - newHeight)
                    }
                    2 -> {
                        newWidth = maxOf(minWidth, initialWidth - deltaX)
                        newHeight = maxOf(minHeight, initialHeight + deltaY)
                        newX = initialX + (initialWidth - newWidth)
                    }
                    3 -> {
                        newWidth = maxOf(minWidth, initialWidth + deltaX)
                        newHeight = maxOf(minHeight, initialHeight + deltaY)
                    }
                }

                newWidth = newWidth.toInt().toFloat()
                newHeight = newHeight.toInt().toFloat()
                newX = clampPosition(newX, newWidth, horizontal = true)
                newY = clampPosition(newY, newHeight, horizontal = false)

                val changed = newX != oldX || newY != oldY ||
                    newWidth != oldWidth.toFloat() || newHeight != oldHeight.toFloat()
                if (newWidth != oldWidth.toFloat() || newHeight != oldHeight.toFloat()) {
                    params.width = newWidth.toInt()
                    params.height = newHeight.toInt()
                    layoutParams = params
                }
                x = newX
                y = newY

                gestureChanged = gestureChanged || changed
                if (changed) onGeometryChanged?.invoke()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                parent?.requestDisallowInterceptTouchEvent(false)
                activeHandle = -1
                tracking = false
                if (!gestureChanged) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activeHandle = -1
                tracking = false
                gestureChanged = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        editingSelected = true
        onSelected?.invoke()
        super.performClick()
        return true
    }

    private fun clampPosition(position: Float, size: Float, horizontal: Boolean): Float {
        val parentSize = (parent as? ViewGroup)?.let { if (horizontal) it.width else it.height } ?: 0
        if (parentSize <= 0) return maxOf(-contentInset, position)
        // Keep at least 20 physical px of the view on screen, while allowing the
        // established -20 px inset used by the overlay's default placement.
        val visible = minOf(size, contentInset + 24f * density)
        val minimum = minOf(-contentInset, visible - size)
        val maximum = parentSize - visible
        return position.coerceIn(minimum, maxOf(minimum, maximum))
    }

    private fun getTouchedHandle(touchX: Float, touchY: Float): Int {
        // Choose the nearest corner when targets overlap on a small stored box.
        var closest = -1
        var distance = Float.MAX_VALUE
        for (handle in 0..3) {
            val cx = if (handle % 2 == 0) contentInset else width - contentInset
            val cy = if (handle < 2) contentInset else height - contentInset
            val dx = abs(touchX - cx)
            val dy = abs(touchY - cy)
            val squared = dx * dx + dy * dy
            if (dx <= handleTouchRadius && dy <= handleTouchRadius && squared < distance) {
                closest = handle
                distance = squared
            }
        }
        return closest
    }
}
