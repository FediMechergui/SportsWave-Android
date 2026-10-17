# Beta.4 verification - 2026-10-18

- All 44 Android unit tests and three Python importer tests pass. Six added cases cover replacement of one provider's records, preservation of pending sources, failed-fetch timestamps, confirmed empty results, cross-provider IDs, invalid provider records and clearing an old disk snapshot.
- Catalogue callbacks now check that their WebView is still active and loading. Timeouts invalidate the view before destroying it. A cancelled OpenLigaDB request restores the thread interruption and releases its worker pool.
- The importer regression tests now also run in GitHub Actions. Existing upstream coverage and playback limitations below still apply.

# Beta.3 verification - 2026-10-17

- 38 Android unit tests and 3 Python importer tests pass. Signed release assembly, vital lint, R8 and 16 KB APK zip alignment pass using installed Gradle 8.11.1; the externally changed wrapper remains outside these checks.
- The APK signature matches the existing SportsWave release certificate. The tested preview retained beta.2 identity; the publication build increments to 1.0.0-beta.3, versionCode 4, and receives separate artifact checks.
- Resolver metadata WebViews are removed and destroyed after bounded discovery, network errors, HTTP errors and initial-load timeouts. Stale callbacks cannot restart a disposed scan. HTTP failures now show the actual status rather than scanning the error document.
- README roadmap entries now distinguish completed preview features from remaining work.
- Installed the final signed preview on SportsWave_API35, port 5558, without touching the Cartoony emulator on port 5556. The home screen loaded 87 OpenLigaDB fixtures and available published crests; source failures preserved those records. After loading finished, the UI hierarchy contained zero metadata WebViews. Match details reported the source, retrieval age and unavailable score, without implying a linked broadcast. This run does not newly verify provider video playback. [Home](docs/audit/sportswave-home-2026-10-17.png), [match details](docs/audit/sportswave-details-2026-10-17.png).

# Interface preview verification - 2026-10-14 (unreleased)

- 30 offline unit tests pass, including competition priority, distinct women's/youth identities, UTC fixture parsing, final vs provisional score handling and malformed-record isolation.
- Signed Android 15 preview installed as an update on the separate SportsWave emulator. The aggregated Football view loaded 268 records: 195 VIPRow and 73 OpenLigaDB. Availability can change between requests.
- Compact rows and competition headers rendered; Today filtering displayed 91 records in that check. The source chooser opened the actual VIPRow resolver and exposed two published TodayStreams server choices, with unknown commentary language correctly labeled.
- Source coverage reported Livekora and KorahLive as unavailable during that emulator check while preserving other sources' results. OpenLigaDB-only selection loaded records separately.
- Subsequent preview fixes replace the old brand/live wording, exclude metadata-loader WebViews from the accessibility tree, and allow provisional scores only from published goal records. These are covered by the final build/unit checks; the earlier screenshots precede those final textual/accessibility refinements.
- Final signed preview build and vital lint pass; signature matches the existing release identity and 16 KB zip alignment passes. Installed final APK again displayed 268 combined events with the corrected “scheduled now” wording and clean native accessibility labels. [Final screen](docs/audit/interface-preview-api35.png). Cold startup was slow on this memory-constrained emulator; startup performance is not certified on physical hardware.
- Public data covers selected football, hockey and darts competitions, not comprehensive verified multi-sport live scores. Raster logos are attempted from published reviewed hosts; unsupported/missing artwork retains initials. See [coverage](docs/PUBLIC_DATA.md).

# Published beta.2 verification - 2026-10-14

Version 1.0.0-beta.2, versionCode 3. The signed, minified release APK was installed directly in a dedicated SportsWave_API35 Android 15 emulator (emulator-5556), separate from the emulator used by another agent.

- All 24 offline unit tests pass. Signed APK and AAB builds, vital lint, R8 and resource shrinking pass.
- APK signature verifies and matches the beta.1 release certificate. Zip alignment including 16 KB page alignment passes. Bundletool validates the AAB; jarsigner verifies it with the same self-signed/timestamp/archive-order warnings documented for beta.1.
- Signed release VIPRow Football loaded 195 events. Changing to League sorting visibly reordered upcoming rows. The corrected MMA route loaded ONE Friday Fights 171 as a single upcoming event; Ice hockey loaded 34 events.
- Arryadia: Channels -> published channel redirect -> AlbaPlayer -> SNRT produced visible moving video in the signed APK. Normal playback and landscape HTML fullscreen were observed; the activity retained the same ActivityRecord during rotation, and Back returned to player choices. The unconditional no-video hint no longer overlays successful playback.
- Earlier Android 17 debug measurements recorded readyState 4, videoWidth 854, unpaused playback and currentTime advancing from 21580.613967 to 21590.14457.
- Native public Mux HLS parsed its duration and rendered a sample frame on Android 15 after buffering. Sustained playback is not certified by that frame alone; Android 17 had previously shown corruption. DASH remains untested.
- Livekora published 11 match cards on October 13 and zero on the October 14 morning check. The empty state is truthful; reviewed channel-page links remain in Channels rather than being fabricated as fixtures.
- beIN Sports 4: the missing Chrome namespace caused a Cast-plugin exception before video creation. The scoped document-start compatibility fix now initializes the player, but its observed remote media request stalled/aborted. No successful beIN playback is claimed and no access keys or headers were forged.

