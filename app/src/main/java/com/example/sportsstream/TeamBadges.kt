package com.example.sportsstream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Team crest provider. Every badge shows an instant initials avatar; a real crest from the public
 * TheSportsDB API replaces it when one is found. Results are cached in memory and on disk, and
 * API calls are throttled to stay inside the free tier.
 */
object TeamBadges {
    private const val DEFAULT_KEY = "3"
    private const val MISS_TTL_MS = 7L * 24 * 3600 * 1000
    private const val MIN_GAP_MS = 2100L
    private val MISS = Any()
    private val main = Handler(Looper.getMainLooper())
    private val memory = HashMap<String, Any>()
    private val inFlight = HashSet<String>()
    private val waiting = HashMap<String, MutableList<ImageView>>()
    private val executor = Executors.newSingleThreadExecutor()
    private var dir: File? = null
    private var apiKey = DEFAULT_KEY
    private var lastCall = 0L
    private val individualSports = setOf("tennis", "boxing", "mma", "mma-events")

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.cacheDir, "badges").apply { mkdirs() }
        apiKey = runCatching {
            context.assets.open("logo_api_key.txt").bufferedReader().readLines()
                .map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        }.getOrNull() ?: DEFAULT_KEY
    }

    fun bind(view: ImageView, team: String, sport: String) {
        val key = normalize(team)
        view.tag = key
        view.setImageDrawable(InitialsDrawable(initials(team), colorFor(key)))
        if (key.length < 2 || sport in individualSports) return
        synchronized(memory) {
            when (val cached = memory[key]) {
                is Bitmap -> { view.setImageBitmap(cached); return }
                MISS -> return
            }
            waiting.getOrPut(key) { mutableListOf() }.add(view)
            if (!inFlight.add(key)) return
        }
        val folder = dir ?: return
        val sportName = sportFor(sport)
        executor.execute {
            val hash = sha1(key)
            val png = File(folder, "$hash.png")
            val miss = File(folder, "$hash.miss")
            var bitmap: Bitmap? = if (png.exists()) BitmapFactory.decodeFile(png.path) else null
            val missFresh = miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS
            if (bitmap == null && !missFresh) {
                // Skip the network call if every card that asked for this team has scrolled away.
                val stillWanted = synchronized(memory) { waiting[key]?.any { it.tag == key } == true }
                if (!stillWanted) {
                    synchronized(memory) { inFlight.remove(key); waiting.remove(key) }
                    return@execute
                }
                val bytes = fetchBadge(key, sportName)
                bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                runCatching { if (bitmap != null && bytes != null) png.writeBytes(bytes) else miss.writeText("") }
            }
            val views: List<ImageView>
            synchronized(memory) {
                memory[key] = bitmap ?: MISS
                inFlight.remove(key)
                views = waiting.remove(key) ?: emptyList()
            }
            val ready = bitmap ?: return@execute
            main.post { views.forEach { if (it.tag == key) it.setImageBitmap(ready) } }
        }
    }

    /** Strips youth / women / reserve suffixes so "Arsenal U19" and "Arsenal W" share one crest. */
    fun normalize(name: String): String {
        // VIPRow appends flag emoji to names; drop emoji/symbols so lookups and initials use letters only.
        var n = name.replace(Regex("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]+"), " ").replace(Regex("\\s+"), " ").trim()
        repeat(2) {
            n = n.replace(Regex("\\s*\\((?:W|Women|Reserves?|Youth|II|B)\\)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:U\\d{2}|W|Women|Reserves?|II|B|Youth)$"), "")
                .trim()
        }
        return n
    }

    fun initials(name: String): String {
        val stop = setOf("fc", "cf", "sc", "ac", "afc", "club", "de", "the", "w", "women", "team", "ii")
        val words = normalize(name).split(Regex("[\\s\\-]+")).filter { it.isNotBlank() && it.lowercase() !in stop }
        return when {
            words.isEmpty() -> name.trim().take(2).uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    private fun colorFor(key: String): Int {
        val hue = ((key.hashCode() and 0x7fffffff) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.70f))
    }

    private fun sportFor(slug: String) = when (slug) {
        "football" -> "Soccer"
        "basketball" -> "Basketball"
        "american-football" -> "American Football"
        "motorsports" -> "Motorsport"
        else -> ""
    }

    private fun fetchBadge(team: String, sportName: String): ByteArray? {
        val wait = MIN_GAP_MS - (System.currentTimeMillis() - lastCall)
        if (wait > 0) Thread.sleep(wait)
        lastCall = System.currentTimeMillis()
        val query = URLEncoder.encode(team, "UTF-8")
        val body = get("https://www.thesportsdb.com/api/v1/json/$apiKey/searchteams.php?t=$query") ?: return null
        val teams = runCatching { JSONObject(String(body)).optJSONArray("teams") }.getOrNull() ?: return null
        var pick: JSONObject? = null
        for (i in 0 until teams.length()) {
            val candidate = teams.optJSONObject(i) ?: continue
            if (pick == null) pick = candidate
            if (sportName.isNotEmpty() && candidate.optString("strSport").equals(sportName, true)) { pick = candidate; break }
        }
        val chosen = pick ?: return null
        if (sportName.isNotEmpty() && !chosen.optString("strSport").equals(sportName, true)) return null
        val badge = listOf("strBadge", "strTeamBadge").map { chosen.optString(it) }
            .firstOrNull { it.startsWith("https://") } ?: return null
        return get("$badge/small") ?: get(badge)
    }

    private fun get(url: String): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "SportsWave/1.0 (Android)")
        try {
            if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
        } finally { connection.disconnect() }
    }.getOrNull()

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Circle with the team initials, used until a crest arrives or when none exists. */
class InitialsDrawable(private val label: String, private val color: Int) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val radius = minOf(b.width(), b.height()) / 2f
        fill.color = color
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, fill)
        text.textSize = radius * 0.8f
        val baseline = b.exactCenterY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, b.exactCenterX(), baseline, text)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; text.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
