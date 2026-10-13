package com.example.sportsstream

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class SportEvent(val id: String, val catalogueId: String, val name: String, val league: String,
    val start: Long, val end: Long, val sport: String, val url: String,
    val home: String = "", val away: String = "")
object EventMatching {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    fun same(a: SportEvent, b: SportEvent): Boolean {
        if (a.catalogueId == b.catalogueId) return a.id == b.id
        if (a.start <= 0 || b.start <= 0 || abs(a.start - b.start) > 300 || a.sport != b.sport) return false
        if (a.home.isBlank() || a.away.isBlank() || b.home.isBlank() || b.away.isBlank()) return false
        return normalize(a.home) == normalize(b.home) && normalize(a.away) == normalize(b.away) &&
            a.league.isNotBlank() && b.league.isNotBlank() && normalize(a.league) == normalize(b.league)
    }
}
