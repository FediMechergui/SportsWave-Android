package com.example.sportsstream

import org.json.JSONArray
import org.jsoup.Jsoup
import java.time.Instant

/** Community matchday results. Shortcuts checked against the public league directory, October 2026. */
class OpenLigaCatalogue : CatalogueProvider {
    override val id = "openliga"
    override fun load(sport: String): List<SportEvent> {
        val leagues = when (sport) {
            "football" -> listOf("ucl", "bl1", "bl2", "bl3", "pl", "la1", "ffb1")
            "ice-hockey" -> listOf("CHL", "del", "DEL2")
            "darts" -> listOf("PDCSDT")
            else -> return emptyList()
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val results = try { leagues.map { league ->
            pool.submit<List<SportEvent>> {
                val response = Jsoup.connect("https://api.openligadb.de/getmatchdata/$league")
                    .ignoreContentType(true).followRedirects(false).timeout(10000)
                    .maxBodySize(2_000_000).execute()
                check(response.statusCode() == 200)
                parse(response.body(), sport)
            }
        }.map { task ->
            try {
                Result.success(task.get(45, java.util.concurrent.TimeUnit.SECONDS))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
        } finally { pool.shutdownNow() }
        if (results.all { it.isFailure }) error("OpenLigaDB unavailable")
        return results.flatMap { it.getOrDefault(emptyList()) }
    }

    fun parse(json: String, sport: String = "football"): List<SportEvent> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val row = array.getJSONObject(index)
                val home = row.getJSONObject("team1").getString("teamName")
                val away = row.getJSONObject("team2").getString("teamName")
                val start = Instant.parse(row.getString("matchDateTimeUTC")).epochSecond
                val finished = row.optBoolean("matchIsFinished")
                val scores = row.optJSONArray("matchResults") ?: JSONArray()
                val result = (0 until scores.length()).map { scores.getJSONObject(it) }
                    .firstOrNull { it.optInt("resultTypeID") == 2 }
                val goals = row.optJSONArray("goals") ?: JSONArray()
                val reported = (0 until goals.length()).map { goals.getJSONObject(it) }
                    .filter { it.has("scoreTeam1") && it.has("scoreTeam2") }
                    .maxByOrNull { it.optInt("scoreTeam1") + it.optInt("scoreTeam2") }
                // A default 0:0 on an unplayed match is not a verified live score.
                SportEvent("openliga|${row.getLong("matchID")}", id, "$home - $away",
                    row.getString("leagueName"), start, if (finished) start else 0L,
                    sport, "", home, away,
                    if (finished) result?.takeIf { it.has("pointsTeam1") }?.getInt("pointsTeam1") else reported?.getInt("scoreTeam1"),
                    if (finished) result?.takeIf { it.has("pointsTeam2") }?.getInt("pointsTeam2") else reported?.getInt("scoreTeam2"),
                    row.getJSONObject("team1").optString("teamIconUrl"),
                    row.getJSONObject("team2").optString("teamIconUrl"), System.currentTimeMillis(),
                    if (finished) "finished" else "unknown")
            }.getOrNull()
        }
    }
}
