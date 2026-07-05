package com.quyetgd.keyvieweroverlay

object SharedTouchData {
    class TouchPoint(var x: Float = -1f, var y: Float = -1f, var isActive: Boolean = false)

    // Khởi tạo sẵn vùng nhớ cho 10 điểm chạm, không cấp phát thêm lúc runtime
    val points = Array(10) { TouchPoint() }

    var invalidateCallback: (() -> Unit)? = null // Gọi để ép vẽ lại ngay lập tức
}