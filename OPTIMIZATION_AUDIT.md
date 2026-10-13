# SportsWave emulator and product audit

Inspected 13 October 2026, Africa/Lagos. This is a prioritized implementation backlog, not a claim that all recommendations have been built. Source pages and provider offerings were checked online on this date; cached pages may be older. No sports-data subscriptions were purchased.

## What was actually observed

- Connected emulator: `emulator-5554`, Android x86_64, 1080 × 2400, density 420, approximately 411 × 914 dp. Font scale 1.0, timezone Africa/Lagos.
- The app was initially open in WebPlayerActivity with an almost entirely black screen and only Back / Switch server visible. [Screenshot](docs/audit/01-current-screen.png).
- Football loaded 199 events. One capture showed All 199, Live 56, Upcoming 150. Live and Upcoming overlap because SOON is included in both; these are not disjoint totals. [Screenshot](docs/audit/02-player-selection.png), despite the early filename, shows the catalogue.
- A football card showed a scheduled kickoff at 4:15 PM and `3h 14m in`, with a LIVE badge but no score. This is elapsed wall time derived from the schedule, not a verified match clock or result.
- Tennis showed no listed events and Retry, while the header said No schedule loaded. A valid empty result should not be described as a failed load. [Screenshot](docs/audit/04-home-stable.png).
- Selecting Al Bukayriyah vs Al Zulfi discovered one published TodayStreams option, Thmanyah 2. [Resolver evidence](docs/audit/10-resolver.png).
- Live WebView debugging found the actual nesting chain: TodayStreams → DaddyLive (`dlive.sx/stream/stream-24.php`) → Dembed (`dembed.st/premiumtv/daddy.php`). The original resource policy blocked these required child documents.
- A process snapshot while several screens were retained showed about 159 MB total PSS, 324 MB RSS, four Activities and three WebViews. This is one diagnostic snapshot, not a leak determination or performance benchmark.
- Playback follow-up fixed the verified nested frame/resource filtering and added bounded document timeouts, child error reporting and a delayed recovery hint. The sampled web stream still buffered at zero seconds after media requests became allowed. Native Mux HLS rendered an initial frame but a later screenshot showed green corruption on this Android 17 emulator. Neither test is a clean playback success; see [TEST_REPORT.md](TEST_REPORT.md).
- Some early UI captures raced with concurrent emulator navigation. Treat their filenames as labels only; screenshot/XML content is the evidence. Subsequent playback reproduction was coordinated with the user.

## First priority: trustworthy scores and playback

| Priority | Finding | Required change | Completion check |
| --- | --- | --- | --- |
| P0 | The app has no actual live-score fields or score feed. LIVE is inferred from kickoff/end timestamps. | Add a ScoreProvider adapter and explicit provider status, period, clock, score, coverage, fetchedAt and staleAfter. Until verified, label an event Scheduled / Expected in progress / Status unavailable. | A postponed match never becomes live from the clock alone; scoreless coverage never shows a fabricated 0–0. |
| P0 | Scheduled end times can leave football marked LIVE hours after kickoff. | Feed-driven final, halftime, extra-time, penalties, postponed, cancelled, suspended and abandoned states. Keep schedule availability separate from score status. | Replay synthetic state transitions and stale-feed cases. |
| P0 | TodayStreams' nested player documents were denied by the host restriction. | Separate top-level navigation from explicit nested-provider permissions. Allow verified TodayStreams → DaddyLive → Dembed document dependencies without permitting arbitrary redirects or advertising hosts. | Observe the child documents load; verify an unrelated main-frame navigation and ad iframe remain rejected. |
| P0 | Parent onPageFinished hides the spinner even if the child player is empty. | Track document loading separately from video readiness. Timeout main-document stalls; report approved child HTTP/network errors. Provide useful retry/switch controls and a delayed assistance hint. | Blank/error provider page offers a recovery action; never report successful video solely from HTML completion. |
| P0 | Web player compatibility and authorized native playback need real device checks. | Verify HLS with the retained public Mux sample, DASH with an authorized public sample, WebView embeds, sound, fullscreen, back and lifecycle. | Record actual video frames and progressing playback time; document each provider independently. |
| P1 | A stream choice can exist without being playable. | Separate Published / Opening / Unavailable / Recently verified states. Scope provider health to a recent attempt, not a permanent guarantee. | Discovery does not imply availability. |
| P1 | Scores and stream URLs belong to different providers with different identifiers. | Maintain explicit mapping of source event IDs to canonical score event IDs. Use sport, both participants, league, gender/age category and start time; reject ambiguous mapping. | A youth/women match or same-team second fixture never receives the wrong score or stream. |

