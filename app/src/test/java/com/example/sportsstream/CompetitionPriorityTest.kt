package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class CompetitionPriorityTest {
    @Test fun worldEventsOutrankClubAndDomesticEvents() {
        assertTrue(CompetitionPriority.tier("FIFA World Cup Women") < CompetitionPriority.tier("UEFA Champions League"))
        assertTrue(CompetitionPriority.tier("Olympic Handball") < CompetitionPriority.tier("NBA"))
        assertTrue(CompetitionPriority.tier("UEFA Champions League") < CompetitionPriority.tier("Premier League"))
        assertEquals(4, CompetitionPriority.tier("Regional friendly"))
    }
    @Test fun distinctTeamCategoriesNeverMerge() {
        val event = SportEvent("one", "a", "A - B", "League", 1000, 0, "football", "", "A", "B")
        assertFalse(EventMatching.same(event, event.copy(id = "two", catalogueId = "b", home = "A Women")))
        assertFalse(EventMatching.same(event, event.copy(id = "two", catalogueId = "b", home = "A U19")))
        assertTrue(EventMatching.same(event, event.copy(id = "two", catalogueId = "b")))
    }
}