Current evidence: [signed fullscreen playback](docs/audit/signed-snrt-fullscreen-api35.png), [signed channel video](docs/audit/signed-snrt-playing-api35.png), [native HLS sample frame](docs/audit/signed-native-hls-api35.png), [debug playback samples](docs/audit/snrt-playback-2026-10-14.json).

The earlier failed release gate below is historical and is superseded for this specifically scoped prerelease by the verified channel playback above. It is not a certification of all providers. KooraTV/KorahLive schedules, live-score feeds, complete RTL/localization, canonical team identities and broad provider playback still require further work or external data access.

---

# Verification — 2026-10-13

## Signed prerelease packaging

Version 1.0.0-beta.1 (versionCode 2) adds adaptive, legacy vector and monochrome launcher icons. The release build uses Android Gradle Plugin 8.10.1, Gradle 8.11.1, Kotlin 2.2.10 and SDK 36. Updating AGP resolved the Kotlin metadata/R8 compatibility warnings seen with 8.9.2 during the first minified build.

`testDebugUnitTest assembleRelease bundleRelease` completed successfully, including release vital lint, R8 minification and resource shrinking. All 19 offline tests pass. The signed APK verifies with Android SDK apksigner (v2 signing), passes zipalign including 16 KB page alignment checking, and declares Android 8.0+ / package com.example.sportsstream / versionCode 2. The AAB passes official bundletool validation and JDK jarsigner signature verification; jarsigner also reports self-signed-certificate, timestamp and archive-entry ordering warnings. The public signing certificate fingerprint is in docs/SIGNING_CERTIFICATE_SHA256.txt. The private keystore/password are stored outside the repository.

Release packaging does not resolve the playback limitations below. The signed release APK was not installed over the existing debug-signed app because their signing identities differ; this avoids deleting emulator app data. Prior catalogue/player observations were made with debug builds.

## Emulator follow-up (latest findings)

This section supersedes the earlier no-device snapshot below. Connected and inspected `emulator-5554`, Android 17 x86_64, 1080 × 2400 at density 420. The APK was installed successfully and the football catalogue → published player choices → embedded player flow was exercised.

The reproduced source was Al Bukayriyah vs Al Zulfi, published choice Thmanyah 2. Debugging the existing WebView found TodayStreams → DaddyLive → Dembed. The original same-provider-only policy blocked both required child frames, then the JW Player/Pako libraries, entitlement endpoint and media resource hosts. The request policy now separates top-level navigation from explicit `frameProviders` and inherits those approved providers' reviewed resource hosts. It retains advertising host blocking. No signed media URL or query value was added to app configuration, no license check was disabled, and no web media was handed to native playback.

After these fixes, both nested documents, player scripts, the entitlement request and provider media requests returned HTTP 200. The sampled web player nevertheless remained buffering with video readyState 0, videoWidth 0 and currentTime 0 during the observed run; a media request was aborted/retried. Successful live video playback is **not verified**. The remaining failure is in media delivery/player processing, not proof that another arbitrary advertising host should be allowed. Captured metadata excludes request URL queries and response bodies. [Network evidence](docs/audit/player-network-metadata.json), [buffering screenshot](docs/audit/22-playback-result.png).

A separate catalogue readiness race was reproduced: event data could be available before event anchors, producing a Watch live action without a URL. Extraction now retries missing data/links within a bounded window, and events lacking a published link are marked unavailable instead of offering a working-looking action.

The public Mux HLS test reached native video rendering: an initial sample frame was visible, but another capture showed green corruption. This is **not a clean native playback pass**. The Android 17 emulator's codec/rendering path needs confirmation against a stable emulator image or physical device before attributing the corruption to the app. [Initial frame](docs/audit/23-native-hls-start.png), [corrupted frame](docs/audit/24-native-hls-later.png). DASH was not exercised.

19 unit tests pass, including four PlayerRequestPolicy tests for approved nested frames, rejection of main-frame redirects/ads/lookalikes/unapproved paths, explicit frame permissions and reviewed script-resource inheritance without navigation permission. Builds remain successful. See [OPTIMIZATION_AUDIT.md](OPTIMIZATION_AUDIT.md) for all 19 VIPRow categories, live-score requirements, layout findings, sources and the prioritized backlog.

