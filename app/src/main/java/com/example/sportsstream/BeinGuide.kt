package com.example.sportsstream

import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.ZoneId

data class Broadcast(val id: String, val title: String, val channel: String, val start: Long,
    val end: Long, val live: Boolean, val replay: Boolean, val category: String, val watchUrl: String)

/** Official EPG, not a scoreboard: airtime must never replace a fixture's kickoff or score. */
object BeinGuide {
    private val channelName = Regex("^beIN (?:SPORTS(?: (?:[1-9]|EN [12]|MAX [1-6]|XTRA [1-9]|[1-6] AFC|AFC|NEWS|NBA|4K HDR))?|4K)$")
    private var channels: Pair<Long, String>? = null
    private val cache = linkedMapOf<String, Pair<Long, List<Broadcast>>>()
    fun load(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Broadcast> {
        val key = "$day|$zone"
        synchronized(cache) { cache[key]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second } }
        val channelJson = synchronized(this) { channels?.takeIf { System.currentTimeMillis() - it.first < 3600_000 }?.second }
            ?: get("tv-channel", mapOf("region" to "en-mena")).also { synchronized(this) { channels = System.currentTimeMillis() to it } }
        val from = day.atStartOfDay(zone).toInstant()
        val until = day.plusDays(1).atStartOfDay(zone).toInstant()
        val json = get("tv-event", mapOf("startBefore" to until.toString(), "endAfter" to from.toString(), "limit" to "2000"))
        val rows = JSONObject(json)
        require(rows.getInt("count") <= rows.getJSONArray("rows").length()) { "Incomplete beIN schedule" }
        return parse(channelJson, json).filter { it.end > from.epochSecond && it.start < until.epochSecond }.also {
            synchronized(cache) { cache[key] = System.currentTimeMillis() to it; if(cache.size > 7) cache.remove(cache.keys.first()) }
        }
    }
    private fun get(path: String, params: Map<String,String>): String = Jsoup.connect("https://www.beinsports.com/api/opta/$path")
        .userAgent("Mozilla/5.0").data(params).ignoreContentType(true).followRedirects(false)
        .timeout(15000).maxBodySize(8_000_000).execute().body()

    fun parse(channelJson: String, eventJson: String): List<Broadcast> {
        val rows = JSONObject(channelJson).getJSONArray("rows")
        val channels = (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { c ->
            val name = c.optString("name")
            if(channelName.matches(name)) c.getString("id") to (name to c.optString("redirectUrl").trim()) else null
        } }.toMap()
        val events = JSONObject(eventJson).getJSONArray("rows")
        return (0 until events.length()).mapNotNull { i -> runCatching {
            val e = events.getJSONObject(i)
            val channel = channels[e.optString("channelId")] ?: return@runCatching null
            val start = java.time.OffsetDateTime.parse(e.getString("startDate")).toEpochSecond()
            val end = java.time.OffsetDateTime.parse(e.getString("endDate")).toEpochSecond()
            require(end > start)
            val title = e.getString("title").trim()
            if(title.isBlank() || title.equals("SIGN OFF",true)) return@runCatching null
            val url = channel.second.takeIf { UrlPolicy.allowsResource(it,listOf("beinconnect.app","connect.bein.com")) }.orEmpty()
            Broadcast(e.getString("id"),title,channel.first,start,end,e.optBoolean("live"),e.optBoolean("replay"),e.optString("category"),url)
        }.getOrNull() }.distinctBy { it.id }.sortedBy { it.start }
    }
    /** Both full team identities must occur; single short tokens cannot attach broadcasts to matches. */
    fun matches(broadcast: Broadcast, home: String, away: String): Boolean {
        if(home.isBlank() || away.isBlank()) return false
        val title = " ${EventMatching.teamKey(broadcast.title,"football")} "
        return listOf(home,away).all { team ->
            EventMatching.teamSpellings(team,"football").any { name -> name.length >= 3 && title.contains(" $name ") }
        }
    }
}
