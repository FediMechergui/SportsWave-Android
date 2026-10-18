package com.example.sportsstream

/** Editorial regional preference, independent of sport or commentary claims. */
object RegionalPreference {
    private val regionalCache = linkedMapOf<String, Int>()
    fun sourceRank(source: String): Int = when(source) {
        "filgoal" -> 0
        "yalla" -> 1
        "livekora", "korahlive" -> 2
        else -> 3
    }
    fun competitionRank(league: String): Int = synchronized(regionalCache) {
        regionalCache[league] ?: run {
            val name = EventMatching.normalize(league)
            val rank = if(listOf("tunisia", "tunisian", "تونس", "التونس", "الرابطة المحترفة").any { name.contains(it) }) 0 else 1
            regionalCache[league] = rank
            if(regionalCache.size > 256) regionalCache.remove(regionalCache.keys.first())
            rank
        }
    }
}
