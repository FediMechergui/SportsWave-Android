# Public source investigation — 2026-10-13

These are ordinary HTTPS/HTML observations, not playback certification. No access controls were bypassed. Search-engine snapshots differed from fresh responses; they must not supply today's fixtures.

## Sources checked

| Source | Fresh request result | Role and useful evidence |
| --- | --- | --- |
| robotiva.online/kora.html (user's match 332 URL) | 200 HTML | Match/player shell, not a schedule. Its separately requested metadata endpoint returned 403. |
| yacinetv.website | 403 | User-reported entry to Robotiva; landing-page relationship not independently verified. |
| q.korahlive.online | 200 HTML | Blogger shell with yesterday/today/tomorrow navigation, channel posts, and externally loaded schedule script. Static channel posts are not fixtures. |
| www.livekora.vip | 200 HTML | Eleven match cards in the inspected response; names, league, channel, timezone-bearing kickoff, logo URLs, and actual outbound channel anchors. |
| pl.koralive1.cc/bein1 | 200 HTML | Published channel wrapper containing iframe to pl.matchlivehd.com/albaplayer/1bein1/. |
| pl.matchlivehd.com/albaplayer/1bein1/ | 200 HTML | AlbaPlayer page with Clappr and HLS/DASH playback libraries. Video playback was not tested. |
| yassirtv.com, tv-sir.co, koooracity.news, kooratv.club | 403 | No new usable streams verified by these requests. |
| yasirtv.com | DNS lookup failed | The one-s host differs from yassirtv.com; previous published embed rules do not prove current availability. |

## How the pages work

Robotiva uses the `m` parameter as the match ID. Its JavaScript requests `https://cdn.kora-api.org/api/v1/matche/332` with a cache timestamp, expects match/channel metadata, builds server buttons, and selects a player. The implementation supports embedded frames and an HLS player. Some frame paths use rotating edges and visitor/time parameters. The metadata decoder supports plain JSON and an encrypted payload format; neither the protected response nor its decoder was reproduced in the app. `lang` changes interface and team/league names; `d` participates in return-home/branding behavior. Neither proves commentary language.

KorahLive's initial document has an empty Today container. The schedule is supplied by the published external script at `kora.ahmed3311154.workers.dev/matches`. A JSON-LD-only parser therefore cannot read the actual schedule observed on this template. Its channel resolver also has key-generating fallbacks, so executing that resolver indiscriminately is inappropriate for the existing implementation constraints.

Livekora renders real match cards directly into HTML. `.match-container` is inside an anchor with `target=_blank`; the enclosing anchor is the published channel URL. Team names are under `.right-team .team-name` and `.left-team .team-name`. `span.date[data-start]` contains an ISO timestamp with an offset. `.match-info li` contains commentator, channel, and league labels in that order in the inspected template. Images contain published crest URLs from cdn.sportfeeds.io. Reuse permissions and availability of those images have not been established.

The inspected Livekora response displayed `0:0` on every card and empty status elements. Those values must not be treated as verified live scores. Multiple fixtures share channel URLs, so event identity must include participants and kickoff rather than the playback URL alone. The sample channel wrapper embeds AlbaPlayer; advertising scripts are separate dependencies and should not be added to an unrestricted resource allowlist.

## Integration implications

1. Parse the freshly retrieved Livekora cards as catalogue metadata, retaining original names and kickoff offsets. Keep the schedule separate from channel availability.
2. Discover only the actual enclosing anchor and published player iframe. Review frame/script/media dependencies before enabling playback; document retrieval is insufficient verification.
3. Preserve both source-language and canonical names. Use explicit team IDs or verified aliases, with league/sport context, to avoid merging similarly named clubs.
4. Group streams by published commentary-language metadata. Leave unknown language as unknown; page language and Arabic team names are insufficient evidence.
5. Obtain live scores from a maintained data feed, independent of video providers. API-SPORTS documents multiple sports and account-based APIs (https://api-sports.io/). TheSportsDB documents schedules, artwork, and plan-dependent live-score endpoints (https://www.thesportsdb.com/documentation). Neither supplies general broadcast rights or guarantees all VIPRow categories.

The subsequent app implementation adds Livekora static match parsing, its channel-wrapper resolver, and recognition of the published AlbaPlayer document. The subsequent runtime investigation adds reviewed SNRT/Easybroadcast dependencies and a scoped compatibility fix for the optional Cast plugin. Arryadia/SNRT playback is now verified on debug and signed release builds; the sampled beIN media request still stalled. No streams or access keys are synthesized. See TEST_REPORT.md for dated results.
