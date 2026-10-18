# FMHY sports-source audit — 18 October 2026

Discovery starting point: [FMHY Live Sports](https://fmhy.net/video#live-sports). This records discovery results and integration limits. Version 1.0.1 integrates Streamed schedules/player discovery, Yalla Arabic fixtures, Raceweek meeting calendars and a browser source directory. It does not certify video playback.

## Strongest candidate: Streamed

[Streamed's own API documentation](https://streamed.pk/docs) describes public, unauthenticated JSON endpoints for matches, streams, sport categories and images. Ordinary HTTPS requests retrieved 135 records across 13 populated categories in this check. The sport endpoint listed 15 supported categories; a listed category does not guarantee current events.

Actual sampled stream responses included explicit English labels for football, basketball, American football, hockey, baseball and darts. Some labels contain a broadcaster suffix, such as `English - ESPN`. The adapter must handle reviewed language/broadcaster metadata without loosening the existing Arabic/English policy for arbitrary labels. Motorsport and another sampled event used only `Main`; those choices must remain hidden. No Arabic stream was verified in the sampled responses.

Useful documented endpoints:

- `/api/matches/all`: match IDs, millisecond timestamps, categories, optional team names/badge handles and published source references.
- `/api/stream/{source}/{id}`: use the actual source/id pair returned by the match endpoint. Responses provide language, HD/SD and embed URL. Never enumerate IDs or derive protected playback credentials.
- `/api/sports` and the documented image endpoints: category discovery and published artwork.

Integration constraints:

- Some returned items are channels (for example Sky Sports Golf, Tennis Channel or Fox League), not fixtures. Do not fabricate opponents, kickoff times or match scores from these records.
- The generic `fight` category includes AEW wrestling; it cannot all be mapped to boxing or MMA.
- These match responses do not establish a live-score or complete competition/league feed. Keep ESPN/FilGoal score attribution and conservative fixture matching; missing leagues must not silently weaken existing score-merging rules.
- The inspected embed document returned HTTP 200 on `embed.st`. It referenced scripts on `cdn.jsdelivr.net` and `strmd.b-cdn.net` plus an advertising loader and a hidden ad iframe. Actual player/network dependencies and video progression still need an isolated browser/emulator check before enabling a provider. Do not broaden host rules to make an unverified player load.
- Sampled source IDs used PPV-labelled names and the same embed host. Treat apparent site diversity as a potential shared backend, not independently verified fallback servers.

## Other sources inspected

| Source | Actual check | Integration implication |
| --- | --- | --- |
| [Raceweek](https://raceweek.io/) | English calendar index and F1/MotoGP season pages accessible | Candidate for broader racing schedules; validate dates against organisers. No live timing or player API verified. |
| [OnHockey](https://onhockey.tv/) | Landing page and its published English schedule iframe returned 200 | Hockey-specific discovery candidate; no video or commentary validation yet. |
| [Tiz-Cycling](https://tiz-cycling.tv/) | Landing page returned 200 | Cycling-specific candidate; live/replay and language classification remain unverified. |
| [PPV](https://ppv.st/) | Script-rendered page returned 200 | No public API verified in this check; a probed API-like path returned HTML and is not an API. |
| [TimStreams](https://timst.top/) | Script shell returned 200 | Needs rendered schedule/player inspection. |
| [StreamCorner](https://streamcorner.st/) | Script shell returned 200 | Needs rendered inspection and dependency review. |
| [LiveTV](https://livetv.sx/enx/) | English landing page returned 200 | Event-link candidate; language/date/score mapping not certified. |
| [Matchora](https://matchora.to/) | Landing page returned 200 | No independently verified player backend yet. |
| [FalconStreams](https://falconstreams.app/) | Redirected to `falconstreams.st/home1`, returned 200 | Redirect and actual players need review before registration. |
| [CricHD](https://crichd.at/) | Landing page returned 200 and linked another site | Landing-page response does not establish working cricket streams. |
| [Pitsport](https://pitsport.st/) | Landing page returned 200 | Motorsport candidate; player availability unverified. |
| [OvertakeFans](https://overtakefans.com/) | HTTP 403 | Not usable through the tested ordinary request. |
| [SportsBite](https://sportsbite.org/) | HTTP 403 | Not usable through the tested ordinary request. |
| [Watch Footy](https://watchfooty.st/) | DNS resolution failed | Not usable from the test environment. |
| [FawaNews](https://www.fawanews.sc/) | TLS hostname verification failed | Do not disable certificate verification or enable this URL. |
| [DaddyLive](https://dlive.sx/) | Landing page returned 200 | Already recognised by SportsWave; this is not a new independent source. |

## Calendars, Arabic and remaining gaps

FMHY also links a [calendar-resource directory](https://fmhy.net/misc#sports), including calendar subscription providers and score sites. Subscription-calendar links are schedules, not streaming servers or guaranteed real-time scores. Any iCalendar importer must preserve timezone, all-day/multi-day semantics, cancellations and event identity.

This pass does not verify new Tunisian-league streams, Arabic commentary, universal team logos or horse-racing live timing. Existing FilGoal/Tunisia and official Watania/beIN resources remain relevant. A site's interface language or an English-looking channel name is not commentary-language evidence.

The accompanying inventory records the FMHY links, including mirrors and reference/status pages; it is not an allowlist. Raw bounded HTTP captures, probe results and language samples are retained locally under ignored `audit/fmhy-*` files. Only reviewed Streamed embed/resource hosts are enabled inside the app; other directory sites open in the browser. No paid tasks or credentials were added.
