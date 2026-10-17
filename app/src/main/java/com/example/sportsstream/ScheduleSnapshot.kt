package com.example.sportsstream

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Public catalogue metadata only. Each snapshot is bounded and replaced atomically. */
object ScheduleSnapshot {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun save(file: File, events: List<SportEvent>) { executor.execute { write(file, events) } }
    fun encode(events: List<SportEvent>): String = JSONArray(events.take(2000).map { event ->
        JSONObject().apply {
            put("id", event.id); put("source", event.catalogueId); put("name", event.name)
            put("league", event.league); put("sport", event.sport); put("start", event.start); put("end", event.end)
            put("url", event.url); put("home", event.home); put("away", event.away)
            put("homeScore", event.homeScore ?: JSONObject.NULL); put("awayScore", event.awayScore ?: JSONObject.NULL)
            put("homeLogo", event.homeLogo); put("awayLogo", event.awayLogo)
            put("receivedAt", event.receivedAt); put("status", event.status)
        }
    }).toString()
    fun decode(json: String, now: Long = System.currentTimeMillis()): List<SportEvent> {
        require(json.length <= 2_000_000)
        val array = JSONArray(json)
        return (0 until minOf(array.length(), 2000)).mapNotNull { index -> runCatching {
            val row = array.getJSONObject(index)
            val received = row.getLong("receivedAt")
            require(received in (now - 24 * 3600_000L)..(now + 60_000))
            val url = row.optString("url")
            require(url.isBlank() || UrlPolicy.https(url) != null)
            SportEvent(row.getString("id"), row.getString("source"), row.getString("name"), row.getString("league"),
                row.getLong("start"), row.optLong("end"), row.getString("sport"), url,
                row.optString("home"), row.optString("away"),
                if (row.isNull("homeScore")) null else row.getInt("homeScore"),
                if (row.isNull("awayScore")) null else row.getInt("awayScore"),
                row.optString("homeLogo"), row.optString("awayLogo"), received, row.optString("status", "unknown"))
        }.getOrNull() }
    }
    fun read(file: File): List<SportEvent> = runCatching {
        require(file.length() <= 2_000_000)
        decode(file.readText())
    }.getOrDefault(emptyList())
    fun write(file: File, events: List<SportEvent>) = runCatching {
        val json = encode(events)
        require(json.length <= 2_000_000)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json)
        java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
