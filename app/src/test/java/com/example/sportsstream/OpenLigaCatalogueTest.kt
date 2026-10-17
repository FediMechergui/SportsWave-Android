package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class OpenLigaCatalogueTest {
    private fun payload(finished: Boolean) = """[{
      "matchID":123,"matchDateTimeUTC":"2026-10-14T13:00:00Z","leagueName":"Bundesliga",
      "team1":{"teamName":"Home","teamIconUrl":"https://i.imgur.com/home.png"},
      "team2":{"teamName":"Away"},"matchIsFinished":$finished,
      "matchResults":[{"resultTypeID":1,"pointsTeam1":1,"pointsTeam2":0},
      {"resultTypeID":2,"pointsTeam1":2,"pointsTeam2":3}]}]"""
    @Test fun finalResultUsesFullTimeAndPublishedTimestamp() {
        val event = OpenLigaCatalogue().parse(payload(true)).single()
        assertEquals(2, event.homeScore); assertEquals(3, event.awayScore)
        assertEquals(java.time.Instant.parse("2026-10-14T13:00:00Z").epochSecond, event.start)
        assertTrue(event.url.isBlank()); assertEquals("Home", event.home)
        assertEquals("https://i.imgur.com/home.png", event.homeLogo)
    }
    @Test fun unfinishedDefaultResultsAreNotLiveScores() {
        val event = OpenLigaCatalogue().parse(payload(false)).single()
        assertNull(event.homeScore); assertNull(event.awayScore); assertEquals(0L, event.end)
    }
    @Test fun malformedRecordDoesNotDiscardValidRecords() {
        val input = payload(true).dropLast(1) + ",{\"matchID\":999}]"
        assertEquals(1, OpenLigaCatalogue().parse(input).size)
    }
    @Test fun publishedGoalsProvideAReportedScoreWithoutAnInventedClock() {
        val row = org.json.JSONArray(payload(false)).getJSONObject(0)
        row.put("goals", org.json.JSONArray("""[{"scoreTeam1":1,"scoreTeam2":0},{"scoreTeam1":1,"scoreTeam2":1}]"""))
        val event = OpenLigaCatalogue().parse(org.json.JSONArray().put(row).toString()).single()
        assertEquals(1, event.homeScore); assertEquals(1, event.awayScore)
        assertEquals(0L, event.end)
    }
}
