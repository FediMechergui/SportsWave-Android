package com.example.sportsstream

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Public English scoreboard data; not a supported commercial API or a playback source. */
class EspnCatalogue : CatalogueProvider {
    override val id = "espn"
    override fun load(sport: String): List<SportEvent> = load(sport, null)
    override fun load(sport: String, day: LocalDate?): List<SportEvent> {
        val routes = routes(sport)
        if (routes.isEmpty()) return emptyList()
        val anchor = day ?: LocalDate.now()
        val dates = (-1L..1L).map { anchor.plusDays(it) }.let { if(sport == "motorsports") it.distinctBy { date -> date.year } else it }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        try {
            val tasks = routes.flatMap { route -> dates.map { date -> pool.submit<List<SportEvent>> {
                val key = if(sport == "motorsports") "$route/${date.year}" else "$route/$date"
                val cached = synchronized(cache) { cache[key] }
                if (cached != null && System.currentTimeMillis() - cached.first < 300_000) cached.second else {
                    // Racing scoreboards publish season calendars; day queries can omit entire meetings.
                    val queryDate = if(sport == "motorsports") date.year.toString() else date.format(DateTimeFormatter.BASIC_ISO_DATE)
                    val response = Jsoup.connect("https://site.api.espn.com/apis/site/v2/sports/$route/scoreboard")
                        .data("dates", queryDate, "limit", "1000", "lang", "en")
                        .ignoreContentType(true).followRedirects(false).timeout(10000).maxBodySize(4_000_000).execute()
                    val events = parse(response.body(), sport)
                    synchronized(cache) { cache[key] = System.currentTimeMillis() to events; if(cache.size > 128) cache.remove(cache.keys.first()) }
                    events
                }
            } } }
            val results = tasks.map { task ->
                try { Result.success(task.get(40, java.util.concurrent.TimeUnit.SECONDS)) }
                catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw e }
                catch (e: Exception) { Result.failure(e) }
            }
            if(results.all { it.isFailure }) error("ESPN scoreboard unavailable")
            // Do not replace a saved source with an incomplete window after a request failure.
            if(results.any { it.isFailure }) error("ESPN calendar window incomplete; saved results retained")
            return results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }.filter {
                val date = Instant.ofEpochSecond(it.start).atZone(ZoneId.systemDefault()).toLocalDate()
                date in anchor.minusDays(1)..anchor.plusDays(1) || (sport == "golf" && date <= anchor.plusDays(1) && it.end > 0 && Instant.ofEpochSecond(it.end - 1).atZone(ZoneId.systemDefault()).toLocalDate() >= anchor.minusDays(1))
            }
        } finally { pool.shutdownNow() }
    }

    fun parse(json: String, sport: String, received: Long = System.currentTimeMillis()): List<SportEvent> {
        val root = JSONObject(json)
        require(root.has("events")) { "Missing ESPN events" }
        val league = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name").orEmpty()
        val out = mutableListOf<SportEvent>()
        val events = root.getJSONArray("events")
        for(i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val groups = event.optJSONArray("groupings")
            val competitions = mutableListOf<Pair<JSONObject,String>>()
            fun add(array: JSONArray?, suffix: String = "") { if(array != null) for(j in 0 until array.length()) array.optJSONObject(j)?.let { competitions += it to suffix } }
            add(event.optJSONArray("competitions"))
            if(groups != null) for(g in 0 until groups.length()) groups.optJSONObject(g)?.let { add(it.optJSONArray("competitions"), it.optJSONObject("grouping")?.optString("displayName").orEmpty()) }
            if(competitions.isEmpty()) competitions += event to ""
            for((competition,group) in competitions) runCatching {
                val cid = competition.getString("id")
                val start = java.time.OffsetDateTime.parse(competition.optString("date").ifBlank { event.getString("date") }).toEpochSecond()
                val sides = competition.optJSONArray("competitors") ?: JSONArray()
                val paired = sides.length() == 2 && sport !in setOf("motorsports", "golf", "horse-racing", "cycling")
                val first = (0 until sides.length()).mapNotNull { sides.optJSONObject(it) }
                val home = first.firstOrNull { it.optString("homeAway") == "home" } ?: first.firstOrNull()
                val away = first.firstOrNull { it.optString("homeAway") == "away" } ?: first.lastOrNull()
                fun name(side: JSONObject?): String = side?.optJSONObject("team")?.optString("displayName")
                    ?: side?.optJSONObject("athlete")?.optString("displayName").orEmpty()
                fun logo(side: JSONObject?): String = side?.optJSONObject("team")?.optString("logo").orEmpty()
                val state = (competition.optJSONObject("status") ?: event.optJSONObject("status"))?.optJSONObject("type")
                val raw = state?.optString("name").orEmpty()
                val status = when {
                    raw.contains("POSTPON",true) -> "postponed"
                    raw.contains("CANCEL",true) -> "cancelled"
                    state?.optBoolean("completed") == true -> "finished"
                    state?.optString("state") == "in" -> "live"
                    state?.optString("state") == "pre" -> "scheduled"
                    else -> "unknown"
                }
                val scored = paired && status in setOf("live","finished")
                val h = if(paired) name(home) else ""
                val a = if(paired) name(away) else ""
                val session = competition.optJSONObject("type")?.optString("abbreviation").orEmpty()
                val title = if(h.isNotBlank() && a.isNotBlank()) "$h - $a" else event.optString("name") + if(session.isNotBlank()) " ($session)" else ""
                if(title.isBlank()) return@runCatching
                val competitionName = competition.optString("altGameNote").ifBlank { league.ifBlank { event.optString("name") } } + if(group.isNotBlank()) " / $group" else ""
                val publishedEnd = runCatching { java.time.OffsetDateTime.parse(competition.optString("endDate").ifBlank { event.optString("endDate") }).toEpochSecond() }.getOrDefault(0L)
                out += SportEvent("espn|$sport|${event.getString("id")}|$cid", id, title, competitionName, start,
                    if(publishedEnd > start) publishedEnd else if(status == "finished") start else 0L, sport, "", h, a,
                    if(scored) home?.optString("score")?.toIntOrNull() else null,
                    if(scored) away?.optString("score")?.toIntOrNull() else null,
                    if(paired) logo(home) else "", if(paired) logo(away) else "", received, status,
                    if(paired) emptyList() else first.take(100).mapNotNull { side ->
                        val entrantName = name(side)
                        if(entrantName.isBlank()) null else EventEntrant(entrantName,
                            // ESPN order is a classification only once the session has started.
                            if(status in setOf("live","finished")) side.optInt("order").takeIf { it > 0 } else null,
                            side.optString("score"), logo(side))
                    })
            }
        }
        return out.distinctBy { it.id }
    }
    companion object {
        private val cache = linkedMapOf<String,Pair<Long,List<SportEvent>>>()
        fun routes(sport: String): List<String> = when(sport) {
            "football" -> listOf("soccer/all")
            "basketball" -> listOf("basketball/nba","basketball/wnba","basketball/mens-college-basketball","basketball/womens-college-basketball")
            "american-football" -> listOf("football/nfl","football/college-football")
            "baseball" -> listOf("baseball/mlb")
            "ice-hockey" -> listOf("hockey/nhl")
            "tennis" -> listOf("tennis/atp","tennis/wta")
            "golf" -> listOf("golf/pga","golf/lpga")
            "motorsports" -> listOf("racing/f1","racing/nascar-premier","racing/irl")
            "mma-events" -> listOf("mma/ufc")
            "aussie-rules" -> listOf("australian-football/afl")
            "rugby" -> listOf("rugby/164205","rugby/267979","rugby/244293","rugby-league/3")
            else -> emptyList()
        }
    }
}
