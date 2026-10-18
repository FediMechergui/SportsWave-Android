package com.example.sportsstream

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.Instant

/** Public Arabic cards with explicit UTC kickoffs. Estimated CSS live states are not verified scores. */
class YallaCatalogue:CatalogueProvider {
    override val id="yalla"
    override fun load(sport:String)=load(sport,null)
    override fun load(sport:String,day:LocalDate?):List<SportEvent> {
        if(sport!="football") return emptyList()
        val today=LocalDate.now();val path=when(day) { null,today->"/ar/";today.minusDays(1)->"/ar/yesterday-matches/";today.plusDays(1)->"/ar/tomorrow-matches/";else->return emptyList() }
        val html=Jsoup.connect("https://yallashootkoora.online$path").userAgent("Mozilla/5.0").timeout(12000).maxBodySize(2_000_000).followRedirects(false).execute().body()
        return parse(html).filter { day==null || Instant.ofEpochSecond(it.start).atZone(ZoneId.systemDefault()).toLocalDate()==day }
    }
    fun parse(html:String,received:Long=System.currentTimeMillis()):List<SportEvent> = Jsoup.parse(html).select("a.AY_Match[data-kickoff-utc][href]").mapNotNull { c -> runCatching {
        val url=c.attr("href");require(UrlPolicy.allowsResource(url,listOf("yallashootkoora.online")))
        val home=c.selectFirst(".TM1 .TM_Name")?.text().orEmpty();val away=c.selectFirst(".TM2 .TM_Name")?.text().orEmpty();require(home.isNotBlank()&&away.isNotBlank())
        val start=OffsetDateTime.parse(c.attr("data-kickoff-utc")).toEpochSecond()
        fun logo(side:String)=c.selectFirst(".$side img")?.attr("src")?.takeIf { UrlPolicy.allowsResource(it,listOf("yallashootkoora.online")) }.orEmpty()
        SportEvent("yalla|${c.attr("data-match-id")}",id,"$home - $away",c.select(".MT_Info li").getOrNull(2)?.text().orEmpty(),start,0,"football",url,home,away,
            homeLogo=logo("TM1"),awayLogo=logo("TM2"),receivedAt=received)
    }.getOrNull() }.distinctBy { it.id }
}
