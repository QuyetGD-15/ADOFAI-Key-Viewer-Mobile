package com.quyetgd.keyvieweroverlay

import android.content.SharedPreferences
import android.graphics.Color

data class ThemeColorSet(
    val textNormal: String = "#FFFFFFFF",
    val textPressed: String = "#FF000000",
    val bgNormal: String = "#FF000000",
    val bgPressed: String = "#FFFFFFFF",
    val borderNormal: String = "#FFFFFFFF",
    val borderPressed: String = "#FFFFFFFF",
    val trail: String = "#FFFFFFFF",
    val shadow: String = "#FF000000"
) {
    fun normalized(): ThemeColorSet = copy(
        textNormal = normalize(textNormal), textPressed = normalize(textPressed),
        bgNormal = normalize(bgNormal), bgPressed = normalize(bgPressed),
        borderNormal = normalize(borderNormal), borderPressed = normalize(borderPressed),
        trail = normalize(trail), shadow = normalize(shadow)
    )

    companion object {
        fun normalize(value: String): String = try {
            String.format("#%08X", Color.parseColor(value).toLong() and 0xFFFFFFFFL)
        } catch (_: Exception) { "#FFFFFFFF" }
    }
}

/** A system preset is shared by Basic and Advanced and contains a color set for every key. */
data class SystemThemePreset(
    val name: String,
    val keys: Array<ThemeColorSet>,
    val kps: ThemeColorSet,
    val total: ThemeColorSet
)

object ThemeColorStore {
    const val BASIC = "basic"
    const val ADVANCED = "advanced"
    const val SYSTEM_PRESET_COUNT = 3
    const val BASIC_CUSTOM_FIRST = 1
    const val ADVANCED_CUSTOM_FIRST = 4
    private const val MODE_PREFIX = "theme_color_mode_"
    private const val ADV_PREFIX = "advanced_colors_"

    // ===== SYSTEM PRESETS: edit these definitions manually if desired. =====
    // The slot order is undname 1, undname 2, undname 3. Each generated key
    // below is deliberately separated so its per-key color can be customized.
    private val SYSTEM_NAMES = arrayOf("undname 1", "undname 2", "undname 3")
    private val SYSTEM_ACCENTS = arrayOf("#FF60A5FA", "#FFA78BFA", "#FF34D399")
    private val SYSTEM_BACKGROUNDS = arrayOf("#FF101827", "#FF181329", "#FF071F1A")

    fun systemPreset(keyMode: Int, slot: Int, fallback: ThemeColorSet): SystemThemePreset {
        val safeSlot = slot.coerceIn(0, SYSTEM_PRESET_COUNT - 1)
        val accent = Color.parseColor(SYSTEM_ACCENTS[safeSlot])
        val background = SYSTEM_BACKGROUNDS[safeSlot]
        val keys = Array(keyMode.coerceIn(4, 16)) { index ->
            // Per-key system color location: edit the accent/background formulas here.
            val hueShift = (index * 18 + safeSlot * 24) % 360
            val hsv = floatArrayOf(hueShift.toFloat(), .72f, .96f)
            val keyAccent = Color.HSVToColor(255, hsv)
            ThemeColorSet(
                textNormal = "#FFFFFFFF", textPressed = "#FF101010",
                bgNormal = background, bgPressed = String.format("#%08X", keyAccent.toLong() and 0xFFFFFFFFL),
                borderNormal = String.format("#%08X", accent.toLong() and 0xFFFFFFFFL),
                borderPressed = "#FFFFFFFF", trail = String.format("#%08X", keyAccent.toLong() and 0xFFFFFFFFL),
                shadow = String.format("#AA%06X", keyAccent and 0xFFFFFF)
            ).normalized()
        }
        return SystemThemePreset(SYSTEM_NAMES[safeSlot], keys, fallback, fallback)
    }

    fun mode(pref: SharedPreferences, keyMode: Int): String =
        pref.getString(MODE_PREFIX + keyMode, BASIC).let { if (it == ADVANCED) ADVANCED else BASIC }

    fun setMode(editor: SharedPreferences.Editor, keyMode: Int, mode: String) {
        editor.putString(MODE_PREFIX + keyMode, if (mode == ADVANCED) ADVANCED else BASIC)
    }

