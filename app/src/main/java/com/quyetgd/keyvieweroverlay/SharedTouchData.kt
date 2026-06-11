package com.quyetgd.keyvieweroverlay

object SharedTouchData {
    class TouchPoint(var x: Float = -1f, var y: Float = -1f, var isActive: Boolean = false)
    val points = Array(10) { TouchPoint() }
    var invalidateCallback: (() -> Unit)? = null // Gọi để ép vẽ lại ngay lập tức
}
