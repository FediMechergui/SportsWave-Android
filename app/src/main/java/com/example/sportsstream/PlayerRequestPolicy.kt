package com.example.sportsstream

/** Entry navigation and approved nested player documents have separate permissions. */
class PlayerRequestPolicy(private val selected: ProviderRule, providers: List<ProviderRule>) {
    private val frames = providers.filter { it.id in selected.frameProviders }
    private val resourceHosts = selected.resourceHosts + frames.flatMap { it.resourceHosts }
    fun allowsNavigation(url: String, mainFrame: Boolean): Boolean =
        selected.accepts(url) || (!mainFrame && frames.any { it.accepts(url) })
    fun allowsResource(url: String): Boolean {
        val uri=UrlPolicy.https(url) ?: return false
        if(selected.id=="streamed-embed" && uri.host.equals("embed.st",true) && uri.path=="/ad.html") return false
        return UrlPolicy.allowsResource(url, resourceHosts)
    }
}
