package com.example.sportsstream

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class SportEvent(val id: String, val catalogueId: String, val name: String, val league: String,
    val start: Long, val end: Long, val sport: String, val url: String,
    val home: String = "", val away: String = "",
    val homeScore: Int? = null, val awayScore: Int? = null,
    val homeLogo: String = "", val awayLogo: String = "",
    val receivedAt: Long = 0L, val status: String = "unknown",
    val entrants: List<EventEntrant> = emptyList())

/** Published classification/order only; an entry list is not a live timing feed. */
data class EventEntrant(val name: String, val position: Int? = null, val result: String = "", val logo: String = "")
object EventMatching {
    private val nonWord = Regex("[^\\p{L}\\p{N} ]")
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{M}+")
    private val season = Regex("\\b20\\d{2}(?: (?:20)?\\d{2})?\\b")
    private val countryPrefix = Regex("^(?:europe|germany|england|spain|france|italy) ")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(nonWord, " ")
        .replace(whitespace, " ").trim()
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
        val key = normalize(Normalizer.normalize(name, Normalizer.Form.NFD).replace(combiningMarks, ""))
        if (sport != "football") return key
        return teamAliases[key] ?: key
    }
    fun teamSpellings(name: String, sport: String): Set<String> {
        val key = teamKey(name,sport)
        return if(sport == "football") teamAliases.filterValues { it == key }.keys + key else setOf(key)
    }
    private val teamAliases = mapOf(
        "fc bayern munchen" to "bayern munich", "bayern munchen" to "bayern munich",
        "fc liverpool" to "liverpool", "liverpool fc" to "liverpool",
        "fc barcelona" to "barcelona", "fc internazionale milano" to "inter milan",
        "internazionale" to "inter milan", "paris saint germain" to "psg",
        "manchester united fc" to "manchester united", "manchester city fc" to "manchester city",
        "borussia monchengladbach" to "borussia m gladbach",
        "الترجي" to "esperance tunis", "الترجي التونسي" to "esperance tunis", "esperance sportive de tunis" to "esperance tunis", "esperance de tunis" to "esperance tunis",
        "الإفريقي" to "club africain", "الافريقي" to "club africain", "النادي الإفريقي" to "club africain",
        "النجم الساحلي" to "etoile du sahel", "etoile sportive du sahel" to "etoile du sahel",
        "الصفاقسي" to "cs sfaxien", "النادي الصفاقسي" to "cs sfaxien", "sfaxien" to "cs sfaxien",
        "الملعب التونسي" to "stade tunisien", "الاتحاد المنستيري" to "us monastir", "monastir" to "us monastir",
        "الترجي الجرجيسي" to "es zarzis", "esperance zarzis" to "es zarzis",
        "البنزرتي" to "ca bizertin", "الأولمبي الباجي" to "olympique beja", "الاولمبي الباجي" to "olympique beja",
        "نجم المتلوي" to "es metlaoui", "اتحاد بن قردان" to "us ben guerdane",
        "مستقبل المرسى" to "as marsa", "مستقبل سليمان" to "as soliman",
        "ريال مدريد" to "real madrid", "برشلونة" to "barcelona", "ليفربول" to "liverpool",
        "مانشستر سيتي" to "manchester city", "مانشستر يونايتد" to "manchester united",
        "أرسنال" to "arsenal", "ارسنال" to "arsenal", "تشيلسي" to "chelsea",
        "بايرن ميونيخ" to "bayern munich", "باريس سان جيرمان" to "psg",
        "إنتر" to "inter milan", "يوفنتوس" to "juventus", "تونس" to "tunisia"
    ).mapKeys { normalize(Normalizer.normalize(it.key, Normalizer.Form.NFD).replace(combiningMarks, "")) }
    fun leagueKey(name: String, sport: String): String {
        val key = normalize(name).replace(season, "")
            .replace(countryPrefix, "").trim()
        if (sport != "football") return key
        return when (key) {
            "الرابطة التونسية المحترفة", "الدوري التونسي", "tunisian ligue professionnelle 1", "tunisia ligue 1" -> "tunisia ligue 1"
            "دوري أبطال أوروبا", "دوري ابطال اوروبا" -> "uefa champions league"
            "الدوري الإنجليزي الممتاز", "الدوري الانجليزي الممتاز", "english premier league" -> "premier league"
            "الدوري الإسباني", "الدوري الاسباني", "spanish laliga" -> "la liga"
            "الدوري الإيطالي", "الدوري الايطالي", "italian serie a" -> "serie a"
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
        val byIdentity = mutableMapOf<String, MutableList<MutableList<SportEvent>>>()
        events.sortedByDescending { it.receivedAt }.distinctBy { it.catalogueId to it.id }
            .sortedBy { it.catalogueId + it.id }.forEach { event ->
            val key = EventMatching.identity(event)
            val candidates = if(key == null) emptyList() else byIdentity[key].orEmpty().filter { group ->
                group.all { it.start > 0 && event.start > 0 && abs(it.start - event.start) <= 300 }
            }
            if(candidates.size == 1) candidates.single().add(event)
            else {
                val group = mutableListOf(event)
                groups.add(group)
                if(key != null) byIdentity.getOrPut(key) { mutableListOf() }.add(group)
            }
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
