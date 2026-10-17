package com.example.sportsstream

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI

/** Fullscreen player webpage, with no browser chrome and no popup windows. */
class WebPlayerActivity : AppCompatActivity() {
    private var web: WebView? = null
    private var customView: View? = null
    private var previousOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var errorView: TextView? = null
    private var documentLoaded = false
    private var loadGeneration = 0
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var root: FrameLayout
    private val registry by lazy { ProviderRegistry(this) }
    private var activeRule: ProviderRule? = null
    private lateinit var requestPolicy: PlayerRequestPolicy
    private var blockedHosts: List<String> = emptyList()
    private val loggedBlocks = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun readBlockedHosts(): List<String> = runCatching {
        assets.open("blocked_hosts.txt").bufferedReader().readLines()
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }.getOrDefault(emptyList())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val target = intent.getStringExtra("player_url") ?: return finish()
        activeRule = registry.byId(intent.getStringExtra("provider_id") ?: "")
            ?.takeIf { it.accepts(target) }
        if (activeRule == null) return finish()
        requestPolicy = PlayerRequestPolicy(activeRule!!, registry.providers)
        blockedHosts = readBlockedHosts()
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        val browser = WebView(this)
        web = browser
        root.addView(browser, FrameLayout.LayoutParams(-1, -1))
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        browser.settings.mediaPlaybackRequiresUserGesture = false
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.useWideViewPort = true
        browser.settings.loadWithOverviewMode = true
        val loading = android.widget.ProgressBar(this)
        root.addView(loading, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep the selected player document; block redirects to ad landing pages.
                return !requestPolicy.allowsNavigation(request.url.toString(), request.isForMainFrame)
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                val host = request.url.host?.lowercase().orEmpty()
                val blocked = blockedHosts.any { host == it || host.endsWith(".$it") }
                val allowed = !blocked && if (request.isForMainFrame) allowPage(request.url.toString())
                    else requestPolicy.allowsResource(request.url.toString())
                if (!allowed && loggedBlocks.add(host) &&
                    applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.i("SportsWave", "Blocked player request host=$host mainFrame=${request.isForMainFrame}")
                }
                return if (allowed) null else android.webkit.WebResourceResponse(
                    "text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                documentLoaded = false
                val generation = ++loadGeneration
                loading.visibility = View.VISIBLE
                view.postDelayed({
                    if (web === view && generation == loadGeneration && !documentLoaded && !isFinishing) {
                        view.stopLoading(); loading.visibility = View.GONE; showError(target, "Player page timed out")
                    }
                }, 25000L)
            }
            override fun onPageFinished(view: WebView, url: String) {
                documentLoaded = true
                loading.visibility = View.GONE
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame || requestPolicy.allowsNavigation(request.url.toString(), false)) {
                    loading.visibility = View.GONE; showError(target, "Player page/frame returned HTTP ${response.statusCode}")
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                         error: android.webkit.WebResourceError) {
                if (request.isForMainFrame || requestPolicy.allowsNavigation(request.url.toString(), false)) {
                    loading.visibility = View.GONE; showError(target, "Player page/frame network error (${error.errorCode})")
                }
            }
        }

        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView?.let { callback.onCustomViewHidden(); return }
                previousOrientation = requestedOrientation
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                customView = view
                customCallback = callback
                browser.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            override fun onHideCustomView() {
                if (customView != null) requestedOrientation = previousOrientation
                customView?.let { root.removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                browser.visibility = View.VISIBLE
            }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
                                        resultMsg: android.os.Message?): Boolean = false
        }
        root.addView(TextView(this).apply {
            text = "‹ Back / Switch server"; setTextColor(Color.WHITE)
            setBackgroundColor(0xCC111A2C.toInt()); setPadding(24, 18, 24, 18)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.START))
        if (activeRule?.id == "albaplayer" && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // The optional Cast plugin dereferences window.chrome before testing cast availability.
            // Supply only the missing namespace; do not fabricate a Cast API or touch player credentials.
            WebViewCompat.addDocumentStartJavaScript(browser,
                "if (typeof window.chrome === 'undefined') window.chrome = {};",
                setOf("https://pl.matchlivehd.com"))
        }
        browser.loadUrl(target)
    }

    private fun showError(target: String, reason: String) {
        if (errorView != null || isFinishing) return
        PlayerFailures.record(this, target)
        errorView = TextView(this).apply {
            text = "$reason.\nTap to retry, or use Back / Switch server."
            textSize = 17f; setTextColor(Color.WHITE); setBackgroundColor(0xDD111A2C.toInt())
            setPadding(30, 30, 30, 30)
            setOnClickListener { root.removeView(this); errorView = null; web?.loadUrl(target) }
        }
        root.addView(errorView, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
    }

    private fun allowPage(url: String): Boolean = activeRule?.accepts(url) == true

    override fun onBackPressed() {
        if (customView != null) web?.webChromeClient?.onHideCustomView()
        else super.onBackPressed()
    }

    override fun onPause() { web?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web?.onResume() }

    override fun onDestroy() {
        loadGeneration++
        web?.webChromeClient?.onHideCustomView()
        web?.let { root.removeView(it) }
        web?.stopLoading(); web?.destroy(); web = null
        super.onDestroy()
    }
}
