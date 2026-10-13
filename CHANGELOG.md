# Changelog

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
