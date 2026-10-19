# Visual QA of the tour screenshots (2026-10-15, main at 7a26f57)

Three reviewers checked all 45 shots in `client/shots/` against DESIGN.md and UX.md. They found duplicates across
batches, which this file merges. Shot numbers are the file prefixes.

## New automatic checks (so these classes cannot return)

Built on the polish branch (`Screens.shot` runs C1–C3 and C5 on every shot and fails the run; C4 is
`RowViewTest`, C1 also has `SnapListTest`):

- **C1 Whole rows only.** On every `shot`, no list child may be partly visible at the top or the bottom of its
  list. Either snap scrolling to whole rows, or fade the top and bottom 40 px. A partly visible row fails the run.
  This covers clipped accents ("FORSLAG" in 19) and half rows (02, 11, 14, 16, 30, 51).
- **C2 One focus look.** Exactly one view per shot may draw the focus style (FOCUS fill or white ring): a
  row, a keyboard key or a pill of the player controls. The search field never uses it.
- **C3 Honest hints.** The footer hints come from the state: no "→ Förslag" when the right list is empty, no
  "OK …" for an action the focused row does not have.
- **C4 Title priority.** In a row, the title slot is reduced last: pills and meta text give up space first.
  Assert in a unit test that "Fråga Lund" with the NU pill and "–21:00" is not truncated at the row width.
- **C5 Text on the centre line.** The text block of every row (title, plus the subtitle when there is one) is
  centred in the row within 4 px, measured after the draw (`RowView.textBlockCentre`). A lone title in a
  two-line list therefore reads centred in its card, and the progress lane lives in the bottom margin.
- **C6 Two deep below the rail.** A section's stack never holds more than two surfaces (sheets on top do
  not count): Kanaler → Tablå, or Sök → Serie. A sheet from the video starts a fresh stack.
