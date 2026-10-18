package com.example.sportsstream

import java.util.Locale

/** Only explicit source language metadata is evidence; channel names and page language are not. */
object CommentaryLanguage {
    /** Unlabelled players are visible by user request; never infer their commentary. */
    fun display(value:String?):String = allowed(value) ?: when {
        value.isNullOrBlank() || value.equals("Main",true) || value.equals("unknown",true) -> "Language unverified"
        value.lowercase(Locale.ROOT) in setOf("fr","fra","french","français") -> "French"
        value.lowercase(Locale.ROOT) in setOf("es","spa","spanish","español") -> "Spanish"
        else -> "Language unverified · ${value.take(60)}"
    }
    fun allowed(value: String?): String? {
        val code = value?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        return when {
            code in setOf("english - apple tv","english - espn","english - tnt","english - scripps sports","english - tbs","english - nowtv","english - kayo","english - pdc.tv") -> "English"
            code in setOf("ar", "ara", "arabic", "العربية", "عربي") || Regex("^ar-[a-z]{2}$").matches(code) -> "Arabic"
            code in setOf("en", "eng", "english", "الإنجليزية", "الانجليزية") || Regex("^en-[a-z]{2}$").matches(code) -> "English"
            else -> null
        }
    }
}
