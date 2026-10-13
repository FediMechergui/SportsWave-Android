package com.example.sportsstream

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** On-device, view-only catalogue of events exposed by public pages. Never extracts video or credentials. */
class MainActivity : AppCompatActivity() {
    private data class Sport(val name: String, val slug: String, val emoji: String)
    private data class Fixture(
        val id: String, val name: String, val league: String,
        val start: Long, val end: Long, val sport: String, val url: String = ""
    ) {
        val teams: Pair<String, String>? = name.split(TEAM_SPLIT, limit = 2).map { it.trim() }
            .takeIf { it.size == 2 && it.all { part -> part.isNotEmpty() } }?.let { it[0] to it[1] }
    }
    private data class Source(val type: String, val url: String)
    private enum class Phase { LIVE, SOON, UPCOMING, ENDED }
    private sealed class Row {
        data class Header(val title: String, val count: Int, val live: Boolean) : Row()
        data class Match(val fixture: Fixture) : Row()
        object Skeleton : Row()
        data class Message(val text: String) : Row()
    }

    companion object {
        private val TEAM_SPLIT = Regex("\\s+(?:-|–|—|vs\\.?|v)\\s+", RegexOption.IGNORE_CASE)
        private const val SOON_WINDOW = 1200L
        private const val TICK_MS = 30_000L
        private const val AUTO_RELOAD_MS = 5 * 60_000L
        private const val LOAD_TIMEOUT_MS = 25_000L
    }

    private val sports = listOf(
        Sport("Football", "football", "⚽"), Sport("Basketball", "basketball", "🏀"),
        Sport("Tennis", "tennis", "🎾"), Sport("Motorsport", "motorsports", "🏎"),
        Sport("American football", "american-football", "🏈"),
        Sport("Boxing", "boxing", "🥊"), Sport("MMA", "mma", "🥋")
    )
    private val bg = Color.rgb(9, 14, 26)
    private val surface = Color.rgb(20, 29, 46)
    private val surfaceDim = Color.rgb(15, 22, 36)
    private val surface2 = Color.rgb(30, 42, 62)
    private val divider = Color.rgb(38, 51, 74)
    private val white = Color.rgb(239, 244, 252)
    private val muted = Color.rgb(151, 163, 184)
    private val accent = Color.rgb(95, 220, 165)
    private val accentDim = Color.argb(110, 95, 220, 165)
    private val amber = Color.rgb(255, 196, 92)
    private val amberDim = Color.argb(46, 255, 196, 92)

