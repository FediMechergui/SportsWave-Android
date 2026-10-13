package com.example.sportsstream
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PublicPageTest {
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
