package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class BroadcastCoverageTest {
    private val channels = """{"rows":[{"id":"en","name":"beIN SPORTS EN 1","redirectUrl":"https://beinconnect.app/beINSPORTS1EN"},{"id":"ar","name":"beIN SPORTS 1","redirectUrl":"https://evil.example/player"},{"id":"fr","name":"beIN SPORTS FR 1","redirectUrl":"https://beinconnect.app/beINSPORTS1FR"},{"id":"other","name":"Unreviewed channel"}]}"""
    private fun programme(id:String,channel:String,title:String,live:Boolean=false,replay:Boolean=false) = """{"id":"$id","channelId":"$channel","title":"$title","startDate":"2026-10-18T13:00Z","endDate":"2026-10-18T15:00Z","live":$live,"replay":$replay}"""
    @Test fun guideExcludesFrenchAndUnknownChannelsAndUnapprovedWatchHosts() {
        val json="""{"rows":[${programme("1","en","Home vs Away",true)},${programme("2","ar","Another game")},${programme("3","fr","French game")},${programme("4","other","Unknown")},${programme("5","en","SIGN OFF")}]}"""
        val found=BeinGuide.parse(channels,json)
        assertEquals(2,found.size);assertTrue(found[0].live)
        assertEquals("https://beinconnect.app/beINSPORTS1EN",found[0].watchUrl)
        assertEquals("",found[1].watchUrl)
    }
    @Test fun replayAndBroadcastAirtimeCannotBecomeALiveFixture() {
        val row=BeinGuide.parse(channels,"""{"rows":[${programme("1","en","Liverpool vs Chelsea - Replay",true,true)}]}""").single()
        assertTrue(row.replay)
        assertTrue(BeinGuide.matches(row,"Liverpool","Chelsea"))
        assertFalse(BeinGuide.matches(row,"Liverpool","Arsenal"))
        assertFalse(BeinGuide.matches(row,"Pool","Chelsea"))
        assertFalse(BeinGuide.matches(row,"",""))
    }
    @Test fun tunisianLeaguesArePrioritisedBelowWorldChampionships() {
        assertEquals(1,CompetitionPriority.tier("الرابطة التونسية المحترفة"))
        assertEquals(1,CompetitionPriority.tier("Tunisian Ligue Professionnelle 1"))
        assertEquals(0,CompetitionPriority.tier("World Cup"))
    }
    @Test fun logosShareReviewedIdentitiesButKeepSportsAndWomensTeamsSeparate() {
        TeamLogoIndex.remember(listOf(SportEvent("logo","filgoal","","",0,0,"football","",home="الترجي",homeLogo="https://semedia.filgoal.com/team.png")))
        assertEquals("https://semedia.filgoal.com/team.png",TeamLogoIndex.find("Espérance de Tunis","football"))
        assertEquals("",TeamLogoIndex.find("Espérance de Tunis","handball"))
        assertEquals("",TeamLogoIndex.find("Espérance de Tunis Women","football"))
    }
    @Test fun raceKeepsEntrantsAndClassificationWithoutCreatingTeamScores() {
        val json="""{"leagues":[{"name":"Formula 1"}],"events":[{"id":"race","name":"Grand Prix","date":"2026-10-18T13:00Z","competitions":[{"id":"session","type":{"abbreviation":"Race"},"status":{"type":{"completed":true}},"competitors":[{"order":1,"athlete":{"displayName":"Driver A"}},{"order":2,"athlete":{"displayName":"Driver B"}}]}]}]}"""
        val event=EspnCatalogue().parse(json,"motorsports").single()
        assertEquals("",event.home);assertNull(event.homeScore)
        assertEquals(listOf("Driver A","Driver B"),event.entrants.map { it.name })
        assertEquals(1,event.entrants.first().position)
        assertEquals(event.entrants,ScheduleSnapshot.decode(ScheduleSnapshot.encode(listOf(event))).single().entrants)
    }
}
