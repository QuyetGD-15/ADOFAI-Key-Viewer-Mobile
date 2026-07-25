package com.quyetgd.keyvieweroverlay

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

object LayoutEngine {
    fun apply10KJipperLayout(
        frameWorkspace: FrameLayout, keyContainers: Array<LinearLayout?>,
        keyWidthPx: Int, keyHeightPx: Int, spacingPx: Int,
        kpsContainer: View?, totalContainer: View?
    ) {
        val stepX = keyWidthPx + spacingPx
        val totalWidth = (8 * keyWidthPx) + (7 * spacingPx)
        val totalHeight = (2 * keyHeightPx) + spacingPx
        val backWidthPx = (2.5f * keyWidthPx).toInt() + (2 * spacingPx)
        val centerX = totalWidth / 2f
        val backXLeft = (centerX - (spacingPx / 2f) - backWidthPx).toInt()
        val backXRight = (centerX + (spacingPx / 2f)).toInt()

        for (i in 0 until 10) {
            val container = keyContainers[i] ?: continue
            val params = container.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.height = keyHeightPx
            when (i) {
                in 0..7 -> { params.width = keyWidthPx; params.leftMargin = i * stepX; params.bottomMargin = keyHeightPx + spacingPx }
                8 -> { params.width = backWidthPx; params.leftMargin = backXLeft; params.bottomMargin = 0 }
                9 -> { params.width = backWidthPx; params.leftMargin = backXRight; params.bottomMargin = 0 }
            }
            container.layoutParams = params
        }
        val kpsTotalWidth = backXLeft - spacingPx
        kpsContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = kpsTotalWidth; params.height = keyHeightPx; params.gravity = Gravity.BOTTOM or Gravity.START; params.leftMargin = 0; params.bottomMargin = 0
            it.layoutParams = params
        }
        totalContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = kpsTotalWidth; params.height = keyHeightPx; params.gravity = Gravity.BOTTOM or Gravity.START; params.leftMargin = totalWidth - kpsTotalWidth; params.bottomMargin = 0
            it.layoutParams = params
        }
        val frameParams = frameWorkspace.layoutParams as LinearLayout.LayoutParams
        frameParams.width = totalWidth; frameParams.height = totalHeight; frameParams.gravity = Gravity.CENTER_HORIZONTAL
        frameWorkspace.layoutParams = frameParams
    }

    fun apply12KLayout(
        frameWorkspace: FrameLayout, keyContainers: Array<LinearLayout?>,
        keyWidthPx: Int, keyHeightPx: Int, spacingPx: Int,
        kpsContainer: View?, totalContainer: View?
    ) {
        val stepX = keyWidthPx + spacingPx
        val totalWidth = (8 * keyWidthPx) + (7 * spacingPx)
        val totalHeight = (2 * keyHeightPx) + spacingPx
        val wOuter = keyWidthPx
        val wInner = (1.5f * keyWidthPx).toInt() + spacingPx
        val centerX = totalWidth / 2f
        val key10Left = (centerX - (spacingPx / 2f) - wInner).toInt()
        val key9Left = key10Left - spacingPx - wOuter
        val key11Left = (centerX + (spacingPx / 2f)).toInt()
        val key12Left = key11Left + wInner + spacingPx

        for (i in 0 until 12) {
            val container = keyContainers[i] ?: continue
            val params = container.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.height = keyHeightPx
            when (i) {
                in 0..7 -> { params.width = keyWidthPx; params.leftMargin = i * stepX; params.bottomMargin = keyHeightPx + spacingPx }
                8 -> { params.width = wOuter; params.leftMargin = key9Left; params.bottomMargin = 0 }
                9 -> { params.width = wInner; params.leftMargin = key10Left; params.bottomMargin = 0 }
                10 -> { params.width = wInner; params.leftMargin = key11Left; params.bottomMargin = 0 }
                11 -> { params.width = wOuter; params.leftMargin = key12Left; params.bottomMargin = 0 }
            }
            container.layoutParams = params
        }
        val kpsTotalWidth = key9Left - spacingPx
        kpsContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = kpsTotalWidth; params.height = keyHeightPx; params.gravity = Gravity.BOTTOM or Gravity.START; params.leftMargin = 0; params.bottomMargin = 0
            it.layoutParams = params
        }
        totalContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = kpsTotalWidth; params.height = keyHeightPx; params.gravity = Gravity.BOTTOM or Gravity.START; params.leftMargin = totalWidth - kpsTotalWidth; params.bottomMargin = 0
            it.layoutParams = params
        }
        val frameParams = frameWorkspace.layoutParams as LinearLayout.LayoutParams
        frameParams.width = totalWidth; frameParams.height = totalHeight; frameParams.gravity = Gravity.CENTER_HORIZONTAL
        frameWorkspace.layoutParams = frameParams
    }

    // ================== KIẾN TRÚC MỚI: 16K ==================
    fun apply16KLayout(
        frameWorkspace: FrameLayout, keyContainers: Array<LinearLayout?>,
        keyWidthPx: Int, keyHeightPx: Int, spacingPx: Int,
        kpsContainer: View?, totalContainer: View?
    ) {
        val stepX = keyWidthPx + spacingPx
        val totalWidth = (8 * keyWidthPx) + (7 * spacingPx)

        // 1. NÂNG CHIỀU CAO: Tăng từ 0.5f lên 0.65f (65%) để bù lại tỷ lệ 4/5 bị hụt
        val counterHeight = (keyHeightPx * 0.65f).toInt()

        // 2. BỔ SUNG SPACING: Tổng chiều cao = 2 hàng phím + 2 khoảng cách (spacing) + 1 hàng KPS/Total
        val totalHeight = (2 * keyHeightPx) + (2 * spacingPx) + counterHeight

        // 3. SẮP XẾP 16 PHÍM
        for (i in 0 until 16) {
            val container = keyContainers[i] ?: continue
            val params = container.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.width = keyWidthPx
            params.height = keyHeightPx

            if (i < 8) {
                // HÀNG 1 (Nằm trên cùng)
                params.leftMargin = i * stepX
                // Đáy của Hàng 1 = Hàng 2 + Spacing 1 + KPS + Spacing 2
                params.bottomMargin = keyHeightPx + (2 * spacingPx) + counterHeight
            } else {
                // HÀNG 2 (Nằm ở giữa)
                params.leftMargin = (i - 8) * stepX
                // Đáy của Hàng 2 = KPS + Spacing 1 (Đã tách rời hoàn toàn khỏi KPS)
                params.bottomMargin = counterHeight + spacingPx
            }
            container.layoutParams = params
        }

        // 4. SẮP XẾP KPS & TOTAL
        val counterWidth = (totalWidth - spacingPx) / 2

        kpsContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = counterWidth
            params.height = counterHeight
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.leftMargin = 0
            params.bottomMargin = 0
            it.layoutParams = params
        }

        totalContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = counterWidth
            params.height = counterHeight
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.leftMargin = totalWidth - counterWidth
            params.bottomMargin = 0
            it.layoutParams = params
        }

        val frameParams = frameWorkspace.layoutParams as LinearLayout.LayoutParams
        frameParams.width = totalWidth; frameParams.height = totalHeight; frameParams.gravity = Gravity.CENTER_HORIZONTAL
        frameWorkspace.layoutParams = frameParams
    }
    // ================== KIẾN TRÚC MỚI: 4K, 6K, 8K ==================
    fun applyStandardLayout(
        frameWorkspace: FrameLayout, keyContainers: Array<LinearLayout?>,
        keyWidthPx: Int, keyHeightPx: Int, spacingPx: Int,
        kpsContainer: View?, totalContainer: View?, keyCount: Int
    ) {
        val stepX = keyWidthPx + spacingPx
        val totalWidth = (keyCount * keyWidthPx) + ((keyCount - 1) * spacingPx)

        // 1. Chiều cao KPS/Total bằng 65% phím (Kế thừa từ 16K)
        val counterHeight = (keyHeightPx * 0.65f).toInt()

        // Tổng chiều cao = 1 hàng phím + 1 khoảng cách + 1 hàng KPS/Total
        val totalHeight = keyHeightPx + spacingPx + counterHeight

        // 2. Xếp các phím thành 1 hàng duy nhất
        for (i in 0 until keyCount) {
            val container = keyContainers[i] ?: continue
            val params = container.layoutParams as FrameLayout.LayoutParams
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.width = keyWidthPx
            params.height = keyHeightPx
            params.leftMargin = i * stepX
            // Đẩy hàng phím lên trên để nhường chỗ cho Spacing và KPS
            params.bottomMargin = counterHeight + spacingPx
            container.layoutParams = params
        }

        // 3. Xếp hộp KPS và Total ở đáy (Tầng 1)
        val counterWidth = (totalWidth - spacingPx) / 2

        kpsContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = counterWidth
            params.height = counterHeight
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.leftMargin = 0
            params.bottomMargin = 0
            it.layoutParams = params
        }

        totalContainer?.let {
            val params = it.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(0, 0)
            params.width = counterWidth
            params.height = counterHeight
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.leftMargin = totalWidth - counterWidth
            params.bottomMargin = 0
            it.layoutParams = params
        }

        // Chốt khung tổng
        val frameParams = frameWorkspace.layoutParams as LinearLayout.LayoutParams
        frameParams.width = totalWidth; frameParams.height = totalHeight; frameParams.gravity = Gravity.CENTER_HORIZONTAL
        frameWorkspace.layoutParams = frameParams
    }
}