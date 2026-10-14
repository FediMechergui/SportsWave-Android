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

## YacineTV / Robotiva investigation (2026-10-13)

The user supplied `https://robotiva.online/kora.html?m=332&lang=en&d=robotiva.online` as a match window opened from YacineTV. A normal HTTPS request retrieved the Robotiva HTML with status 200; the YacineTV landing page returned 403. The relationship is user-reported, not independently confirmed by a landing-page link.

Robotiva reads the supplied match ID and requests `https://cdn.kora-api.org/api/v1/matche/332` (with a cache timestamp in the page implementation). A direct request returned 403. The HTML alone contains no verified match identity or usable server choices. Do not enumerate IDs, register guessed streams, or claim playback support from this document response.

The page's `lang` parameter selects Arabic/English interface text and team/league names. It is not commentary-language metadata. Server buttons are populated from returned channel records; player frames may use rotating edge hosts and visitor/session parameters. No generated player URLs, tokens, decrypted payloads or media manifests were copied into the app. Integration remains pending accessible published metadata and review of the actual player dependencies.

## Native sources

Configure authorized HLS/DASH manifests in playback_sources.json. IDs currently belong to VIPRow. Do not store private credentials or ephemeral session keys. Native sources appear in the player list. The public Mux HLS test action remains available. There is no native media extraction from web providers.

## Verification

Add offline fixture tests for recognition, unsafe hosts, query preservation, Arabic labels, multiple sources and routing. Successful HTML retrieval is not successful video playback. Record actual device testing in TEST_REPORT.md before claiming any provider works.


## Livekora catalogue and AlbaPlayer

LivekoraCatalogue reads only published football match cards from www.livekora.vip. Participants and timezone-bearing start timestamps form event identities; shared channel URLs do not merge fixtures. Placeholder scores are ignored. The resolver loads only the actual pl.koralive1.cc channel page and extracts its published iframe. AlbaPlayer recognition is restricted to pl.matchlivehd.com/albaplayer/; resources and SNRT frame dependencies are explicitly reviewed. Arryadia/SNRT playback is verified; other channel dependencies and availability still require inspection rather than broad wildcard permissions. No native media extraction is added. Explicit server data-language values form language groups; missing commentary metadata remains unknown.


## Runtime fixes verified 2026-10-14

VIPRow's catalogue WebView is attached at 1x1 pixels to the activity, then removed and destroyed on completion, cancellation or failure. HTTP rejection now has its own state rather than a generic timeout. The attached loader returned 140 fixtures on the evening check and 195 on the following morning.

Livekora channel pages redirect from pl.koralive1.cc to pl.matchlivehd.com before exposing their AlbaPlayer iframe. Only those two hosts are allowed for that resolver branch; KorahLive remains separately scoped. PublicPage validates each redirect destination before requesting it. SNRT is an explicit AlbaPlayer frame dependency with observed Easybroadcast library, token-service and media hosts. No token or media URL is generated, decoded, replayed or copied into native playback.

The AlbaPlayer page's optional Cast plugin assumes window.chrome exists. AndroidX WebKit document-start injection supplies an empty namespace only on https://pl.matchlivehd.com when missing. No Cast capability is supplied and access credentials are untouched. The page then initializes its own player. Observed pull.gosh6.app media requests and www.gstatic.com Cast-library requests are permitted; this does not guarantee that the remote media responds.

channels.json lists reviewed channel-page links separately from fixtures. This keeps channels available when Livekora publishes no match cards; it does not manufacture a schedule. Commentary languages still require explicit server metadata. WebPlayerActivity retains its WebView during orientation changes, and the unconditional no-video hint is removed because it appeared even during successful playback.
