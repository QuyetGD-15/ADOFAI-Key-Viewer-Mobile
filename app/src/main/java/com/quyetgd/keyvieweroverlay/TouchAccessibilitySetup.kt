package com.quyetgd.keyvieweroverlay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import rikka.shizuku.Shizuku

/** Manual permission UI shared by onboarding and Main. Never writes accessibility settings. */
class TouchAccessibilitySetup(
    private val activity: AppCompatActivity,
    private val isTouch: () -> Boolean,
    private val refresh: () -> Unit
) {
    companion object {
        private const val REQUEST_CODE = 100
        fun available(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        fun hasPermission(): Boolean = try {
            available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

        fun openSettings(activity: AppCompatActivity) {
            for (action in listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)) {
                try {
                    if (action == Settings.ACTION_SETTINGS) {
                        Toast.makeText(activity, R.string.permission_accessibility_steps, Toast.LENGTH_LONG).show()
                    }
                    activity.startActivity(Intent(action))
                    return
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) { }
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.overlay_accessibility_title)
                .setMessage(R.string.permission_accessibility_steps)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private var foreground = false
    private var requesting = false
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == REQUEST_CODE) {
            requesting = false
            activity.runOnUiThread { if (foreground) refresh() }
        }
    }

    fun resume() {
        foreground = true
        requesting = false
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    fun pause() {
        foreground = false
        requesting = false
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun cancel() { requesting = false }
    fun enterTouchStep() = refresh()

    fun request() {
        if (!foreground || !isTouch() || requesting) return
        if (!available() || hasPermission()) {
            openShizuku()
            return
        }
        try {
            requesting = true
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Exception) {
            requesting = false
            Toast.makeText(activity, R.string.shizuku_error, Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun openShizuku() {
        if (!isTouch()) return
        try {
            val intent = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                activity.startActivity(intent)
                return
            }
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) { }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permission_shizuku_title)
            .setMessage(R.string.permission_shizuku_steps)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    fun updateCards() {
        val touch = isTouch()
        val permitted = hasPermission()
        val ready = TouchRendererService.isReady(activity)
        val enabled = TouchRendererService.isEnabled(activity)
        activity.findViewById<View>(R.id.cardShizuku).visibility = if (touch) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.cardAccessibility).visibility = View.VISIBLE
        activity.findViewById<TextView>(R.id.tvStatus).setText(R.string.permission_shizuku_title)
        activity.findViewById<TextView>(R.id.tvShizukuStatus).setText(when {
            !available() -> R.string.permission_shizuku_unavailable
            permitted -> R.string.permission_shizuku_ready
            requesting -> R.string.shizuku_requesting_permission
            else -> R.string.permission_shizuku_required
        })
        activity.findViewById<Button>(R.id.btnCheck).apply {
            setText(when {
                requesting -> R.string.shizuku_requesting_permission
                !available() || permitted -> R.string.permission_open_shizuku
                else -> R.string.permission_shizuku_allow
            })
            isEnabled = touch && !requesting
        }
        activity.findViewById<TextView>(R.id.tvAccessibilityStatus).setText(when {
            ready -> R.string.overlay_accessibility_connected
            enabled -> R.string.overlay_accessibility_connecting
            else -> R.string.overlay_accessibility_required
        })
        activity.findViewById<Button>(R.id.btnAccessibility).apply {
            setText(if (enabled) R.string.permission_manage else R.string.permission_open_settings)
            isEnabled = true
        }
        renderCard(
            R.id.cardShizuku, R.id.permissionShizukuHeader, R.id.detailsShizuku,
            R.id.btnTouchInfo, R.id.dotShizukuStatus, R.id.tvShizukuStatus,
            R.string.permission_shizuku_title, permitted,
            when {
                !touch -> R.color.permission_inactive
                permitted -> R.color.permission_ready
                requesting && available() -> R.color.permission_connecting
                else -> R.color.permission_missing
            }, true
        )
        renderCard(
            R.id.cardAccessibility, R.id.permissionAccessibilityHeader, R.id.detailsAccessibility,
            R.id.btnAccessibilityInfo, R.id.dotAccessibilityStatus, R.id.tvAccessibilityStatus,
            R.string.overlay_accessibility_title, ready,
            when {
                ready -> R.color.permission_ready
                enabled -> R.color.permission_connecting
                else -> R.color.permission_missing
            }, false
        )
    }

    /** Presentation only: never changes readiness, requests permissions, or stores collapse state. */
    private fun renderCard(
        cardId: Int, headerId: Int, detailsId: Int, infoId: Int, dotId: Int,
        statusId: Int, titleId: Int, ready: Boolean, colorId: Int, touch: Boolean
    ) {
        val card = activity.findViewById<View>(cardId)
        val header = activity.findViewById<View>(headerId)
        val status = activity.findViewById<TextView>(statusId)
        val color = ContextCompat.getColor(activity, colorId)
        status.setTextColor(color)
        activity.findViewById<View>(dotId).backgroundTintList = ColorStateList.valueOf(color)
        activity.findViewById<View>(detailsId).visibility = if (ready) View.GONE else View.VISIBLE
        activity.findViewById<View>(infoId).visibility = if (ready) View.GONE else View.VISIBLE
        // A single accessible target announces the translated ready status even though only
        // the title and green dot are visible. Expanded children remain individually accessible.
        header.importantForAccessibility = if (ready) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        card.contentDescription = if (ready) "${activity.getString(titleId)}: ${status.text}" else null
        card.setOnClickListener(if (ready) View.OnClickListener { showDetails(touch) } else null)
        card.isClickable = ready
        card.isFocusable = ready
        card.importantForAccessibility = if (ready) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val padding = (activity.resources.displayMetrics.density * if (ready) 8 else 20).toInt()
        val container = header.parent as View
        container.setPadding(container.paddingLeft, padding, container.paddingRight, padding)
    }

    fun showDetails(touch: Boolean = isTouch()) {
        val builder = MaterialAlertDialogBuilder(activity)
        val content = LayoutInflater.from(builder.context)
            .inflate(R.layout.dialog_permission_info, null)
        content.findViewById<TextView>(R.id.permissionPurpose).setText(
            if (touch) R.string.permission_shizuku_purpose else R.string.permission_accessibility_purpose)
        content.findViewById<TextView>(R.id.permissionSteps).setText(
            if (touch) R.string.permission_shizuku_steps else R.string.permission_accessibility_steps)
        content.findViewById<TextView>(R.id.permissionPrivacy).setText(
            if (touch) R.string.permission_shizuku_scope else R.string.permission_accessibility_scope)
        builder.setTitle(if (touch) R.string.permission_shizuku_title else R.string.overlay_accessibility_title)
            .setIcon(if (touch) R.drawable.ic_touch_app else R.drawable.ic_keyboard)
            .setView(content)
            .setPositiveButton(if (touch) R.string.permission_open_shizuku else R.string.permission_open_settings) { _, _ ->
                if (touch) openShizuku() else openSettings(activity)
            }
            .setNegativeButton(R.string.permission_close, null).show()
    }
}
