package com.example.sportsstream

enum class PlaybackMode { WEB_EMBED, HLS, DASH }
data class PlayerSource(val id: String, val providerId: String, val label: String, val url: String,
    val mode: PlaybackMode, val language: String? = null)
sealed interface PlaybackResult {
    data class Native(val url: String, val mode: PlaybackMode): PlaybackResult
    data class Embedded(val url: String): PlaybackResult
    data class Unavailable(val reason: String): PlaybackResult
}
interface StreamingProvider {
    val id: String
    fun recognizes(url: String): Boolean
    fun resolve(source: PlayerSource): PlaybackResult
}
class WebStreamingProvider(private val rule: ProviderRule): StreamingProvider {
    override val id = rule.id
    override fun recognizes(url: String) = rule.accepts(url)
    override fun resolve(source: PlayerSource): PlaybackResult =
        if (source.mode == PlaybackMode.WEB_EMBED && source.providerId == id && recognizes(source.url))
            PlaybackResult.Embedded(source.url) else PlaybackResult.Unavailable("Unsupported provider URL")
}
class NativeStreamingProvider(override val id: String, private val mode: PlaybackMode): StreamingProvider {
    override fun recognizes(url: String): Boolean = UrlPolicy.https(url)?.path?.endsWith(
        if (mode == PlaybackMode.HLS) ".m3u8" else ".mpd", true) == true
    override fun resolve(source: PlayerSource): PlaybackResult =
        if (source.providerId == id && source.mode == mode && recognizes(source.url)) PlaybackResult.Native(source.url, mode)
        else PlaybackResult.Unavailable("Expected an authorized HLS or DASH manifest")
}
object PlayerDiscovery {
    fun published(base: String, candidates: List<Pair<String, String>>, rules: List<ProviderRule>): List<PlayerSource> =
        candidates.mapNotNull { (link, label) ->
            val url = UrlPolicy.resolve(base, link) ?: return@mapNotNull null
            val rule = rules.firstOrNull { it.accepts(url) } ?: return@mapNotNull null
            PlayerSource(url, rule.id, UrlPolicy.label("", label, rule.name), url, PlaybackMode.WEB_EMBED)
        }.distinctBy { it.url }
}
