# Independent review, round 1 (2026-10-15, 22:40, main 1e25923)

Two reviewers who did not build the app checked all 105 tour shots and the journeys in WALKTHROUGH.md.
Verdict: **FAIL**, 5 high and 22 medium after merging overlaps. Gate: zero high and zero medium.

## Round 2 branch

Every High and Medium is fixed or re-checked (status column below); the five rules and C6 are in
`Screens.java` (`shot` R1, `expect`/`expectfocus`/`expecttitle` R2, `checkFocusOutsideFade` R3,
`StartOverTest` R4, `end` R5, panel depth C6) and the suite is green with F16 merged. Deliberate
repeats are `repeat:` steps (06, 13, 27, 67, 162, 167, 176, 178, 193).

## New harness rules (they make each defect class fail the tour)

- **R1 No silent duplicates.** A `shot` whose PNG is byte-identical to an earlier shot in the same run fails,
  unless the step says `same:<name>` on purpose. (The walkthrough claimed J9/J10 passed on copied frames.)
- **R2 Assert the surface.** Each journey step names the surface it expects (`expect:Inställningar`); the tour
  fails when the panel title or the OSD mode differs.
- **R3 Focus is never faded.** The focused row, and its group eyebrow when it starts the group, lie wholly
  outside the list's fade zones.
- **R4 One predicate per capability.** `hasStartOver(programme)` drives the banner hint, the control pill, the
  Tablå row and the Alternativ row; a unit test checks all four agree for every fixture programme.
- **R5 Next episode differs.** The Nästa avsnitt card never offers the episode that just ended.

## High

| id | shots | finding | fix | status (round 2 branch) |
|---|---|---|---|---|
| H1 | 184–189 | J9 Bildformat and J10 Logga ut never ran: their shots are copies of other screens (Min lista, Storage Wars, TV4 live). Meny restored the last focused row, so "OK" opened the wrong item; Logga ut does not reach the login screen | Fix the journeys and the logout path; R1 + R2 | fixed: the journeys walk by row label and assert each surface (R2); 184–189 are Inställningar, Bildformat, Alternativ, the toast, Logga ut and the login screen |
| H2 | 188 | Kanaler pushed on top of a Serie surface, four levels deep (`‹ Storage Wars`) | Kanaler from the controls opens alone (stack reset); max depth 3 | fixed: a surface opened from the video (Kanaler, Tablå, Avsnitt from the controls) starts a fresh stack; C6 fails any stack over three |
| H3 | 10, 36 | The list fade is drawn over the focused card | R3 | fixed: SnapList moves the focused row and its eyebrow out of both fade zones (R3 on every shot) |
| H4 | 64–69 | Nästa avsnitt offers the episode that just ended (suggestedEpisode used) | Next = the episode after the finished one; R5 | not reproduced: the card offered the episode after the one played (68: ep 10 played, ep 11 offered; `expecttitle` + R5 assert it); R5 guard added in the end step |
| H5 | 07 | Fortsätt titta lanes contradict their text ("4 min kvar" and "44 min kvar" both ≈4 %) | Lane from position/duration, never Telia's rounded percent | fixed: the lane is position / (position + minutes left), Telia's percent only when minutes are unknown |

## Medium

