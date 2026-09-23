package com.quyetgd.keyvieweroverlay

object SharedTouchData {
    class TouchPoint(@Volatile var x: Float = -1f, @Volatile var y: Float = -1f, @Volatile var isActive: Boolean = false)

    // Khởi tạo sẵn vùng nhớ cho 10 điểm chạm, không cấp phát thêm lúc runtime
    val points = Array(10) { TouchPoint() }

    @Volatile var invalidateCallback: (() -> Unit)? = null // Gọi để ép vẽ lại ngay lập tức
}