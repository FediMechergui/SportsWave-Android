package com.example.sportsstream

/** Editorial defaults, not an official ranking. Youth and women's events retain their identities. */
object CompetitionPriority {
    private val cache = linkedMapOf<String,Int>()
    fun tier(competition: String): Int = synchronized(cache) {
        cache[competition] ?: calculate(competition).also { value ->
            cache[competition] = value
            if(cache.size > 256) cache.remove(cache.keys.first())
        }
    }
    private fun calculate(competition: String): Int {
        val name = EventMatching.normalize(competition)
        return when {
            listOf("tunisian", "tunisia ligue", "tunisia cup", "tunisian ligue", "الدوري التونسي", "كأس تونس", "الرابطة التونسية", "الرابطة المحترفة").any { name.contains(it) } -> 1
            listOf("كأس العالم", "كاس العالم", "أولمب", "اولمب").any { name.contains(it) } -> 0
            listOf("كأس أمم", "كاس امم", "كأس آسيا", "كوبا أمريكا", "بطولة أوروبا", "أمم أوروبا").any { name.contains(it) } -> 1
            listOf("دوري أبطال", "دوري ابطال", "كأس الكونفدرالية", "الدوري الأوروبي").any { name.contains(it) } -> 2
            listOf("الدوري الإنجليزي", "الدوري الإسباني", "الدوري الإيطالي", "الدوري الألماني", "الدوري الفرنسي").any { name.contains(it) } -> 3
            Regex("world (cup|championship)|olympic|كأس العالم").containsMatchIn(name) -> 0
            Regex("european championship|uefa euro|copa america|africa cup of nations|asian cup|continental championship").containsMatchIn(name) -> 1
            Regex("champions league|libertadores|europa league|conference league").containsMatchIn(name) -> 2
            Regex("premier league|la liga|laliga|bundesliga|serie a|ligue 1|nba|nfl|nhl|mlb|grand slam|wimbledon|formula 1").containsMatchIn(name) -> 3
            else -> 4
        }
    }
}
