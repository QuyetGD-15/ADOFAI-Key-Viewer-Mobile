package com.quyetgd.keyvieweroverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Visual-only preview for the future automatic left/right hitbox mode. */
class AutoHitboxPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var dividerFraction: Float = 0.5f
        set(value) { field = value.coerceIn(0.12f, 0.88f); invalidate() }
    var onDividerChanged: (() -> Unit)? = null

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66F5E9")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BFA5")
        style = Paint.Style.FILL
    }
    private val leftRegionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1826B9B0")
        style = Paint.Style.FILL
    }
    private val rightRegionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18268BDE")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 13f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val hintPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B21E1E1E")
        style = Paint.Style.FILL
    }
    private var draggingDivider = false

    init { isClickable = true; contentDescription = context.getString(R.string.hitbox_auto_preview_description) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val dividerX = width * dividerFraction

        canvas.drawColor(Color.parseColor("#151515"))
        canvas.drawRect(0f, 0f, dividerX, height.toFloat(), leftRegionPaint)
        canvas.drawRect(dividerX, 0f, width.toFloat(), height.toFloat(), rightRegionPaint)

        // Subtle edge accents make the two touch zones readable without competing with the game.
        dividerPaint.strokeWidth = 2f * density
        canvas.drawLine(dividerX, 0f, dividerX, height.toFloat(), dividerPaint)
        canvas.drawCircle(dividerX, height / 2f, 28f * density, handlePaint)
        canvas.drawCircle(dividerX, height / 2f, 28f * density, dividerPaint)
        canvas.drawRoundRect(
            dividerX - 3f * density, height / 2f - 13f * density,
            dividerX + 3f * density, height / 2f + 13f * density,
            3f * density, 3f * density, dividerPaint
        )

        val labelY = height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(context.getString(R.string.hitbox_auto_left_hand), dividerX / 2f, labelY, labelPaint)
        canvas.drawText(context.getString(R.string.hitbox_auto_right_hand), dividerX + (width - dividerX) / 2f, labelY, labelPaint)

        val hint = context.getString(R.string.hitbox_auto_drag_hint)
        val hintY = height - 28f * density
        val hintWidth = hintPaint.measureText(hint) + 32f * density
        hintPillPaint.color = Color.parseColor("#B21E1E1E")
        canvas.drawRoundRect(
            width / 2f - hintWidth / 2f, hintY - 22f * density,
            width / 2f + hintWidth / 2f, hintY + 10f * density,
            16f * density, 16f * density, hintPillPaint
        )
        canvas.drawText(hint, width / 2f, hintY, hintPaint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dividerX = width * dividerFraction
        val touchRadius = 42f * resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingDivider = abs(event.x - dividerX) <= touchRadius
                if (draggingDivider) { parent?.requestDisallowInterceptTouchEvent(true); return true }
            }
            MotionEvent.ACTION_MOVE -> if (draggingDivider && width > 0) { dividerFraction = event.x / width.toFloat(); onDividerChanged?.invoke(); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (draggingDivider) { parent?.requestDisallowInterceptTouchEvent(false); draggingDivider = false; return true }
        }
        return draggingDivider
    }
}
