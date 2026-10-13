package com.example.sportsstream
import org.junit.Assert.*
import org.junit.Test

class PlaybackTest {
    private val rule = ProviderRule("yasirtv", "YasirTV", listOf("yasirtv.com", "*.yasirtv.com"), "/playerv5.php", "web", 1)
    @Test fun discoveryDeduplicatesAndRejectsAds() {
        val sources = PlayerDiscovery.published("https://yasirtv.com/event", listOf(
            "/playerv5.php?match=1" to "Server 1", "/playerv5.php?match=1" to "Duplicate",
            "/playerv5.php?match=2" to "الخادم ٢", "https://adsco.re/ad" to "Ad"), listOf(rule))
        assertEquals(2, sources.size)
        assertEquals("Server 1", sources[0].label)
        assertEquals("الخادم ٢", sources[1].label)
    }
    @Test fun webAndNativeRouting() {
        val web = PlayerSource("1", "yasirtv", "Server", "https://yasirtv.com/playerv5.php?match=1", PlaybackMode.WEB_EMBED)
        assertTrue(WebStreamingProvider(rule).resolve(web) is PlaybackResult.Embedded)
        for ((id, mode, ext) in listOf(Triple("authorized-hls", PlaybackMode.HLS, "m3u8"), Triple("authorized-dash", PlaybackMode.DASH, "mpd"))) {
            val adapter = NativeStreamingProvider(id, mode)
            assertTrue(adapter.resolve(PlayerSource("1", id, "Native", "https://example.org/test.$ext", mode)) is PlaybackResult.Native)
            assertTrue(adapter.resolve(web) is PlaybackResult.Unavailable)
            assertFalse(adapter.recognizes("blob:https://example.org/video"))
            assertFalse(adapter.recognizes("https://example.org/player.html"))
        }
    }
    @Test fun noStreamProducesEmptyList() {
        assertTrue(PlayerDiscovery.published("https://example.org/", emptyList(), listOf(rule)).isEmpty())
    }
    @Test fun normalizationRetainsArabic() {
        assertEquals("صباح", EventMatching.normalize("  صباح  "))
        assertEquals("team a", EventMatching.normalize(" Team   A! "))
    }
    @Test fun matchingRequiresTeamsLeagueAndTime() {
        val a = SportEvent("a", "viprow", "Team A - Team B", "League", 10000, 11000, "football", "", "Team A", "Team B")
        val b = a.copy(id="b", catalogueId="korahlive")
        assertTrue(EventMatching.same(a, b))
        assertFalse(EventMatching.same(a, b.copy(start=12000)))
        assertFalse(EventMatching.same(a, b.copy(home="")))
        assertFalse(EventMatching.same(a, b.copy(league="Other")))
        assertFalse(EventMatching.same(a, b.copy(away="Team C")))
    }
}
