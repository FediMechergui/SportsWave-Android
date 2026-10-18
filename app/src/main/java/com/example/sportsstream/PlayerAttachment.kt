package com.example.sportsstream

/** Attach an unambiguous published player choice without merging its scores or inventing a league. */
object PlayerAttachment {
    fun streamed(event:SportEvent,events:List<SportEvent>):List<SportEvent> {
        if(event.home.isBlank() || event.away.isBlank() || event.start<=0) return emptyList()
        val candidates=events.filter { candidate -> candidate.catalogueId=="streamed" && candidate.url.isNotBlank() &&
            candidate.sport==event.sport && candidate.start>0 && kotlin.math.abs(candidate.start-event.start)<=300 &&
            EventMatching.teamKey(candidate.home,event.sport)==EventMatching.teamKey(event.home,event.sport) &&
            EventMatching.teamKey(candidate.away,event.sport)==EventMatching.teamKey(event.away,event.sport)
        }.distinctBy { it.id }
        return if(candidates.size==1) candidates else emptyList()
    }
}
