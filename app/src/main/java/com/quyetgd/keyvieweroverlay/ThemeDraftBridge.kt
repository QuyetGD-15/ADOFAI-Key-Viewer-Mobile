package com.quyetgd.keyvieweroverlay

/** An in-process transaction; only KeyViewerConfigActivity commits it. */
data class ThemeDraft(
    var mode: String,
    var basic: ThemeColorSet,
    var trail2: String,
    var shadow2: String,
    val advanced: LinkedHashMap<String, ThemeColorSet>,
    var bold: Boolean,
    var italic: Boolean,
    var underline: Boolean,
    var showCounters: Boolean,
    var performanceShadow: Boolean,
    var keySpacing: Int,
    var borderWidth: Int,
    var cornerRadius: Int,
    var keyRainEnabled: Boolean,
    var trailSpeed: Float,
    var trailLimitPx: Int,
    var shadowEnabled: Boolean,
    var activePreset: Int = 0,
    val presetColors: LinkedHashMap<String, ThemeColorSet> = LinkedHashMap(),
    val presetTrail2: LinkedHashMap<String, Pair<String, String>> = LinkedHashMap()
) {
    fun snapshot() = copy(
        advanced = LinkedHashMap(advanced),
        presetColors = LinkedHashMap(presetColors),
        presetTrail2 = LinkedHashMap(presetTrail2)
    )
}

/** Lives only while settings is open. A canceled Theme editor never replaces [draft]. */
object ThemeDraftBridge {
    var draft: ThemeDraft? = null
}