- **U10 The rail.** On every shot with a panel and no sheet on top, the rail is on screen and marks the
  section; from the focus one ← reaches the rail (a pill row leaves from its first pill, a keyboard key from
  its left column; the Sök list's left neighbour is the keyboard, so the rail is two presses there). A sheet
  on top hides the rail and the check skips it (`sectionForTest` is null). Steps `expectsection:` and
  `expectrail:` assert the section shown and the rail's focus. C3 also fails a footer or OSD hint that
  spells the Back key as `‹` (it is `↩`, `Theme.BACK`). The rail is added to the panel width (owner decision
  B): `RowViewTest` lays rows at the 328 dp column the rail leaves alone, and no logo slot, lead column or
  NU meta changed for it.
- **R1–R5** (REVIEW-R1.md): no identical frames unless the step is `repeat:`; `expect:` steps assert the
  surface; the focused row and its eyebrow are clear of the fades; one `hasStartOver` predicate with
  `StartOverTest`; the Nästa avsnitt card never offers the episode that ended.
- **REVIEW-R2 checks** (`Screens.checkReviewR2`, after the draw of every shot): R4 every start-over call
  site agrees with the predicate; X1 no channel name cut in a logo slot; Y1/Y2 the top fade erases the
  list's edge and the band names the first visible row's group; Y3 one text column per list; Y5 the
  keyboard footer holds key–action pairs only; Y6 an asset eyebrow names mode and source; Y7 a held OK
  opens an Alternativ sheet; Y8 a cut chip row is faded; Y10 the chip and the OSD never both show the
  time behind live. Steps `expecthint:`, `expectrow:`, `expectnorow:` assert the footer and the rows.

## High

| # | Shots | Issue | Fix | Status (polish branch) |
|---|---|---|---|---|
| H1 | 02, 03, 06, 11, 13, 14, 16, 17, 19, 30, 51, 52 | Partly visible rows at the top or bottom of lists. In 19 the "FÖRSLAG" heading loses its Ö dots, and in 30 half a row shows under the header. | C1 | done: `SnapList` snaps whole rows at the top after every layout and key; both ends fade 40 dp; at the very end of a list the top fade carries the cut (02, 11, 30-channels-last) |
| H2 | 33, 35 (also 04) | "Fråga L…": the NU pill and the end time take the title's space on the NU row | NU moves to the time column or the progress line; drop "–21:00" on the NU row; C4 | done: no end time on the NU row, the pill moves into the meta slot, title priority in `RowView` (33-guide-loaded, 35-back-to-guide) |
| H3 | 54 | The OSD logo-fallback shot shows no OSD at all | Capture right after the zap or pin the OSD in the test. If the OSD crashed or hid on a missing logo, that is a real bug: fix it | implementer |
| H4 | 19 | When the error row appears, the suggestion rows re-space (112 → 144 px) | Give the error row its own fixed slot (E6) |
 done: the error row has no second line; "OK Försök igen" is the hint (19-search-error) |
## Medium

| # | Shots | Issue | Fix | Status (polish branch) |
|---|---|---|---|---|
| M1 | 08, 17–21, 39 | Two focus looks: the search field's white ring plus the focused key | C2: field = hairline + accent caret | done: hairline only, raised fill while typing, accent caret (08-search-empty, 18-search-suggestions) |
| M2 | 08, 39 | Placeholder cut: "…filmer och ka…" | "Sök program och kanaler" or another string that fits | done: `Program, serier, filmer, kanaler` (08-search-empty) |
| M3 | 08, 20, 21 | Hints do not match the state ("→ Förslag" with nothing to the right; missing when suggestions exist). "Skriv minst två bokstäver" sits in the key-hint row | C3. Put the two-letter notice in the right panel | done: "→ Förslag" only when the list has a row; the two-letter notice is a note row in the list column (20-search-nohits, 21-search-one-letter) |
| M4 | 04, 05, 32, 33 | The list's top inset differs between loading and loaded and between states, so content jumps and the first row touches the header | One fixed top inset for placeholder and list | done: the loading row is a note (no card), so loading and loaded share the list top (32-guide-loading, 33-guide-loaded) |
| M5 | 09, 10 | One-line suggestion rows use the two-line height, with the text at the top of the focus card | Single-line row height, text centred | done: a row without a subtitle centres its title in the shared row height (09-search-results, 10-search-list-first) |
| M6 | 11, 13 | The focused episode title is cut ("4. Nya steg och oväntade be…") | A focused row may wrap its title to 2 lines (fixed 2-line slot) or marquee | not done, by design: a two-line focused title changes the row height (a layout shift, E6) and a marquee is per-frame work (DESIGN.md); the ellipsis stays |
| M7 | 11, 13 | The filter row text is cut ("Visa bara de 2 du k…") | "Bara de du kan se (2)" | done: `Bara de du kan se`, ✓ in the meta slot (11-series-partial when the season needs it) |
| M8 | 26, 55 | The Alternativ title "SVT1 · Fråga…" loses the programme | Channel as eyebrow, programme as title | done: channel as eyebrow, programme as title (26-alternativ, 55-alternativ-logo) |
| M9 | 30 | Channel names cut ("TV4 Fil…", "SVT B…") while the "−21:00" slot keeps its width | Name takes priority (C4); time moves to the programme line | done: Kanaler rows drop the end time (the lane shows how far the programme has come; Tablå has the times); the name has priority (30-channels-last) |
| M10 | 04 | Three purple markers: the bar on the NU card and the bar on "Börja om" make two rows look selected | One bar for the NU group | done: one bar on the NU row; the Börja om row has none (33-guide-loaded) |
| M11 | 12, 34 | The VOD and catch-up OSD offers "← → Spola" but shows no position bar. Its hints are UPPERCASE bold, unlike every other footer | Position bar with elapsed and total (as the live timeline); sentence case | done for the hints (sentence case, key + action like every footer). The bar needs the player's position: on the TV it appears at the first tick; the render-only harness never has one, so the shot shows the asset banner (12-asset-osd, 34-catchup-osd) |
| M12 | 33, 35 | "Lokala nyhe…" is cut although it fits before "5 min" | The title slot runs up to the duration slot |
 done: the title runs up to the meta text (33-guide-loaded, `RowViewTest`) |
## Low

Status on the polish branch: footer glyphs: `↩` = Back (U10), `›` = the child level, `← →` = the D-pad on the keyboard, the rail and when
seeking (UX.md 6). OSD
alignment: the clock is centred on line 1 next to the logo slot; the badge and the logo differ in height by
design (number pill vs 16:9 plate). Logo plate: centred on the row's text block, 3 dp inner padding for all,
the live bar inset by the card radius. Fallback text: bold 16 sp TEXT_2 (52-channels-fallback). Single-line
cards: centred (07-menu, 37-series-none, 40-recent-sheet). Loading row: a note, not a card (32-guide-loading).
Error cards: no "OK Försök igen" line; the hint carries it (15-series-none, 31-guide-error). Avbryt: subtitle
`Stanna inloggad` (24-logout-confirm); the sheet stays on the right (sheets always do). Wording: `Hela
bilden` / `Fyll skärmen` in Inställningar and Alternativ alike (23-settings, 26-alternativ). Guide contrast:
TEXT_3 raised to `#85848F` (about 4.6:1 on the panel). "–21:00": gone from Kanaler; the guide shows
durations and start times. Series header: kept as UX.md 7.8 specifies (eyebrow series, title the focused
season). Scroll cue: the bottom fade (09-search-results, 02-channels).

- **Footer glyphs:** both ‹ › and ← → appear. Use one set (08, 10, 14, 16–20 vs 30, 33).
- **OSD alignment (01, 50):** the channel badge (66 px) and the logo box (108 px) differ in height. The clock sits
  about 12 px below the name baseline. The right edges of "41 min kvar" and the clock differ (1805 vs 1823 px).
- **Logo plate (02, 51):** the plate is top-aligned in rows; centre it. The inner padding differs per logo;
  use 4 dp for all. The purple bar covers the focused card's rounded corner; inset it.
- **Fallback text (52, 53):** the grey channel name in the plate is small and low in contrast. Use bold, about
  16 sp, TEXT_2.
- **Single-line focus cards (07, 37, 40, 55):** the text sits 8–9 px above centre. Centre it.
- **Loading row (32):** "Laddar …" is drawn as a focused card. Draw it unfocused.
- **Error cards (11, 15, 31):** "OK Försök igen" repeats on the card. Show it only when the card has focus;
  otherwise give a reason.
- **Avbryt (24):** the card has an empty second line. Give it a subtitle ("Stanna inloggad") or make it one line.
  The confirm opens on the right while its parent is on the left; keep the side.
- **Wording:** "Hela bilden (svarta kanter)" in 23 against "Bildformat: Hela bilden" in 26. Use one label.
- **Guide contrast (04):** future rows are at about 3:1. Raise them to about 4.5:1.
- **"–21:00" next to "30 min" (02, 04, 05)** is cryptic. Use "till 21:00" or "40 min kvar".
- **Series header (11):** "Säsong 21" over a list of three seasons. Make the series the title.
- **Scroll cue (09):** "SERIER 8" with one row visible. Show part of the next row (after C1: a fade).

## Harness artifacts (not app bugs)

- "Kunde inte hämta listan" in 11, 13, 15 and 31 came from missing fixtures for some seasons. Synthesised on the
  polish branch (`episodes__59f9…` Bonde söker fru säsong 19, `episodes__655a…` Hundra procent bonde säsong 15,
  and `current__1.json` so Börja om appears on SVT1). 31-guide-error (Sjuan) and 19-search-error stay deliberate.
- The gradient video area is the intended stand-in. The fixed clock (20:18) comes from the recording.
