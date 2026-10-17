package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class EventAggregationTest {
    private fun event(id: String, source: String, home: String = "Liverpool", league: String = "UEFA Champions League") =
        SportEvent(id, source, "$home - Barcelona", league, 1000, 0, "football", "", home, "Barcelona", receivedAt = 100)
    @Test fun reviewedAliasesMergeAndPreserveAllPlayerChoices() {
        val score = event("a", "openliga", "FC Liverpool", "Champions League 2026/2027").copy(homeScore = 2, awayScore = 1)
        val vip = event("b", "viprow").copy(url = "https://viprow.im/sports/football/b")
        val arabic = event("c", "livekora").copy(url = "https://pl.koralive1.cc/test")
        val result = EventAggregation.merge(listOf(score, vip, arabic)).single()
        assertEquals(3, result.members.size); assertEquals(2, result.event.homeScore)
        assertTrue(result.event.url.isNotEmpty())
        assertEquals(2, result.members.count { it.url.isNotBlank() })
    }
    @Test fun ambiguousKickoffChainDoesNotBecomeOneFixture() {
        val a = event("a", "one")
        val b = event("b", "two").copy(start = 1240)
        val c = event("c", "three").copy(start = 1480)
        // The middle report matches two incompatible fixtures, so all three stay separate.
        assertEquals(3, EventAggregation.merge(listOf(a, b, c)).size)
    }
    @Test fun reversedTeamsAndDifferentCategoriesRemainSeparate() {
        val a = event("a", "one")
        assertEquals(2, EventAggregation.merge(listOf(a, event("b", "two").copy(home = "Barcelona", away = "Liverpool"))).size)
        assertEquals(2, EventAggregation.merge(listOf(a, event("b", "two", "Liverpool Women"))).size)
        assertEquals(2, EventAggregation.merge(listOf(a, event("b", "two", league = "UEFA Youth League"))).size)
    }
    @Test fun repeatedProviderIdKeepsNewestReport() {
        val old = event("a", "one").copy(homeScore = 1, awayScore = 0)
        val fresh = old.copy(homeScore = 2, receivedAt = 200)
        assertEquals(2, EventAggregation.merge(listOf(old, fresh)).single().event.homeScore)
    }
    @Test fun sportIdentityAndSeasonSuffixAreConservative() {
        assertEquals("uefa champions league", EventMatching.leagueKey("Europe - Champions League 2026/27", "football"))
        assertFalse(EventMatching.same(event("a", "one"), event("b", "two").copy(sport = "basketball")))
        assertNotEquals(EventMatching.teamKey("Liverpool U19", "football"), EventMatching.teamKey("FC Liverpool", "football"))
    }
}
