package com.example.sportsstream

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URI

/** Event -> public player-link list. No media extraction, credential replay or player-site browsing. */
class ResolveActivity : AppCompatActivity() {
    private var page: WebView? = null
    private val staticExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var staticTask: java.util.concurrent.Future<*>? = null
    private lateinit var choices: LinearLayout
    private lateinit var status: TextView
    private val languages = mutableMapOf<String, String>()
    private val discovered = linkedMapOf<String, Pair<ProviderRule, String>>()
    private var scanningStarted = false
    private var attempts = 0
    private val maxAttempts = 8
    private val bg = 0xFF090E1A.toInt()
    private val panel = 0xFF1C2C43.toInt()
    private val accent = 0xFF5FDCA5.toInt()
    private val registry by lazy { ProviderRegistry(this) }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 18 * resources.displayMetrics.density
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("event_name") ?: "Match"
        val eventUrl = intent.getStringExtra("event_url") ?: return finish()
        if (!safeEventUrl(eventUrl)) return finish()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(35), dp(20), dp(18))
        }
        setContentView(root)
        root.addView(TextView(this).apply {
            text = "‹  Back to matches"
            textSize = 14f; setTextColor(accent)
            setPadding(0, 0, 0, dp(20))
            setOnClickListener { finish() }
        })
        root.addView(TextView(this).apply {
            text = name; textSize = 22f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "Looking for available players…"; textSize = 14f
            setTextColor(0xFF97A3B8.toInt()); setPadding(0, dp(22), 0, dp(18))
        }
        root.addView(status)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(choices)
        addNativeChoice()

        if (!URI(eventUrl).host.equals("viprow.im", true)) {
            // Static discovery only. Livekora's channel host redirects to its reviewed wrapper host.
            staticTask = staticExecutor.submit {
                val result = runCatching {
                    val hosts = if (URI(eventUrl).host.equals("pl.koralive1.cc", true))
                        listOf("pl.koralive1.cc", "pl.matchlivehd.com") else listOf("q.korahlive.online")
                    PublicPage.players(PublicPage.load(eventUrl, hosts), registry.providers)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val found = linkedMapOf<String, Pair<ProviderRule, String>>()
                    result.getOrDefault(emptyList()).forEach { source ->
                        source.language?.let { languages[source.url] = it }
                        registry.byId(source.providerId)?.let { found[source.url] = it to source.label }
                    }
                    if (found.isNotEmpty()) showPlayers(found)
                    else status.text = if (result.isFailure) "Event page unavailable. Return and retry."
                        else "No published compatible player links. Script-generated access keys are unsupported."
                }
            }
            return
        }

        // Invisible view reads the ordinary public event links only.
        val browser = WebView(this)
        page = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !safeEventUrl(request.url.toString())

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? =
                if (UrlPolicy.allowsResource(request.url.toString(), listOf(URI(eventUrl).host))) null
                else android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) status.text = "Event page unavailable. Try another match."
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (safeEventUrl(url) && !scanningStarted) {
                    scanningStarted = true
                    attempts = 0
                    scanAgain(view)
                }
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(1, 1))
        browser.loadUrl(eventUrl)
        browser.postDelayed({
            if (page === browser && attempts == 0 && !isFinishing) {
                browser.stopLoading(); status.text = "Event page timed out. Return and retry."
            }
        }, 15000L)
    }

    private fun safeEventUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        UrlPolicy.https(value) != null &&
            ((uri.host.equals("viprow.im", true) && uri.path.startsWith("/sports/")) ||
                uri.host.equals("q.korahlive.online", true) ||
                uri.host.equals("pl.koralive1.cc", true))
    }.getOrDefault(false)

    private fun scanAgain(view: WebView) {
        if (isFinishing || isDestroyed || page !== view) return
        // Only published anchor href / data-href / src attributes, not video manifests or page secrets.
        val script = """(function(){let a=[...document.querySelectorAll('a[href],a[data-href],a[data-url],iframe[src],iframe[data-src]')];return JSON.stringify(a.map(x=>{let c=x.cloneNode(true);c.querySelectorAll('i,svg,img,.material-icons,.material-symbols-outlined,[class*="icon"]').forEach(n=>n.remove());let w=document.createTreeWalker(c,NodeFilter.SHOW_TEXT),parts=[];while(w.nextNode()){let t=w.currentNode.nodeValue.trim();if(t)parts.push(t)}let label=x.getAttribute('title')||x.getAttribute('aria-label')||parts.join(' ')||'';return {url:new URL(x.getAttribute('href')||x.dataset.href||x.dataset.url||x.getAttribute('src')||x.dataset.src||'',document.baseURI).href,language:x.getAttribute('data-language')||'',label:label.replace(/\s+/g,' ').trim().slice(0,90)};}));})()"""

        view.evaluateJavascript(script) { encoded ->
            if (isFinishing || isDestroyed) return@evaluateJavascript
            val found = discovered
            try {
                val decoded = JSONTokener(encoded).nextValue() as String
                val array = JSONArray(decoded)
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val url = obj.optString("url")
                    val provider = registry.match(url) ?: continue
                    val label = UrlPolicy.label(obj.optString("title"), obj.optString("label"), provider.name)
                    obj.optString("language").trim().takeIf { it.isNotBlank() && it.length <= 40 }?.let { languages[url] = it }
                    found.putIfAbsent(url, provider to label)
                }
            } catch (_: Exception) { /* Retry; page JS may not be ready. */ }
            if (found.isNotEmpty()) showPlayers(found)
            attempts++
            if (attempts < maxAttempts) {
                if (found.isEmpty()) status.text = "Loading player choices… ($attempts/$maxAttempts)"
                view.postDelayed({ scanAgain(view) }, 800L)
            } else if (found.isEmpty()) {
                status.text = "No supported player links found for this match."
            }
        }
    }

    /** Drops icon-font ligature words such as "open_in_new" or "play_arrow" from link text. */
    private fun cleanLabel(raw: String): String =
        raw.replace(Regex("\\b[a-z]+(?:_[a-z]+)+\\b"), " ").replace(Regex("\\s+"), " ").trim()

    private fun showPlayers(players: Map<String, Pair<ProviderRule, String>>) {
        status.text = "${players.size} available player choice(s)"
        choices.removeAllViews()
        addNativeChoice()
        var previousLanguage: String? = null
        players.entries.sortedWith(compareBy({ languages[it.key] ?: "Unknown commentary language" }, { it.value.first.priority }, { it.value.second })).forEach { (url, info) ->
            val language = languages[url] ?: "Unknown commentary language"
            if (language != previousLanguage) {
                choices.addView(TextView(this).apply {
                    text = language; textSize = 14f; setTextColor(accent)
                    setPadding(0, dp(12), 0, dp(12))
                })
                previousLanguage = language
            }
            val label = info.second.takeIf { it.isNotBlank() && it.length < 60 } ?: info.first.name
            val quality = Regex("\\b(4K|UHD|FHD|HD|SD)\\b", RegexOption.IGNORE_CASE).find(label)?.value?.uppercase()
            val title = if (quality != null) label.replace(Regex("\\s*\\b$quality\\b\\s*", RegexOption.IGNORE_CASE), " ").trim() else label
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setOnClickListener {
                    val source = PlayerSource(url, info.first.id, label, url, PlaybackMode.WEB_EMBED, languages[url])
                    val resolved = registry.adapter(info.first.id)?.resolve(source)
                    if (resolved !is PlaybackResult.Embedded) { status.text = "Player unavailable"; return@setOnClickListener }
                    startActivity(Intent(this@ResolveActivity, WebPlayerActivity::class.java).apply {
                        putExtra("player_url", url)
                        putExtra("provider_id", info.first.id)
                    })
                }
            }
            row.addView(TextView(this).apply {
                text = "▶"; textSize = 15f; setTextColor(bg); gravity = Gravity.CENTER
                background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(accent) }
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(14) })
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            column.addView(TextView(this).apply {
                text = title.ifBlank { info.first.name }; textSize = 16f; setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD); maxLines = 2
            })
            column.addView(TextView(this).apply {
                text = "${info.first.name} · fullscreen web player"; textSize = 12.5f
                setTextColor(0xFF97A3B8.toInt()); setPadding(0, dp(4), 0, 0)
            })
            if (quality != null) row.addView(TextView(this).apply {
                text = quality; textSize = 11f; setTextColor(accent); setTypeface(null, Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply { setColor(0x335FDCA5); cornerRadius = 8 * resources.displayMetrics.density }
            })
            choices.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun addNativeChoice() {
        val url = intent.getStringExtra("native_url") ?: return
        val type = intent.getStringExtra("native_type") ?: return
        val mode = when (type) { "hls" -> PlaybackMode.HLS; "dash" -> PlaybackMode.DASH; else -> return }
        val source = PlayerSource(url, "authorized-$type", "Authorized ${type.uppercase()}", url, mode)
        val result = registry.adapter(source.providerId)?.resolve(source) as? PlaybackResult.Native ?: return
        choices.addView(TextView(this).apply {
            text = "▶ ${source.label} · native player"; textSize = 16f; setTextColor(Color.WHITE)
            background = rounded(panel); setPadding(dp(18), dp(18), dp(18), dp(18))
            setOnClickListener { startActivity(Intent(this@ResolveActivity, PlayerActivity::class.java).apply {
                putExtra("playback_url", result.url); putExtra("playback_type", type)
            }) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    override fun onDestroy() {
        staticTask?.cancel(true)
        staticExecutor.shutdownNow()
        page?.stopLoading()
        page?.destroy()
        page = null
        super.onDestroy()
    }
}
