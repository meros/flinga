# TV feedback round 1 (2026-10-16, build c61f9b7): analysis and decisions

Source: the owner's notes on 17 of the 31 TV screenshots. Each row gives the quote, the decision and the
requirement it changes. Decisions marked **(proposal)** go beyond the words of the owner. The owner can veto them
on the TV. Principle U0 (few modes, seamless) decides every conflict.

## A. Player overlay and banner

| id | quote (shots) | decision |
|---|---|---|
| A1 | "Jag gillar inte den flytande 07:06", "Gillar inte 07:11, 06-10. 168 min kvar ... löst hängande och svåra att tolka", "gillar inte dom siffrorna" (02, 48, 49, 50, 55, 62) | Remove the wall clock, the programme range at the right, the "N min kvar" and the "−1:22:15" offset text. **The bar holds the numbers**: the programme start time at the left end, the end time at the right end, and one label under the playhead with the wall-clock time of the picture on screen (for example 06:48). The live edge carries the word DIREKT. One font size, aligned to the bar, nothing floats free. For VOD the same layout reads elapsed and total (0:42 · 1:30:00). |
| A2 | "Ta bort den där fula bannern. ... visa en passiv OSD en stund när man byter kanal? Så man ser var man är i programmet, vilken kanal etc." (61) | Delete the separate asset banner (`showAssetBanner`, "SE I EFTERHAND" eyebrow). Every start (zap, Från början, Tablå start, Sök, Fortsätt titta) shows the **passive OSD** for 4 s: logo, title, the bar with the A1 labels, no pills, no focus. OK or ←/→ turns it into the interactive overlay. One component, two states (passive, interactive). |
| A3 | "vi kanske behöver en direktknapp också, likt från början?" (56) | Add the pill **Direkt** when the picture is behind live (symmetrical to Från början). Order: Paus/Spela · Från början HH:MM · Direkt (only when behind live or in catch-up) . |
| A4 | "skippa kanaler/tablå/sök/alternativ och istället ha vänster som meny, och visa en hint ... låta menyn kika fram" (51) | Remove the pills Kanaler, Tablå, Sök, Alternativ. The overlay shows a **peek of the side rail** at the left edge (rail icons only, dimmed, 56 dp, with the hint "‹ Meny"). ← on the first pill, or ← from the bar's left end when it is not scrubbing **(proposal: ← on the first pill)**, opens the rail with focus on Kanaler. Scrubbing stays: ←/→ from clean video opens the overlay on the bar (U11 unchanged). |
| A5 | "Tablå/från början etc känns lite duplicerat? Alternativ kan nog vara lite mer dolt som inställningar i menyn istället" (60) | Remove the held-OK Alternativ sheet from the player. **Bildformat** moves to Inställningar. Held OK on a list row stays (A9). Held OK on video does nothing new **(proposal: it opens the overlay like OK)**. Föregående kanal is reached through the rail (the last channel is the first row in Kanaler) **(proposal)**. |

## B. Panels

| id | quote | decision | status (step 2, build of the commit "Panels round 1") |
|---|---|---|---|
| B1 | "Onödigt med meny-titlarna 'Kanaler' etc. Tar bara onödig plats" (40); Inställningar: "ta bort titel" (28) | No panel title for rail destinations (Kanaler, Tablå, Sök, Min lista, Inställningar): the rail highlight already names it. Eyebrows stay only where they carry data (day names, the group names). | built; check `F1-B1` (failed before) |
| B2 | "Varför högerpil om ingen tablå?" (33) | A row shows the › chevron only when it opens something. Correction by the owner: "Ingen tablå just nu" meant no programme on air, so the text is "Inget program just nu" and the chevron shows when the channel's guide holds any programme (past, now or future); no chevron only for an empty or failed guide, which plays on OK. Rule: **no affordance without an action**. | built; check `F1-B2` (failed before) |
| B3 | "Skippa NU och -08:00 etc.. timelinen håller infon istället! Vi kan bredda denna meny lite också" (42) | In a channel's guide list: no NU badge, no end time on the row. The progress lane on the current row says "now". Titles get the freed width. Widen the panel (width decision B, 40 % panel, becomes **48 %**, rail included about 58 %) **(proposal: 48 %)**. | built at 48 %; check `F1-B3`, `PanelWidthTest` (failed before) |
| B4 | "Jag tycker denna tablå bör vara en grafisk lanes-tablå med alla kanaler ... start live eller i efterhand med ett OK-tryck. Egen helsida" (45) | The rail's **Tablå** opens a **full-screen guide grid**: channels as rows (logo at the left), time as columns (a now-line, 3 h visible, ←/→ moves in time, ↑/↓ across channels), programme blocks sized by length. OK on the current block plays live; OK on a past block plays from its start (catch-up); a future block shows its info and does nothing else. The rail is not shown on this page; ← from the first column returns to video or the rail. Kanaler (the list) stays for fast zapping. This is a new surface, specified in UX.md §Guide grid. | built (step 3): `Screens.grid`, `GuideGridViewTest`, check `B4` |
| B5 | "Onödigt lite plats för episoderna. Relayout?" (17) | Series view: hero shrinks to a 22 % band (title, one-line meta, pills); the season chips scroll without a cut chip; the episode list gets the rest, at least 5 rows at 1080p. | built; `expectband:22` (failed before), `expectepisodes:5`, `PillsViewTest` |
| B6 | "Fokus på stjärnor först! Fortsätt titta sekundärt, ... senast tittat först" (24) | Min lista: the starred titles come first and take the initial focus. Fortsätt titta is the second group. Both sorted by **last watched first** (Telia's last-viewed timestamp). | built; `LibraryTest.starredTitlesCome...`, `expectorder`, `expectfocus` (failed before); no last-viewed timestamp in Telia's rows: the API order stands for it |
| B7 | "Bra, håll-ok som UX-mönster där vi behöver djupare kontextuell meny" (26) | Keep. Written into UX.md as the one pattern for deeper context. | already in UX.md 3.2; checks `Y7`, `WALK_SETTINGS` |
| B8 | "Skippa namnet flinga i appen" (30) | The name Flinga appears nowhere inside the app: "Logga ut?" The launcher label and icon keep the name. | step 1, done |
| B9 | "Helt ok" (63), "Det här är bra" (56) | No change. | no change |

## Order of work

1. UX.md, DESIGN.md and REQUIREMENTS.md updated first (this file lists the rows to touch).
2. Step 1: A1–A5 and B8 (overlay, passive OSD, rail peek).
3. Step 2: B1–B3, B5–B7 (panels).
4. Step 3: B4 (guide grid).
Each step: assert-first tests, harness shots, all tests green, merge, install on Vardagsrummet.
