package com.example.sportsstream
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PublicPageTest {
    @Test fun reviewedChannelRedirectWorksWithoutAllowingOtherHosts() {
        val hosts = listOf("pl.koralive1.cc", "pl.matchlivehd.com")
        assertEquals("https://pl.matchlivehd.com/bein4/", PublicPage.redirect(
            "https://pl.koralive1.cc/bein4/", "https://pl.matchlivehd.com/bein4/", hosts))
        for (target in listOf("https://pl.matchlivehd.com.evil.org/bein4/", "http://pl.matchlivehd.com/bein4/", "https://evil.org/")) {
            assertTrue(runCatching { PublicPage.redirect("https://pl.koralive1.cc/bein4/", target, hosts) }.isFailure)
        }
        assertTrue(runCatching { PublicPage.redirect("https://q.korahlive.online/", "https://pl.matchlivehd.com/", listOf("q.korahlive.online")) }.isFailure)
    }
    @Test fun commentaryLanguageUsesExplicitMetadataNotPageLanguage() {
        val html = """<html lang="en"><a href="https://yasirtv.com/playerv5.php?match=1" data-language="Arabic">Server 1</a><a href="https://yasirtv.com/playerv5.php?match=2&amp;lang=en">Server 2</a></html>"""
        val sources = PublicPage.players(Jsoup.parse(html, "https://q.korahlive.online/"), listOf(rule))
        assertEquals("Arabic", sources[0].language)
        assertNull(sources[1].language)
    }
    private val rule = ProviderRule("yasirtv", "YasirTV", listOf("yasirtv.com", "*.yasirtv.com"), "/playerv5.php", "web", 1)
    @Test fun allEmbeddedUrlsKeepOriginalKeysAndArabicLabels() {
        val html = """<a href="https://a.yasirtv.com/playerv5.php?match=1&amp;key=first%2Bkey" title="الخادم ١"><i>open_in_new</i>wrong label</a>
            <iframe src="https://a.yasirtv.com/playerv5.php?match=1&amp;key=second" title="الخادم ٢"></iframe>
            <iframe data-src="https://b.yasirtv.com/playerv5.php?match=2&amp;key=third" title="الخادم ٣"></iframe>
            <iframe src="https://a.yasirtv.com/playerv5.php?match=1&amp;key=second"></iframe>
            <iframe src="https://yasirtv.com.evil.org/playerv5.php?match=1"></iframe>"""
        val result = PublicPage.players(Jsoup.parse(html, "https://q.korahlive.online/event"), listOf(rule))
        assertEquals(3, result.size)
        assertEquals("الخادم ١", result[0].label)
        assertEquals("https://a.yasirtv.com/playerv5.php?match=1&key=first%2Bkey", result[0].url)
        assertEquals("الخادم ٢", result[1].label)
    }
    @Test fun scriptOnlyEmptyIframeDoesNotInventPlayer() {
        val html = """<iframe id="alba-iframe"></iframe><script>const url = 'https://a.yasirtv.com/playerv5.php?match=unresolved&key=fallback';</script>"""
        assertTrue(PublicPage.players(Jsoup.parse(html, "https://q.korahlive.online/event"), listOf(rule)).isEmpty())
    }
    @Test fun channelPostsDoNotBecomeEvents() {
        assertTrue(KorahLiveCatalogue().parse("<article><a href='/ch1.html'>Channel 1</a></article>", "https://q.korahlive.online/", "football").isEmpty())
    }
    @Test fun structuredEventRequiresPublishedUrlAndTime() {
        val html = """<script type="application/ld+json">{"@type":"SportsEvent","name":"صباح - سلافيا","sport":"football","startDate":"2026-10-13T19:00:00+01:00","url":"/event.html"}</script>"""
        val events = KorahLiveCatalogue().parse(html, "https://q.korahlive.online/", "football")
        assertEquals(1, events.size)
        assertEquals("https://q.korahlive.online/event.html", events[0].url)
    }
}
