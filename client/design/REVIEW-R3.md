# Independent review, round 3 (2026-10-16, main 6570845)

Both reviewers: 0 high. Every round-1 and round-2 High and Medium confirmed fixed except one part of Y1.
Remaining mediums (A = reviewer A, B = reviewer B), each with its assert-first test and status. The
method stays: the assertion failed on the old code (the unit tests on the old formulas, the harness checks
on the old shots' measured values), and every after-shot named below was opened.

## Medium

| id | shots | finding | assert-first test | status |
|---|---|---|---|---|
| M-a (A1, Y1 part 2) | 172, 174, 53 vs 05; 36, 71 | A list scrolled by whole rows shows no "more above" cue: the sticky day band is pixel-identical to the unscrolled header | Band pixels differ between scrolled and unscrolled states, and a scrolled list shows the cue | fixed — `SnapListTest.stickyEyebrowNamesTheFirstVisibleRowsGroup` (the row above the first whole row is cut through its text under the band and its ink is in the band's fade zone) and harness `CHECK M-a` on every scrolled grouped list (`SnapList.cutRow` exists; the band's lower 16 dp hold ink that differs from the panel ground). The cue is the mirror of the bottom end: the band's upper 24 dp are erased whole and carry the eyebrow, its lower 16 dp fade the first visible row, which is snapped so that its text centre sits 6 dp above the band's edge and slides out under the eyebrow. A header is never the cut row (the row before it is). After-shots 04, 05, 33, 35, 171–176, 14, 16, 36, 71, 194 |
| M-b (A2, A6) | 38, 87 | The band names the group of a scrolled-out row while the first visible item is a group header ("FÖRSLAG" band with nothing under it, then "SERIER 8"); the band lacks the count | Band text == group of the first visible row; no band when the first visible item is a header (or that header becomes the band); the count in the band | fixed — `SnapListTest` (a header is never cut: when it would be, the row before it is, and the band names that row's group) and harness `CHECK M-b` (band text == `groupLabelForTest` of the cut row). The band draws the group's count right-aligned as the header row does (`Groups.metaOf`, "SERIER  8"). After-shots 38, 87, 14, 16, 194 |
| M-c (A3, B1) | 192, 18, 19, 21, 39 vs 09, 10, 14, 36, 80 | The Sök text column jumps 216 px when the first thumbnail result lands (833 → 1049) | Over a scenario that types until results land, the text x of all Sök rows is constant across all shots | fixed — harness `CHECK M-c`: in every shot with the keyboard on screen, every text row of the column starts at `SP_L + RESULT_W + SP_M` (the thumbnail column), with or without a result on screen; the TOUR types "bonde" and shots 08 (empty), 18 (completions only) and 09 (results). `indent(Screen, …)` fixes Sök's lead column at the thumbnail slot in every state. After-shots 08, 09, 10, 14, 18, 19, 20, 21, 36, 39, 191–194 |
| M-d (A4) | 07 vs 34 | Rapport is "5 min · SVT" in Fortsätt titta but plays as "0:00 / 25:00": one title, two lengths | Over all Fortsätt titta fixture items: row length == banner bar total | fixed for the row and its own playback — `ReviewR2Test.continueRowLengthIsTheBannerBarTotal`: for every poster with a recorded media (4), the minutes on the row equal the bar's starting total, which is now the same (position, position + Telia's minutes left) pair (`playbackDurationMs`), not Telia's nominal duration (Lyxfällan: 60 min nominal, 45 min real: the row said 45, the bar would have said 60:00). Shot 34 is a different Rapport episode: the Tablå slot 19:30–19:55 (`m-9147…`, 25 min), while Fortsätt titta holds `m-21741…` (5 min). Catch-up from Tablå keeps the slot as its span (the guide carries no asset duration) until the player reports the real one; the two shots now read "4 min · SVT" (07, the length the bar starts with: 0:00 / 4:06) and the slot's range (34). After-shots 07, 60, 62, 65, 34 |

## Low

| id | finding | status |
|---|---|---|
| A5 | osd-asset shows no bar although a span exists | the shot now renders the one state with no span (a film without a duration: `FILM · SVT PLAY`, no range, no bar); with a span the bar is there (osd-seek, 12, 34, 175) |
| B3 | F14 catch-up eyebrow lacks the day that Tablå's catch-up has | one builder `catchupEyebrow(ch, programme)` for Tablå and F14: `SE I EFTERHAND · SVT1 · TORSDAG 15 OKTOBER` (34, 175, osd-f14-next-programme unchanged in frame) |
| B4 | Tablå error row without the secondary `OK Försök igen` (UX.md 6) | the row has it (31) |
| B5 | Alternativ says `Tablå`, UX.md 7.3 says `Tablå för SVT1` | `Tablå för <kanal>` (26, 55, 165, 186) |
| B7 | the asset bar label is left-aligned in its slot while the range above is right-aligned | right-aligned to the range's edge, slot unchanged (12, 34, 65, osd-seek, osd-startover; `OsdLayoutTest` rects identical) |
| A11 | the login after Logga ut shows the same code as the first login | the replay serves a new code on the second device-code request of the test run (`Fixtures.logins`), so the first login (00) and the login after Logga ut (189) never show the same code |
