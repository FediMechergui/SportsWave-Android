<p align="center"><img src="docs/branding/sportswave-icon.png" width="112" alt="SportsWave wave and play icon"></p>
<h1 align="center">SportsWave</h1>
<p align="center">A native Android sports catalogue with in-app player discovery and fullscreen playback.</p>
<p align="center"><a href="https://github.com/FediMechergui/SportsWave-Android/releases/tag/v1.0.2">Download the APK</a> · <a href="PROVIDERS.md">Provider guide</a> · <a href="TEST_REPORT.md">Test report</a></p>

## Install

Download **SportsWave-1.0.2.apk** from the [GitHub release](https://github.com/FediMechergui/SportsWave-Android/releases/tag/v1.0.2). Open it on an Android 8.0+ device and allow installation from that download source if Android asks. The APK is a signed release build, not a debug APK.

The release also includes **SportsWave-1.0.2.aab** for Android store distribution and **SHA256SUMS.txt** for checking downloads. An AAB is not directly installable; use the APK on your phone. This GitHub release is not a Google Play listing.

If you previously installed a development/debug APK with the same package name, Android cannot update it with the new release key. Remove that debug installation first; this removes its local app data. Future release APKs will use the same release-signing identity.

## Calendars, scores and Arabic/English players

Version 1.0.2 defaults to **Arabic & Tunisia · all sports**, retaining international fallback calendars and all 20 sports. Tunisian competitions and Arabic catalogue sources receive relevance priority, and the source directory initially shows Arabic/Tunisian websites. Watania, Diwan, beIN Match and researched Yacine website handoffs are accessible across sports. See [Arabic-channel investigation](docs/ARABIC_CHANNELS.md) for the unverified Yacine/beIN Match playback.

ESPN's English public scoreboards and FilGoal's Arabic football cards are integrated. Selecting a calendar date fetches that date, with adjacent API dates to cover timezone boundaries; a seven-day strip and the device timezone appear above the fixtures. The default metadata sources are English or Arabic; the legacy German OpenLigaDB adapter is no longer in the source picker.

All 20 sport tabs have local vector icons. ESPN adds selected football, basketball, American football, baseball, hockey, tennis, golf, Formula 1, UFC and rugby competitions. Other sports retain available VIPRow schedules; complete worldwide coverage is not claimed. FilGoal covers its published football fixtures. Score-only entries never become invented stream links.

Arabic and English commentary choices are prioritized. Published players with missing commentary metadata remain selectable and are labelled Language unverified. Other explicitly reported languages are labelled. Page language and channel names are not proof of commentary language.

## Added in 1.0.1

Streamed adds public event schedules and published player choices; Yalla Shoot Koora adds Arabic football cards and crests. Raceweek adds multi-day racing meeting calendars, without inventing session times, scores or streams. Cricket has its own tab and icon. Fixture aggregation runs off the UI thread to address the source-switching freeze found during testing.

The Sources screen lists audited websites and mirrors, including Arabic and Tunisian services such as Watania and Diwan Sport. Directory links open in the browser and remain playback unverified; they are not a count of working in-app servers. Channel posts remain separate from match fixtures. See [source audit](docs/FMHy_SPORTS_AUDIT.md).

## Release coverage

**1.0.0:** native left/right team scoreboards, selected English ESPN score feeds, Arabic FilGoal fixtures, Tunisian/international competition priorities, date navigation, race entrants/classifications, shared published crests and an integrated official beIN MENA TV guide. The TV guide offers date/channel/live-broadcast filters and official channel handoffs. Match-name filtering requires both team names; spelling differences can hide a broadcast.

**Coverage remains source-dependent.** This release does not guarantee a server for every event, every team crest, second-by-second scores or comprehensive worldwide calendars. Unknown commentary languages are labelled rather than hidden. Official watch services open in their own app/browser and can require subscriptions or regional availability. Calendar entries and guide listings are not proof of working video. Earlier verified playback is dated in the test report; YacineTV/Robotiva has no newly verified player integration.

| Feature | Current behavior |
| --- | --- |
| Catalogue | VIPRow, ESPN, FilGoal, Streamed, Yalla Shoot Koora, Raceweek and available Livekora/KorahLive metadata; date-specific calendars, search, sorting and Tunisia shortcut. |
| Sport coverage | All 19 published VIPRow categories are configured, including the corrected MMA route. Availability varies by category and date. |
| Scores/status | Source-reported scores/status when published; other started fixtures are labelled status unverified. No invented match clocks. |
| Player discovery | Actual published anchors, iframe src/data-src and server data attributes; bounded scans, title labels and exact URL deduplication. |
| Web providers | Streamed, TodayStreams, DaddyLive, Dembed, YasirTV, AlbaPlayer and SNRT recognition. Full published YasirTV query parameters are preserved. |
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

Team crests use published ESPN/FilGoal images, reused across reviewed Arabic/English team identities within the same sport, with local caching and initials when artwork is missing. Optional TheSportsDB lookups require appropriate API access; universal artwork coverage is not claimed.

## Build from source

Requirements: Android SDK 36, JDK 17+ (local verification used JDK 21), and internet access for Gradle dependencies. The project uses Android Gradle Plugin **8.10.1**, Kotlin **2.2.10** and Media3 **1.11.1**. The committed Gradle wrapper is pinned to **8.11.1**; this release was verified with that Gradle version.

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

Version 1.0.2 passes 70 offline Android tests and three Python importer tests, adding coverage for competition priority, conservative aggregation, public scores and saved schedules. Offline tests cover provider recognition, unsafe URLs, hostname boundaries, YasirTV query preservation, Arabic labels, multiple sources, deduplication, routing, event normalization/matching and nested-provider permissions. Tests do not depend on live sports feeds. GitHub Actions runs tests and produces a debug APK; signed releases use the private local signing key.

- [Actual test results and emulator limitations](TEST_REPORT.md)
- [How to add a provider](PROVIDERS.md)
- [Complete optimization audit](OPTIMIZATION_AUDIT.md): all 19 VIPRow routes, score-data candidates, compact league-based layouts, accessibility, Arabic support and performance.
- [Release history](CHANGELOG.md)

Remaining priorities are wider provider playback verification, broader reliable score coverage, physical-device performance checks and complete Arabic localization. Beta.3 includes compact rows, date controls and favourites.


### Catalogue update

The source picker now includes Livekora's published football match cards alongside VIPRow and KorahLive. Livekora retains kickoff timezone offsets and separate fixture identities even when matches share a channel. Its placeholder scores are ignored. Live/Upcoming labels remain schedule-based, not a verified live-score feed.

All 19 published VIPRow categories are available, including the corrected `mma-events` route. Sort within schedule sections by Relevance (search-name match, available player link, proximity to kickoff), Kickoff, or League. This is not audience popularity ranking.

Player choices are grouped by explicit server `data-language` metadata; otherwise commentary language is unknown. Page language is never used as commentary evidence. Livekora channel wrappers resolve published AlbaPlayer iframe links with a restricted resource allowlist. Arryadia/SNRT playback is verified on the emulator; other channels remain subject to their own dependencies and availability; Robotiva's inaccessible metadata endpoint is not enabled. See [provider research](docs/PROVIDER_RESEARCH.md).

Livekora status is explicitly unverified. Started fixtures remain in the schedule-based current section for at most four hours, then move to Earlier fixtures; this window is not an official match-end time.

The Channels menu contains reviewed published Arryadia/SNRT and beIN Sports 4 channel-page links independently of the match schedule. A listed channel is not proof of a particular match broadcast. Arryadia is verified; the sampled beIN media request still stalled.
