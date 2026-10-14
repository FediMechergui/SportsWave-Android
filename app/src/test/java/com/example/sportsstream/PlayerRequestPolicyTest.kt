package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class PlayerRequestPolicyTest {
    @Test fun shippedAlbaRulesAllowSnrtPlaybackWithoutAdOrNavigationEscape() {
        val asset = sequenceOf(java.io.File("src/main/assets/providers.json"), java.io.File("app/src/main/assets/providers.json"))
            .first { it.exists() }
        val rules = ProviderRegistry.parse(asset.readText())
        val policy = PlayerRequestPolicy(rules.first { it.id == "albaplayer" }, rules)
        assertTrue(policy.allowsNavigation("https://snrt.player.easybroadcast.io/events/73_arryadia_k2tgcj0", false))
        assertFalse(policy.allowsNavigation("https://snrt.player.easybroadcast.io/events/73_arryadia_k2tgcj0", true))
        for (host in listOf("token.easybroadcast.io", "libs.easybroadcast.io", "cdn.live.easybroadcast.io")) {
            assertTrue(host, policy.allowsResource("https://$host/test"))
            assertFalse(host, policy.allowsNavigation("https://$host/test", false))
        }
        for (host in listOf("acscdn.com", "llvpn.com", "adsco.re", "snrt.player.easybroadcast.io.evil.org")) {
            assertFalse(host, policy.allowsResource("https://$host/test"))
        }
    }
    private val daddy = ProviderRule("daddylive", "DaddyLive", listOf("dlive.sx"), "/stream/", "web", 20)
    private val today = ProviderRule("todaystreams", "TodayStreams", listOf("todaystreams.cc"), "/play/", "web", 10,
        listOf("todaystreams.cc", "dlive.sx"), listOf("daddylive"))
    private val policy = PlayerRequestPolicy(today, listOf(today, daddy))
    @Test fun approvedNestedPlayerLoadsWithoutAllowingMainFrameRedirect() {
        val nested = "https://dlive.sx/stream/stream-24.php"
        assertTrue(policy.allowsNavigation(nested, false))
        assertTrue(policy.allowsResource(nested))
        assertFalse(policy.allowsNavigation(nested, true))
    }
    @Test fun adFramesLookalikesAndUnapprovedPathsStayBlocked() {
        for (url in listOf("https://adsco.re/ad", "https://dlive.sx.evil.org/stream/stream-24.php",
            "https://dlive.sx/unrelated/", "javascript:alert(1)")) {
            assertFalse(url, policy.allowsNavigation(url, false))
        }
        assertFalse(policy.allowsResource("https://adsco.re/ad.js"))
    }
    @Test fun unrelatedProviderNeedsExplicitFramePermission() {
        val noFrames = today.copy(frameProviders=emptyList())
        assertFalse(PlayerRequestPolicy(noFrames, listOf(today, daddy))
            .allowsNavigation("https://dlive.sx/stream/stream-24.php", false))
    }
    @Test fun reviewedNestedProviderResourcesDoNotGrantNavigation() {
        val embedded = daddy.copy(resourceHosts=listOf("dlive.sx", "cdn.jwplayer.com"))
        val nestedPolicy = PlayerRequestPolicy(today, listOf(today, embedded))
        val script = "https://cdn.jwplayer.com/libraries/example.js"
        assertTrue(nestedPolicy.allowsResource(script))
        assertFalse(nestedPolicy.allowsNavigation(script, false))
        assertFalse(nestedPolicy.allowsNavigation(script, true))
    }
}
