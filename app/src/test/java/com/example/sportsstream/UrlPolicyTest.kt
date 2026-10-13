package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class UrlPolicyTest {
    private val yasir = ProviderRule("yasirtv", "YasirTV", listOf("yasirtv.com", "*.yasirtv.com"), "/playerv5.php", "web", 40)
    @Test fun publishedYasirUrlPreserved() {
        val url = "https://abc.yasirtv.com/playerv5.php?match=4828484&key=a%2Bb"
        assertTrue(yasir.accepts(url))
        assertEquals(url, UrlPolicy.resolve("https://viprow.im/", url))
    }
    @Test fun rejectsLookalikesAndUnsafeUrls() {
        listOf("https://yasirtv.com.evil.org/playerv5.php", "https://evilyasirtv.com/playerv5.php",
            "http://yasirtv.com/playerv5.php", "javascript:alert(1)", "https://user@yasirtv.com/playerv5.php",
            "https://yasirtv.com/playerv5.php/extra").forEach { assertFalse(it, yasir.accepts(it)) }
    }
    @Test fun exactAndWildcardHosts() {
        assertFalse(UrlPolicy.hostMatches("sub.example.org", "example.org"))
        assertTrue(UrlPolicy.hostMatches("sub.example.org", "*.example.org"))
        assertFalse(UrlPolicy.hostMatches("example.org", "*.example.org"))
    }
    @Test fun adResourcesBlocked() {
        assertFalse(UrlPolicy.allowsResource("https://adsco.re/ad.js", yasir.resourceHosts))
        assertFalse(UrlPolicy.allowsResource("https://sub.adsco.re/frame", yasir.resourceHosts))
        assertTrue(UrlPolicy.allowsResource("https://abc.yasirtv.com/player.js", yasir.resourceHosts))
    }
    @Test fun labelsPreferTitleAndStripLigatures() {
        assertEquals("Server 1", UrlPolicy.label("Server 1", "open_in_new garbage", "YasirTV"))
        assertEquals("الخادم ٢", UrlPolicy.label("", "open_in_new  الخادم ٢", "YasirTV"))
        assertEquals("YasirTV", UrlPolicy.label("", "open_in_new", "YasirTV"))
    }
    @Test fun resolvesRelativeUrls() {
        assertEquals("https://example.org/player?id=2", UrlPolicy.resolve("https://example.org/event/", "../player?id=2"))
        assertNull(UrlPolicy.resolve("https://example.org", "javascript:alert(1)"))
    }
}