    fun basic(pref: SharedPreferences): ThemeColorSet = ThemeColorSet(
        pref.getString("theme_text_color", "#FFFFFFFF") ?: "#FFFFFFFF",
        pref.getString("theme_text_color_pressed", "#FF000000") ?: "#FF000000",
        pref.getString("theme_bg_normal", "#FF000000") ?: "#FF000000",
        pref.getString("theme_bg_pressed", "#FFFFFFFF") ?: "#FFFFFFFF",
        pref.getString("theme_border_normal", "#FFFFFFFF") ?: "#FFFFFFFF",
        pref.getString("theme_border_pressed", "#FFFFFFFF") ?: "#FFFFFFFF",
        pref.getString("theme_rain_color", "#FFFFFFFF") ?: "#FFFFFFFF",
        pref.getString("theme_rain_shadow", "#FF000000") ?: "#FF000000"
    ).normalized()

    fun basicTrail2(pref: SharedPreferences): Pair<String, String> =
        (pref.getString("theme_rain_color_2", "#FFA78BFA") ?: "#FFA78BFA") to
            (pref.getString("theme_rain_shadow_2", "#FF7C3AED") ?: "#FF7C3AED")

    fun writeBasic(editor: SharedPreferences.Editor, colors: ThemeColorSet, trail2: String? = null, shadow2: String? = null) {
        val c = colors.normalized()
        editor.putString("theme_text_color", c.textNormal)
            .putString("theme_text_color_pressed", c.textPressed)
            .putString("theme_bg_normal", c.bgNormal)
            .putString("theme_bg_pressed", c.bgPressed)
            .putString("theme_border_normal", c.borderNormal)
            .putString("theme_border_pressed", c.borderPressed)
            .putString("theme_rain_color", c.trail)
            .putString("theme_rain_shadow", c.shadow)
        trail2?.let { editor.putString("theme_rain_color_2", normalize(it)) }
        shadow2?.let { editor.putString("theme_rain_shadow_2", normalize(it)) }
    }

    private fun prefix(keyMode: Int, target: String): String = "$ADV_PREFIX${keyMode}_${target}_"
    private fun normalize(value: String) = ThemeColorSet.normalize(value)

    fun advanced(pref: SharedPreferences, keyMode: Int, target: String, fallback: ThemeColorSet): ThemeColorSet {
        val p = prefix(keyMode, target)
        fun get(name: String, fallbackValue: String) = pref.getString(p + name, fallbackValue) ?: fallbackValue
        return ThemeColorSet(get("text", fallback.textNormal), get("text_pressed", fallback.textPressed), get("bg", fallback.bgNormal), get("bg_pressed", fallback.bgPressed), get("border", fallback.borderNormal), get("border_pressed", fallback.borderPressed), get("trail", fallback.trail), get("shadow", fallback.shadow)).normalized()
    }

    private fun presetPrefix(keyMode: Int, slot: Int, target: String): String = "advanced_preset_${keyMode}_${slot}_${target}_"
    fun advancedPreset(pref: SharedPreferences, keyMode: Int, slot: Int, target: String, fallback: ThemeColorSet): ThemeColorSet {
        val p = presetPrefix(keyMode, slot, target)
        fun get(name: String, fallbackValue: String) = pref.getString(p + name, fallbackValue) ?: fallbackValue
        return ThemeColorSet(get("text", fallback.textNormal), get("text_pressed", fallback.textPressed), get("bg", fallback.bgNormal), get("bg_pressed", fallback.bgPressed), get("border", fallback.borderNormal), get("border_pressed", fallback.borderPressed), get("trail", fallback.trail), get("shadow", fallback.shadow)).normalized()
    }

    fun writeAdvancedPreset(editor: SharedPreferences.Editor, keyMode: Int, slot: Int, target: String, colors: ThemeColorSet) {
        val p = presetPrefix(keyMode, slot, target); val c = colors.normalized()
        editor.putString(p + "text", c.textNormal).putString(p + "text_pressed", c.textPressed).putString(p + "bg", c.bgNormal).putString(p + "bg_pressed", c.bgPressed).putString(p + "border", c.borderNormal).putString(p + "border_pressed", c.borderPressed).putString(p + "trail", c.trail).putString(p + "shadow", c.shadow)
    }

    fun writeAdvanced(editor: SharedPreferences.Editor, keyMode: Int, target: String, colors: ThemeColorSet) {
        val p = prefix(keyMode, target); val c = colors.normalized()
        editor.putString(p + "text", c.textNormal).putString(p + "text_pressed", c.textPressed).putString(p + "bg", c.bgNormal).putString(p + "bg_pressed", c.bgPressed).putString(p + "border", c.borderNormal).putString(p + "border_pressed", c.borderPressed).putString(p + "trail", c.trail).putString(p + "shadow", c.shadow)
    }
}