| id | shots | finding | fix | status (round 2 branch) |
|---|---|---|---|---|
| M1 | 09, 10, 14, 18, 19, 194 | Suggestion rows use the 72 dp height; four take 576 px, results fall under the fade; FÖRSLAG eyebrow scrolls away | 56 dp suggestion rows (per-section height) or max 2; keep eyebrows with their rows | fixed: suggestion and recent rows are 56 dp (`Item.compact`); result rows keep their own height; the FÖRSLAG eyebrow stays with its rows (R3) |
| M2 | 19, 191 | Search failure drawn as a plain row, no "OK Försök igen", not reachable | Failure row per UX §6, focusable, retry | fixed: `Sökningen fungerade inte · Kontrollera anslutningen`, focusable, `→ Försök igen` from the keyboard |
| M3 | 08, 17, 18, 20, 21, 39, 190, 191, 192 | Keyboard hints are a template: "OK Skriv" on ✕/⌫/Röst/Mellanslag, "→ Förslag" with nothing selectable or with results; no 2-letter hint | Per-key action text; "→ Lista" only when a selectable row exists | fixed: `OK Skriv / Tala / Mellanslag / Radera / Rensa`, nothing for an erase key with no text; `→ Förslag / Senaste / Resultat / Försök igen` only with a selectable row; the two-letter note in the footer |
| M4 | 193 | After Rensa, focus lands on the note "Inga tidigare sökningar" | Focus stays on the pressed key; → never enters a list without selectable rows | fixed: → on the last key never leaves the keyboard for a column with nothing to select (193, the repeat after →) |
| M5 | 07, 22 | Meny rows inherit the tall height: Min lista/Inställningar below the fold without a cue; restored Meny scrolled with no label above | 56 dp plain rows so the root fits; Meny opens at its top, restore focus not scroll | fixed: plain rows 56 dp; as many Fortsätt titta rows as keep the root on one screen (one and Visa alla at 1080p); focus restored, not scrolled |
| M6 | 11, 13 | Serie list opens at episode 2 with the season header and ep 1 scrolled off; dim row's secondary brighter than its title | Snap to the season header; dim both lines | superseded by F16; re-checked on the new Serie: opens on the chips with the episode in progress reachable in one ↓, eyebrow kept with its rows (R3 on 81–89); dim rows draw both lines in TEXT_3 |
| M7 | 04, 05, 33, 35, 171–176 | Tablå has no day eyebrows; no "more above" cue; NU row lost its end time | Day eyebrow rows; top fade when rows exist above; keep "–21:00" | fixed: the NU row keeps `–21:00` left of the pill; the list draws its own top and bottom fades (the framework's top edge never drew after a whole-row scroll); day eyebrows stay with their first row (R3) |
| M8 | 165 | Alternativ titles ragged (logo lead only on one row); icons differ from Tablå | One lead column on every sheet row; same icons per action | fixed: a sheet with a logo on any row keeps a 56 × 32 dp logo column on every row; Föregående kanal and Börja om carry the play icon as in Tablå |
| M9 | 71 | Toast covers the first Sök result | Anchor toasts away from lists; size to text | fixed: a toast while a panel is open is the panel's footer line for its duration; the box stays for video only |
| M10 | 175 vs osd-f14 | Two layouts for the same SE I EFTERHAND banner | One asset-banner frame in every entry path | fixed: one `showAssetBanner` for Tablå, Sök, Fortsätt titta, Nästa avsnitt and F14; osd-f14 uses the same frame |
| M11 | 65, 12 | Resume cue missing ("Fortsätter från 10:05"); episode banner names no mode/source and has no bar | Eyebrow "AVSNITT · TV4 PLAY · …", bar from the first frame, resume line | fixed: `AVSNITT · TV4 PLAY · STORAGE WARS` eyebrow, the bar from the first frame, `Fortsätter från 10:05` in the footer (65) |
| M12 | 61 | Held OK on a started Fortsätt titta row lacks "Spela från början" | Add it when position > 0 | fixed: Spela från början on every started row (the media is resolved from the title when needed) |
| M13 | 01, 160–166, 02; 04 vs 55 | Börja om offered inconsistently for the same programme | R4 | fixed: one predicate (R4, `hasStartOver`); the banner's hint and pill follow the fact when it lands; StartOverTest checks the fixtures agree |
| M14 | 53, osd-channel-norewind | Logo fallback text is caption size where the title role is | Fallback at the slot's role size and baseline | fixed: the fallback name at the title size in rows, headers (the slot grows to its text) and the OSD |
| M15 | 66, 40 | Alternativ for VOD lists Tablå; sheet eyebrow convention differs | Leave out rows that do not apply; one sheet header convention | fixed: Tablå only when a channel plays, Föregående kanal only on live; eyebrow `Alternativ` (`Alternativ · Storage Wars` for an episode), title = what plays |
| M16 | osd-text | Fatal screen hints use their own style and position | Standard footer slot and hint style | fixed: the fatal screen draws its hints in the standard footer slot |
| M17 | 179 vs 175 | Programme time range in two different slots | One slot for the range in every asset mode | fixed: the range sits right of the title in every asset mode (`OsdView.range`); the eyebrow carries mode, source and day |

## Low (fix where cheap)

Status: B22 fixed (hint labels TEXT_2, keys TEXT); B20 fixed by R3; A14/B10 and A16 covered by M14 and
M10; A19 covered by F16's shot 86 (the retry row); B18 the box stays for video only (M9 moves panel
toasts to the footer). Not changed, with the reason: A15 bar labels sit in a fixed right-aligned slot
(E6); A17 login line pitch is the implementer's screen; A18 the Börja om card's empty lead keeps it
under the titles on purpose; B15 "3" is TV3's own mark; B16 the pill row is stable while a title plays
(E6) and Börja om at 0:00 is harmless; B17 `OK Varför?` stays, it opens the explanation; B21 the banner
on return to live is the Kanalbanner of a tune, as on any zap.

A14/B10 fallback legibility · A15 ragged right edge of bar labels · A16 banner variants differ for one state ·
A17 login line pitch · A18 empty lead on the Börja om card · A19 missing retry-row shot · B15 lone-glyph logo
as a title ("Tablå 3") · B16 Börja om pill at 0:00 · B17 "OK Varför?" wording · B18 toast box size and
contradictory frame · B20 stray progress line under the top fade · B21 banner on return to live · B22 footer
hints 28 px TEXT_3 at 3 m.
