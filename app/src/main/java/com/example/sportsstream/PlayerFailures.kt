package com.example.sportsstream

import android.content.Context
import java.security.MessageDigest

/** Records actual page/frame load errors only. A loaded document is not evidence of working video. */
object PlayerFailures {
    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun record(context: Context, url: String) {
        val prefs = context.getSharedPreferences("player_load_failures", Context.MODE_PRIVATE)
        val editor = prefs.edit().putLong(key(url), System.currentTimeMillis())
        prefs.all.entries.sortedByDescending { it.value as? Long ?: 0L }.drop(49).forEach { editor.remove(it.key) }
        editor.apply()
    }
    fun recent(context: Context, url: String): Boolean {
        val time = context.getSharedPreferences("player_load_failures", Context.MODE_PRIVATE).getLong(key(url), 0L)
        return time > 0 && System.currentTimeMillis() - time in 0 until 24 * 3600_000L
    }
}
