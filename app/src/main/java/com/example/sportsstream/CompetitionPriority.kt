package com.example.sportsstream

/** Editorial defaults, not an official ranking. Youth and women's events retain their identities. */
object CompetitionPriority {
    fun tier(competition: String): Int {
        val name = EventMatching.normalize(competition)
        return when {
            Regex("world (cup|championship)|olympic|كأس العالم").containsMatchIn(name) -> 0
            Regex("european championship|uefa euro|copa america|africa cup of nations|asian cup|continental championship").containsMatchIn(name) -> 1
            Regex("champions league|libertadores|europa league|conference league").containsMatchIn(name) -> 2
            Regex("premier league|la liga|laliga|bundesliga|serie a|ligue 1|nba|nfl|nhl|mlb|grand slam|wimbledon|formula 1").containsMatchIn(name) -> 3
            else -> 4
        }
    }
}
