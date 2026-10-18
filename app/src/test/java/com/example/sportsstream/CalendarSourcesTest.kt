package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class CalendarSourcesTest {
    @Test fun largeCalendarKeepsDistinctFixturesAndMergesOnlyTheirActualOtherSource() {
        val originals = (1..1000).map { i -> SportEvent("a$i","espn","Home$i - Away$i","League",1000L + i * 1000L,0,"football","", "Home$i","Away$i", receivedAt = 100) }
        val second = originals.map { it.copy(id = "other-${it.id}",catalogueId = "viprow",url = "https://viprow.im/sports/event",receivedAt = 200) }
        val result = EventAggregation.merge(originals + second)
        assertEquals(1000,result.size)
        assertTrue(result.all { it.members.size == 2 && it.event.url.isNotBlank() })
    }
    @Test fun golfSpansItsPublishedDaysWhileTeamFixturesKeepTheirStartDate() {
        val start = OffsetDateTime.parse("2026-10-15T12:00Z").toEpochSecond()
        val end = OffsetDateTime.parse("2026-10-19T00:00Z").toEpochSecond()
        val utc = java.time.ZoneId.of("UTC")
        assertTrue(CalendarDates.matches("golf",start,end,LocalDate.of(2026,10,18),utc))
        assertFalse(CalendarDates.matches("golf",start,end,LocalDate.of(2026,10,19),utc))
        assertFalse(CalendarDates.matches("football",start,end,LocalDate.of(2026,10,18),utc))
    }
    @Test fun arabicInternationalCompetitionsReceiveTheSamePriority() {
        assertEquals(0, CompetitionPriority.tier("كأس العالم"))
        assertEquals(1, CompetitionPriority.tier("كأس أمم إفريقيا"))
        assertEquals(2, CompetitionPriority.tier("دوري أبطال إفريقيا"))
        assertEquals(3, CompetitionPriority.tier("الدوري الإنجليزي الممتاز"))
    }
    private fun event(state: String, completed: Boolean = false) = """{"leagues":[{"name":"NBA"}],"events":[{"id":"event","date":"2026-10-18T13:00Z","name":"Away at Home","competitions":[{"id":"game","date":"2026-10-18T13:00Z","status":{"type":{"state":"$state","completed":$completed}},"competitors":[{"homeAway":"away","score":"2","team":{"displayName":"Away"}},{"homeAway":"home","score":"3","team":{"displayName":"Home","logo":"https://a.espncdn.com/team.png"}}]}]}]}"""

    @Test fun espnUsesPublishedHomeAwayOrderAndMinutePrecisionUtc() {
        val item = EspnCatalogue().parse(event("post",true),"basketball",100).single()
        assertEquals("Home - Away",item.name)
        assertEquals(OffsetDateTime.parse("2026-10-18T13:00Z").toEpochSecond(),item.start)
        assertEquals(3,item.homeScore); assertEquals(2,item.awayScore)
        assertEquals("finished",item.status); assertTrue(item.url.isEmpty())
    }
    @Test fun scheduledPlaceholdersAreNotPublishedAsScores() {
        val item = EspnCatalogue().parse(event("pre"),"basketball").single()
        assertNull(item.homeScore); assertNull(item.awayScore); assertEquals("scheduled",item.status)
    }
    @Test fun tennisGroupsUseActualMatchesAndSeparateDraws() {
        val json = """{"events":[{"id":"tournament","name":"Open","date":"2026-10-01T00:00Z","groupings":[{"grouping":{"displayName":"Women's Singles"},"competitions":[{"id":"match","date":"2026-10-18T13:00Z","competitors":[{"athlete":{"displayName":"Player One"}},{"athlete":{"displayName":"Player Two"}}]}]}]}]}"""
        val item = EspnCatalogue().parse(json,"tennis").single()
        assertEquals("Player One - Player Two",item.name)
        assertTrue(item.league.contains("Women's Singles")); assertTrue(item.id.endsWith("|match"))
    }
    @Test fun racesWithoutTwoCompetitorsStaySessionsRatherThanInventedMatches() {
        val json = """{"events":[{"id":"grand-prix","name":"Grand Prix","competitions":[{"id":"practice","date":"2026-10-18T13:00Z","type":{"abbreviation":"FP1"},"competitors":[]},{"id":"race","date":"2026-10-19T13:00Z","type":{"abbreviation":"Race"},"competitors":[]}]}]}"""
        val items = EspnCatalogue().parse(json,"motorsports")
        assertEquals(2,items.size); assertEquals("Grand Prix (FP1)",items.first().name)
        assertTrue(items.all { it.home.isEmpty() && it.homeScore == null })
    }
    private fun arabic(status: String) = """<div id="match-list-viewer" data-current-date="2026-10-18"><div class="mc-block"><h6><span>الدوري الإنجليزي الممتاز</span></h6><div class="cin_cntnr"><a href="/matches/123/test"></a><div class="f"><strong>الأهلي</strong><b>0</b><img data-src="//semedia.filgoal.com/team.png"></div><div class="s"><strong>الزمالك</strong><b>0</b></div><span class="status">$status</span><div class="match-aux">18-10-2026 - 16:00</div></div></div></div>"""
    @Test fun arabicCardsUseCairoDstAndNeverTurnSchedulePagesIntoPlayers() {
        val item = FilGoalCatalogue().parse(arabic("لم تبدأ"),LocalDate.of(2026,10,18)).single()
        assertEquals(OffsetDateTime.parse("2026-10-18T13:00Z").toEpochSecond(),item.start)
        assertEquals("الأهلي - الزمالك",item.name); assertNull(item.homeScore); assertTrue(item.url.isEmpty())
    }
    @Test fun arabicFinalScoresAreDistinctFromScheduledZeroPlaceholders() {
        val item = FilGoalCatalogue().parse(arabic("انتهت"),LocalDate.of(2026,10,18)).single()
        assertEquals(0,item.homeScore); assertEquals("finished",item.status)
    }
    @Test(expected = IllegalArgumentException::class) fun wrongCalendarDateCannotMasqueradeAsSelectedDate() {
        FilGoalCatalogue().parse(arabic("انتهت"),LocalDate.of(2026,10,19))
    }
    @Test fun commentaryIsStrictlyExplicitArabicOrEnglish() {
        for(code in listOf("ar","ara","AR-SA","Arabic","العربية")) assertEquals("Arabic",CommentaryLanguage.allowed(code))
        for(code in listOf("en","eng","en_US","English")) assertEquals("English",CommentaryLanguage.allowed(code))
        for(code in listOf(null,"","unknown","es","fr","de","English Premier League","English / Spanish","العربية / الفرنسية")) assertNull(CommentaryLanguage.allowed(code))
    }
}
