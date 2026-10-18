package com.example.sportsstream

data class WatchResource(val name: String, val url: String, val note: String)

/** Service links, not assertions that a specific event or commentary track is available. */
object WatchResources {
    fun forSport(sport: String): List<WatchResource> = buildList {
        add(WatchResource("Watania · Tunisia live", "https://www.tunisiatv.tn/ar/live", "Arabic broadcaster · check programme and availability"))
        add(WatchResource("Diwan Sport · Tunisia", "https://www.diwansport.com/", "Tunisian sports service · subscription and event availability apply"))
        add(WatchResource("Yacine TV - website", "https://yacinetv.website/", "Arabic website - channel availability and playback unverified"))
        add(WatchResource("beIN Match - website", "https://beinmatchatv.com/", "Arabic football website - channel availability and playback unverified"))
        add(WatchResource("Yacine TV - alternate website", "https://www.yacinetv.dev/", "Separate Arabic website - published channel selector - playback unverified"))
        add(WatchResource("beIN SPORTS · TV guide", "https://www.beinsports.com/en-mena/tv-guide", "English / Arabic guide · regional rights apply"))
        add(WatchResource("beIN CONNECT", "https://connect.bein.com/", "Arabic / English service · subscription and region apply"))

        if(sport == "motorsports") add(WatchResource("F1 TV", "https://f1tv.formula1.com/", "English service · subscription and region apply"))
        if(sport == "horse-racing") {
            add(WatchResource("Racing TV", "https://www.racingtv.com/", "English racecards and service · subscription and region apply"))
            add(WatchResource("Dubai Racing", "https://www.dubairacing.ae/", "Arabic broadcaster · check programme and availability"))
        }
        if(sport == "basketball") add(WatchResource("NBA League Pass", "https://www.nba.com/watch/league-pass-stream", "English service · subscription and regional blackouts apply"))
        if(sport == "tennis") add(WatchResource("Tennis TV", "https://www.tennistv.com/", "English ATP service · subscription and region apply"))
        if(sport == "golf") add(WatchResource("PGA TOUR · watch", "https://www.pgatour.com/watch", "English coverage information · regional rights apply"))
        if(sport == "rugby") add(WatchResource("RugbyPass TV", "https://rugbypass.tv/", "English service · event and region restrictions apply"))
        if(sport == "volleyball") add(WatchResource("Volleyball World TV", "https://tv.volleyballworld.com/", "English service · subscription and region apply"))
        if(sport == "handball") add(WatchResource("EHFTV", "https://ehftv.com/", "English service · check event audio and regional availability"))
        if(sport == "baseball") add(WatchResource("MLB.TV", "https://www.mlb.com/live-stream-games", "English service · subscription and blackouts apply"))
        if(sport == "ice-hockey") add(WatchResource("NHL · how to watch", "https://www.nhl.com/info/how-to-watch-and-stream-nhl-games", "English broadcast information · regional rights apply"))
        if(sport == "american-football") add(WatchResource("NFL Game Pass · international", "https://support.nfl.com/hc/en-us/articles/40568374746644-How-to-access-LIVE-NFL-Regular-Season-games", "English official viewing information · subscription and region apply"))
        if(sport == "mma-events") add(WatchResource("UFC Fight Pass", "https://ufcfightpass.com/", "English service · event rights and region apply"))
        if(sport == "snooker") add(WatchResource("WST Play", "https://www.wst.tv/live", "English service · subscription and region apply"))
    }
}
