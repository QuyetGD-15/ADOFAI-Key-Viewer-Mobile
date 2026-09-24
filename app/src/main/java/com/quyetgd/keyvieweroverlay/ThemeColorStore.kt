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

    // ===== PRESET BASE COLORS =====
    private val SYSTEM_NAMES = arrayOf("minhle", "Candy Floss Delight", "Feel good")
    private val SYSTEM_ACCENTS = arrayOf("#FF60A5FA", "#FFA78BFA", "#FF34D399")
    private val SYSTEM_BACKGROUNDS = arrayOf("#FF101827", "#FF181329", "#FF071F1A")

    private fun systemKey(slot: Int, index: Int): ThemeColorSet {
        val accent = Color.parseColor(SYSTEM_ACCENTS[slot])
        val keyAccent = Color.HSVToColor(255, floatArrayOf(
            ((index * 18 + slot * 24) % 360).toFloat(), .72f, .96f
        ))
        val keyColor = String.format("#%08X", keyAccent.toLong() and 0xFFFFFFFFL)
        return ThemeColorSet(
            textNormal = "#FFFFFFFF", borderNormal = String.format("#%08X", accent.toLong() and 0xFFFFFFFFL),
            bgNormal = SYSTEM_BACKGROUNDS[slot],
            textPressed = "#FF101010", borderPressed = "#FFFFFFFF", bgPressed = keyColor,
            trail = keyColor, shadow = String.format("#AA%06X", keyAccent and 0xFFFFFF)
        )
    }

    private fun kps(slot: Int) = ThemeColorSet(
        textNormal = "#FFFFFFFF", borderNormal = SYSTEM_ACCENTS[slot], bgNormal = SYSTEM_BACKGROUNDS[slot],
        textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF000000"
    )

    private fun total(slot: Int) = ThemeColorSet(
        textNormal = "#FFFFFFFF", borderNormal = SYSTEM_ACCENTS[slot], bgNormal = SYSTEM_BACKGROUNDS[slot],
        textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF000000"
    )

    // ==============================================================
    // 1. SLOT 0: PRESET MINHLE
    // ==============================================================
    private val PRESET_MINHLE: Map<Int, SystemThemePreset> = mapOf(
        4 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#A5345F", bgNormal = "#3EA5345F", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#A5345F", shadow = "#A5345F"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#7A497F", bgNormal = "#3E7A497F", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7A497F", shadow = "#7A497F"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3E505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        ),
        6 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#B62C52", bgNormal = "#3FB62C52", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#B62C52", shadow = "#B62C52"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#9C3865", bgNormal = "#3F9C3865", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#9C3865", shadow = "#9C3865"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#834579", bgNormal = "#3F834579", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#834579", shadow = "#834579"),
                systemKey(0, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#69518C", bgNormal = "#3F69518C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#69518C", shadow = "#69518C"),
                systemKey(0, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        ),
        8 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#BD294C", bgNormal = "#3FBD294C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BD294C", shadow = "#BD294C"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AB315A", shadow = "#AB315A"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#993A68", shadow = "#993A68"),
                systemKey(0, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#864376", shadow = "#864376"),
                systemKey(0, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#744C84", shadow = "#744C84"),
                systemKey(0, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#625492", bgNormal = "#3F625492", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#625492", shadow = "#625492"),
                systemKey(0, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        ),
        10 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#BD294C", bgNormal = "#3FBD294C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BD294C", shadow = "#BD294C"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AB315A", shadow = "#AB315A"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#993A68", shadow = "#993A68"),
                systemKey(0, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#864376", shadow = "#864376"),
                systemKey(0, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#744C84", shadow = "#744C84"),
                systemKey(0, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#625492", bgNormal = "#3F625492", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#625492", shadow = "#625492"),
                systemKey(0, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0"),
                systemKey(0, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        ),
        12 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#BD294C", bgNormal = "#3FBD294C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BD294C", shadow = "#BD294C"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AB315A", shadow = "#AB315A"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#993A68", shadow = "#993A68"),
                systemKey(0, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#864376", shadow = "#864376"),
                systemKey(0, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#744C84", shadow = "#744C84"),
                systemKey(0, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#625492", bgNormal = "#3F625492", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#625492", shadow = "#625492"),
                systemKey(0, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0"),
                systemKey(0, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        ),
        16 to SystemThemePreset(
            name = SYSTEM_NAMES[0],
            keys = arrayOf(
                systemKey(0, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CF203E", shadow = "#CF203E"),
                systemKey(0, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#BD294C", bgNormal = "#3FBD294C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BD294C", shadow = "#BD294C"),
                systemKey(0, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AB315A", shadow = "#AB315A"),
                systemKey(0, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#993A68", shadow = "#993A68"),
                systemKey(0, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#864376", shadow = "#864376"),
                systemKey(0, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#744C84", shadow = "#744C84"),
                systemKey(0, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#625492", bgNormal = "#3F625492", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#625492", shadow = "#625492"),
                systemKey(0, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#505DA0", shadow = "#505DA0"),
                systemKey(0, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#BD294C", bgNormal = "#3FBD294C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#AB315A", bgNormal = "#3FAB315A", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#993A68", bgNormal = "#3F993A68", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 12).copy(textNormal = "#FFFFFFFF", borderNormal = "#864376", bgNormal = "#3F864376", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 13).copy(textNormal = "#FFFFFFFF", borderNormal = "#744C84", bgNormal = "#3F744C84", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 14).copy(textNormal = "#FFFFFFFF", borderNormal = "#625492", bgNormal = "#3F625492", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(0, 15).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#CF203E", bgNormal = "#3FCF203E"),
            total = total(0).copy(textNormal = "#FFFFFFFF", borderNormal = "#505DA0", bgNormal = "#3F505DA0")
        )
    )


    // ==============================================================
    // 2. SLOT 1: PRESET UNDNAME 2
    // ==============================================================
    private val PRESET_UNDNAME2: Map<Int, SystemThemePreset> = mapOf(
        4 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#88BECD", bgNormal = "#3F88BECD", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#88BECD", shadow = "#88BECD"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#B88DAF", bgNormal = "#3FB88DAF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#B88DAF", shadow = "#B88DAF"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        ),
        6 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#75D2DA", bgNormal = "#3F75D2DA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#75D2DA", shadow = "#75D2DA"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#92B4C7", bgNormal = "#3F92B4C7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#92B4C7", shadow = "#92B4C7"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#AE97B5", bgNormal = "#3FAE97B5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AE97B5", shadow = "#AE97B5"),
                systemKey(1, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#CB79A2", bgNormal = "#3FCB79A2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#CB79A2", shadow = "#CB79A2"),
                systemKey(1, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        ),
        8 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#6DDADF", bgNormal = "#3F6DDADF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#6DDADF", shadow = "#6DDADF"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#81C5D2", shadow = "#81C5D2"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#96B0C5", shadow = "#96B0C5"),
                systemKey(1, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AA9BB7", shadow = "#AA9BB7"),
                systemKey(1, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BF86AA", shadow = "#BF86AA"),
                systemKey(1, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#D3719D", bgNormal = "#3FD3719D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#D3719D", shadow = "#D3719D"),
                systemKey(1, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        ),
        10 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#6DDADF", bgNormal = "#3F6DDADF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#6DDADF", shadow = "#6DDADF"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#81C5D2", shadow = "#81C5D2"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#96B0C5", shadow = "#96B0C5"),
                systemKey(1, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AA9BB7", shadow = "#AA9BB7"),
                systemKey(1, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BF86AA", shadow = "#BF86AA"),
                systemKey(1, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#D3719D", bgNormal = "#3FD3719D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#D3719D", shadow = "#D3719D"),
                systemKey(1, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90"),
                systemKey(1, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        ),
        12 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#6DDADF", bgNormal = "#3F6DDADF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#6DDADF", shadow = "#6DDADF"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#81C5D2", shadow = "#81C5D2"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#96B0C5", shadow = "#96B0C5"),
                systemKey(1, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AA9BB7", shadow = "#AA9BB7"),
                systemKey(1, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BF86AA", shadow = "#BF86AA"),
                systemKey(1, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#D3719D", bgNormal = "#3FD3719D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#D3719D", shadow = "#D3719D"),
                systemKey(1, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90"),
                systemKey(1, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        ),
        16 to SystemThemePreset(
            name = SYSTEM_NAMES[1],
            keys = arrayOf(
                systemKey(1, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58EFEC", shadow = "#58EFEC"),
                systemKey(1, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#6DDADF", bgNormal = "#3F6DDADF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#6DDADF", shadow = "#6DDADF"),
                systemKey(1, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#81C5D2", shadow = "#81C5D2"),
                systemKey(1, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#96B0C5", shadow = "#96B0C5"),
                systemKey(1, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#AA9BB7", shadow = "#AA9BB7"),
                systemKey(1, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#BF86AA", shadow = "#BF86AA"),
                systemKey(1, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#D3719D", bgNormal = "#3FD3719D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#D3719D", shadow = "#D3719D"),
                systemKey(1, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#E85C90", shadow = "#E85C90"),
                systemKey(1, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#6DDADF", bgNormal = "#3F6DDADF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#81C5D2", bgNormal = "#3F81C5D2", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#96B0C5", bgNormal = "#3F96B0C5", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 12).copy(textNormal = "#FFFFFFFF", borderNormal = "#AA9BB7", bgNormal = "#3FAA9BB7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 13).copy(textNormal = "#FFFFFFFF", borderNormal = "#BF86AA", bgNormal = "#3FBF86AA", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 14).copy(textNormal = "#FFFFFFFF", borderNormal = "#D3719D", bgNormal = "#3FD3719D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(1, 15).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#58EFEC", bgNormal = "#3F58EFEC"),
            total = total(1).copy(textNormal = "#FFFFFFFF", borderNormal = "#E85C90", bgNormal = "#3FE85C90")
        )
    )


    // ==============================================================
    // 3. SLOT 2: PRESET UNDNAME 3
    // ==============================================================
    private val PRESET_UNDNAME3: Map<Int, SystemThemePreset> = mapOf(
        4 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#787EC6", bgNormal = "#3F787EC6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#787EC6", shadow = "#787EC6"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#63BF8D", bgNormal = "#3F63BF8D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#63BF8D", shadow = "#63BF8D"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        ),
        6 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#8065DD", bgNormal = "#3F8065DD", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8065DD", shadow = "#8065DD"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#748BBB", bgNormal = "#3F748BBB", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#748BBB", shadow = "#748BBB"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#67B298", bgNormal = "#3F67B298", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#67B298", shadow = "#67B298"),
                systemKey(2, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#5BD876", bgNormal = "#3F5BD876", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#5BD876", shadow = "#5BD876"),
                systemKey(2, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        ),
        8 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#835AE7", bgNormal = "#3F835AE7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#835AE7", shadow = "#835AE7"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7B75CE", shadow = "#7B75CE"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7291B6", shadow = "#7291B6"),
                systemKey(2, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#69AC9D", shadow = "#69AC9D"),
                systemKey(2, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#60C885", shadow = "#60C885"),
                systemKey(2, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#58E36C", bgNormal = "#3F58E36C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58E36C", shadow = "#58E36C"),
                systemKey(2, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        ),
        10 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#835AE7", bgNormal = "#3F835AE7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#835AE7", shadow = "#835AE7"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7B75CE", shadow = "#7B75CE"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7291B6", shadow = "#7291B6"),
                systemKey(2, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#69AC9D", shadow = "#69AC9D"),
                systemKey(2, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#60C885", shadow = "#60C885"),
                systemKey(2, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#58E36C", bgNormal = "#3F58E36C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58E36C", shadow = "#58E36C"),
                systemKey(2, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54"),
                systemKey(2, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        ),
        12 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#835AE7", bgNormal = "#3F835AE7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#835AE7", shadow = "#835AE7"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7B75CE", shadow = "#7B75CE"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7291B6", shadow = "#7291B6"),
                systemKey(2, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#69AC9D", shadow = "#69AC9D"),
                systemKey(2, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#60C885", shadow = "#60C885"),
                systemKey(2, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#58E36C", bgNormal = "#3F58E36C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58E36C", shadow = "#58E36C"),
                systemKey(2, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54"),
                systemKey(2, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        ),
        16 to SystemThemePreset(
            name = SYSTEM_NAMES[2],
            keys = arrayOf(
                systemKey(2, 0).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#8C3EFF", shadow = "#8C3EFF"),
                systemKey(2, 1).copy(textNormal = "#FFFFFFFF", borderNormal = "#835AE7", bgNormal = "#3F835AE7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#835AE7", shadow = "#835AE7"),
                systemKey(2, 2).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7B75CE", shadow = "#7B75CE"),
                systemKey(2, 3).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#7291B6", shadow = "#7291B6"),
                systemKey(2, 4).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#69AC9D", shadow = "#69AC9D"),
                systemKey(2, 5).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#60C885", shadow = "#60C885"),
                systemKey(2, 6).copy(textNormal = "#FFFFFFFF", borderNormal = "#58E36C", bgNormal = "#3F58E36C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#58E36C", shadow = "#58E36C"),
                systemKey(2, 7).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#4FFF54", shadow = "#4FFF54"),
                systemKey(2, 8).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 9).copy(textNormal = "#FFFFFFFF", borderNormal = "#835AE7", bgNormal = "#3F835AE7", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 10).copy(textNormal = "#FFFFFFFF", borderNormal = "#7B75CE", bgNormal = "#3F7B75CE", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 11).copy(textNormal = "#FFFFFFFF", borderNormal = "#7291B6", bgNormal = "#3F7291B6", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 12).copy(textNormal = "#FFFFFFFF", borderNormal = "#69AC9D", bgNormal = "#3F69AC9D", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 13).copy(textNormal = "#FFFFFFFF", borderNormal = "#60C885", bgNormal = "#3F60C885", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 14).copy(textNormal = "#FFFFFFFF", borderNormal = "#58E36C", bgNormal = "#3F58E36C", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898"),
                systemKey(2, 15).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54", textPressed = "#FF000000", borderPressed = "#FFFFFFFF", bgPressed = "#FFFFFFFF", trail = "#FFFFFFFF", shadow = "#FF989898")
            ),
            kps = kps(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#8C3EFF", bgNormal = "#3F8C3EFF"),
            total = total(2).copy(textNormal = "#FFFFFFFF", borderNormal = "#4FFF54", bgNormal = "#3F4FFF54")
        )
    )

    // ==============================================================
// 4. PRESET JIPPER
// ==============================================================
    private val JIPPER_BASE = ThemeColorSet(
        textNormal = "#FFFFFFFF",
        borderNormal = "#8C3EFF",
        bgNormal = "#3F8C3EFF",     // Nền giảm alpha xuống 25%
        textPressed = "#FF000000",
        borderPressed = "#FFFFFFFF",
        bgPressed = "#FFFFFFFF",
        trail = "#8C3EFF",        // Các trail còn lại dùng màu đen
        shadow = "#FF000000"
    )

    // Sinh ra key đặc biệt áp dụng quy tắc từ key 8 trở lên
    private val JIPPER_SPECIAL = JIPPER_BASE.copy(
        trail = "#FFFFFFFF",
        shadow = "#FF989898"
    )

    private val PRESET_JIPPER: Map<Int, SystemThemePreset> = listOf(4, 6, 8, 10, 12, 16).associateWith { mode ->
        SystemThemePreset(
            name = "Jipper",
            keys = Array(mode) { index ->
                if (index >= 8) JIPPER_SPECIAL else JIPPER_BASE
            },
            kps = JIPPER_BASE,       // Áp dụng màu gốc cho KPS
            total = JIPPER_BASE      // Áp dụng màu gốc cho Total
        )
    }

    fun jipperPreset(keyMode: Int): SystemThemePreset = PRESET_JIPPER[keyMode]
        ?: SystemThemePreset(
            name = "Jipper",
            keys = Array(keyMode.coerceIn(4, 16)) { index ->
                if (index >= 8) JIPPER_SPECIAL else JIPPER_BASE
            },
            kps = JIPPER_BASE,
            total = JIPPER_BASE
        )
    fun systemPreset(keyMode: Int, slot: Int, fallback: ThemeColorSet): SystemThemePreset {
        return when (slot.coerceIn(0, SYSTEM_PRESET_COUNT - 1)) {
            1 -> PRESET_UNDNAME2[keyMode] ?: SystemThemePreset(SYSTEM_NAMES[1], Array(keyMode.coerceIn(4, 16)) { systemKey(1, it) }, kps(1), total(1))
            2 -> PRESET_UNDNAME3[keyMode] ?: SystemThemePreset(SYSTEM_NAMES[2], Array(keyMode.coerceIn(4, 16)) { systemKey(2, it) }, kps(2), total(2))
            else -> PRESET_MINHLE[keyMode] ?: SystemThemePreset(SYSTEM_NAMES[0], Array(keyMode.coerceIn(4, 16)) { systemKey(0, it) }, kps(0), total(0))
        }
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