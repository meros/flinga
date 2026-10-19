# Independent review, round 4 (2026-10-16, main bbb3494)

1 high, 3 medium; everything else from rounds 1–3 confirmed fixed. Fixed together with F12b (the chip
rule) and F12c (rewinding is rewinding: no start-over mode, Back never jumps to live), assert-first as
before: the harness checks and tests named below, every after-shot opened.

## High

| id | shots | finding | fix | status |
|---|---|---|---|---|
| B1 | 19, 191 | The search-failure title is ellipsized to "Sökningen fungerade…": the negation is cut, so it reads as "the search worked" | A string that fits, and a check that no UX.md §6 string is ever ellipsized | fixed — the row reads `Sökningen gick inte · Kontrollera anslutningen` (UX.md 6); harness `CHECK B1` on every shot: a row whose title is a §6 string (`Screens.UX6`) must show it whole. After-shots 19, 191 |

## Medium

| id | shots | finding | fix | status |
|---|---|---|---|---|
| A1, B6 | 83→85, 82→86 | → from "Säsong 21" to "Säsong 20" scrolled the Serie list 210 px (info and description gone, pills jumped); moving from the Min lista pill to a chip scrolled the info block off | A horizontal move in the chip row must not change the vertical layout; scroll only on ↓ into episodes | fixed — the scroll came from ↑ out of the episodes (ListView's arrow scroll left the list where the episodes had put it), not from ←/→; now the actions and the chips always sit where the screen opened (`keepTopInView`), so a ←/→ never scrolls and ↑ back to them restores the top. Harness step `expectlisttop` after 82, 83, ↑×4, each → (85, 86): first position 0 at top 0, hence hero, info and pills at the same rects. After-shots 82, 83, 85, 86 |
| A2 | 67-back-to-series | The Storage Wars hero stopped at x≈1480 with pure black to the right and near-black bands top and bottom | Cover the hero area; never black | fixed — the source art has baked-in bars; `HeroView.content` trims black edge rows and columns (every sampled pixel under 24 on all channels, at most a quarter per side) and cover-scales the picture that is left. `RewindTest.heroTrimsBakedInBlackBars` (pillarbox and letterbox trimmed, clean art kept whole) and harness `CHECK A2`: no pure-black column or row band right of the panel in any Serie shot with art. After-shots 64, 67-back-to-series, 67-episode-ten-focused, 81–86 |
| B2 | 67-episode-ten-focused, 84 | The ungrouped Serie episode list scrolled by whole rows shows no "more above" cue | Apply the cut-row/fade cue to every SnapList with rows above | fixed — the band (solid above, the first visible row cut through its text and fading in below) applies to every list scrolled by whole rows, grouped or not (`SnapList.band` no longer needs a group; the caption only when there is one); the pinned chips start where the band's cut puts them (`firstWholeTop`). `CHECK M-a` now covers ungrouped lists. After-shots 84, 67-episode-ten-focused, 30 |

## Low

| id | finding | status |
|---|---|---|
| A3 | the cut row under the band was hard-clipped through glyph tops before the fade | the cut row's text centre sits 2 dp above the band's edge, so its text starts below the band's solid part and only fades (`cutDepth`) |
| B3 | osd-f14 eyebrow without the day | the shot uses `catchupEyebrow`'s form (`SE I EFTERHAND · SVT1 · TORSDAG 15 OKTOBER`), as the app does since round 3 |
| B5 | the list's first row was cut when only its header was above | when only the first header is scrolled out, the list goes back to its top (`onlyFirstHeaderAbove`), if the selection stays clear of the bottom fade |
| B7 | `SÄSONG 21 · AVSNITT 4` in the eyebrow | `S21 A4` (`shortEpisode`) in every episode eyebrow (12, 65, 67-episode-ten-playing) |
| A6 | `Fortsätter från 10:05` in the key-hint line | gone: the bar's own label says `10:05 / 30:00` (65) |

## F12c in the same pass

Owner, verbatim: "Jag vill inte ha distinktion börja om för bakåt (det är bara spola bakåt för användaren) och
inte bakåt för att gå tillbaka... Man spolar helt enkelt framåt till live igen!" No BÖRJA OM mode, eyebrow,
chip text or hint: a rewound channel shows the delayed channel's frame (`SVT1` (the channel alone)) on the live
window and the start-over stream alike; → to the live edge is the only way back; Back hides the banner or the
controls and never jumps to live (long Back neither); the shortcut is `Från början HH:MM`. Tests:
`RewindTest.aStartOverSessionLooksLikeAnyDelayedChannel`, `expecteyebrow` / `expectnohint:Direkt` /
`expectplayback:delayed` in WALK_GUIDE (179, 180-held-back-stays-delayed), the R4 check reads `Från början` and
`Spola tillbaka`. On-demand catch-up and VOD keep U2 (Back → live and the list it came from), which the
owner's rule does not speak about.
