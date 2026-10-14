package com.example.sportsstream

import android.content.Context
import org.json.JSONObject
import java.net.URI

/** Provider rules are data, not extracted secrets. Add approved Arabic services in providers.json. */
data class ProviderRule(
    val id: String, val name: String, val hosts: List<String>,
    val pathPrefix: String, val mode: String, val priority: Int,
    val resourceHosts: List<String> = hosts,
    val frameProviders: List<String> = emptyList()
) {
    fun accepts(url: String): Boolean {
        val u = UrlPolicy.https(url) ?: return false
        return hosts.any { UrlPolicy.hostMatches(u.host, it) } &&
            (if (id == "yasirtv") u.path == "/playerv5.php" else u.path.startsWith(pathPrefix))
    }

}

class ProviderRegistry(context: Context) {
    val providers: List<ProviderRule> = runCatching {
        val text = context.assets.open("providers.json").bufferedReader().use { it.readText() }
        parse(text)
    }.getOrElse { emptyList() }

    companion object {
        internal fun parse(text: String): List<ProviderRule> {
            val arr = JSONObject(text).getJSONArray("providers")
            return (0 until arr.length()).mapNotNull { n ->
                val obj = arr.getJSONObject(n)
                if (!obj.optBoolean("enabled", false)) return@mapNotNull null
                val hosts = obj.getJSONArray("hosts")
                val hostList = (0 until hosts.length()).map { hosts.getString(it).lowercase() }
                if (hostList.isEmpty() || hostList.any { it.isBlank() || '/' in it }) return@mapNotNull null
                val mode = obj.optString("mode", "web")
                if (mode != "web") return@mapNotNull null // native sources are in playback_sources.json
                ProviderRule(obj.getString("id"), obj.getString("name"), hostList,
                    obj.optString("pathPrefix", "/"), mode, obj.optInt("priority", 100),
                    obj.optJSONArray("resourceHosts")?.let { resources ->
                        (0 until resources.length()).map { resources.getString(it).lowercase() }
                    } ?: hostList,
                    obj.optJSONArray("frameProviders")?.let { frames ->
                        (0 until frames.length()).map { frames.getString(it) }
                    }.orEmpty())
            }.sortedBy { it.priority }
        }
    }

    fun adapter(id: String): StreamingProvider? = when (id) {
        "authorized-hls" -> NativeStreamingProvider(id, PlaybackMode.HLS)
        "authorized-dash" -> NativeStreamingProvider(id, PlaybackMode.DASH)
        else -> byId(id)?.let { WebStreamingProvider(it) }
    }

    fun match(url: String): ProviderRule? = providers.firstOrNull { it.accepts(url) }
    fun byId(id: String): ProviderRule? = providers.firstOrNull { it.id == id }
}
