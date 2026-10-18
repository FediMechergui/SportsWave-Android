package com.example.sportsstream

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException

/** Load the WebView native library off the UI thread before constructing a WebView. */
object BrowserStartup {
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var starting = false
    private val waiting = mutableListOf<(Boolean) -> Unit>()

    @androidx.annotation.OptIn(markerClass = [WebViewCompat.ExperimentalAsyncStartUp::class])
    fun prepare(context: Context, callback: (Boolean) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if(ready) { callback(true); return }
        waiting += callback
        if(starting) return
        starting = true
        fun finish(success: Boolean) {
            main.post {
                ready = success; starting = false
                val callbacks = waiting.toList(); waiting.clear()
                callbacks.forEach { it(success) }
            }
        }
        try {
            WebViewCompat.startUpWebView(context.applicationContext, WebViewStartUpConfig.Builder(executor).build(),
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) { finish(true) }
                    override fun onError(error: WebViewStartupException) { finish(false) }
                })
        } catch (_: Exception) { finish(false) }
    }
}