Do not use wall-clock elapsed duration as football minute 63, basketball quarter clock or tennis set state. Do not infer that a score source also grants stream availability.

## Every VIPRow category

VIPRow's [published navigation](https://viprow.im/) lists 19 categories. SportsWave currently declares seven. Twelve are missing and the MMA route is wrong. These routes were followed from the site's navigation; none were guessed. A route being published does not prove its live schedule or player compatibility.

| Category | Published path under `/sports/` | Current app | Required score/detail presentation |
| --- | --- | --- | --- |
| Football | `football/` | Present | Goals, provider match clock, half/extra-time/penalties, cards. |
| Basketball | `basketball/` | Present | Points, quarter/period, remaining clock, overtime. |
| Ice hockey | `ice-hockey/` | Missing | Goals, period, clock, overtime/shootout. |
| Motorsports | `motorsports/` | Present | Session type, lap/total laps, positions, gaps and classification; not two-team scores. |
| Boxing | `boxing/` | Present | Fight card, round, scheduled rounds, winner and result method when available. |
| MMA | `mma-events/` | Present with incorrect `mma` slug | Event card, bout, round, winner and result method. |
| American football | `american-football/` | Present | Points, quarter, clock, down/distance when covered. |
| Rugby | `rugby/` | Missing | Points, half, match clock; distinguish union/league coverage. |
| Baseball | `baseball/` | Missing | Runs, inning, top/bottom, outs; optional bases/hits/errors. |
| Tennis | `tennis/` | Present; empty in captured run | Sets, games, points, tiebreak and serving indicator; singles/doubles. |
| Aussie rules | `aussie-rules/` | Missing | Goals, behinds, total points, quarter. |
| Golf | `golf/` | Missing | Tournament/round leaderboard, score relative to par, holes completed. |
| Darts | `darts/` | Missing | Sets/legs and match format. |
| Snooker | `snooker/` | Missing | Frame score, frames won, best-of format. |
| Volleyball | `volleyball/` | Missing | Sets and current set points. |
| Handball | `handball/` | Missing | Goals, half and clock. |
| Horse racing | `horse-racing/` | Missing | Meeting, race/off time, distance, runners and official placings. |
| Cycling | `cycling/` | Missing | Race/stage, distance remaining, leader/group gaps and classification. |
| More sports | `more-sports/` | Missing | Heterogeneous event types; show schedule/title unless the actual sport is identified. |

Implementation: replace the seven hardcoded choices with a reviewed category registry carrying source URL, canonical sport ID, icon, ordering, score-renderer family and coverage capability. Prefer refreshing this registry from vetted public navigation with a local fallback. Keep full discoverability in a searchable All sports sheet; pin 4–6 user favorites in the horizontal strip. Do not put 19 large chips in a scrolling row as the only navigation. Cricket and esports may be useful additions from other providers, but do not claim VIPRow offers them as dedicated categories when it does not.

## Recommended interface structure

Keep the existing Kotlin Views/RecyclerView stack. A framework migration is not required to fix the product.

| Surface | Proposed structure |
| --- | --- |
| Catalogue | Compact SportsWave header; reachable search; favorite sport shortcuts + All sports; date strip with Today and calendar; All / Live / Finished; league sections containing compact match rows. |
| Match row | Competition shown once per group. Two participant lines with consistent 24–28 dp crests. Score aligned in a tabular column. Verified period/clock next to it. Kickoff when upcoming. Favorite and a small Watch affordance only where published choices exist. |
| Match detail | Participants + score/status; Overview, Timeline, Stats, Lineups where coverage exists; clear Watch / Available players entry. Hide unsupported tabs instead of showing empty fake statistics. |
| Player choices | Event and league context; server label, provider, published quality/language when supplied; full-width selectable rows; retry discovery; a distinct empty state. Quality badges describe source labels, not a tested bitrate. |
| Player | Video-first landscape layout; safe-area Back; separate Switch server; loading/error recovery; controls that fade and reappear on tap; preserve position/fullscreen state across recreation. |
| Bottom navigation | Matches, Favorites, Sports, Settings. Move HLS test into a developer/test menu. Scores and playback choices remain linked through the event. |

Target hierarchy: date/sport → competition → participants and score → stream action. The current source selector before the brand and a repeated large Watch button on every card consume too much space. Approximately 2–3 match cards are visible on the captured tall phone. Target 4–5 compact rows on the same viewport while retaining readable text and 48 dp controls. Treat this as a layout target to validate, not a measured achieved improvement.

### Layout and interaction backlog

