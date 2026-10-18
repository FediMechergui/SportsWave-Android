package com.example.sportsstream

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Arabic published fixture cards; match pages are metadata, never assumed to be players. */
class FilGoalCatalogue : CatalogueProvider {
    override val id = "filgoal"
    override fun load(sport: String): List<SportEvent> = load(sport, null)
    override fun load(sport: String, day: LocalDate?): List<SportEvent> {
        if(sport != "football") return emptyList()
        val date = day ?: LocalDate.now()
        val response = Jsoup.connect("https://www.filgoal.com/matches")
            .userAgent("Mozilla/5.0").data("date", date.toString())
            .timeout(12000).maxBodySize(2_000_000).followRedirects(false).execute()
        return parse(response.body(), date)
    }
    fun parse(html: String, requested: LocalDate, received: Long = System.currentTimeMillis()): List<SportEvent> {
        val doc = Jsoup.parse(html, "https://www.filgoal.com/")
        val viewer = doc.selectFirst("#match-list-viewer") ?: error("FilGoal fixture markup unavailable")
        require(viewer.attr("data-current-date") == requested.toString()) { "FilGoal returned a different calendar date" }
        val out = mutableListOf<SportEvent>()
        viewer.select(".mc-block").forEach { block ->
            val league = block.selectFirst("h6 span")?.text().orEmpty()
            if(listOf("كرة الصالات", "كرة السلة", "كرة اليد", "كرة الطائرة", "تنس").any { league.contains(it) }) return@forEach
            block.select(".cin_cntnr").forEach { card -> runCatching {
                val link = card.selectFirst("a[href^=/matches/]")?.attr("href").orEmpty()
                val matchId = Regex("^/matches/(\\d+)(?:/|$)").find(link)?.groupValues?.get(1) ?: return@runCatching
                val home = card.selectFirst(".f strong")?.text().orEmpty()
                val away = card.selectFirst(".s strong")?.text().orEmpty()
                if(home.isBlank() || away.isBlank()) return@runCatching
                val time = Regex("\\b(\\d{2}-\\d{2}-\\d{4} - \\d{2}:\\d{2})\\b")
                    .find(card.select(".match-aux").text())?.groupValues?.get(1) ?: return@runCatching
                val start = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("dd-MM-yyyy - HH:mm"))
                    .atZone(ZoneId.of("Africa/Cairo")).toEpochSecond()
                val label = card.selectFirst(".status")?.text().orEmpty()
                val status = when {
                    label.contains("انتهت") -> "finished"
                    label.contains("تأجيل") || label.contains("مؤجل") -> "postponed"
                    label.contains("إلغاء") || label.contains("ملغ") -> "cancelled"
                    label.contains("لم تبدأ") -> "scheduled"
                    label.contains("الشوط") || label.contains("استراحة") || label.contains("مباشر") -> "live"
                    else -> "unknown"
                }
                val scored = status in setOf("finished", "live")
                fun logo(side: String): String {
                    val img = card.selectFirst(".$side img") ?: return ""
                    val value = img.attr("data-src").ifBlank { img.attr("src") }
                    val url = if(value.startsWith("//")) "https:$value" else value
                    return url.takeIf { UrlPolicy.allowsResource(it, listOf("semedia.filgoal.com")) }.orEmpty()
                }
                out += SportEvent("filgoal|$matchId", id, "$home - $away", league, start,
                    if(status == "finished") start else 0L, "football", "", home, away,
                    if(scored) card.selectFirst(".f b")?.text()?.trim()?.toIntOrNull() else null,
                    if(scored) card.selectFirst(".s b")?.text()?.trim()?.toIntOrNull() else null,
                    logo("f"), logo("s"), received, status)
            } }
        }
        return out.distinctBy { it.id }
    }
}
