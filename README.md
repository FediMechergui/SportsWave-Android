<p align="center"><img src="docs/branding/sportswave-icon.png" width="112" alt="SportsWave wave and play icon"></p>
<h1 align="center">SportsWave</h1>
<p align="center">A native Android sports catalogue with in-app player discovery and fullscreen playback.</p>
<p align="center"><a href="https://github.com/FediMechergui/SportsWave-Android/releases/tag/v1.0.0-beta.2">Download the beta</a> · <a href="PROVIDERS.md">Provider guide</a> · <a href="TEST_REPORT.md">Test report</a></p>

## Install

Download **SportsWave-1.0.0-beta.2.apk** from the [GitHub release](https://github.com/FediMechergui/SportsWave-Android/releases/tag/v1.0.0-beta.2). Open it on an Android 8.0+ device and allow installation from that download source if Android asks. The APK is a signed release build, not a debug APK.

The release also includes **SportsWave-1.0.0-beta.2.aab** for Android store distribution and **SHA256SUMS.txt** for checking downloads. An AAB is not directly installable; use the APK on your phone. This GitHub release is not a Google Play listing.

If you previously installed a development/debug APK with the same package name, Android cannot update it with the new release key. Remove that debug installation first; this removes its local app data. Future release APKs will use the same release-signing identity.

## Current beta status

**This is an experimental prerelease.** VIPRow catalogue loading and Arryadia/SNRT web playback have been verified in the signed APK on Android 15, including landscape fullscreen, and in the Android 17 debug build. This does not certify every provider or channel. Native HLS previously showed emulator corruption; KooraTV/KorahLive schedules, verified live scores and full Arabic localization remain limited. See the dated checks in the test report.

| Feature | Current behavior |
| --- | --- |
| Catalogue | VIPRow public event metadata and Livekora football match cards; search, refresh, status/day grouping and selectable sorting. |
| Sport coverage | All 19 published VIPRow categories are configured, including the corrected MMA route. Availability varies by category and date. |
| Live indicators | Estimated from scheduled times, **not verified live scores or match clocks**. |
| Player discovery | Actual published anchors, iframe src/data-src and server data attributes; bounded scans, title labels and exact URL deduplication. |
| Web providers | TodayStreams, DaddyLive, Dembed, YasirTV, AlbaPlayer and SNRT recognition. Full published YasirTV query parameters are preserved. |
| Web playback | Fullscreen WebView, explicit nested-provider/resource rules, popup restrictions, loading timeout, retry and back/switch-server controls. |
| Native playback | Media3 HLS/DASH routing for configured authorized manifests, buffering/error/retry and lifecycle handling. |
| Arabic | UTF-8 team/server labels are retained; complete localization and RTL layout remain planned. |
| KorahLive | Conservative static structured-event adapter; its inspected script-generated resolver is unsupported. |
| KooraTV | Investigated, but direct requests returned 403 and its server flow remains unverified. |

SportsWave does not invent stream URLs, construct access keys, extract protected web-player sessions or claim broadcast rights. Provider availability can change independently of the app. Media CDN host changes require reviewed provider configuration updates.

## How it works

**Catalogue → Event → Available players → Fullscreen playback**

The app runs on the Android device without a local desktop server. VIPRow metadata loads in a constrained internal WebView. Event pages supply published player links; the shared provider registry recognizes them and routes a selected source to the appropriate engine. The user never needs to paste a URL.

Web documents remain in the WebView. Only explicitly configured authorized `.m3u8` / `.mpd` manifests route to Media3. The verified dependency chain TodayStreams → DaddyLive → Dembed has explicit frame rules; unrelated navigation and advertising hosts remain restricted. HTML loading is not treated as proof of video playback.

Team crests use optional TheSportsDB lookups with local caching and initials as a fallback. Current public API restrictions can limit artwork availability; see the audit before choosing a production data plan.

## Build from source

Requirements: Android SDK 36, JDK 17+ (local verification used JDK 21), and internet access for Gradle dependencies. The repository includes **Gradle 8.11.1**, Android Gradle Plugin **8.10.1**, Kotlin **2.2.10** and Media3 **1.11.1**.

Open the project in Android Studio and sync, or run:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

On macOS/Linux:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

Signed release builds require your private signing properties file:

```powershell
$env:SPORTSWAVE_SIGNING_PROPERTIES = "C:/private/sportswave/release.properties"
./gradlew.bat testDebugUnitTest assembleRelease bundleRelease
```

See [Release guide](docs/RELEASING.md) for signing, verification, bundle usage and reproducible artifact preparation. Keystores, signing passwords, local SDK paths and private API-key files are excluded from Git.

## Project map

| File | Responsibility |
| --- | --- |
| `MainActivity.kt` | Native catalogue, filters, scheduling and event selection. |
| `ResolveActivity.kt` | Published player choices and native options. |
| `Playback.kt` | Shared source models, provider adapters and routing. |
| `ProviderRegistry.kt` / `PlayerRequestPolicy.kt` | Provider identification, permitted nested documents and resource policy. |
| `WebPlayerActivity.kt` | Fullscreen embedded playback and recovery controls. |
| `PlayerActivity.kt` | Native Media3 playback. |
| `CatalogueProvider.kt` / `SportEvent.kt` | Conservative extra catalogue support and event matching. |
| `app/src/main/assets/providers.json` | Reviewed provider host/path/resource/frame rules. |

The source namespace/package remains `com.example.sportsstream` to preserve the existing application identity.

## Tests and roadmap

24 offline tests cover provider recognition, unsafe URLs, hostname boundaries, YasirTV query preservation, Arabic labels, multiple sources, deduplication, routing, event normalization/matching and nested-provider permissions. Tests do not depend on live sports feeds. GitHub Actions runs tests and produces a debug APK; signed releases use the private local signing key.

- [Actual test results and emulator limitations](TEST_REPORT.md)
- [How to add a provider](PROVIDERS.md)
- [Complete optimization audit](OPTIMIZATION_AUDIT.md): all 19 VIPRow routes, score-data candidates, compact league-based layouts, accessibility, Arabic support and performance.
- [Release history](CHANGELOG.md)

Immediate priorities are wider provider playback verification, a licensed live-score feed with truthful status/freshness, and compact match rows with date, league and favorite controls.


### Catalogue update

The source picker now includes Livekora's published football match cards alongside VIPRow and KorahLive. Livekora retains kickoff timezone offsets and separate fixture identities even when matches share a channel. Its placeholder scores are ignored. Live/Upcoming labels remain schedule-based, not a verified live-score feed.

All 19 published VIPRow categories are available, including the corrected `mma-events` route. Sort within schedule sections by Relevance (search-name match, available player link, proximity to kickoff), Kickoff, or League. This is not audience popularity ranking.

Player choices are grouped by explicit server `data-language` metadata; otherwise commentary language is unknown. Page language is never used as commentary evidence. Livekora channel wrappers resolve published AlbaPlayer iframe links with a restricted resource allowlist. Arryadia/SNRT playback is verified on the emulator; other channels remain subject to their own dependencies and availability; Robotiva's inaccessible metadata endpoint is not enabled. See [provider research](docs/PROVIDER_RESEARCH.md).

Livekora status is explicitly unverified. Started fixtures remain in the schedule-based current section for at most four hours, then move to Earlier fixtures; this window is not an official match-end time.

The Channels menu contains reviewed published Arryadia/SNRT and beIN Sports 4 channel-page links independently of the match schedule. A listed channel is not proof of a particular match broadcast. Arryadia is verified; the sampled beIN media request still stalled.
