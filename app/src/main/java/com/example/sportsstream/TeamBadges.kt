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
    private const val DEFAULT_KEY = "123"
    private const val MISS_TTL_MS = 7L * 24 * 3600 * 1000
    private const val MIN_GAP_MS = 2100L
    private val MISS = Any()
    private val main = Handler(Looper.getMainLooper())
    private val memory = object : LinkedHashMap<String, Any>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?) = size > 128
    }
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

    fun bind(view: ImageView, team: String, sport: String, publishedLogo: String = "") {
        val lookup = normalize(team)
        val key = "$sport|$lookup|$publishedLogo"
        view.tag = key
        view.setImageDrawable(InitialsDrawable(initials(team), colorFor(key)))
        if (lookup.length < 2 || (sport in individualSports && publishedLogo.isBlank())) return
        // Public demo API coverage is restricted. Do not queue hundreds of unsupported lookups.
        if (publishedLogo.isBlank() && apiKey == DEFAULT_KEY) return
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
            val png = File(folder, "$hash-v2.png")
            val miss = File(folder, "$hash-v2.miss")
            var bitmap: Bitmap? = if (png.exists()) BitmapFactory.decodeFile(png.path) else null
            val missFresh = miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS
            if (bitmap == null && !missFresh) {
                // Skip the network call if every card that asked for this team has scrolled away.
                val stillWanted = synchronized(memory) { waiting[key]?.any { it.tag == key } == true }
                if (!stillWanted) {
                    synchronized(memory) { inFlight.remove(key); waiting.remove(key) }
                    return@execute
                }
                val bytes = if (publishedLogo.isNotBlank()) {
                    if (UrlPolicy.allowsResource(publishedLogo, listOf("i.imgur.com", "upload.wikimedia.org"))) get(publishedLogo) else null
                } else fetchBadge(lookup, sportName)
                bitmap = bytes?.let { decode(it) }
                runCatching { if (bitmap != null) png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } else miss.writeText("") }
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

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        if (String(bytes.take(512).toByteArray(), Charsets.UTF_8).contains("<svg")) {
            val source = bytes.toString(Charsets.UTF_8)
            // No external entities, nested documents or remote SVG resources.
            require(!Regex("<!DOCTYPE|<!ENTITY|<image|<script", RegexOption.IGNORE_CASE).containsMatchIn(source))
            require(Regex("<use\\b[^>]*", RegexOption.IGNORE_CASE).findAll(source).all { tag ->
                Regex("(?:href|xlink:href)\\s*=\\s*['\"]([^'\"]+)", RegexOption.IGNORE_CASE)
                    .find(tag.value)?.groupValues?.get(1)?.startsWith("#") == true
            })
            val svg = com.caverock.androidsvg.SVG.getFromString(source)
            svg.documentWidth = 96f; svg.documentHeight = 96f
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { svg.renderToCanvas(Canvas(it)) }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth in 1..16384 && options.outHeight in 1..16384)
            options.inJustDecodeBounds = false
            while (options.outWidth / options.inSampleSize.coerceAtLeast(1) > 192 || options.outHeight / options.inSampleSize.coerceAtLeast(1) > 192)
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }.getOrNull()

    /** Keeps youth, women's and reserve identities distinct. */
    fun normalize(name: String): String {
        // VIPRow appends flag emoji to names; drop emoji/symbols so lookups and initials use letters only.
        var n = name.replace(Regex("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}]+"), " ").replace(Regex("\\s+"), " ").trim()
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
            if (EventMatching.normalize(candidate.optString("strTeam")) == EventMatching.normalize(team) &&
                sportName.isNotEmpty() && candidate.optString("strSport").equals(sportName, true)) { pick = candidate; break }
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
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "SportsWave/1.0 (Android)")
        try {
            if (connection.responseCode == 200) connection.inputStream.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1_000_000) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                bytes.takeIf { data -> data.size <= 1_000_000 }
            } else null
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
