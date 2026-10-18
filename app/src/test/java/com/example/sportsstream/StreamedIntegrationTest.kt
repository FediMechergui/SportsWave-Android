package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class StreamedIntegrationTest {
    @Test fun numberedPlayerLoadBalancersDoNotAllowUnrelatedSubdomainsOrAds() {
        assertTrue(UrlPolicy.hostMatches("lb31.strmd.st","lb*.strmd.st"))
        assertFalse(UrlPolicy.hostMatches("ads.strmd.st","lb*.strmd.st"))
        assertFalse(UrlPolicy.hostMatches("lb31.strmd.st.evil.example","lb*.strmd.st"))
        val rule=ProviderRule("streamed-embed","Streamed",listOf("embed.st"),"/embed/","web",1,listOf("embed.st","lb*.strmd.st"))
        val policy=PlayerRequestPolicy(rule,listOf(rule))
        assertFalse(policy.allowsResource("https://embed.st/ad.html"))
        assertTrue(policy.allowsResource("https://lb31.strmd.st/resource"))
    }
    @Test fun playerAttachmentNeverChangesScoresAndRejectsAmbiguousOrReversedMatches() {
        val score=SportEvent("score","espn","Home - Away","League",10000,0,"football","","Home","Away",homeScore=2,awayScore=1)
        val player=score.copy(id="player",catalogueId="streamed",league="",url="https://streamed.pk/watch/player",homeScore=null,awayScore=null)
        assertEquals(listOf(player),PlayerAttachment.streamed(score,listOf(player)))
        assertTrue(PlayerAttachment.streamed(score,listOf(player,player.copy(id="second"))).isEmpty())
        assertTrue(PlayerAttachment.streamed(score,listOf(player.copy(home="Away",away="Home"))).isEmpty())
        assertEquals(2,score.homeScore)
    }
    @Test fun publishedRaceMeetingsUseTheirIntervalWithoutBecomingPlayers() {
        val html="""<script type="application/ld+json">{"@graph":[{"@type":"SportsEvent","name":"Formula 1 — Grand Prix","startDate":"2026-10-23T12:00Z","endDate":"2026-10-25T20:00Z","url":"https://raceweek.io/f1/?open=F1-1","eventStatus":"https://schema.org/EventScheduled"}]}</script>"""
        val event=RaceweekCatalogue.parse(html).single()
        assertTrue(event.url.isBlank());assertNull(event.homeScore);assertEquals("unknown",event.status)
        assertTrue(CalendarDates.matches(event.sport,event.start,event.end,java.time.LocalDate.of(2026,10,24),java.time.ZoneId.of("UTC")))
    }
    @Test fun channelsDoNotBecomeMatchesAndWrestlingIsNotMma() {
        val records="""[{"id":"golf","title":"Sky Sports Golf","category":"golf","date":1792283400000},{"id":"aew","title":"AEW Saturday Night Collision","category":"fight","date":1792283400000},{"id":"ufc","title":"UFC Fight Night","category":"fight","date":1792283400000},{"id":"race","title":"Grand Prix Race","category":"motor-sports","date":1792283400000}]"""
        val result=StreamedCatalogue.parse(records)
        assertEquals(3,result.size);assertEquals("more-sports",result.first().sport)
        assertEquals("mma-events",result[1].sport);assertEquals("motorsports",result[2].sport)
        assertTrue(result.all { it.homeScore==null && it.status=="unknown" })
    }
    @Test fun apiTimestampsAreMillisecondsAndInvalidIdentifiersAreRejected() {
        val json="""[{"id":"good","title":"Home vs Away","category":"football","date":1792283400000,"teams":{"home":{"name":"Home","badge":"some+handle"},"away":{"name":"Away"}}},{"id":"../bad","title":"Bad","category":"fight","date":1792283400000}]"""
        val e=StreamedCatalogue.parse(json).single()
        assertEquals(1792283400,e.start);assertEquals("",e.league)
        assertTrue(e.homeLogo.endsWith("some%2Bhandle.webp"));assertNull(e.homeScore)
    }
    @Test fun streamMetadataPreservesUnknownLanguageButRejectsUnreviewedEmbedHosts() {
        val rule=ProviderRule("streamed-embed","Streamed",listOf("embed.st"),"/embed/","web",1)
        val rows="""[{"embedUrl":"https://embed.st/embed/admin/a/1?original=yes","language":"English - ESPN","hd":true,"streamNo":1},{"embedUrl":"https://embed.st/embed/admin/a/2","language":"Main"},{"embedUrl":"https://embed.st.evil.example/embed/a","language":"Arabic"}]"""
        val result=StreamedCatalogue.parsePlayers(rows,listOf(rule))
        assertEquals(2,result.size);assertTrue(result.first().url.endsWith("?original=yes"))
        assertEquals("English",CommentaryLanguage.allowed(result.first().language))
        assertEquals("Language unverified",CommentaryLanguage.display(result.last().language))
        assertNull(CommentaryLanguage.allowed("English / Spanish"))
    }
    @Test fun arabicCardPlaceholderScoresAndEstimatedLiveStatusAreNotTrusted() {
        val html="""<a class="AY_Match is-live" data-match-id="1" data-kickoff-utc="2026-10-18T13:00:00+00:00" data-home-score="0" data-away-score="0" href="https://yallashootkoora.online/ar/match/example/"><div class="TM1"><span class="TM_Name">الترجي</span></div><div class="TM2"><span class="TM_Name">الإفريقي</span></div><div class="MT_Info"><ul><li>Channel</li><li>Unknown</li><li>الدوري التونسي</li></ul></div></a>"""
        val event=YallaCatalogue().parse(html).single()
        assertNull(event.homeScore);assertNull(event.awayScore);assertEquals("unknown",event.status)
        assertEquals(1792328400,event.start)
        assertTrue(YallaCatalogue().parse(html.replace("yallashootkoora.online/ar/match/","evil.example/ar/match/")).isEmpty())
    }
}
