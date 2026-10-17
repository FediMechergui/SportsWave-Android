package com.example.sportsstream

/** A failed source retains its records; a successful source replaces only its own records. */
object CatalogueRefresh {
    fun replaceSource(current: List<SportEvent>, source: String, incoming: List<SportEvent>?): List<SportEvent> {
        if (incoming == null) return current
        require(incoming.all { it.catalogueId == source })
        return (current.filterNot { it.catalogueId == source } + incoming)
            .distinctBy { it.catalogueId to it.id }
    }
    fun receivedAt(events: List<SportEvent>): Long = events.maxOfOrNull { it.receivedAt } ?: 0L
}
