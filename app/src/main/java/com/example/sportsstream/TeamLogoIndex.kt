package com.example.sportsstream

/** Reuse published logos across providers only for the same sport and exact reviewed identity. */
object TeamLogoIndex {
    private val logos = linkedMapOf<String,String>()
    private fun key(name: String, sport: String) = "$sport|${EventMatching.teamKey(name,sport)}"
    @Synchronized fun remember(events: List<SportEvent>) {
        events.forEach { e ->
            listOf(e.home to e.homeLogo,e.away to e.awayLogo).forEach { (team,logo) ->
                if(team.isNotBlank() && logo.isNotBlank() && UrlPolicy.allowsResource(logo,listOf("a.espncdn.com","semedia.filgoal.com","upload.wikimedia.org","i.imgur.com"))) {
                    logos[key(team,e.sport)] = logo
                }
            }
        }
        while(logos.size>4000) logos.remove(logos.keys.first())
    }
    @Synchronized fun find(name: String, sport: String): String = logos[key(name,sport)].orEmpty()
}
