# Changelog

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
