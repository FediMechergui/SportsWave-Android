# Public calendars and scores - 1.0.0 (2026-10-18)

- English: ESPN public scoreboard responses, queried one date at a time. Date ranges return HTTP 400 on several sports; they are not used. Requests have bounded sizes/timeouts, three workers and a five-minute bounded memory cache. Failed date windows retain saved source data rather than replacing it with an incomplete window. Public endpoints are undocumented and may change.
- Arabic: [FilGoal fixture pages](https://www.filgoal.com/matches?date=2026-10-18). The returned calendar date must match the request. Only main fixture cards are parsed; header sliders are excluded. Cairo local times use Africa/Cairo daylight-saving rules. On the checked date, Bournemouth/Sunderland at 16:00 on FilGoal matched ESPN's 13:00Z. This timezone interpretation is explicit, not the phone's timezone. The app displays device-local times. Cards without a published kickoff remain unavailable.
- Parser checks against fetched primary-source data returned 100 football records, 12 NFL games, one MLB game, 324 nested ATP matches, five F1 sessions, 11 UFC bouts and 75 FilGoal fixtures. These are parser results for sampled responses, not counts of current live events. The adapter's three-day live football window returned 753 ESPN records and FilGoal's selected day returned 75. Availability changes.
- ESPN maps actual home/away metadata and nested draw/session/bout IDs. Scheduled zero scores stay unknown. Finished/live scores are published snapshots, not certified real-time delivery. No TV listing, publisher match page or score record is converted into a player URL.
- Arabic/English player choices are prioritized. Published players without commentary metadata remain available with a Language unverified label. Streamed embed hosts and narrowly numbered load balancers have explicit resource rules; directory websites open in the browser.
- All 20 tabs have bundled vectors. Google Material Design icons are Apache-2.0; license/notice files are in docs/licenses and the APK assets. Darts, billiards and racing-flag vectors are local geometric artwork.
- Working English endpoints checked: https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=20261018&limit=1000&lang=en and equivalent reviewed sport/league routes in EspnCatalogue.kt. Empty NBA/WNBA/college-football/rugby responses are valid schedules without events for that date, not proof of year-round coverage.

The older OpenLigaDB adapter below remains in source for compatibility/tests but is excluded from the active picker/default aggregation to honour the English/Arabic resource selection. Older coverage details are historical.

# Public sports data and source coverage

Working-tree redesign, checked 14 October 2026. These changes are not in the published beta.2 APK.

The enhanced preview additionally merges conservative fixture identities, retains their player choices, supports persistent favourites, stores public schedules for up to 24 hours, and displays retrieval age and stale scores. Unavailable source results retain their original fetch timestamps. Unknown match status remains unknown. SVG crests are rendered locally with bounded raster decoding and no external SVG resources. Actual page/frame load errors are remembered for 24 hours; document loading is not classified as successful video. [Apify checks](APIFY.md) did not yield a usable feed.

Catalogue and player-link discovery WebViews bypass their HTTP cache. Offline use comes from SportsWave's saved metadata snapshots, so a cached web document cannot masquerade as a freshly retrieved schedule. Automatic failed-load retries are limited to once per five minutes while the activity is active. Match details expose the retrieval age and source; favourite filters preserve their separate match count.

SportsWave uses compact competition-grouped rows, date navigation and search. The default schedule combines available VIPRow, Livekora, KorahLive and OpenLigaDB records. A failure in one supplemental feed does not discard records from other feeds. Sources reports availability; no single public feed covers all sports worldwide.

## Competition order

Relevance uses an editorial order: world championships/World Cup/Olympics, continental national championships, major continental club competitions, major domestic competitions, then other events. Search relevance takes precedence. This is a configurable-code default, not a universal official sporting ranking. Kickoff and League sorts remain available. Women's and youth competitions are not stripped from event identities.

## Integrated sources

| Source | Data used | Coverage and limits |
| --- | --- | --- |
| VIPRow | Published event metadata and event-page links | 19 configured sport categories; event availability varies. Schedule-based “Now” is not a verified live match status. |
| Livekora | Published football match cards and their linked channel pages | May publish no fixtures on a given day. Shared channel links do not merge different matches. |
| KorahLive | Published static structured SportsEvent metadata | Does not execute its script-generated access-key flow. Empty schedules remain empty. |
| [OpenLigaDB](https://www.openligadb.de/) | Community current-matchday schedules, reported final results, team names and published crest URLs | Football: Champions League, Bundesliga 1–3, Premier League, La Liga and women's Bundesliga. Ice hockey: CHL, DEL and DEL2. Darts: Swiss Darts Trophy. Reviewed shortcuts are season-dependent; coverage is not guaranteed. |

OpenLigaDB API: [official examples](https://github.com/OpenLigaDB/OpenLigaDB-Samples), [public league directory](https://api.openligadb.de/getavailableleagues). Data is community maintained under [ODbL](https://opendatacommons.org/licenses/odbl/1-0/); SportsWave attributes it in-app. Source names are preserved; a small reviewed football alias map and competition-season normalization support matching. Ambiguous candidates, reversed teams and women's/youth categories remain separate. Artwork can have separate rights. Published raster and SVG artwork from reviewed Imgur/Wikimedia hosts is attempted; unavailable images retain initials. This does not provide logos for every sport.

Final scores use the provider's full-time result, only when the match is marked finished. Published goal records can provide a provisional reported score; these community snapshots may be delayed and do not establish a verified live clock or status. Unfinished default 0:0 results are not displayed as live scores. No verified minute-by-minute multi-sport feed is configured. Refresh is every five minutes while the activity is active, with manual refresh available.

## Player selection

Tapping a match opens a source chooser for its actual published event links. The most recently selected VIPRow/Arabic source family is preferred next time. Another catalogue's link is offered only when both team names, sport, competition and kickoff match conservatively. Missing aliases can leave duplicates; attaching the wrong broadcast is worse. Score-only events show details and explicitly report no linked player. A catalogue source is not a commentary language; resolver language groups use published language metadata only.

Existing reviewed player families: TodayStreams, DaddyLive, Dembed, YasirTV, AlbaPlayer and SNRT. Recognition does not guarantee every stream works.

## Investigated, not newly integrated

| Source | Reason |
| --- | --- |
| KooraTV | Direct requests returned HTTP 403; no reliable reviewed server integration. |
| YacineTV / Robotiva | Site/API requests blocked or returned a player shell requiring an unsupported session flow. The interface language is not evidence of commentary language. |
| YassirTV / YasirTV | Different domains, different availability. Existing YasirTV recognition uses actual published links; no invented replacement links. |
| Sofascore / Flashscore | Interface references, not undocumented production data APIs. |
| [football-data.org](https://www.football-data.org/pricing) | Account token required; free scores are delayed, football only. |
| [API-SPORTS](https://api-sports.io/) | Account/API key required; broad sport coverage subject to plan and quotas. |
| [TheSportsDB](https://api.thesportsdb.com/documentation) | Public demo coverage is restricted; live score access is not a free universal feed. Optional configured artwork lookup requires exact team/sport matching. |

Broader dependable live coverage needs an authorized data service or hosted feed. Secrets should remain on a backend, not in a publicly distributed APK. This list records investigated candidates, not every sports website on the Internet.

A completed refresh replaces only the responding source's fixtures. Pending and failed sources retain their records and original retrieval times; successful empty responses clear only that source. Confirmed empty snapshots are persisted, so removed fixtures do not reappear after restarting. Disposed metadata loaders cannot update the current catalogue.
## Official TV guide and 1.0.0 coverage

The native **TV & watch** screen requests the official beIN MENA `tv-channel?region=en-mena` and `tv-event` endpoints at `www.beinsports.com/api/opta/`. Requests cover the selected device-local day using UTC boundaries, request up to 2,000 programmes, and reject truncated responses. Only reviewed MENA channel names are retained; French-labelled and unknown channels are excluded. Channel listings are Arabic/English services, not proof of a particular commentary track. In-app choices prioritize explicit Arabic/English commentary; unknown commentary is labelled.

Programme airtimes never replace fixture kickoffs or scores. Published replay/live flags remain distinct. A match filter requires both complete team identities with reviewed aliases; it can miss spelling variants. Official channel URLs are accepted only on `beinconnect.app` or `connect.bein.com` and open with the official app/browser. Accounts, rights and subscriptions remain with the service. The app does not extract protected playback sessions.

Additional official service links include Watania/Tunisia, F1 TV, NBA League Pass, Tennis TV, Racing TV, Dubai Racing, RugbyPass TV and Volleyball World TV where relevant to the selected sport. These are labelled service links, not verified event servers. YacineTV's inspected public feed returned an encoded payload; no new YacineTV or Robotiva resolver is enabled. The official Watania page also publishes an HLS manifest, but channel availability alone does not establish a match's commentary language or carriage; no inferred match server was added.

ESPN motorsport uses season queries because day queries can omit meetings, then filters by the requested local window. Published entrants/classifications are retained in bounded snapshots and displayed separately from team scores. Horse-racing live timing and complete worldwide/team artwork coverage remain unavailable. Published logos can be reused only within the same sport and an exact reviewed team identity; missing crests use initials.

Sources inspected on 18 October 2026: [beIN guide](https://www.beinsports.com/en-mena/tv-guide), [FilGoal calendars](https://www.filgoal.com/matches), [Watania live](https://www.tunisiatv.tn/ar/live), [YacineTV](https://yacinetv.website/). The ESPN public endpoints remain undocumented and can change independently of the app.

## 1.0.1 public adapters

Streamed uses its documented public match and stream APIs, preserving returned source IDs and embed URLs. Its records do not publish league names or scores. Channel posts are offered separately. Yalla Shoot Koora reads published UTC fixture cards and crests for today, yesterday and tomorrow; placeholder scores and time-generated live flags are ignored. Raceweek reads published SportsEvent meeting intervals, not individual session timing or live classifications. Exact paired-team/time matching may attach a unique Streamed player option to a score fixture without overwriting scores. See [audit](FMHy_SPORTS_AUDIT.md).
