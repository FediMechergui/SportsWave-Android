# Provider integration

Use the existing activities. `Playback.kt` defines PlayerSource, PlaybackMode, StreamingProvider and PlaybackResult. Web adapters route recognized documents to WebPlayerActivity; authorized native manifests route to PlayerActivity. HTML and blob URLs cannot enter native playback.

## Add a web provider

Add an enabled entry to `app/src/main/assets/providers.json` with id, name, hosts, pathPrefix, mode `web`, priority and resourceHosts. Bare hosts match exactly. `*.example.org` matches subdomains only; include the bare domain separately if needed. HTTPS, no userinfo and default/443 ports are required. YasirTV requires the exact `/playerv5.php` path.

`resourceHosts` lists reviewed scripts, frame and media hosts independently from entry-page hosts. Unlisted requests receive an empty response. `blocked_hosts.txt` also rejects listed advertising hosts and subdomains. Navigation callbacks apply to subframes and main documents. WebView interception has platform limitations, particularly redirected resources; it is not a sandbox for arbitrary pages.

`frameProviders` explicitly names other registered providers whose player documents may appear inside the selected provider. This does not permit top-level redirects to them. Their reviewed resource hosts are inherited for the selected embed session. Emulator tracing verified TodayStreams → DaddyLive → Dembed; the configuration now permits this chain and the observed JW Player, entitlement and media dependencies. Media CDN hosts can change and must be reviewed again when a provider changes. No signed media URLs or session parameters are stored in this configuration, and discovered web media never routes to ExoPlayer.

Discovery uses published links, never manufactures IDs, manifests or keys. `PublicPage.players` extracts every matching anchor, iframe src/data-src and server data attribute without executing scripts. `PlayerDiscovery.published` resolves relative URLs, validates providers and deduplicates exact URLs. Preserve the entire signed query. Different YasirTV URLs remain separate choices even when they share a provider or match ID. Titles take precedence over text; icon-font ligatures are removed.

VIPRow uses a bounded scan for delayed DOM links. KorahLive is static-only because the inspected resolver synthesizes fallback keys. Do not enable execution of that resolver or copy its fallback URLs. Third-party cookies are not explicitly enabled, SSL errors are not ignored, Safe Browsing remains enabled and sensitive permissions are not granted.

## Add a catalogue

Implement CatalogueProvider with normalized SportEvent metadata and published event URLs. Keep network work off the UI thread, bound response sizes and timeouts, validate redirects and cancel work on screen destruction. KorahLive currently accepts public JSON-LD SportsEvent records and otherwise shows an empty state. Its script-rendered schedule is not supported. KooraTV direct requests returned 403; no endpoints or server routes were invented.

EventMatching requires exact normalized home/away teams, league, sport and start times within five minutes across catalogues. Unknown team names never merge. This utility supports future combined catalogues; the current picker displays catalogues separately. Remote configuration fetching and bilingual aliases are not implemented; provider rules ship as local reviewed assets.

## Native sources

Configure authorized HLS/DASH manifests in playback_sources.json. IDs currently belong to VIPRow. Do not store private credentials or ephemeral session keys. Native sources appear in the player list. The public Mux HLS test action remains available. There is no native media extraction from web providers.

## Verification

Add offline fixture tests for recognition, unsafe hosts, query preservation, Arabic labels, multiple sources and routing. Successful HTML retrieval is not successful video playback. Record actual device testing in TEST_REPORT.md before claiming any provider works.
