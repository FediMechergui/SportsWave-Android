package com.example.sportsstream

import org.jsoup.Jsoup
import java.time.OffsetDateTime

/** Published match cards only. Placeholder scores are deliberately ignored. */
class LivekoraCatalogue : CatalogueProvider {
    override val id = "livekora"
    override fun load(sport: String): List<SportEvent> {
        if (sport != "football") return emptyList()
        val doc = PublicPage.load("https://www.livekora.vip/", listOf("www.livekora.vip", "livekora.vip"))
        return parse(doc.outerHtml(), doc.location(), sport)
    }

    fun parse(html: String, base: String, sport: String): List<SportEvent> {
        if (sport != "football") return emptyList()
        return Jsoup.parse(html, base).select(".match-container").mapNotNull { card ->
            val home = card.selectFirst(".right-team .team-name")?.text().orEmpty()
            val away = card.selectFirst(".left-team .team-name")?.text().orEmpty()
            val start = runCatching {
                OffsetDateTime.parse(card.selectFirst(".date[data-start]")?.attr("data-start")).toEpochSecond()
            }.getOrNull() ?: return@mapNotNull null
            if (home.isBlank() || away.isBlank() || start <= 0) return@mapNotNull null
            val anchor = card.parents().firstOrNull { it.tagName() == "a" && it.hasAttr("href") }
                ?: return@mapNotNull null
            val url = UrlPolicy.resolve(base, anchor.attr("href")) ?: return@mapNotNull null
            if (!UrlPolicy.allowsResource(url, listOf("pl.koralive1.cc"))) return@mapNotNull null
            val league = card.select(".match-info li").getOrNull(2)?.text().orEmpty()
            // Channel URLs are shared across fixtures; never use them as event IDs.
            val eventId = "$id|$start|$home|$away|$league"
            SportEvent(eventId, id, "$home - $away", league, start, 0, sport, url, home, away)
        }.distinctBy { it.id }
    }
}
