package com.example.sportsstream

import java.util.Locale

/** Only explicit source language metadata is evidence; channel names and page language are not. */
object CommentaryLanguage {
    fun allowed(value: String?): String? {
        val code = value?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        return when {
            code in setOf("ar", "ara", "arabic", "العربية", "عربي") || Regex("^ar-[a-z]{2}$").matches(code) -> "Arabic"
            code in setOf("en", "eng", "english", "الإنجليزية", "الانجليزية") || Regex("^en-[a-z]{2}$").matches(code) -> "English"
            else -> null
        }
    }
}