## Initial build snapshot (before the emulator was connected)

## Build

Gradle wrapper 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.2.10, compileSdk 36, Media3 1.11.1. Local execution used Microsoft JDK 21. Dependency resolution, Android resource processing, Kotlin compilation, unit tests and APK assembly succeeded.

Final command: `./gradlew.bat testDebugUnitTest assembleDebug '-Pkotlin.incremental=false' --console=plain`.

Result: BUILD SUCCESSFUL. An earlier incremental compiler cache failure recovered through full compilation; final verification disabled incremental compilation for this invocation. Existing Android fullscreen/status-bar deprecation warnings remain.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Offline tests

15 tests, zero failures or errors:

- UrlPolicyTest: 6 tests for YasirTV recognition, query preservation, unsafe URLs and domain lookalikes, exact/wildcard hosts, adsco.re resource rejection, relative resolution, icon ligature cleanup and Arabic labels.
- PlaybackTest: 5 tests for multiple sources and exact deduplication, native HLS/DASH versus web routing, empty discovery, normalization and conservative event matching.
- PublicPageTest: 4 tests for all published YasirTV anchors/iframes including data-src, distinct keys and Arabic titles, script-only empty iframe rejection, channel-post exclusion and structured event parsing.

Fixtures are synthetic and require no live sports feed. Filtering tests exercise policy and static extraction, not a running Android WebView network stack. HTTP errors, popup rejection, fullscreen callbacks and lifecycle controls were inspected and compiled, but not exercised on a device.

## Public page inspection

| Source | Actual result |
| --- | --- |
| VIPRow | Homepage readable through web search. Local football schedule fetch returned 403. Existing scoped WebView integration retained; installed catalogue flow unverified. |
| q.korahlive.online | Homepage returned HTTP 200 with an empty schedule container and channel posts. The published external schedule script returned 403 when fetched locally. Static structured-event adapter shows an empty state rather than treating channels as matches. |
| Supplied KorahLive ch1 page | HTTP 200. Its iframe initially has no src. Resolver script searches other sites by one team and constructs fallback YasirTV keys/URLs; SportsWave does not execute or reproduce this behavior. No ordinary published compatible iframe source found. |
| kooratv.club | Landing page and published matches-today page readable through web search. Direct local request returned 403; actual per-match server links could not be verified. No fabricated route or provider integration added. |
| YasirTV | Recognition and extraction verified with offline fixtures. No claim of successful live playback. |
| TodayStreams / DaddyLive / Dembed | Existing recognition retained. Live playback unverified. |
| Public Mux HLS | Existing authorized test action retained. Playback not executed on a device. |

## Device status and remaining work

`adb devices` returned no attached device or emulator. Installation, catalogue → player list → fullscreen, popup behavior, refusal states, Arabic rendering and actual HLS/DASH playback could not be exercised. No automated playback test was claimed.

The complete v2 acceptance criteria are not yet met: KooraTV server discovery and KorahLive script-rendered schedules remain unsupported; remote trusted configuration is not implemented. Catalogues remain separate; conservative matching exists as a tested utility. WebView filtering can block required provider resources until explicit reviewed hosts are configured and does not cover all browser-managed traffic or redirected subresource chains.


## Livekora integration - 2026-10-13

Added offline tests for distinct fixtures sharing a channel URL, kickoff timezone conversion, invalid timestamps, lookalike hosts, unsupported sports, and explicit commentary-language metadata. The full suite has 22 tests. Debug build and initial test run passed. Emulator installation succeeded; selecting Livekora loaded 11 published football fixtures. Opening the selected FUS Rabat - Raja channel returned the resolver's unavailable state; no successful video playback is claimed. This check exposed schedule-only fixtures being marked live indefinitely. Their labels now say scheduled/status unverified and use a four-hour current-section window, moving older fixtures to Earlier fixtures without asserting an official end time.


## Release verification gate - 2026-10-13

Result: FAILED; no commit, tag, push, or release published for this update.

- Latest debug build installed successfully on emulator-5554.
- Livekora loaded 11 fixtures with scheduled/status-unverified labels.
- Changing Relevance to Kickoff visibly reordered the current fixtures.
- Opening Arsenal - Lille returned Event page unavailable in the resolver. The preceding FUS Rabat - Raja check had the same result. Neither reached verified video playback.
- VIPRow Football displayed its schedule timeout state during this run.
- The preceding final build passed all 22 offline unit tests, but these do not certify external feed availability or video playback.

Local evidence: audit/release-home, audit/release-livekora, audit/release-sort, audit/release-player-result (PNG/XML/JSON; ignored development artifacts). These observations prevent representing this update as a working playback release.
