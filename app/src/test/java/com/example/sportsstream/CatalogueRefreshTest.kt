package com.example.sportsstream

import org.junit.Assert.*
import org.junit.Test

class CatalogueRefreshTest {
    private fun event(source: String, id: String = source, received: Long = 100L) =
        SportEvent(id, source, "Home - Away", "League", 1000, 0, "football", "", receivedAt = received)

    @Test fun refreshKeepsSourcesThatHaveNotRespondedAndReplacesOldScores() {
        val old = event("openliga").copy(homeScore = 0, awayScore = 0)
        val other = event("livekora")
        val incoming = old.copy(homeScore = 1, receivedAt = 200)
        assertEquals(listOf(other, incoming), CatalogueRefresh.replaceSource(listOf(old, other), "openliga", listOf(incoming)))
    }
    @Test fun failureRetainsRecordsWithoutMakingThemFresh() {
        val old = listOf(event("viprow"), event("openliga", received = 150))
        val retained = CatalogueRefresh.replaceSource(old, "openliga", null)
        assertEquals(old, retained)
        assertEquals(150L, CatalogueRefresh.receivedAt(retained))
    }
    @Test fun confirmedEmptyRemovesOnlyItsSourceAndDoesNotDropOtherProviders() {
        val other = event("viprow")
        assertEquals(listOf(other), CatalogueRefresh.replaceSource(listOf(other, event("openliga")), "openliga", emptyList()))
        assertEquals(0L, CatalogueRefresh.receivedAt(emptyList()))
    }
    @Test fun sharedIdsFromDifferentProvidersRemainSeparate() {
        val other = event("viprow", "same")
        val fresh = event("openliga", "same")
        assertEquals(listOf(other, fresh), CatalogueRefresh.replaceSource(listOf(other), "openliga", listOf(fresh)))
    }
    @Test(expected = IllegalArgumentException::class) fun wrongProviderCannotReplaceSourceRecords() {
        CatalogueRefresh.replaceSource(emptyList(), "viprow", listOf(event("openliga")))
    }
}
