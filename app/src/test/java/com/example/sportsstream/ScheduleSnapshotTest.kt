package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class ScheduleSnapshotTest {
    private val now = 2_000_000_000_000L
    private fun event() = SportEvent("id", "openliga", "Home - Away", "League", 1000, 0, "football", "",
        "Home", "Away", 1, 2, receivedAt = now, status = "reported")
    @Test fun roundTripRetainsProvenanceScoreAndFetchTime() {
        assertEquals(event(), ScheduleSnapshot.decode(ScheduleSnapshot.encode(listOf(event())), now).single())
    }
    @Test fun unknownScoresStayUnknown() {
        val item = event().copy(homeScore = null, awayScore = null)
        val decoded = ScheduleSnapshot.decode(ScheduleSnapshot.encode(listOf(item)), now).single()
        assertNull(decoded.homeScore); assertNull(decoded.awayScore)
    }
    @Test fun expiredFutureAndUnsafeRecordsAreRejectedIndividually() {
        val records = listOf(event(), event().copy(receivedAt = now - 25 * 3600_000L),
            event().copy(receivedAt = now + 3600_000), event().copy(url = "javascript:alert(1)"))
        assertEquals(listOf(event()), ScheduleSnapshot.decode(ScheduleSnapshot.encode(records), now))
    }
}
