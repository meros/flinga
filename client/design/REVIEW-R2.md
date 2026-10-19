# Independent review, round 2 (2026-10-16, about 00:30, main 4fcfa77)

Two new reviewers checked all 119 shots and re-verified round 1. Verdict: **FAIL**.
Five round-1 findings marked "fixed" were not fixed. Rule from now on: **a finding is fixed only when an
automated assertion that failed before the fix passes after it** (unit test or a harness check on the named
shots). A status without its test name does not count.

## Round 3 branch

Status per finding below. The harness checks live in `Screens.checkReviewR2` (run after the draw of every
shot) and the steps `expecthint:`, `expectrow:`, `expectnorow:`; the unit tests in `ReviewR2Test`,
`StartOverTest`, `RowViewTest`, `SnapListTest`, `SnackTest` and `OsdShots`. Baseline measurements come from
the round-2 shots (`png.py` pixel runs): where a check could not be run on the old code, the number the
check now asserts was measured on the old shot. The stress replay stays at 0 geometry differences
(13 + 11 + 28 + 8 shots) and `ImagesTest` keeps its cap.

## High

| id | shots | finding | assert-first test | status |
|---|---|---|---|---|
| X1 | 51, 52, 56 | Logo fallback in the 80×45 dp row slot ellipsizes to "3 T…" (regression from M14) | No channel identity text is ever ellipsized in any slot (scan all RowView/OsdView fallback draws) | fixed — test `RowViewTest.channelFallbackNameIsNeverCut` (eight recorded names, both card states) and harness `CHECK X1` on every shot; root cause: M8's 56×32 sheet logo column was applied to every list with a channel row, so Kanaler drew its 80×45 slot at 56×32 (`sheetLogos`), and the fallback now fits by size, then two lines, then the number (`LogoText`). After-shots 51, 52, 53, 54, 55, 56 |
| X2 | 07, 62 (H5) | "4 min kvar" / "44 min kvar" over a ≈1 % lane; text and lane from different numbers | Text and lane derive from one (position, duration) pair; for every Fortsätt titta fixture item, lane fraction and "N min kvar" agree within 1 min | fixed — test `ReviewR2Test.continueRowsAgreeWithTheirLane` over all 12 fixture posters (`MainActivity.continueState`). Telia's numbers were honest (Rapport: 6 s in, "4 min återstår") but a title under the 1 min resume threshold plays from its start, so its row now says the length ("5 min · SVT") with no lane; a started title says "N min kvar" with the lane at position / (position + N min). The Serie episode rows use the same pair instead of Telia's rounded percent. After-shots 07, 60, 62 |
| X3 | 53 vs 55, 166, 54 (H/M13) | Start-over in Tablå but not in Alternativ or the TV3 banner hint | StartOverTest covers every tour fixture (TOUR, LOGOS, STATES) and all five call sites per channel | fixed — test `StartOverTest.everyTourChannelResolvesItsProgrammeOnAirFromTheGuide` (channels 1–5) and harness `CHECK R4` on every shot (banner hint, control pill, Tablå row, Alternativ row against `hasStartOver(onAirNow)`), plus `expecthint:Börja om` (54) and `expectrow:Börja om från` (53, 55). Root cause: the banner, the pill, Alternativ and ← read `onAir`, filled only by the "current" query, which has a replay fixture for SVT1 alone (TV3's 404 left them blank), while Tablå read the guide. `onAirNow` now falls back to the channel's cached guide, and the prefetch fetches the guide when "current" fails. After-shots 53, 54, 54-osd-fallback-controls, 55, 166 |
| X4 | 64, 66, 68 (H4) | The journey shows the card offering the episode that was playing | Journey plays ep N with `expecttitle`, card offers N+1, titles distinct after ellipsizing | fixed (journey) — the app offered N+1 all along (R5 guard); the shots showed A11 resuming (65, 66) and A11 offered (68) because the step that played A10 took no shot. The journey now shots A10 focused and playing (`expectfocus`, `expecttitle`) and asserts the card with `expectrow:Avsnitt 11` and `expectnorow:A Plane Flew`. After-shots 67-episode-ten-focused, 67-episode-ten-playing, 68 |

## Medium

| id | shots | finding | assert-first test | status |
|---|---|---|---|---|
| Y1 | 04, 05, 33, 35, 171–176 (M7) | No date anywhere in Tablå; no "more above" cue | Header shows the day (`TABLÅ · TORSDAG 15 OKT`, sticky); top fade drawn whenever firstVisiblePosition > 0 or the first child is above the list top (pixel check on the fade band) | fixed — test `SnapListTest.stickyEyebrowNamesTheFirstVisibleRowsGroup` and harness `CHECK Y1`/`Y2`. The pixel check on the fade band passes on the old code too (the fade was drawn; nothing readable stood in it, so it read as nothing), so the cue is now the band itself: while rows are scrolled out above, the top 40 dp of a grouped list is the eyebrow band, whole rows start under it, and it names the first visible row's group (`TORSDAG 15 OKT`), drawn where a header row stands. Placed in the list's own band rather than the panel eyebrow so that Sök's right-hand column gets the same cue and nothing moves (E6). After-shots 04, 05, 33, 35, 171–176 |
| Y2 | 14, 16, 17, 36, 71, 80, 87, 90, 194 (M1) | Group rows orphaned at the top without their eyebrow; no top cue | Sticky group eyebrow for the group of the first visible row; same fade test | fixed — same test and check as Y1 (`FÖRSLAG`, `SERIER` pinned in the band). After-shots 14, 16, 17, 36, 71, 80, 87, 90-search-film-club, 194 |
| Y3 | 09, 36, 38, 80, 92 | Three text left edges in one Sök list, set by content | All rows in one list share one text column x (harness check) | fixed — test `RowViewTest.rowsShareTheListsTextColumn` and harness `CHECK Y3` (every text row of a list at one x). Old shot 14 measured titles at x = 835 (completions) and 1051 (results). Every row now starts after the list's widest slot (`RowView.indent`). After-shots 09, 14, 36, 38, 80, 92 |
| Y4 | 17, 190, 21 (M3) | Hints per key: ✕ "OK Skriv" in 17; Röst "OK Skriv"; no two-letter note | Table test: every key → expected hint; Röst = "OK Tala" or dim with the no-recognizer text | fixed — test `ReviewR2Test.everyKeyHasItsOwnHint` (`MainActivity.keyHint`) and steps `expecthint:Rensa` (17), `expecthint:Tala` (190). Root cause: moving between keys never updated the footer (`KeyboardView.Listener.onFocusMoved`). The two-letter note is a note row below the completions for a one-letter query (21). After-shots 17, 21, 190, 192, 193 |
| Y5 | 08, 193 | Instruction sentence in the key-hint slot | Footer contains only key–action pairs (regex on the hint model) | fixed — harness `CHECK Y5` (every pair of the keyboard footer has a key, from `hintPairsForTest`). The notes explain themselves in the column (`Inga tidigare sökningar. Skriv med tangentbordet till vänster.`); the footer reads `OK Skriv ‹ Meny`. After-shots 08, 20, 21, 193 |
| Y6 | 12, 65 (M11) | Episode eyebrow names no mode or source | Asset eyebrow always starts with the mode word (SE I EFTERHAND / BÖRJA OM / AVSNITT / FILM) and includes the source | fixed — harness `CHECK Y6` on every bar-mode OSD and the same assertion in `OsdShots` (`OsdView.namesModeAndSource`). One builder `episodeEyebrow` for Serie, Fortsätt titta, Min lista and Nästa avsnitt (`AVSNITT · TV4 PLAY · BONDE SÖKER FRU`); time-shifted live reads `SVT1` (the channel alone). After-shots 12, 65, 67-episode-ten-playing, osd-timeline |
| Y7 | 40 vs 195, 70 (M15) | Two sheet eyebrow conventions | All held-OK sheets: eyebrow ALTERNATIV, context as title | fixed — harness `CHECK Y7` after every `hold:DPAD_CENTER` (the sheet's eyebrow starts with Alternativ). After-shots 40, 70, 195 |
| Y8 | 64, 67 | Season chip row hard-clipped mid-word ("Säsor") | Chips end on a chip boundary with a fade, or scroll so the focused chip is whole; no glyph cut at the row edge | fixed — harness `CHECK Y8` (a pill rect past the row's edge requires the `fadeR` strip; `fadeL` when scrolled). The focused chip was already whole; a cut chip now fades out over 40 dp like a list row. After-shots 64, 67-back-to-series, 81–85 |
| Y9 | 187 | One-line toast top-aligned in a two-line box | Text vertically centred in the toast box (±4 px) | not reproduced — test `SnackTest.oneLineTextIsCentredInTheTwoLineBox` passes on the unchanged `Snack` (one and two lines, box height constant). Old shot 187 measured: box rows 150–287 (centre 218.5), text ink rows 202–231 (centre 216.5): 2 px off. The test stays as the guard |
| Y10 | 179 vs osd-startover (now osd-timeline-from-start), osd-timeline | Chip shown in one state and not the other; duplicates the bar label | One chip rule; bar label hidden when the chip is up | fixed — test `ReviewR2Test.oneChipRule` (`MainActivity.chipShown`), the `OsdShots` assertion (chip and OSD never both up) and harness `CHECK Y10`. The rule chosen: the time behind live is in one place; while the banner is up its bar label by the playhead carries it, the chip takes over when the banner hides (`updateChip` on tick and on hide). After-shots osd-startover, osd-timeline, osd-chip-toast (chip and toast, banner hidden), 179 |
| Y11 | 05 | Past-row duration meta squeezes titles to about 15 characters | Title keeps priority; drop the duration on past rows | fixed — test `RowViewTest.pastGuideRowKeepsALongTitle` ("Stugfixarna Norge" whole without the meta, cut with it). After-shots 05, 33, 171, 174 |

## Low (fix where cheap, each with a reason when not)

Serie voids without description or with a one-line note (37, 89, 91) · hero fallback flat black (37, 91) ·
Bildformat verb differs (Byt / Välj) · caret drawn while the list has focus · "1. Avsnitt 1" · dim chip without
an OK hint (85) · Alternativ titles at two x positions (26 vs 165) · osd-timeline eyebrow without a mode ·
extra air above dim episodes (15, 88) · login line pitch (00) · shots whose names claim states they do not show
(86, 90, 189) · ACCENT icons at rest on every row · "Laddar …" twice (32) · DESIGN.md says 360 dp sheet, shots
show 400; DESIGN/F13 say the Meny Tablå row has a logo, shots show "Tablå · SVT1".

Status: hero fallback is `SURFACE`, not flat black (91) · Bildformat reads `OK Byt` in Alternativ too (186) ·
the caret is drawn only while the keyboard has focus (14, 16) · an episode titled only "Avsnitt N" is not
numbered twice (`Api.episodes`) · every season chip says `OK Avsnitt`; a dim season's note and a failed season's retry row explain themselves (85, 86) · a live Alternativ
sheet always keeps the logo column, so its titles share one x with and without Föregående kanal (26, 165) ·
osd-timeline reads `SVT1` (the channel alone) · shots renamed to what they show (86-series-season-older,
90-search-film-club, 189-login-after-logout) · trailing icons are `TEXT_2` at rest, `ACCENT` on the focused card
(every list) · the loading row's footer is only `‹ Back` (32) · DESIGN.md: sheet 400 dp, the Meny Tablå row
in text. Not changed, with the reason: Serie voids and the extra air above dim episodes are the reserved
description and note heights (E6, a fixed slot per kind; a one-line note would move the rows below it);
the login line pitch is the implementer's screen.
