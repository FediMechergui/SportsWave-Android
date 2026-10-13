package com.example.sportsstream

import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Fullscreen native player. Never loads a website or an advertising iframe. */
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var resumePlayback = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val url = intent.getStringExtra("playback_url") ?: return finish()
        val type = intent.getStringExtra("playback_type") ?: return finish()
        val uri = Uri.parse(url)
        if (UrlPolicy.https(url) == null || type !in setOf("hls", "dash")) return finish()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        val source = PlayerSource(url, "authorized-$type", "Native stream", url, if (type == "hls") PlaybackMode.HLS else PlaybackMode.DASH)
        if (ProviderRegistry(this).adapter(source.providerId)?.resolve(source) !is PlaybackResult.Native) return finish()
        val video = PlayerView(this).apply { setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) }
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        val error = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(0xDD111A2C.toInt()); textSize = 16f
            setPadding(35, 28, 35, 28); visibility = View.GONE
        }
        root.addView(error, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        root.addView(TextView(this).apply {
            text = "‹ Back / Switch server"; setTextColor(Color.WHITE); setBackgroundColor(0xCC111A2C.toInt())
            setPadding(24, 18, 24, 18); setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        error.setOnClickListener { error.visibility = View.GONE; player?.prepare(); player?.play() }
        player = ExoPlayer.Builder(this).build().also { exo ->
            video.player = exo
            exo.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { video.keepScreenOn = isPlaying }
                override fun onPlayerError(exception: PlaybackException) {
                    error.text = "Unable to play this stream (${exception.errorCodeName}). Tap to retry or switch server."
                    error.visibility = View.VISIBLE
                }
            })
            exo.setMediaItem(MediaItem.Builder().setUri(uri).setMimeType(
                if (type == "dash") MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8
            ).build())
            exo.prepare(); exo.play()
        }
    }
    override fun onStop() { resumePlayback = player?.playWhenReady == true; player?.pause(); super.onStop() }
    override fun onStart() { super.onStart(); if (resumePlayback) player?.play() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
