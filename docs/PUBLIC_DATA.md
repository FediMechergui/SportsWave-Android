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
