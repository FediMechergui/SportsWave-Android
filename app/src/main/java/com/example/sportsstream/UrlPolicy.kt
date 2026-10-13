package com.example.sportsstream

import java.net.URI
import java.util.Locale

object UrlPolicy {
    fun https(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme.equals("https", true) && !it.host.isNullOrBlank() &&
            it.rawUserInfo == null && it.port in listOf(-1, 443)
    }
    fun hostMatches(host: String, pattern: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        val p = pattern.lowercase(Locale.ROOT)
        return if (p.startsWith("*.")) h.endsWith("." + p.removePrefix("*.")) else h == p
    }
    fun allowsResource(url: String, hosts: List<String>): Boolean {
        val uri = https(url) ?: return false
        return hosts.any { hostMatches(uri.host, it) }
    }
    fun resolve(base: String, link: String): String? = runCatching {
        URI(base).resolve(link.trim()).toString().takeIf { https(it) != null }
    }.getOrNull()
    fun label(title: String, text: String, fallback: String): String =
        title.trim().ifBlank { text.replace(Regex("\\b(open_in_new|launch|play_arrow)\\b"), " ") }
            .replace(Regex("\\s+"), " ").trim().take(90).ifBlank { fallback }
}