    private lateinit var listView: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var subtitle: TextView
    private lateinit var query: EditText
    private lateinit var chips: LinearLayout
    private lateinit var tabs: LinearLayout
    private val adapter = CatalogueAdapter()
    private var catalogueId = "viprow"
    private val catalogueExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var catalogueTask: java.util.concurrent.Future<*>? = null
    private var selected = sports.first()
    private var selectedFilter = "All"
    private var allFixtures: List<Fixture> = emptyList()
    private var sources: Map<String, Source> = emptyMap()
    private var webLoader: WebView? = null
    private var generation = 0
    private var loading = false
    private var loadError: String? = null
    private var lastUpdated = 0L
    private var loadedOk = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val tick = object : Runnable {
        override fun run() {
            val stale = lastUpdated > 0 && System.currentTimeMillis() - lastUpdated > AUTO_RELOAD_MS
            if (stale && !loading) loadSchedule(silent = true) else rebuild()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        TeamBadges.init(this)
        sources = readSources()
        createUi()
        loadSchedule(silent = false)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---------- small view helpers ----------
    private fun d(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int = 18, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = d(radius).toFloat()
        if (stroke != 0) setStroke(d(1) + 1, stroke)
    }
    private fun oval(color: Int) = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(color) }
    private fun text(value: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
    private fun pad(v: View, h: Int, vert: Int) { v.setPadding(d(h), d(vert), d(h), d(vert)) }
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun pulse(v: View) {
        if (v.animation != null) return
        v.startAnimation(AlphaAnimation(1f, .35f).apply {
            duration = 750; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE
        })
    }

    // ---------- layout ----------
    private fun createUi() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        setContentView(outer)
        val catalogueChoice = TextView(this).apply {
            text = "Source: VIPRow ▾"; setTextColor(accent); textSize = 14f; setPadding(d(20), d(10), d(20), d(10))
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle("Catalogue source")
                    .setItems(arrayOf("VIPRow", "KorahLive")) { _, index ->
                        catalogueId = if (index == 0) "viprow" else "korahlive"
                        text = "Source: ${if (index == 0) "VIPRow" else "KorahLive"} ▾"
                        allFixtures = emptyList(); loadSchedule(silent = false)
                    }.show()
            }
        }
        outer.addView(catalogueChoice)
        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(12), d(20), d(6)) }
        outer.addView(header)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(top)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(brand, lp(0, -2, 1f))
        brand.addView(text("◉  SPORTSWAVE", 22f, white, true))
        subtitle = text("Loading today's events…", 12f, muted).apply { setPadding(0, d(6), 0, 0) }
        brand.addView(subtitle)
        val refresh = text("↻", 26f, accent, true).apply {
            gravity = Gravity.CENTER; background = shape(surface2, 14)
            setOnClickListener { loadSchedule(silent = allFixtures.isNotEmpty()) }
            contentDescription = "Refresh schedule"
        }
        top.addView(refresh, lp(d(46), d(46)))

        query = EditText(this).apply {
            hint = "Search teams, matches or leagues"
            setHintTextColor(muted); setTextColor(white); textSize = 14f
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = shape(surface, 16); pad(this, 16, 11)
        }
        header.addView(query, lp(-1, d(48)).apply { topMargin = d(14) })
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { rebuild() }
            override fun afterTextChanged(s: Editable?) {}
        })

        val categories = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        outer.addView(categories)
        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; pad(this, 16, 8) }
        categories.addView(chips)
        renderSportChips()

        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(18), d(4), d(18), d(6)) }
        outer.addView(tabs)
        renderTabs(0, 0, 0)

        swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(accent)
            setProgressBackgroundColorSchemeColor(surface2)
            setOnRefreshListener { loadSchedule(silent = true) }
        }
        outer.addView(swipe, lp(-1, 0, 1f))
        listView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(d(16), d(2), d(16), d(16))
            isVerticalScrollBarEnabled = false
        }
        swipe.addView(listView, ViewGroup.LayoutParams(-1, -1))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface); pad(this, 12, 8)
        }
        outer.addView(bottom)
        bottomButton(bottom, "◉ Events") { listView.smoothScrollToPosition(0) }
        bottomButton(bottom, "▶ HLS test") { openPlayer("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "hls") }
    }

    private fun bottomButton(bar: LinearLayout, caption: String, fn: () -> Unit) {
        bar.addView(text(caption, 12f, white, true).apply {
            gravity = Gravity.CENTER; setOnClickListener { fn() }
        }, lp(0, d(40), 1f))
    }

    private fun renderSportChips() {
        chips.removeAllViews()
        sports.forEach { sport ->
            val active = selected.slug == sport.slug
            val chip = text("${sport.emoji}  ${sport.name}", 12f, if (active) bg else white, true).apply {
                gravity = Gravity.CENTER; background = shape(if (active) accent else surface2, 25)
                pad(this, 14, 10)
                setOnClickListener {
                    if (selected != sport) {
                        selected = sport; selectedFilter = "All"; query.setText("")
                        renderSportChips(); loadSchedule(silent = false)
                    }
                }
            }
            chips.addView(chip, lp(-2, d(41)).apply { marginEnd = d(8) })
        }
    }

    private fun renderTabs(all: Int, live: Int, upcoming: Int) {
        tabs.removeAllViews()
        val labels = listOf("All" to all, "Live" to live, "Upcoming" to upcoming)
        labels.forEachIndexed { index, (label, count) ->
            val active = label == selectedFilter
            val caption = if (allFixtures.isEmpty()) label.uppercase() else "${label.uppercase()}  $count"
            val view = text(caption, 11f, if (active) bg else muted, true).apply {
                gravity = Gravity.CENTER; background = shape(if (active) accent else surface, 12)
                setOnClickListener {
                    if (selectedFilter != label) { selectedFilter = label; rebuild(); listView.scrollToPosition(0) }
                }
            }
            tabs.addView(view, lp(0, d(37), 1f).apply { marginEnd = if (index < labels.lastIndex) d(8) else 0 })
        }
    }

    // ---------- data ----------
    private fun readSources(): Map<String, Source> = try {
        val json = JSONObject(assets.open("playback_sources.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().mapNotNull { id ->
            val item = json.optJSONObject(id) ?: return@mapNotNull null
            val url = item.optString("url")
            val type = item.optString("type", "hls")
            if (UrlPolicy.https(url) != null && type in setOf("hls", "dash")) id to Source(type, url) else null
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadSchedule(silent: Boolean) {
        catalogueTask?.cancel(true)
        val seq = ++generation
        webLoader?.apply { stopLoading(); destroy() }
        webLoader = null
        loading = true
        loadError = null
        loadedOk = false
        if (!silent || allFixtures.isEmpty()) {
            allFixtures = emptyList()
            swipe.isRefreshing = false
            listView.scrollToPosition(0)
        } else {
            swipe.isRefreshing = true
        }
        rebuild()
        if (catalogueId == "korahlive") {
            val sport = selected.slug
            catalogueTask = catalogueExecutor.submit {
                val result = runCatching { KorahLiveCatalogue().load(sport) }
                runOnUiThread {
                    if (generation != seq || isFinishing || isDestroyed) return@runOnUiThread
                    allFixtures = result.getOrDefault(emptyList()).map {
                        Fixture(it.id, it.name, it.league, it.start, it.end, it.sport, it.url)
                    }
                    loadError = if (result.isFailure) "KorahLive unavailable. Pull down to retry."
                        else if (allFixtures.isEmpty()) "KorahLive exposes no compatible public event metadata. Channel posts are not matches."
                        else null
                    lastUpdated = System.currentTimeMillis(); loadedOk = result.isSuccess; finishLoad()
                }
            }
            return
        }
        val page = "https://viprow.im/sports/${selected.slug}/"
        val sportSlug = selected.slug
        val browser = WebView(this)
        webLoader = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        browser.settings.setSupportMultipleWindows(false)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request != null && !UrlPolicy.allowsResource(request.url.toString(), listOf("viprow.im"))

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                return if (UrlPolicy.allowsResource(url, listOf("viprow.im"))) null
                else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (generation != seq || url?.startsWith(page) != true) return
                fun collect(attempt: Int) {
                    if (generation != seq || webLoader !== browser || isFinishing || isDestroyed) return
                val script = """(function(){try{let p=window.ftbPageData;if(!p||!p.schedule)return '';let leagues=p.league||p.leagueList||{};let rows=Array.isArray(p.schedule)?p.schedule:Object.values(p.schedule);let links=[...document.querySelectorAll('a[href]')];return JSON.stringify(rows.map(x=>({id:x.id,name:x.name,start:x.start,end:x.end,url:links.find(a=>new URL(a.href).pathname.split('/').includes(String(x.id)))?.href||'',league:leagues[x.league_id]?.name||'Other competition'})));}catch(e){return ''}})()"""
                browser.evaluateJavascript(script) { raw ->
                    if (generation != seq || isFinishing || isDestroyed) return@evaluateJavascript
                    try {
                        val decoded = JSONArray("[$raw]").getString(0)
                        val arr = JSONArray(decoded)
                        val missingLinks = (0 until arr.length()).any { arr.optJSONObject(it)?.optString("url").isNullOrBlank() }
                        if (missingLinks && attempt < 8) {
                            browser.postDelayed({ collect(attempt + 1) }, 400L)
                            return@evaluateJavascript
                        }
                        allFixtures = (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                            val id = obj.optString("id")
                            if (id.isBlank()) null else Fixture(
                                id, obj.optString("name", "Event $id"),
                                obj.optString("league", "Other competition"), obj.optLong("start"),
                                obj.optLong("end"), sportSlug, obj.optString("url")
                            )
                        }.sortedBy { it.start }
                        lastUpdated = System.currentTimeMillis()
                        loadedOk = true
                    } catch (_: Exception) {
                        if (attempt < 8) {
                            browser.postDelayed({ collect(attempt + 1) }, 400L)
                            return@evaluateJavascript
                        }
                        if (allFixtures.isEmpty()) loadError = "Catalogue unavailable right now. Pull down to retry."
                    }
                    finishLoad()
                    if (webLoader === browser) { webLoader = null; browser.destroy() }
                }
                }
                collect(0)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true && generation == seq) {
                    loadError = "Website unavailable. Check your connection and pull down to retry."
                    finishLoad()
                }
            }
        }
        browser.loadUrl(page)
        handler.postDelayed({
            if (generation == seq && loading) {
                webLoader?.apply { stopLoading(); destroy() }
                webLoader = null
                loadError = "The schedule took too long to load. Pull down to retry."
                finishLoad()
            }
        }, LOAD_TIMEOUT_MS)
    }

    private fun finishLoad() {
        loading = false
        swipe.isRefreshing = false
        rebuild()
    }

    private fun nowSec() = System.currentTimeMillis() / 1000L

    private fun phase(f: Fixture, now: Long): Phase = when {
        f.start <= 0L -> Phase.UPCOMING
        f.end in 1 until now -> Phase.ENDED
        f.start <= now -> Phase.LIVE
        f.start - now <= SOON_WINDOW -> Phase.SOON
        else -> Phase.UPCOMING
    }

    private fun dayLabel(sec: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochSecond(sec).atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(LocalDate.now(zone), day)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date(sec * 1000L))
        }
    }

    private fun duration(sec: Long): String {
        val minutes = (sec.coerceAtLeast(0) + 59) / 60
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
    }

    private fun relative(f: Fixture, p: Phase, now: Long): String = when (p) {
        Phase.LIVE -> "${duration(now - f.start)} in"
        Phase.SOON -> "in ${duration(f.start - now)}"
        Phase.UPCOMING -> when {
            f.start <= 0L -> "TBC"
            f.start - now < 86_400 -> "in ${duration(f.start - now)}"
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(f.start * 1000L))
        }
        Phase.ENDED -> "Ended"
    }

    private fun rebuild() {
        if (!::listView.isInitialized) return
        val now = nowSec()
        val search = query.text.toString().trim()
        val matched = allFixtures.filter { it.name.contains(search, true) || it.league.contains(search, true) }
        val phases = matched.associate { it.id to phase(it, now) }
        fun ofPhase(vararg wanted: Phase) = matched.filter { phases[it.id] in wanted }
        val liveNow = ofPhase(Phase.LIVE, Phase.SOON)
        val upcomingAll = ofPhase(Phase.SOON, Phase.UPCOMING)
        val laterOnly = ofPhase(Phase.UPCOMING)
        val ended = ofPhase(Phase.ENDED).sortedByDescending { it.start }

        val rows = mutableListOf<Row>()
        fun section(title: String, list: List<Fixture>, live: Boolean = false) {
            if (list.isEmpty()) return
            rows += Row.Header(title, list.size, live)
            list.forEach { rows += Row.Match(it) }
        }
        fun byDay(list: List<Fixture>) =
            list.groupBy { dayLabel(it.start) }.forEach { (label, items) -> section(label, items) }

        when {
            loading && allFixtures.isEmpty() -> repeat(5) { rows += Row.Skeleton }
            allFixtures.isEmpty() -> rows += Row.Message(loadError ?: if (loadedOk) "No ${selected.name.lowercase()} events are listed right now. Pull down to check again." else "No events loaded. Pull down to retry.")
            else -> {
                when (selectedFilter) {
                    "Live" -> section("Live now", liveNow, live = true)
                    "Upcoming" -> byDay(upcomingAll)
                    else -> { section("Live now", liveNow, live = true); byDay(laterOnly); section("Finished", ended) }
                }
                if (rows.isEmpty()) rows += Row.Message(
                    when {
                        search.isNotEmpty() -> "No matches for “$search”."
                        selectedFilter == "Live" -> "Nothing live right now. Check Upcoming."
                        else -> "No events in this view."
                    }
                )
            }
        }
        adapter.submit(rows)
        renderTabs(matched.size, liveNow.size, upcomingAll.size)
        subtitle.text = when {
            loading && allFixtures.isEmpty() -> "Loading ${selected.name.lowercase()} events…"
            allFixtures.isEmpty() -> "No schedule loaded"
            else -> {
                val updated = if (lastUpdated > 0) " · updated ${timeFormat.format(Date(lastUpdated))}" else ""
                "${liveNow.size} live · ${allFixtures.size} events$updated"
            }
        }
    }

    private fun open(f: Fixture) {
        if (f.url.isBlank()) { subtitle.text = "No published event link available"; return }
        val source = sources[f.id]
        startActivity(Intent(this, ResolveActivity::class.java).apply {
            putExtra("event_id", f.id)
            putExtra("event_name", f.name)
            putExtra("event_url", f.url)
            source?.let { putExtra("native_url", it.url); putExtra("native_type", it.type) }
        })
    }

    private fun openPlayer(url: String, type: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("playback_url", url); putExtra("playback_type", type)
        })
    }

    override fun onDestroy() {
        catalogueTask?.cancel(true)
        catalogueExecutor.shutdownNow()
        generation++
        handler.removeCallbacksAndMessages(null)
        webLoader?.apply { stopLoading(); destroy() }
        webLoader = null
        super.onDestroy()
    }

    // ---------- list ----------
    private inner class CatalogueAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var rows: List<Row> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(next: List<Row>) { rows = next; notifyDataSetChanged() }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.Match -> 1
            Row.Skeleton -> 2
            is Row.Message -> 3
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
            0 -> HeaderHolder()
            1 -> MatchHolder()
            2 -> SkeletonHolder()
            else -> MessageHolder()
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row)
                is Row.Match -> (holder as MatchHolder).bind(row.fixture, nowSec())
                Row.Skeleton -> (holder as SkeletonHolder).bind()
                is Row.Message -> (holder as MessageHolder).bind(row)
            }
        }
    }

    private inner class HeaderHolder : RecyclerView.ViewHolder(LinearLayout(this@MainActivity)) {
        private val dot = View(this@MainActivity).apply { background = oval(accent) }
        private val title = text("", 12.5f, white, true).apply { letterSpacing = .08f }
        private val count = text("", 12f, muted, true)

        init {
            val row = itemView as LinearLayout
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(-1, -2)
            row.setPadding(d(4), d(16), d(4), d(10))
            row.addView(dot, lp(d(8), d(8)).apply { marginEnd = d(9) })
            row.addView(title, lp(0, -2, 1f))
            row.addView(count, lp(-2, -2))
        }

        fun bind(h: Row.Header) {
            title.text = h.title.uppercase()
            title.setTextColor(if (h.live) accent else white)
            count.text = h.count.toString()
            dot.visibility = if (h.live) View.VISIBLE else View.GONE
            if (h.live) pulse(dot) else dot.clearAnimation()
        }
    }

    private inner class MatchHolder : RecyclerView.ViewHolder(LinearLayout(this@MainActivity)) {
        private val card = itemView as LinearLayout
        private val league = text("", 10.5f, muted, true).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; letterSpacing = .06f
        }
        private val pill = text("", 9.5f, bg, true).apply {
            setPadding(d(9), d(4), d(9), d(4)); letterSpacing = .08f
        }
        private val homeBadge = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        private val awayBadge = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        private val homeName = teamText()
        private val awayName = teamText()
        private val pairBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        private val single = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        private val singleIcon = text("", 17f).apply { gravity = Gravity.CENTER; background = oval(surface2) }
        private val singleTitle = text("", 15.5f, white, true).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        private val timeBig = text("", 17f, white, true).apply { gravity = Gravity.CENTER }
        private val timeSmall = text("", 11f, muted, true).apply { gravity = Gravity.CENTER }
        private val action = text("", 11.5f, bg, true).apply { gravity = Gravity.CENTER; letterSpacing = .05f }
        private var fixture: Fixture? = null

        private fun teamText() = text("", 15f, white, true).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        }

        private fun teamRow(badge: ImageView, name: TextView) = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(badge, lp(d(28), d(28)).apply { marginEnd = d(11) })
            addView(name, lp(0, -2, 1f))
        }

        init {
            card.orientation = LinearLayout.VERTICAL
            card.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = d(10) }
            card.setPadding(d(15), d(13), d(15), d(14))

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(league, lp(0, -2, 1f).apply { marginEnd = d(8) })
            top.addView(pill, lp(-2, -2))
            card.addView(top)

            val body = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(body, lp(-1, -2).apply { topMargin = d(13) })
            val left = FrameLayout(this@MainActivity)
            body.addView(left, lp(0, -2, 1f))
            pairBox.addView(teamRow(homeBadge, homeName), lp(-1, -2))
            pairBox.addView(teamRow(awayBadge, awayName), lp(-1, -2).apply { topMargin = d(9) })
            left.addView(pairBox, FrameLayout.LayoutParams(-1, -2))
            single.addView(singleIcon, lp(d(40), d(40)).apply { marginEnd = d(12) })
            single.addView(singleTitle, lp(0, -2, 1f))
            left.addView(single, FrameLayout.LayoutParams(-1, -2))

            body.addView(View(this@MainActivity).apply { setBackgroundColor(divider) },
                lp(d(1), d(46)).apply { marginStart = d(12); marginEnd = d(12) })
            val timeCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            timeCol.addView(timeBig, lp(-2, -2))
            timeCol.addView(timeSmall, lp(-2, -2).apply { topMargin = d(5) })
            body.addView(timeCol, lp(d(76), -2))

            card.addView(action, lp(-1, d(38)).apply { topMargin = d(13) })
            card.setOnClickListener { fixture?.let { open(it) } }
            action.setOnClickListener { fixture?.let { open(it) } }
        }

        fun bind(f: Fixture, now: Long) {
            fixture = f
            val p = phase(f, now)
            league.text = f.league.uppercase()
            val teams = f.teams
            if (teams != null) {
                pairBox.visibility = View.VISIBLE; single.visibility = View.GONE
                homeName.text = teams.first; awayName.text = teams.second
                TeamBadges.bind(homeBadge, teams.first, f.sport)
                TeamBadges.bind(awayBadge, teams.second, f.sport)
            } else {
                pairBox.visibility = View.GONE; single.visibility = View.VISIBLE
                singleIcon.text = sports.firstOrNull { it.slug == f.sport }?.emoji ?: "🏆"
                singleTitle.text = f.name
            }
            timeBig.text = if (f.start > 0) timeFormat.format(Date(f.start * 1000L)) else "--:--"
            timeSmall.text = relative(f, p, now)
            card.alpha = if (p == Phase.ENDED) .72f else 1f

            when (p) {
                Phase.LIVE -> {
                    pill.text = "● LIVE"; pill.setTextColor(bg); pill.background = shape(accent, 20); pulse(pill)
                    card.background = shape(surface, 18, accentDim); timeSmall.setTextColor(accent)
                }
                Phase.SOON -> {
                    pill.clearAnimation()
                    pill.text = "STARTING SOON"; pill.setTextColor(amber); pill.background = shape(amberDim, 20)
                    card.background = shape(surface, 18); timeSmall.setTextColor(amber)
                }
                Phase.UPCOMING -> {
                    pill.clearAnimation()
                    pill.text = "UPCOMING"; pill.setTextColor(muted); pill.background = shape(surface2, 20)
                    card.background = shape(surface, 18); timeSmall.setTextColor(muted)
                }
                Phase.ENDED -> {
                    pill.clearAnimation()
                    pill.text = "ENDED"; pill.setTextColor(muted); pill.background = shape(surface2, 20)
                    card.background = shape(surfaceDim, 18); timeSmall.setTextColor(muted)
                }
            }

            val source = sources[f.id]
            val (caption, filled) = when {
                f.url.isBlank() -> "PLAYER LINK UNAVAILABLE" to false
                source != null -> "PLAY ${source.type.uppercase()}  ▶" to true
                p == Phase.LIVE || p == Phase.SOON -> "WATCH LIVE  ▶" to true
                p == Phase.UPCOMING -> "CHOOSE PLAYER" to false
                else -> "VIEW PLAYERS" to false
            }
            action.text = caption
            action.isEnabled = f.url.isNotBlank()
            card.isEnabled = f.url.isNotBlank()
            action.setTextColor(if (filled) bg else accent)
            action.background = shape(if (filled) accent else surface2, 11)
        }
    }

    private inner class SkeletonHolder : RecyclerView.ViewHolder(LinearLayout(this@MainActivity)) {
        init {
            val card = itemView as LinearLayout
            card.orientation = LinearLayout.VERTICAL
            card.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = d(10) }
            card.background = shape(surface)
            card.setPadding(d(15), d(15), d(15), d(15))
            fun bar(width: Int, height: Int, top: Int) = card.addView(
                View(this@MainActivity).apply { background = shape(surface2, 6) },
                lp(if (width < 0) width else d(width), d(height)).apply { topMargin = d(top) }
            )
            bar(120, 10, 0); bar(210, 18, 16); bar(170, 18, 12); bar(-1, 38, 16)
        }

        fun bind() = pulse(itemView)
    }

    private inner class MessageHolder : RecyclerView.ViewHolder(LinearLayout(this@MainActivity)) {
        private val label = text("", 15f, muted).apply { gravity = Gravity.CENTER }
        private val retry = text("RETRY", 11.5f, bg, true).apply {
            gravity = Gravity.CENTER; background = shape(accent, 11)
            setOnClickListener { loadSchedule(silent = false) }
        }

        init {
            val box = itemView as LinearLayout
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.layoutParams = RecyclerView.LayoutParams(-1, -2)
            box.setPadding(d(16), d(60), d(16), d(20))
            box.addView(label, lp(-1, -2))
            box.addView(retry, lp(d(140), d(38)).apply { topMargin = d(18) })
        }

        fun bind(m: Row.Message) {
            label.text = m.text
            retry.visibility = if (allFixtures.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
