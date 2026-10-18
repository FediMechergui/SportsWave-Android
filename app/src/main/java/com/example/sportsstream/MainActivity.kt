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
        val start: Long, val end: Long, val sport: String, val url: String = "",
        val homeScore: Int? = null, val awayScore: Int? = null,
        val homeLogo: String = "", val awayLogo: String = "",
        val sourceId: String = "viprow", val receivedAt: Long = System.currentTimeMillis(),
        val status: String = "unknown", val memberIds: Set<String> = emptySet(),
        val entrants: List<EventEntrant> = emptyList()
    ) {
        val teams: Pair<String, String>? = name.split(TEAM_SPLIT, limit = 2).map { it.trim() }
            .takeIf { sport !in setOf("motorsports","horse-racing","golf","cycling") && it.size == 2 && it.all { part -> part.isNotEmpty() } }?.let { it[0] to it[1] }
    }
    private fun Fixture.event() = SportEvent(id, sourceId, name, league, start, end, sport, url,
        teams?.first.orEmpty(), teams?.second.orEmpty(), homeScore, awayScore, homeLogo, awayLogo, receivedAt, status, entrants)
    private fun fixture(event: SportEvent) = Fixture(event.id, event.name, event.league, event.start,
        event.end, event.sport, event.url, event.homeScore, event.awayScore, event.homeLogo, event.awayLogo,
        event.catalogueId, event.receivedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), event.status, entrants = event.entrants)
    private data class Source(val type: String, val url: String)
    private enum class Phase { LIVE, SOON, UPCOMING, ENDED, UNKNOWN }
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
        Sport("Boxing", "boxing", "🥊"), Sport("MMA", "mma-events", "🥋"),
        Sport("Ice hockey", "ice-hockey", ""),
        Sport("Rugby", "rugby", ""),
        Sport("Baseball", "baseball", ""),
        Sport("Aussie rules", "aussie-rules", ""),
        Sport("Golf", "golf", ""),
        Sport("Darts", "darts", ""),
        Sport("Snooker", "snooker", ""),
        Sport("Volleyball", "volleyball", ""),
        Sport("Handball", "handball", ""),
        Sport("Horse racing", "horse-racing", ""),
        Sport("Cycling", "cycling", ""),
        Sport("More sports", "more-sports", "")
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
    private lateinit var health: TextView
    private lateinit var query: EditText
    private lateinit var chips: LinearLayout
    private lateinit var tabs: LinearLayout
    private val adapter = CatalogueAdapter()
    private var catalogueId = "all"
    private var sourceStatus = ""
    private val catalogueExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var catalogueTask: java.util.concurrent.Future<*>? = null
    private var selected = sports.first()
    private var selectedFilter = "All"
    private var favouritesOnly = false
    private val favouriteIds by lazy { getPreferences(MODE_PRIVATE).getStringSet("favourite_matches", emptySet())!!.toMutableSet() }
    private var sortMode = "Relevance"
    private var selectedDay: LocalDate? = null
    private var previousFixtures: List<Fixture> = emptyList()
    private var uniqueCount = 0
    private var allFixtures: List<Fixture> = emptyList()
    private var mergedInput: List<Fixture>? = null
    private var mergedFixtures: List<Fixture> = emptyList()
    private var sources: Map<String, Source> = emptyMap()
    private var webLoader: WebView? = null
    private var generation = 0
    private var loading = false
    private var loadError: String? = null
    private var lastUpdated = 0L
    private var lastAttempt = 0L
    private var loadedOk = false
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val tick = object : Runnable {
        override fun run() {
            val stale = lastUpdated > 0 && System.currentTimeMillis() - lastUpdated > AUTO_RELOAD_MS
            val retryDue = System.currentTimeMillis() - lastAttempt >= AUTO_RELOAD_MS
            if (stale && retryDue && !loading) loadSchedule(silent = true) else rebuild()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { saved ->
            selected = sports.firstOrNull { it.slug == saved.getString("sport") } ?: selected
            catalogueId = saved.getString("catalogue", "all")
            selectedDay = runCatching { LocalDate.parse(saved.getString("day")) }.getOrNull()
            selectedFilter = saved.getString("filter", "All")
        }
        window.statusBarColor = bg
        window.navigationBarColor = bg
        TeamBadges.init(this)
        sources = readSources()
        createUi()
        savedInstanceState?.getString("query")?.let { query.setText(it) }
        loading = true
        rebuild()
        // Draw native content before initializing the cold WebView provider.
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(object : android.view.ViewTreeObserver.OnDrawListener {
            private var started = false
            override fun onDraw() {
                if (started) return
                started = true
                decor.post {
                    if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(this)
                    if (!isFinishing && !isDestroyed) loadSchedule(silent = false)
                }
            }
        })
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sport",selected.slug);outState.putString("catalogue",catalogueId)
        outState.putString("day",selectedDay?.toString());outState.putString("filter",selectedFilter)
        outState.putString("query",query.text.toString())
        super.onSaveInstanceState(outState)
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
            val ids=listOf("all", "viprow", "korahlive", "livekora", "espn", "filgoal", "filgoal")
            val names=listOf("All public sources", "VIPRow", "KorahLive", "Livekora", "ESPN", "FilGoal", "Tunisia · FilGoal")
            text = "Schedules: ${names[ids.indexOf(catalogueId).coerceAtLeast(0)]} ▾"; setTextColor(accent); textSize = 14f; setPadding(d(20), d(10), d(20), d(10))
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle("Catalogue source")
                    .setItems(names.toTypedArray()) { _, index ->
                        catalogueId = ids[index]
                        text = "Schedules: ${names[index]} ▾"
                        if(index==6) { selected=sports.first();selectedFilter="All";query.setText("Tunisia");renderSportChips() }
                        else query.setText("")
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
        brand.addView(text("SportsWave", 24f, white, true))
        subtitle = text("Loading today's events…", 12f, muted).apply { setPadding(0, d(6), 0, 0) }
        brand.addView(subtitle)
        health = text("", 11f, amber).apply { maxLines = 2; setPadding(0, d(5), 0, 0) }
        header.addView(health)
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
        header.addView(query, lp(-1, d(42)).apply { topMargin = d(9) })
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
        outer.addView(TextView(this).apply {
            text = "Sort: World events · Tunisia · major leagues"; setTextColor(accent); textSize = 13f
            setPadding(d(20), d(8), d(20), d(8))
            setOnClickListener {
                val modes = arrayOf("Relevance", "Kickoff", "League", "Favourites")
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sort events")
                    .setItems(modes) { _, index ->
                        favouritesOnly = modes[index] == "Favourites"
                        if (!favouritesOnly) sortMode = modes[index]
                        text = if (favouritesOnly) "Favourites only" else "Sort: $sortMode"; rebuild()
                    }.show()
            }
        })
        val dateRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(d(16), 0, d(16), 0) }
        val dateLabel = text(selectedDay?.toString() ?: "All dates ▾", 14f, white, true).apply { gravity = Gravity.CENTER }
        val weekDays = LinearLayout(this).apply { setPadding(d(16),0,d(16),d(6)) }
        fun paintWeek() {
            weekDays.removeAllViews()
            val anchor = selectedDay ?: LocalDate.now()
            (-3L..3L).forEach { offset ->
                val day = anchor.plusDays(offset)
                weekDays.addView(text(day.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)),12f,
                    if(day == selectedDay) bg else white, true).apply {
                    gravity = Gravity.CENTER; background = shape(if(day == selectedDay) accent else surface2,12)
                    contentDescription = "Schedule for $day"
                    setOnClickListener { selectedDay = day; dateLabel.text = day.toString(); paintWeek(); loadSchedule(silent = false) }
                },lp(d(80),d(44)).apply { marginEnd = d(6) })
            }
        }
        fun updateDay() { dateLabel.text = selectedDay?.toString() ?: "All dates"; paintWeek(); loadSchedule(silent = false) }

        dateRow.addView(text("‹", 25f, accent).apply { gravity = Gravity.CENTER; contentDescription = "Previous day"; setOnClickListener { selectedDay = (selectedDay ?: LocalDate.now()).minusDays(1); updateDay() } }, lp(d(48), d(48)))
        dateRow.addView(dateLabel, lp(0, d(48), 1f))
        dateLabel.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Schedule date")
                .setItems(arrayOf("All dates", "Today", "Choose date")) { _, choice ->
                    if (choice < 2) { selectedDay = if (choice == 0) null else LocalDate.now(); updateDay() }
                    else { val day = selectedDay ?: LocalDate.now(); android.app.DatePickerDialog(this, { _, year, month, date -> selectedDay = LocalDate.of(year, month + 1, date); updateDay() }, day.year, day.monthValue - 1, day.dayOfMonth).show() }
                }.show()
        }
        dateRow.addView(text("›", 25f, accent).apply { gravity = Gravity.CENTER; contentDescription = "Next day"; setOnClickListener { selectedDay = (selectedDay ?: LocalDate.now()).plusDays(1); updateDay() } }, lp(d(48), d(48)))
        outer.addView(dateRow)
        outer.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(weekDays) })
        paintWeek()
        outer.addView(text("Times: ${ZoneId.systemDefault().id} | Players: Arabic / English only",10f,muted).apply { setPadding(d(20),0,d(20),d(5)) })
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
        bottomButton(bottom, "TV & watch") { showBroadcasts() }
        bottomButton(bottom, "Sources") {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Public source coverage")
                .setMessage("English: VIPRow published event links and ESPN public multi-sport scoreboards.\nArabic: FilGoal football fixtures, Livekora and KorahLive published metadata.\n\n$sourceStatus\n${loadError.orEmpty()}\n\nPlayer choices require explicit Arabic or English commentary metadata; unknown and other languages are hidden. ESPN and FilGoal schedules do not supply player links. Scores may be delayed. Calendar times use your device timezone.")
                .setNeutralButton("Channel servers") { _, _ -> showChannels() }
                .setPositiveButton("Close", null).show()
        }
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
            val chip = text(sport.name, 12f, if (active) bg else white, true).apply {
                gravity = Gravity.CENTER; background = shape(if (active) accent else surface2, 25)
                val icon = getDrawable(SportIcons.resource(sport.slug))!!.mutate()
                icon.setTint(if(active) bg else white); icon.setBounds(0,0,d(19),d(19))
                setCompoundDrawablesRelative(icon,null,null,null); compoundDrawablePadding = d(7)
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
            val displayLabel = if (label == "Live") "Now" else label
            val caption = if (allFixtures.isEmpty()) displayLabel else "$displayLabel  $count"
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
        lastAttempt = System.currentTimeMillis()
        catalogueTask?.cancel(true)
        val seq = ++generation
        val requestedDay = selectedDay
        previousFixtures = allFixtures
        sourceStatus = ""
        webLoader?.apply { disposeBrowser(this) }
        webLoader = null
        loading = true
        loadError = null
        loadedOk = false
        if (!silent || allFixtures.isEmpty()) {
            val snapshot = snapshotFile()
            val saved = if(snapshot.exists()) ScheduleSnapshot.read(snapshot)
                else if(selectedDay == null) ScheduleSnapshot.read(java.io.File(filesDir,"schedule-${catalogueId}-${selected.slug}.json"))
                else emptyList()
            allFixtures = saved.filter { it.catalogueId in setOf("viprow","espn","filgoal","livekora","korahlive") }.map { fixture(it) }
            previousFixtures = allFixtures
            lastUpdated = allFixtures.maxOfOrNull { it.receivedAt } ?: 0L
            swipe.isRefreshing = false
            listView.scrollToPosition(0)
        } else {
            swipe.isRefreshing = true
        }
        rebuild()
        if (catalogueId == "all") {
            val sport = selected.slug
            catalogueTask = catalogueExecutor.submit {
                val providers = buildList<CatalogueProvider> {
                    if(EspnCatalogue.routes(sport).isNotEmpty()) add(EspnCatalogue())
                    if(sport == "football") { add(FilGoalCatalogue()); add(LivekoraCatalogue()); add(KorahLiveCatalogue()) }
                }
                val statuses = mutableListOf<String>()
                val workers = java.util.concurrent.Executors.newFixedThreadPool(3)
                val completed = java.util.concurrent.ExecutorCompletionService<Pair<CatalogueProvider, Result<List<SportEvent>>>>(workers)
                try {
                    providers.forEach { provider -> completed.submit(java.util.concurrent.Callable {
                        provider to runCatching { provider.load(sport, requestedDay) }
                    }) }
                    repeat(providers.size) {
                        if (Thread.currentThread().isInterrupted) return@submit
                        val (provider, result) = try { completed.take().get() }
                            catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); return@submit }
                    statuses += "${provider.id}: " + if (result.isSuccess) "${result.getOrThrow().size} events" else "unavailable"
                    val statusText = statuses.joinToString(" \u00b7 ")
                    runOnUiThread {
                        if (generation != seq || isFinishing || isDestroyed) return@runOnUiThread
                        allFixtures = CatalogueRefresh.replaceSource(allFixtures.map { it.event() }, provider.id,
                            result.getOrNull()).map { fixture(it) }
                        lastUpdated = CatalogueRefresh.receivedAt(allFixtures.map { it.event() })
                        sourceStatus = statusText
                        saveSnapshot()
                        rebuild()
                    }
                    }
                } finally { workers.shutdownNow() }
            }
        }
        if (catalogueId != "viprow" && catalogueId != "all") {
            val provider: CatalogueProvider = when (catalogueId) { "livekora" -> LivekoraCatalogue(); "openliga" -> OpenLigaCatalogue(); "espn" -> EspnCatalogue(); "filgoal" -> FilGoalCatalogue(); else -> KorahLiveCatalogue() }
            val sourceName = when (catalogueId) { "livekora" -> "Livekora"; "openliga" -> "OpenLigaDB"; "espn" -> "ESPN"; "filgoal" -> "FilGoal"; else -> "KorahLive" }
            val sport = selected.slug
            catalogueTask = catalogueExecutor.submit {
                val result = runCatching { provider.load(sport, requestedDay) }
                runOnUiThread {
                    if (generation != seq || isFinishing || isDestroyed) return@runOnUiThread
                    allFixtures = if (result.isFailure) previousFixtures else result.getOrDefault(emptyList()).map { fixture(it) }
                    loadError = if (result.isFailure) "$sourceName unavailable. Pull down to retry."
                        else if (allFixtures.isEmpty() && provider.id == "livekora") "Livekora currently publishes no ${selected.name.lowercase()} matches. Published channels are available in Channels."
                        else if (allFixtures.isEmpty()) "$sourceName lists no compatible ${selected.name.lowercase()} events. Channel posts are not matches."
                        else null
                    lastUpdated = CatalogueRefresh.receivedAt(allFixtures.map { it.event() }); loadedOk = result.isSuccess; finishLoad()
                }
            }
            return
        }
        val page = "https://viprow.im/sports/${selected.slug}/"
        val sportSlug = selected.slug
        BrowserStartup.prepare(this) { ready ->
            if(generation != seq || !loading || isFinishing || isDestroyed) return@prepare
            if(ready) loadViprow(page,sportSlug,seq)
            else { loadError = "Web player engine unavailable. Other schedule sources remain available."; finishLoad() }
        }
        handler.postDelayed({
            if(generation == seq && loading && webLoader == null) {
                loadError = "Web player engine is still preparing. Pull down to retry; other sources remain available."
                finishLoad()
            }
        },15000L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadViprow(page: String, sportSlug: String, seq: Int) {
        val browser = WebView(this)
        browser.isFocusable = false
        browser.isFocusableInTouchMode = false
        browser.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webLoader = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        browser.settings.setSupportMultipleWindows(false)
        browser.webViewClient = object : WebViewClient() {
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                if (request?.isForMainFrame == true && generation == seq && webLoader === browser) {
                    loadError = "VIPRow returned HTTP ${response?.statusCode ?: 0}. Try another catalogue or retry later."
                    finishLoad()
                    browser.stopLoading()
                    webLoader = null
                    handler.post { disposeBrowser(browser) }
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request != null && !UrlPolicy.allowsResource(request.url.toString(), listOf("viprow.im"))

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                return if (UrlPolicy.allowsResource(url, listOf("viprow.im"))) null
                else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (generation != seq || webLoader !== browser || !loading || url?.startsWith(page) != true) return
                fun collect(attempt: Int) {
                    if (generation != seq || webLoader !== browser || isFinishing || isDestroyed) return
                val script = """(function(){try{let p=window.ftbPageData;if(!p||!p.schedule)return '';let leagues=p.league||p.leagueList||{};let rows=Array.isArray(p.schedule)?p.schedule:Object.values(p.schedule);let links=[...document.querySelectorAll('a[href]')];return JSON.stringify(rows.map(x=>({id:x.id,name:x.name,start:x.start,end:x.end,url:links.find(a=>new URL(a.href).pathname.split('/').includes(String(x.id)))?.href||'',league:leagues[x.league_id]?.name||'Other competition'})));}catch(e){return ''}})()"""
                browser.evaluateJavascript(script) { raw ->
                    if (generation != seq || webLoader !== browser || !loading || isFinishing || isDestroyed) return@evaluateJavascript
                    try {
                        val decoded = JSONArray("[$raw]").getString(0)
                        val arr = JSONArray(decoded)
                        val missingLinks = (0 until arr.length()).any { arr.optJSONObject(it)?.optString("url").isNullOrBlank() }
                        if (missingLinks && attempt < 8) {
                            browser.postDelayed({ collect(attempt + 1) }, 400L)
                            return@evaluateJavascript
                        }
                        val incoming = (0 until arr.length()).mapNotNull { i ->
                            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                            val id = obj.optString("id")
                            if (id.isBlank()) null else Fixture(
                                id, obj.optString("name", "Event $id"),
                                obj.optString("league", "Other competition"), obj.optLong("start"),
                                obj.optLong("end"), sportSlug, obj.optString("url")
                            )
                        }
                        allFixtures = CatalogueRefresh.replaceSource(allFixtures.map { it.event() }, "viprow",
                            incoming.map { it.event() }).map { fixture(it) }.sortedBy { it.start }
                        lastUpdated = CatalogueRefresh.receivedAt(allFixtures.map { it.event() })
                        loadedOk = true
                    } catch (_: Exception) {
                        if (attempt < 8) {
                            browser.postDelayed({ collect(attempt + 1) }, 400L)
                            return@evaluateJavascript
                        }
                        if (allFixtures.isEmpty()) loadError = "Catalogue unavailable right now. Pull down to retry."
                    }
                    finishLoad()
                    if (webLoader === browser) { webLoader = null; disposeBrowser(browser) }
                }
                }
                collect(0)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true && generation == seq && webLoader === browser) {
                    loadError = "Website unavailable. Check your connection and pull down to retry."
                    finishLoad()
                    if (webLoader === browser) {
                        webLoader = null
                        handler.post { disposeBrowser(browser) }
                    }
                }
            }
        }
        // Attached WebViews participate in the Android/Chromium rendering lifecycle.
        findViewById<FrameLayout>(android.R.id.content).addView(browser, FrameLayout.LayoutParams(1, 1))
        browser.loadUrl(page)
        handler.postDelayed({
            if (generation == seq && loading) {
                val expired = webLoader
                webLoader = null
                expired?.let { disposeBrowser(it) }
                loadError = "The schedule took too long to load. Pull down to retry."
                finishLoad()
            }
        }, LOAD_TIMEOUT_MS)
    }

    private fun disposeBrowser(browser: WebView) {
        (browser.parent as? ViewGroup)?.removeView(browser)
        browser.stopLoading()
        browser.destroy()
    }

    private fun showChannels() {
        val channels = runCatching {
            val data = assets.open("channels.json").bufferedReader().use { JSONObject(it.readText()).getJSONArray("channels") }
            (0 until data.length()).mapNotNull { index ->
                val item = data.getJSONObject(index)
                val url = item.optString("url")
                if (UrlPolicy.allowsResource(url, listOf("pl.koralive1.cc"))) item.optString("name") to url else null
            }
        }.getOrDefault(emptyList())
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Channels")
            .setItems(channels.map { it.first }.toTypedArray()) { _, index ->
                val (name, url) = channels[index]
                startActivity(Intent(this, ResolveActivity::class.java).apply {
                    putExtra("event_name", "$name - channel")
                    putExtra("event_url", url)
                })
            }.setNegativeButton("Close", null).show()
    }

    private fun finishLoad() {
        loading = false
        swipe.isRefreshing = false
        saveSnapshot()
        rebuild()
    }

    private fun nowSec() = System.currentTimeMillis() / 1000L

    private fun snapshotFile() = java.io.File(filesDir, "schedule-${catalogueId}-${selected.slug}-${selectedDay ?: "current"}.json")
    private fun saveSnapshot() {
        ScheduleSnapshot.save(snapshotFile(), allFixtures.map { it.event() })
    }

    private fun phase(f: Fixture, now: Long): Phase = when {
        f.status == "finished" -> Phase.ENDED
        f.status in setOf("postponed", "cancelled") -> Phase.UNKNOWN
        f.status == "live" -> Phase.LIVE
        f.sourceId in setOf("espn", "filgoal") && f.start <= now -> Phase.UNKNOWN
        f.start <= 0L -> Phase.UPCOMING
        f.end in 1 until now -> Phase.ENDED
        f.id.startsWith("livekora|") && now - f.start >= 4 * 3600 -> Phase.ENDED
        f.sourceId == "openliga" && f.start <= now -> Phase.UNKNOWN
        f.end == 0L && now - f.start >= 6 * 3600 -> Phase.UNKNOWN
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
        Phase.UNKNOWN -> "Status unavailable"
    }

    private fun rebuild() {
        if (!::listView.isInitialized) return
        val now = nowSec()
        val search = query.text.toString().trim()
        if (mergedInput !== allFixtures) {
            TeamLogoIndex.remember(allFixtures.map { it.event() })
            val merged = EventAggregation.merge(allFixtures.map { it.event() })
            mergedFixtures = merged.map { group -> fixture(group.event).copy(memberIds = group.members.map { it.catalogueId + "|" + it.id }.toSet()) }
            mergedInput = allFixtures
        }
        val displayFixtures = mergedFixtures
        uniqueCount = displayFixtures.size
        val searchKey = EventMatching.teamKey(search,selected.slug)
        val matched = displayFixtures.filter { (!favouritesOnly || favouriteKey(it) in favouriteIds) && (search.isBlank() || it.name.contains(search, true) || it.league.contains(search, true) ||
            EventMatching.teamKey(it.teams?.first ?: it.name,it.sport).contains(searchKey) || EventMatching.teamKey(it.teams?.second.orEmpty(),it.sport).contains(searchKey) ||
            EventMatching.leagueKey(it.league,it.sport).contains(searchKey)) &&
            (selectedDay == null || CalendarDates.matches(it.sport,it.start,it.end,selectedDay!!)) }
        val phases = matched.associate { it.id to phase(it, now) }
        val ordering = when (sortMode) {
            "League" -> compareBy<Fixture> { it.league.lowercase(Locale.ROOT) }.thenBy { it.start }
            "Kickoff" -> compareBy<Fixture> { it.start }
            else -> compareBy<Fixture> {
                when {
                    search.isNotBlank() && it.name.startsWith(search, true) -> 0
                    search.isNotBlank() && it.name.contains(search, true) -> 1
                    else -> 2
                }
            }.thenBy { CompetitionPriority.tier(it.league) }.thenBy { if (it.url.isNotBlank()) 0 else 1 }
                .thenBy { kotlin.math.abs(it.start - now) }
        }.thenBy { it.id }
        fun ofPhase(vararg wanted: Phase) = matched.filter { phases[it.id] in wanted }.sortedWith(ordering)
        val liveNow = ofPhase(Phase.LIVE)
        val upcomingAll = ofPhase(Phase.SOON, Phase.UPCOMING)
        val laterOnly = upcomingAll
        val ended = ofPhase(Phase.ENDED).sortedByDescending { it.start }

        val rows = mutableListOf<Row>()
        fun section(title: String, list: List<Fixture>, live: Boolean = false) {
            if (list.isEmpty()) return
            rows += Row.Header(if (live) "Scheduled now · status unverified" else if (catalogueId == "livekora" && title == "Finished") "Earlier fixtures" else title, list.size, live)
            list.groupBy { it.league }.forEach { (league, events) ->
                rows += Row.Header(league, events.size, false)
                events.forEach { rows += Row.Match(it) }
            }
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
                    else -> { section("Live now", liveNow, live = true); byDay(laterOnly); section("Status unavailable", ofPhase(Phase.UNKNOWN)); section("Finished", ended) }
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
        health.text = when {
            loading -> "Refreshing schedules…"
            loadError != null -> "Some data unavailable · saved results may be shown"
            sourceStatus.contains("unavailable") -> "Some sources unavailable · open Sources for details"
            else -> "Long-press to favourite · scores may be delayed"
        }
        renderTabs(matched.size, liveNow.size, upcomingAll.size)
        subtitle.text = when {
            loading && allFixtures.isEmpty() -> "Loading ${selected.name.lowercase()} events…"
            allFixtures.isEmpty() -> "No schedule loaded"
            else -> {
                val updated = if (lastUpdated > 0) " · updated ${timeFormat.format(Date(lastUpdated))}" else ""
                if (favouritesOnly || search.isNotBlank() || selectedDay != null) "${matched.size} shown · ${uniqueCount} total$updated"
                else if (catalogueId == "livekora") "${allFixtures.size} scheduled events · status unverified$updated"
                else if (catalogueId == "openliga") "OpenLigaDB · community results · ODbL$updated"
                else "${liveNow.size} scheduled now · ${uniqueCount} matches$updated"
            }
        }
    }

    private fun favouriteKey(f: Fixture) = "${f.sport}|${EventMatching.teamKey(f.teams?.first ?: f.name, f.sport)}|${EventMatching.teamKey(f.teams?.second.orEmpty(), f.sport)}|${EventMatching.leagueKey(f.league, f.sport)}|${(f.start + 300) / 1800}"
    private fun toggleFavourite(f: Fixture) {
        val key = favouriteKey(f)
        val added = if (key in favouriteIds) { favouriteIds.remove(key); false } else { favouriteIds.add(key); true }
        getPreferences(MODE_PRIVATE).edit().putStringSet("favourite_matches", favouriteIds.toSet()).apply()
        android.widget.Toast.makeText(this, if (added) "Added to favourites" else "Removed from favourites", android.widget.Toast.LENGTH_SHORT).show()
        rebuild()
    }
    private fun matchDetails(f: Fixture) {
        val score = if (f.homeScore != null && f.awayScore != null) "Reported score: ${f.homeScore}–${f.awayScore}" else "Score unavailable"
        val classification = if(f.entrants.isEmpty()) "" else "\n\nPublished entrants / classification\n" + f.entrants.joinToString("\n") { "${it.position?.toString() ?: "–"}  ${it.name}${if(it.result.isBlank()) "" else " · ${it.result}"}" }
        val age = ((System.currentTimeMillis() - f.receivedAt).coerceAtLeast(0) / 60_000)
        val provenance = allFixtures.filter { (it.id == f.id && it.sourceId == f.sourceId) || (it.sourceId + "|" + it.id) in f.memberIds }.map { it.sourceId }.distinct().joinToString(", ")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(f.name)
            .setMessage("${f.league}\n${dayLabel(f.start)} · ${if (f.start > 0) timeFormat.format(Date(f.start * 1000)) else "Time TBC"}\n\n$score$classification\nSource: $provenance\nRetrieved ${age}m ago${if (age > 10) " · stale" else ""}\nMatch clocks are not verified. Commentary language is shown only when published.")
            .setPositiveButton("Close", null)
            .setNeutralButton(if (favouriteKey(f) in favouriteIds) "Remove favourite" else "Favourite") { _, _ -> toggleFavourite(f) }.show()
    }

    private fun open(f: Fixture) {
        val choices = allFixtures.filter { it.url.isNotBlank() && ((it.id == f.id && it.sourceId == f.sourceId) || (it.sourceId + "|" + it.id) in f.memberIds) }.distinctBy { it.url }
        if (choices.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(f.name)
                .setMessage("${f.league}\n${dayLabel(f.start)}\n\n${f.sourceId} · fetched ${timeFormat.format(Date(f.receivedAt))}\nNo published player is linked to this fixture. Scores may be delayed. Long-press a match to favourite it.")
                .setPositiveButton("Match details") { _, _ -> matchDetails(f) }
                .setNeutralButton("TV & official watch") { _, _ -> showBroadcasts(f) }
                .setNegativeButton("Close", null).show()
            return
        }
        val preferred = getPreferences(MODE_PRIVATE).getString("preferred_player_source", "viprow")
        val ordered = choices.sortedBy { if (it.url.contains("viprow.im") == (preferred == "viprow")) 0 else 1 }
        val labels = ordered.map { if (it.url.contains("viprow.im")) "VIPRow · choose server" else "Arabic provider · choose server" }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Player source · ${f.name}")
            .setItems(labels.toTypedArray()) { _, index ->
                val choice = ordered[index]
                getPreferences(MODE_PRIVATE).edit().putString("preferred_player_source", if (choice.url.contains("viprow.im")) "viprow" else "arabic").apply()
                val source = sources[choice.id]
                startActivity(Intent(this, ResolveActivity::class.java).apply {
                    putExtra("event_id", choice.id); putExtra("event_name", choice.name); putExtra("event_url", choice.url)
                    source?.let { putExtra("native_url", it.url); putExtra("native_type", it.type) }
                })
            }.setNeutralButton("TV & official watch") { _, _ -> showBroadcasts(f) }
            .setPositiveButton("Match details") { _, _ -> matchDetails(f) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openPlayer(url: String, type: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("playback_url", url); putExtra("playback_type", type)
        })
    }

    private fun showBroadcasts(f: Fixture? = null) {
        startActivity(Intent(this, BroadcastActivity::class.java).apply {
            putExtra("sport", f?.sport ?: selected.slug); putExtra("sport_name", selected.name)
            val day = selectedDay ?: f?.start?.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
            putExtra("day", day.toString())
            f?.teams?.let { putExtra("home",it.first);putExtra("away",it.second) }
        })
    }

    override fun onDestroy() {
        catalogueTask?.cancel(true)
        catalogueExecutor.shutdownNow()
        generation++
        handler.removeCallbacksAndMessages(null)
        webLoader?.apply { disposeBrowser(this) }
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
            title.text = h.title
            title.setTextColor(if (h.live) accent else white)
            count.text = h.count.toString()
            dot.visibility = if (h.live) View.VISIBLE else View.GONE
            if (h.live) pulse(dot) else dot.clearAnimation()
        }
    }

    private inner class MatchHolder : RecyclerView.ViewHolder(LinearLayout(this@MainActivity)) {
        private val card = itemView as LinearLayout
        private val time = text("", 11f, muted).apply { gravity = Gravity.CENTER }
        private val home = text("", 13f, white, true).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val away = text("", 13f, white, true).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val homeBadge = ImageView(this@MainActivity)
        private val awayBadge = ImageView(this@MainActivity)
        private val score = text("", 22f, white, true).apply {
            gravity = Gravity.CENTER; setTypeface(Typeface.MONOSPACE, Typeface.BOLD); maxLines=1
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,14,22,1,android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        private val play = text("", 10f, accent).apply { gravity = Gravity.CENTER; minHeight = d(28) }
        private val sides = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        private val race = text("", 12f, muted).apply { setPadding(d(8),d(8),d(8),d(4)) }
        private var fixture: Fixture? = null
        init {
            card.orientation = LinearLayout.VERTICAL
            card.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = d(5) }
            card.setPadding(d(10), d(10), d(10), d(7)); card.background = shape(surface, 8)
            card.addView(time, lp(-1, d(24)))
            fun side(badge: ImageView, name: TextView) = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                badge.scaleType = ImageView.ScaleType.FIT_CENTER
                addView(badge, lp(d(30),d(30)).apply { bottomMargin=d(7) })
                addView(name, lp(-1,d(38)))
            }
            sides.addView(side(homeBadge,home),lp(0,-2,1f))
            sides.addView(score,lp(d(80),-2))
            sides.addView(side(awayBadge,away),lp(0,-2,1f))
            card.addView(sides,lp(-1,-2));card.addView(race,lp(-1,-2));card.addView(play,lp(-1,-2))
            card.isFocusable=true
            card.setOnLongClickListener { fixture?.let { toggleFavourite(it) }; true }
            card.setOnClickListener { fixture?.let { open(it) } }
        }
        fun bind(f: Fixture, now: Long) {
            fixture=f
            val pair=f.teams.takeUnless { f.sport in setOf("motorsports","horse-racing","golf","cycling") }
            sides.visibility=if(pair==null) View.GONE else View.VISIBLE
            race.visibility=if(pair==null) View.VISIBLE else View.GONE
            if(pair!=null) {
                home.text=pair.first;away.text=pair.second
                TeamBadges.bind(homeBadge,pair.first,f.sport,f.homeLogo)
                TeamBadges.bind(awayBadge,pair.second,f.sport,f.awayLogo)
            } else {
                race.text=f.name + "\n" + if(f.entrants.isEmpty()) "Entrants / results not published by this source" else
                    f.entrants.take(3).joinToString("\n") { "${it.position?.toString() ?: "–"}  ${it.name}${if(it.result.isBlank()) "" else " · ${it.result}"}" } +
                        if(f.entrants.size>3) "\n${f.entrants.size} entrants · tap for classification" else ""
            }
            time.text=when(f.status) {
                "finished" -> "Finished";"postponed" -> "Postponed";"cancelled" -> "Cancelled";"live" -> "LIVE · reported"
                else -> if(f.start>0) timeFormat.format(Date(f.start*1000L)) else "Time TBC"
            }
            score.text=if(f.homeScore!=null && f.awayScore!=null) "${f.homeScore}–${f.awayScore}" else "– : –"
            val stale=f.receivedAt<=0 || System.currentTimeMillis()-f.receivedAt>10*60_000L
            score.setTextColor(if(stale && f.homeScore!=null) amber else if(f.status=="live") accent else white)
            score.contentDescription=if(f.homeScore==null) "Score unavailable" else "Reported score ${f.homeScore} to ${f.awayScore}${if(stale) ", stale" else ""}"
            play.text=(if(favouriteKey(f) in favouriteIds) "★ · " else "") + if(f.url.isBlank()) "Details · TV & watch" else "Choose player · TV & watch"
            card.contentDescription="${f.name}, ${f.league}, ${time.text}. " + if(f.url.isBlank()) "Match details" else "Choose player"
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
            bar(210, 16, 0); bar(170, 16, 12)
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
