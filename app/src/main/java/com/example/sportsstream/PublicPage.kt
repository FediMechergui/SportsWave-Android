package com.example.sportsstream

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PublicPage {
    fun load(url: String, hosts: List<String>): Document {
        var current = url
        repeat(4) {
            require(UrlPolicy.allowsResource(current, hosts)) { "Unapproved page or redirect" }
            val response = Jsoup.connect(current).timeout(12000).maxBodySize(2_000_000)
                .followRedirects(false).ignoreHttpErrors(true).execute()
            if (response.statusCode() in 300..399) {
                val location = response.header("Location") ?: error("Missing redirect location")
                current = UrlPolicy.resolve(current, location) ?: error("Invalid redirect")
            } else {
                check(response.statusCode() == 200) { "Page unavailable" }
                return response.parse()
            }
        }
        error("Too many redirects")
    }
    fun players(doc: Document, rules: List<ProviderRule>): List<PlayerSource> {
        val candidates = doc.select("a[href],iframe[src],iframe[data-src],[data-href],[data-url]").map { element ->
            val url = listOf("href", "src", "data-href", "data-url", "data-src").firstNotNullOfOrNull {
                element.attr(it).takeIf { value -> value.isNotBlank() }
            }.orEmpty()
            val copy = element.clone()
            copy.select("i,svg,img,.material-icons,.material-symbols-outlined,[aria-hidden=true]").remove()
            url to UrlPolicy.label(element.attr("title"), copy.text(), "")
        }
        return PlayerDiscovery.published(doc.location(), candidates, rules)
    }
}