1. Collapse the provider selector into a source/filter sheet; make coverage and unavailable sources understandable.
2. Group by league within date/status and support pinned/collapsible competitions. Current grouping is primarily status/day, with league repeated per card.
3. Add yesterday/today/tomorrow/calendar and a Finished filter. Current tabs cannot directly select results or date.
4. Reserve the strongest green for selected navigation and verified live score state; remove repeated heavy borders and full-width green buttons from every row.
5. Use one consistent sport icon set rather than variable emoji glyphs. Keep team/competition artwork optically sized.
6. Keep 14–16 sp participant text, tabular scores, subdued supporting labels. Avoid relying on 9.5–11.5 sp text for essential status/action information.
7. Persist selected sport, catalogue, filter, date, query and list position across activity recreation. Use saved state and a screen-level model rather than Activity fields alone.
8. Provide Favorites for teams, leagues and events; allow guest/local use. Add notifications only after the score feed is reliable and deduplicated.
9. Distinguish empty schedule, no search matches, no live matches, unavailable score coverage, offline cache and provider failure. Do not show Retry for a valid empty date as the only next action.
10. Add clear search, normalized Arabic/English aliases, league filtering and search result counts. Avoid dropping age/gender markers from event identity.
11. Make player loading copy useful and avoid treating a loaded webpage as a playing stream. A provider badge or HD label is not a health check.
12. Keep Back and Switch server separate, reachable and outside display cutouts. Avoid a permanent overlay covering the provider's own controls.

## Data and performance backlog

- Introduce a repository/cache for schedules and scores. The current Activity owns UI, loading, parsing, navigation and state. Persist the last successful schedule and mark stale data; do not erase useful content on a transient failure.
- Parse static public schedule data when available before using a WebView. If rendering is necessary, wait for the required data/anchors with a bounded readiness condition rather than a single onPageFinished callback.
- Validate published event links to the catalogue host and expected path when normalizing, not only when opening the resolver. Keep query parameters unchanged.
- Distinguish HTTP failures, JSON/schema failures, empty successful results and cancellation. Do not retry aggressively or repeatedly load unavailable sources in background.
- Use ListAdapter/DiffUtil and stable IDs. Current `notifyDataSetChanged()` on each 30-second rebuild rebinds the visible list, tabs and animated states. This is code evidence of avoidable work, not a measured dropped-frame result.
- Update only changed clocks/scores; pause polling off-screen; select polling cadence from provider limits and coverage. Avoid continuous pulse animations on every live card and skeleton when reduced motion is requested.
- For a 15-second poll interval, one continuously active user would make 5,760 calls/day if unbatched. API-SPORTS' advertised free 100/day is a prototype allowance, not an all-day live-score budget. Batch live events, cache centrally when permitted and implement backoff/429 handling.
- A hosted key-protecting/cache service may be required by the chosen data vendor. This still supports a standalone APK with no local desktop server. Sportradar explicitly says its APIs are B2B and should not be called directly from client apps.
- Release hidden extraction WebViews after bounded discovery, or explicitly pause them when playback opens. Audit retained Activities/WebViews with a repeatable enter/exit test before concluding there is a leak.
- Add a measured startup/scroll/playback benchmark, jank traces, memory measurements and release APK size/R8 validation. The observed debug APK and emulator are not a production performance baseline.

### Team-logo correctness

The current TeamBadges code performs name search, can choose the first result of the right sport, caches by normalized name without the sport, retains bitmaps indefinitely in memory and stores negative hits. Use stable provider team IDs, sport-aware cache keys, exact/verified alias matching, bounded memory, byte-size limits and an explicit retry TTL. Women/youth/reserve name normalization may be useful for artwork, but must never be reused for score identity.

