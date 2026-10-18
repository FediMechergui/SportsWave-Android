package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class RegionalPreferenceTest {
    @Test fun tunisianCompetitionsRankAcrossSportsAndScripts() {
        assertEquals(0,RegionalPreference.competitionRank("Tunisia Basketball Championship"))
        assertEquals(0,RegionalPreference.competitionRank("كأس تونس لكرة اليد"))
        assertEquals(0,RegionalPreference.competitionRank("الرابطة التونسية المحترفة الأولى"))
        assertEquals(1,RegionalPreference.competitionRank("NBA"))
    }
    @Test fun arabicSourcesComeFirstWithoutExcludingInternationalFeeds() {
        val ranked=listOf("espn","streamed","yalla","filgoal").sortedBy(RegionalPreference::sourceRank)
        assertEquals(listOf("filgoal","yalla","espn","streamed"),ranked)
    }
    @Test fun regionalAndYacineHandoffsRemainAvailableOutsideFootball() {
        listOf("tennis","motorsports","horse-racing","cricket","ice-hockey","volleyball").forEach { sport ->
            val links=WatchResources.forSport(sport)
            assertTrue(links.any { it.url=="https://www.tunisiatv.tn/ar/live" })
            assertTrue(links.any { it.url=="https://yacinetv.website/" && it.note.contains("unverified") })
            assertTrue(links.any { it.url=="https://beinmatchatv.com/" && it.note.contains("unverified") })
            assertEquals(links.size,links.distinctBy { it.url }.size)
        }
    }
}
