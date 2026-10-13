package com.example.sportsstream

import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

interface CatalogueProvider {
    val id: String
    fun load(sport: String): List<SportEvent>
}
/** Static public metadata only; does not execute the site's scripts or infer events from channels. */
class KorahLiveCatalogue(private val root: String = "https://q.korahlive.online/"): CatalogueProvider {
    override val id = "korahlive"
    override fun load(sport: String): List<SportEvent> {
        var url = root
        repeat(4) {
            require(UrlPolicy.allowsResource(url, listOf("q.korahlive.online"))) { "Unsupported catalogue redirect" }
            val response = Jsoup.connect(url).timeout(12000).maxBodySize(2_000_000)
                .followRedirects(false).ignoreHttpErrors(true).execute()
            if (response.statusCode() in 300..399) {
                url = UrlPolicy.resolve(url, response.header("Location").orEmpty())
                    ?: error("Invalid catalogue redirect")
            } else {
                check(response.statusCode() == 200) { "Catalogue HTTP error" }
                return parse(response.body(), url, sport)
            }
        }
        error("Too many catalogue redirects")
    }
    fun parse(html: String, base: String, sport: String): List<SportEvent> {
        val doc = Jsoup.parse(html, base)
        val events = mutableListOf<SportEvent>()
        fun walk(value: Any?) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
                is JSONObject -> {
                    val type = value.opt("@type")
                    val sportsEvent = type == "SportsEvent" || (type is JSONArray && (0 until type.length()).any { type.optString(it) == "SportsEvent" })
                    if (sportsEvent) {
                        val url = UrlPolicy.resolve(base, value.optString("url"))
                        val name = value.optString("name")
                        val start = runCatching { OffsetDateTime.parse(value.optString("startDate")).toEpochSecond() }.getOrDefault(0)
                        val end = runCatching { OffsetDateTime.parse(value.optString("endDate")).toEpochSecond() }.getOrDefault(0)
                        val publishedSport = value.optString("sport")
                        if (url != null && UrlPolicy.allowsResource(url, listOf("q.korahlive.online")) &&
                            name.isNotBlank() && start > 0 && publishedSport.equals(sport, true)) {
                            events += SportEvent(url, id, name, value.optJSONObject("superEvent")?.optString("name").orEmpty(),
                                start, end, sport, url, value.optJSONObject("homeTeam")?.optString("name").orEmpty(),
                                value.optJSONObject("awayTeam")?.optString("name").orEmpty())
                        }
                    }
                    value.keys().forEach { walk(value.opt(it)) }
                }
            }
        }
        doc.select("script[type=application/ld+json]").forEach {
            runCatching { walk(org.json.JSONTokener(it.data()).nextValue()) }
        }
        return events.distinctBy { it.url }
    }
}
