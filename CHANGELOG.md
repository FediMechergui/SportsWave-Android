# Changelog

## 1.0.2 - 2026-10-18

- Default to Arabic/Tunisian preference across all sports, with international fallback feeds retained and Tunisian competitions prioritized in relevance sorting.
- Start the Sources directory with its Arabic/Tunisian filter enabled; expose Tunisian, beIN Match and researched Yacine website handoffs in every sport's watch options.
- Restore the missing Raceweek source-picker entry. Yacine and beIN Match video playback remain unverified; see docs/ARABIC_CHANNELS.md.

## 1.0.1 - 2026-10-18

- Streamed schedules from its documented public API, with published HD/SD embed choices; player resources are limited to Streamed's own hosts and numbered load balancers.
- Yalla Shoot Koora Arabic schedules (published UTC kick-offs, team names and crests) and Raceweek racing calendars.
- Sources screen: audited website and mirror links that open in the browser, Arabic and Tunisian sites, Watania and Diwan Sport service links. All are labelled "Playback unverified".
- Players whose commentary language is unknown are now listed with a "Language unverified" label instead of being hidden.
- Fixed an app-not-responding freeze when switching schedule sources: merging about 1,200 events now runs off the main thread and team names are parsed without a regex split.
- Removed 24 nameless Discord/Telegram invite links from the source directory and skip nameless entries.

## 1.0.0 - 2026-10-18

- Left/right team cards with central reported scores, published crests and score freshness.
- English ESPN multi-sport calendars and Arabic FilGoal fixtures; explicit-date requests, timezone display and seven-day navigation.
- Tunisian leagues, world events and major competitions receive priority; added Tunisia/FilGoal shortcut and reviewed team-name aliases.
- Native beIN MENA programme guide with channel/date/live-broadcast filters, replay labels, conservative match-name filtering and official channel/service handoffs.
- Race season-calendar queries, published entrants/classifications and separate race presentation; no fake head-to-head scores for races.
- Local vector sport icons, shared published team logos, saved schedules and strict Arabic/English commentary filtering.
- Asynchronous WebView startup and bounded/cached identity/priority work address the emulator startup stalls found during QA.

External calendars, live scores, artwork and servers remain incomplete. Official service links can require accounts, subscriptions and regional availability. No new YacineTV/Robotiva player is certified. Unknown commentary is hidden; a match may have no eligible in-app server. This is a GitHub release, not a Google Play submission.

## 1.0.0-beta.4 - 2026-10-18

- Preserve records from other sources while each schedule refresh completes, and replace updated source records rather than keeping old scores first.
- Keep actual retrieval timestamps after failed single-source requests.
- Reject late catalogue callbacks after their metadata WebView is disposed or timed out.
- Save confirmed empty schedules so previously removed fixtures do not return on restart.
- Preserve thread interruption when cancelling OpenLigaDB requests during catalogue switches.
- Add six regression tests for source replacement, failure freshness, empty snapshots, identity isolation and invalid provider records.

## 1.0.0-beta.3 - 2026-10-17

- Add compact competition-grouped rows, date navigation, persistent favourites and editorial world-event priorities.
- Combine available schedules with selected OpenLigaDB football, hockey and darts matchdays, reported scores and source freshness.
- Conservatively merge matching fixtures while preserving women's, youth and ambiguous identities and actual player links.
- Retain saved schedules for offline use, preserve retrieval timestamps after provider failures and limit automatic retries.
- Render bounded raster/SVG team crests with initials when artwork is unavailable.
- Add match details and recent player-failure feedback; destroy metadata WebViews after discovery or errors.
- Report resolver HTTP failures explicitly and prevent stale callbacks from restarting disposed scans.
- Expand verification to 38 Android unit tests and three Python importer tests. Apify checks did not produce an active feed; credentials remain outside the app.

Android 15 verification loaded 87 OpenLigaDB fixtures, published crests and truthful score/source details while other schedule sources were unavailable. Earlier channel-playback evidence remains documented; this release does not certify every provider or provide comprehensive verified live scores.

## 1.0.0-beta.2 - 2026-10-14

- Fix VIPRow schedule timeouts by attaching and disposing its loader WebView; add explicit HTTP-error feedback.
- Add all 19 published VIPRow categories and correct the MMA route.
- Add Livekora football metadata, preserving kickoff offsets and distinct fixtures sharing channels; ignore placeholder scores.
- Fix reviewed Livekora wrapper redirects and add scoped AlbaPlayer/SNRT dependencies.
- Add Relevance/Kickoff/League sorting, explicit commentary-language groups, and a separate Channels menu.
- Fix AlbaPlayer's missing-Chrome-namespace crash without fabricating Cast support or changing access credentials.
- Preserve the WebView through fullscreen rotation; remove misleading no-video hints during successful playback.
- Expand offline regression coverage to 24 tests.

Verified Arryadia/SNRT video playback on the Android 17 emulator. Other channels remain best effort: the sampled beIN media request stalled. Livekora may publish zero fixtures; no verified live-score feed or guaranteed match-to-channel broadcast mapping is provided. KooraTV/KorahLive schedule limitations remain documented.

## 1.0.0-beta.1 — 2026-10-13

Initial published Android prerelease.

- New SportsWave adaptive, legacy vector and monochrome launcher icon.
- Signed APK and Android App Bundle, minification/resource shrinking, checksum manifest and public certificate fingerprint.
- Native Kotlin catalogue with search, refresh, sport/status filters, day grouping and crest fallback.
- Discovery of published player choices with relative resolution, Arabic labels, icon-ligature cleanup and exact deduplication.
- YasirTV recognition with complete original query preservation.
- Explicit TodayStreams → DaddyLive → Dembed frame permissions and reviewed resource dependencies.
- Bounded catalogue readiness checks, unavailable links and player loading/retry controls.
- 19 offline regression tests and documented emulator results.
- Android Gradle Plugin 8.10.1 for SDK 36 and Kotlin/R8 compatibility, retaining Gradle 8.11.1 / Kotlin 2.2.10.

Known limitations: the sampled web stream still buffered; native HLS showed later emulator corruption; verified live scores, full 19-category VIPRow support, complete RTL/localization and KooraTV integration are not implemented.
