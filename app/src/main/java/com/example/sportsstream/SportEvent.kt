package com.example.sportsstream

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class SportEvent(val id: String, val catalogueId: String, val name: String, val league: String,
    val start: Long, val end: Long, val sport: String, val url: String,
    val home: String = "", val away: String = "",
    val homeScore: Int? = null, val awayScore: Int? = null,
    val homeLogo: String = "", val awayLogo: String = "",
    val receivedAt: Long = 0L, val status: String = "unknown")
object EventMatching {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    fun same(a: SportEvent, b: SportEvent): Boolean {
        if (a.catalogueId == b.catalogueId && a.id == b.id) return true
        if (a.start <= 0 || b.start <= 0 || abs(a.start - b.start) > 300 || a.sport != b.sport) return false
        if (a.home.isBlank() || a.away.isBlank() || b.home.isBlank() || b.away.isBlank()) return false
        return teamKey(a.home, a.sport) == teamKey(b.home, b.sport) &&
            teamKey(a.away, a.sport) == teamKey(b.away, b.sport) &&
            a.league.isNotBlank() && b.league.isNotBlank() && leagueKey(a.league, a.sport) == leagueKey(b.league, b.sport)
    }
    fun identity(event: SportEvent): String? = if (event.home.isBlank() || event.away.isBlank() || event.league.isBlank()) null
        else listOf(event.sport, teamKey(event.home, event.sport), teamKey(event.away, event.sport), leagueKey(event.league, event.sport)).joinToString("|")

    fun teamKey(name: String, sport: String): String {
        val key = normalize(Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), ""))
        if (sport != "football") return key
        return teamAliases[key] ?: key
    }
    private val teamAliases = mapOf(
        "fc bayern munchen" to "bayern munich", "bayern munchen" to "bayern munich",
        "fc liverpool" to "liverpool", "liverpool fc" to "liverpool",
        "fc barcelona" to "barcelona", "fc internazionale milano" to "inter milan",
        "internazionale" to "inter milan", "paris saint germain" to "psg",
        "manchester united fc" to "manchester united", "manchester city fc" to "manchester city",
        "borussia monchengladbach" to "borussia m gladbach"
    )
    fun leagueKey(name: String, sport: String): String {
        val key = normalize(name).replace(Regex("\\b20\\d{2}(?: (?:20)?\\d{2})?\\b"), "")
            .replace(Regex("^(?:europe|germany|england|spain|france|italy) "), "").trim()
        if (sport != "football") return key
        return when (key) {
            "champions league", "uefa champions league" -> "uefa champions league"
            "1 fußball bundesliga", "bundesliga", "germany bundesliga" -> "bundesliga"
            "laliga ea sports", "la liga", "laliga" -> "la liga"
            else -> key
        }
    }
}

data class MergedEvent(val event: SportEvent, val members: List<SportEvent>)
object EventAggregation {
    fun merge(events: List<SportEvent>): List<MergedEvent> {
        val groups = mutableListOf<MutableList<SportEvent>>()
        val identities = events.associateWith { EventMatching.identity(it) }
        events.sortedByDescending { it.receivedAt }.distinctBy { it.catalogueId to it.id }
            .sortedBy { it.catalogueId + it.id }.forEach { event ->
            val candidates = groups.filter { group -> group.all {
                (it.catalogueId == event.catalogueId && it.id == event.id) ||
                    (it.start > 0 && event.start > 0 && abs(it.start - event.start) <= 300 &&
                        identities[it] != null && identities[it] == identities[event])
            } }
            if (candidates.size == 1) candidates.single().add(event) else groups.add(mutableListOf(event))
        }
        return groups.map { members ->
            val ranked = members.sortedWith(compareByDescending<SportEvent> { it.receivedAt }
                .thenByDescending { it.homeScore != null && it.awayScore != null })
            val score = ranked.firstOrNull { it.homeScore != null && it.awayScore != null }
            val base = score ?: ranked.firstOrNull { it.catalogueId == "openliga" } ?: ranked.first()
            MergedEvent(base.copy(
                url = members.firstOrNull { it.url.isNotBlank() }?.url.orEmpty(),
                homeLogo = ranked.firstOrNull { it.homeLogo.isNotBlank() }?.homeLogo.orEmpty(),
                awayLogo = ranked.firstOrNull { it.awayLogo.isNotBlank() }?.awayLogo.orEmpty()), members)
        }
    }
}
