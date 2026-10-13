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