The current code defaults to public key `3`; [TheSportsDB's current documentation](https://www.thesportsdb.com/documentation) instead documents public key `123` and says free team search is limited to Arsenal. Do not merely swap public keys and assume unrestricted production team-logo lookup. Confirm the account/plan and use licensed bundled or permitted cached artwork where appropriate.

## Online resources worth evaluating

These are evaluated candidates and design references, not integrations already installed. No single source below has been proven to cover every VIPRow event.

| Resource | Best role | Material limitation / decision |
| --- | --- | --- |
| [API-SPORTS](https://api-sports.io/) | Prototype/paid feeds for football, basketball, baseball, AFL, F1, handball, hockey, MMA, NBA, NFL/NCAA, rugby and volleyball. | Official page advertises 15-second live updates and 100 free calls/day per API. Check exact competitions and per-sport endpoints; listed products do not constitute full coverage of all 19 VIPRow categories. |
| [Sportradar](https://developer.sportradar.com/getting-started/docs/get-started) and [coverage matrix](https://coverage-matrix.sportradar.com/) | Broad multisport coverage, including tennis, golf, racing, cycling, darts and snooker. | B2B, licensed packages; coverage varies by competition/tier. Requires server-side integration according to official docs. Validate boxing/horse-racing coverage separately; do not infer it from a broad sports list. |
| [TheSportsDB API](https://www.thesportsdb.com/api.php) / [documentation](https://www.thesportsdb.com/documentation) | Team/league metadata and artwork; lower-frequency score option if its coverage suffices. | Documentation and plans must be checked together: premium offers two-minute livescores; current free search restrictions make arbitrary logo lookup unreliable as a production assumption. |
| [football-data.org](https://www.football-data.org/pricing) | Focused football fixtures, tables and selected leagues. | The free tier explicitly has delayed scores/schedules; live scores require an appropriate paid package. Not a multisport solution. |
| [Sportmonks Football](https://www.sportmonks.com/football-api/) / [Cricket](https://www.sportmonks.com/cricket-api/) | Detailed football or a separately scoped cricket expansion. | Evaluate paid competition coverage and IDs. Does not replace a complete multisport plan. |
| [Sofascore home-screen redesign](https://www.sofascore.com/news/sofascores-new-home-screen-a-smarter-faster-way-to-follow-sports) | Design reference: sport switching, pinned/reorderable leagues, quick filters and favorites. | Use product patterns as inspiration. A consumer interface is not authorization to depend on an undocumented private feed. |
| [Flashscore features](https://www.flashscore.com/faq/capabilities/) | Design reference: favorite matches/teams and personalized notifications. | Official pages can differ as features roll out; use current product announcements for specifics. Do not rely on a presumed public API. |
| [FotMob](https://www.fotmob.com/) | Football reference: match-first hierarchy, Live / On TV / By time and detail organization. | Its site explicitly disallows systematic automated use; use as a visual/product reference, not a scraping backend. |
| [LiveScore](https://www.livescore.com/en/) | Reference: compact multisport score navigation and match details. | Consumer reference, not a verified developer data license. |

Recommended selection process: start with a coverage spreadsheet of the user's preferred leagues and all 19 categories. Test sample events and missing-state behavior against an authorized trial, then select a score vendor and key-hosting arrangement. Keep VIPRow as the stream catalogue, not the authority for live results. Score availability and stream availability must be independent.

## Accessibility, Arabic and device behavior

- [Android accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps) recommends 48 × 48 dp touch targets. Current refresh is 46 dp, sport chips 41 dp high, tabs 37 dp, action controls 38 dp and bottom items 40 dp. Some parent rows increase effective tap area, but tabs/chips still need explicit minimum targets.
- Expose native button/tab semantics and selected states. Existing clickable TextViews report `selected=false` even for highlighted chips. Group a match as a useful announcement; mark crests decorative when names are already spoken.
- Provide real Arabic string resources, [per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages), `supportsRtl`, locale-aware numbers/dates and bidi-safe mixed Arabic/provider labels. Rendering an Arabic name alone is not full RTL support.
- Test 1.3× and 2× font scales, TalkBack, switch/keyboard navigation, contrast, reduced animation, narrow screens, landscape, tablets and display cutouts. These were not all exercised in this audit.
- Follow [Android safe-area/layout guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics) and [edge-to-edge guidance](https://developer.android.com/develop/ui/views/layout/edge-to-edge). Target SDK 35 requires correct insets on modern Android. Resolver and player controls need the same care as the catalogue.
- Restore state through rotation, background/foreground, process death and connection changes. Keep screen on only while appropriate playback is active; do not keep a black failed page awake indefinitely.

## Implementation order and release gates

1. Fix and verify permitted nested player loading, truthful error states and public native playback. Record successful and failed providers separately.
2. Add all 19 reviewed sport routes, correct MMA and introduce sport-aware event/score rendering. Verify each route with empty, populated and failure fixtures.
3. Integrate an authorized score feed for a defined coverage subset; add mapping, freshness and real status. Expand coverage deliberately.
4. Ship compact league-grouped rows, date/results controls, All sports picker and persistent navigation state.
5. Improve logo identity/caching, favorites and language/accessibility support.
6. Benchmark and harden polling, offline cache, lifecycle, fullscreen, provider health and release builds.

Release tests should cover one/multiple/no sources, unavailable provider, permitted cross-provider iframe, blocked ad/popup, stale scores, postponed/final state corrections, Arabic and women/youth identities, every sport renderer, orientation, foreground/background, offline recovery and scrolling with several hundred events. Neither automated fixture passes nor a published HD label proves a live stream works.
