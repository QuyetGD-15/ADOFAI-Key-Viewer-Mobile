package com.quyetgd.keyvieweroverlay

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class LanguageOption(
    val tag: String,
    val displayName: String,
    val flag: String
) {
    val label: String get() = "$flag  $displayName"
}

object SupportedLanguages {
    // Display order is part of the product requirement.
    val all = listOf(
        LanguageOption("en", "English", "🇬🇧"),
        LanguageOption("vi", "Tiếng Việt", "🇻🇳"),
        LanguageOption("ko", "한국어", "🇰🇷"),
        LanguageOption("zh", "中文", "🇨🇳"),
        LanguageOption("ja", "日本語", "🇯🇵"),
        LanguageOption("ru", "Русский", "🇷🇺"),
        LanguageOption("es", "Español", "🇪🇸"),
        LanguageOption("tr", "Türkçe", "🇹🇷")
    )

    fun currentTag(context: Context): String =
        context.getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            .getString("app_language", "en") ?: "en"

    fun apply(context: Context, tag: String) {
        context.getSharedPreferences("KeyViewerPrefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
