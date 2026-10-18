package com.example.sportsstream

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object CalendarDates {
    fun matches(sport: String, start: Long, end: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if(start <= 0) return false
        val first = Instant.ofEpochSecond(start).atZone(zone).toLocalDate()
        if(sport !in setOf("golf","motorsports") || end <= start) return first == day
        val last = Instant.ofEpochSecond(end - 1).atZone(zone).toLocalDate()
        return day in first..last
    }
}
