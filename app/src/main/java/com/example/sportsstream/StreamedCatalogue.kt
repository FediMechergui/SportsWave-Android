package com.example.sportsstream

import org.json.JSONArray
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/** Public documented API: source IDs come only from returned records, never enumerated. */
class StreamedCatalogue : CatalogueProvider {
    override val id="streamed"
    override fun load(sport:String):List<SportEvent> = load(sport,null)
    override fun load(sport:String,day:LocalDate?):List<SportEvent> = matches().filter {
        it.sport==sport && (day==null || CalendarDates.matches(sport,it.start,it.end,day))
    }
    companion object {
        private var cache:Pair<Long,String>?=null
        private val identifier=Regex("^[a-zA-Z0-9_-]{1,200}$")
        private fun get(path:String)=Jsoup.connect("https://streamed.pk$path").userAgent("Mozilla/5.0")
            .ignoreContentType(true).followRedirects(false).timeout(12000).maxBodySize(2_000_000).execute().body()
        @Synchronized private fun body():String {
            cache?.takeIf { System.currentTimeMillis()-it.first<60_000 }?.let { return it.second }
            return get("/api/matches/all").also { JSONArray(it);cache=System.currentTimeMillis() to it }
        }
        fun matches():List<SportEvent> = parse(body())
        fun channels():List<Pair<String,String>> {
            val data=JSONArray(body())
            return (0 until minOf(data.length(),2000)).mapNotNull { i ->
                val e=data.optJSONObject(i) ?: return@mapNotNull null
                val name=e.optString("title");val mid=e.optString("id")
                if(!identifier.matches(mid) || !Regex("channel|(?:sky|fox|bein|tnt|espn)\\s",RegexOption.IGNORE_CASE).containsMatchIn(name)) null
                else name to "https://streamed.pk/watch/$mid"
            }.distinctBy { it.second }
        }
        fun parse(json:String,received:Long=System.currentTimeMillis()):List<SportEvent> {
            val data=JSONArray(json)
            return (0 until minOf(data.length(),2000)).mapNotNull { i -> runCatching {
                val e=data.getJSONObject(i);val mid=e.getString("id");require(identifier.matches(mid))
                val title=e.getString("title").trim();val category=e.getString("category")
                val home=e.optJSONObject("teams")?.optJSONObject("home")?.optString("name").orEmpty()
                val away=e.optJSONObject("teams")?.optJSONObject("away")?.optString("name").orEmpty()
                val race=category=="motor-sports" && Regex("race|grand prix|practice|qualif|sprint",RegexOption.IGNORE_CASE).containsMatchIn(title)
                val combat=category=="fight" && title.isNotBlank()
                // Channel posts are available in the source directory; they must not become fixtures.
                if((home.isBlank() || away.isBlank()) && !race && !combat) return@runCatching null
                val sport=when(category) {
                    "hockey"->"ice-hockey";"motor-sports"->"motorsports";"afl"->"aussie-rules"
                    "fight"->when { title.contains("UFC",true)||title.contains("PFL",true)||title.contains("Bellator",true)->"mma-events"
                        title.contains("AEW",true)||title.contains("WWE",true)||title.contains("Wrestl",true)->"more-sports"
                        else->"boxing" }
                    "billiards"->if(title.contains("snooker",true)) "snooker" else "more-sports"
                    "other"->"more-sports"
                    else->category
                }
                val start=e.getLong("date")/1000;require(start>0)
                fun badge(side:String):String {
                    val handle=e.optJSONObject("teams")?.optJSONObject(side)?.optString("badge").orEmpty()
                    return if(handle.isNotBlank() && handle.length<1000) "https://streamed.pk/api/images/badge/${java.net.URLEncoder.encode(handle,"UTF-8")}.webp" else ""
                }
                SportEvent("streamed|$mid","streamed",title,"",start,0,sport,"https://streamed.pk/watch/$mid",
                    home,away,homeLogo=badge("home"),awayLogo=badge("away"),receivedAt=received)
            }.getOrNull() }.distinctBy { it.id }
        }
        fun players(matchId:String,rules:List<ProviderRule>):List<PlayerSource> {
            require(identifier.matches(matchId))
            // Refresh source references from the public match collection instead of trusting intent input.
            val data=JSONArray(body());val match=(0 until data.length()).mapNotNull { data.optJSONObject(it) }.firstOrNull { it.optString("id")==matchId } ?: return emptyList()
            val refs=match.optJSONArray("sources") ?: return emptyList()
            val found=mutableListOf<PlayerSource>();var success=false
            for(i in 0 until minOf(refs.length(),8)) {
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                val ref=refs.optJSONObject(i) ?: continue
                val source=ref.optString("source");val sid=ref.optString("id")
                if(!identifier.matches(source)||!identifier.matches(sid)) continue
                runCatching { parsePlayers(get("/api/stream/$source/$sid"),rules) }.onSuccess { players ->
                    found+=players;success=true
                }
            }
            check(success) { "Streamed player API unavailable" }
            return found.distinctBy { it.url }
        }
        fun parsePlayers(json:String,rules:List<ProviderRule>):List<PlayerSource> {
            val list=JSONArray(json)
            return (0 until minOf(list.length(),100)).mapNotNull { i ->
                val row=list.optJSONObject(i) ?: return@mapNotNull null
                val url=row.optString("embedUrl");val rule=rules.firstOrNull { it.accepts(url) } ?: return@mapNotNull null
                val raw=row.optString("language").trim().take(80)
                PlayerSource(url,rule.id,"Stream ${row.optInt("streamNo",i+1)} · ${if(row.optBoolean("hd")) "HD" else "SD"}",url,PlaybackMode.WEB_EMBED,raw)
            }.distinctBy { it.url }
        }
    }
}
