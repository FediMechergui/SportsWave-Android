package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class LivekoraCatalogueTest {
    private fun card(home: String, time: String, link: String = "https://pl.koralive1.cc/bein1") = """
        <a href="$link" target="_blank"><div class="match-container">
        <div class="right-team"><div class="team-name">$home</div></div>
        <div class="left-team"><div class="team-name">Away</div></div>
        <span class="date" data-start="$time"></span><div class="match-score">0:0</div>
        <div class="match-info"><ul><li>Commentator</li><li>Channel</li><li>League</li></ul></div>
        </div></a>"""

    @Test fun sharedChannelRetainsDistinctMatchesAndKickoffOffset() {
        val html = card("Home A", "2026-10-13T14:45:00+01:00") + card("Home B", "2026-10-13T17:00:00+01:00")
        val result = LivekoraCatalogue().parse(html, "https://www.livekora.vip/", "football")
        assertEquals(2, result.size)
        assertNotEquals(result[0].id, result[1].id)
        assertEquals(result[0].url, result[1].url)
        assertEquals(java.time.Instant.parse("2026-10-13T13:45:00Z").epochSecond, result[0].start)
        assertEquals("League", result[0].league)
        assertEquals(0L, result[0].end)
    }

    @Test fun rejectsInvalidTimeUnsafeChannelAndOtherSports() {
        val parser = LivekoraCatalogue()
        assertTrue(parser.parse(card("Home", "invalid"), "https://www.livekora.vip/", "football").isEmpty())
        assertTrue(parser.parse(card("Home", "2026-10-13T14:45:00Z", "https://pl.koralive1.cc.evil.org/bein1"), "https://www.livekora.vip/", "football").isEmpty())
        assertTrue(parser.parse(card("Home", "2026-10-13T14:45:00Z"), "https://www.livekora.vip/", "tennis").isEmpty())
    }
}
