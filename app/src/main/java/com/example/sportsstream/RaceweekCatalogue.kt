package com.example.sportsstream

import org.jsoup.Jsoup
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

/** Published meeting intervals, not live timing, individual sessions or player URLs. */
class RaceweekCatalogue:CatalogueProvider {
    override val id="raceweek"
    override fun load(sport:String)=load(sport,null)
    override fun load(sport:String,day:LocalDate?):List<SportEvent> {
        if(sport!="motorsports") return emptyList()
        val events=all()
        return if(day==null) events else events.filter { CalendarDates.matches(sport,it.start,it.end,day) }
    }
    companion object {
        private var cache:Pair<Long,List<SportEvent>>?=null
        private fun page(url:String)=Jsoup.connect(url).userAgent("Mozilla/5.0").followRedirects(false).timeout(12000).maxBodySize(512_000).get()
        @Synchronized private fun all():List<SportEvent> {
            cache?.takeIf { System.currentTimeMillis()-it.first<3600_000 }?.let { return it.second }
            val index=page("https://raceweek.io/")
            val urls=index.select("a[href]").map { it.absUrl("href") }.filter { UrlPolicy.allowsResource(it,listOf("raceweek.io")) && Regex("^/[a-z0-9-]{1,20}/$").matches(java.net.URI(it).path) }.distinct().take(40)
            require(urls.isNotEmpty()) { "Raceweek series index unavailable" }
            val pool=java.util.concurrent.Executors.newFixedThreadPool(3)
            try {
                val tasks=urls.map { url -> pool.submit<List<SportEvent>> { parse(page(url).outerHtml()) } }
                val events=tasks.flatMap { it.get(120,java.util.concurrent.TimeUnit.SECONDS) }.distinctBy { it.id }
                cache=System.currentTimeMillis() to events;return events
            } catch(e:InterruptedException) { Thread.currentThread().interrupt();throw e }
            finally { pool.shutdownNow() }
        }
        fun parse(html:String,received:Long=System.currentTimeMillis()):List<SportEvent> {
            val doc=Jsoup.parse(html);val out=mutableListOf<SportEvent>()
            doc.select("script[type=application/ld+json]").forEach { script -> runCatching {
                val root=JSONObject(script.data());val graph=root.optJSONArray("@graph") ?: return@runCatching
                for(i in 0 until graph.length()) runCatching {
                    val e=graph.getJSONObject(i);if(e.optString("@type")!="SportsEvent") return@runCatching
                    val url=e.getString("url");require(UrlPolicy.allowsResource(url,listOf("raceweek.io")))
                    val name=e.getString("name");val start=OffsetDateTime.parse(e.getString("startDate")).toEpochSecond()
                    val end=OffsetDateTime.parse(e.getString("endDate")).toEpochSecond();require(end>start)
                    // Even old completed meetings can carry EventScheduled in this site's JSON-LD.
                    val status=when { e.optString("eventStatus").endsWith("EventCancelled")->"cancelled";e.optString("eventStatus").endsWith("EventPostponed")->"postponed";else->"unknown" }
                    out+=SportEvent("raceweek|$url","raceweek",name,name.substringBefore(" — "),start,end,"motorsports","",receivedAt=received,status=status)
                }
            } }
            return out.distinctBy { it.id }
        }
    }
}
