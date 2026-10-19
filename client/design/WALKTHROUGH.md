# Flinga: virtual UX walkthrough (2026-10-15)

Every feature in `REQUIREMENTS.md`, walked as a viewer with the Chromecast remote (↑ ↓ ← → OK, held OK,
Back, held Back), entirely in the Robolectric harness. The journeys are scenarios in
`client/app/src/test/java/se/lite/play/Screens.java` (`WALK_*`, test `walkthrough`; the tour, `STATES`
and `LOGOS` cover the rest) and the OSD states that playback cannot reach without a player are in
`OsdShots`. Shots are in `client/shots/`. Every shot passes the harness checks: focus inside its
ancestors (E5), whole rows (C1), one focus look (C2), honest hints (C3), text on the row's centre line
(C5), a section stack of at most two below the rail and the rail itself (C6, U10), no silent duplicate frames (R1), the surface each step expects
(R2), the focused row clear of the fades (R3), and the geometry replay with stressed fixtures (E6,
`Screens.stable`). A frame that repeats an earlier one on purpose is a `repeat:` step.

## Journeys

| # | Journey | Keys | Shots | Found and fixed |
|---|---|---|---|---|
| J1 | First start, login | (none, Back exits) | 00-login, 89-logged-out | Column width, host-only URL, grouped code (implementer); the FLINGA eyebrow kept through the merges |
| J2 | Zap and the passive overlay | ↓, ↓↓ burst, ↑×3, Back, OK ←, Kanaler row above, OK | 160–167, 02-live-controls | A zap shows the passive overlay (logo, title, the bar with its numbers, no pills, 160); OK opens it on its first pill (Paus on SVT1) with the rail's peek at the left edge (02-live-controls), ← on the first pill opens the rail (02-channels); a burst previews each channel without retuning (161 → 162); Back hides the overlay instead of leaving the app (164); Föregående kanal is the first row of Kanaler and OK on it lands on the right channel (165, 166) |
| J3 | Kanaler → rail → Tablå grid → catch-up → Back, step by step (U10, B4, F12c, A2, A3) | OK ←, ↓↓, →, ←←, OK, OK, →→, ←, Back | 170–178, 175-catchup-controls, 176-kanaler-from-catchup, 177-rail-from-kanaler, 178-rail-back-closes | The catch-up is its channel's frame (logo, the programme's times, no live tick, passive: 175-catchup-banner); OK focuses the pills (Paus, Från början, Direkt), → moves, Back hides the overlay and the next Back leaves (175-catchup-controls-right, -hidden); Back never moves playback; ← on the first pill opens the rail and → enters Kanaler on the delayed channel (176), ← reaches the rail in one press (177), Back from the rail closes the panel on clean video (178); the Tablå NU row keeps its title; the upcoming row explains itself in the footer (73) |
| J4 | Rewind from Tablå ("Från början 20:00"), the overlay, Back and held Back on a rewound channel (F12c) | OK ← ↓ →, ↓, OK, OK, Back, held Back | 179-rewound-from-tabla, 179-rewound-controls, 180-held-back-stays-delayed, osd-timeline-from-start, osd-timeline | The rewound channel's frame is the delayed channel's (its logo or name, the picture's time under the playhead, the row Paus, Från början, Direkt, no mode word), the same on the start-over stream as on the live window; Back and held Back never jump to live: 180 is clean video, still behind live (`expectplayback:delayed`) |
| J5 | F14: the delayed channel moves on | (OSD states) | osd-f14-next-programme, osd-toast | The next programme's banner looks like a zap; no chip, no toast |
| J6 | Sök: keys, Mellanslag, ⌫, Rensa, Röst, results, held OK on a result | ↑, ↓, →, OK OK, ↑, →→, OK (⌫), →, OK (Rensa), → | 190–195, 08–21 | Röst is a key like the others (190); ⌫ and Rensa are reached by their own keys (the first round pressed Rensa twice); after Rensa the focus stays on the key and → does not enter the empty column (193 and its repeat); the keyboard hints name the key (`OK Radera`, `OK Rensa`) and the column (`→ Förslag`, `→ Senaste`); held OK on a result shows the row sheet (195) |
| J7 | Unavailable content | OK on a dim row, a series with nothing playable | 11, 15, 37, 73 | `Inte i Telia Play`, `Går inte att se i efterhand`, `Börjar 21:00` in the footer; `Tillbaka till sökningen` when no provider app is installed |
| J8 | Held OK on video (A5) | held OK on live; held OK in Min lista | 26-held-ok-is-ok, 55-held-ok-is-ok-logo, 66-held-ok-media, 61, 70 | Held OK on video is OK: the overlay on its first pill and the rail's peek, no sheet; a list row's held OK still opens its Alternativ sheet (61, 70) |
| J9 | Bildformat (A5) | Rail → Inställningar, OK; Back, held OK, Back, rail → Inställningar, OK | 184–187 | `expectformat:` checks the picture and the saved setting together; one label (`Hela bilden` / `Fyll skärmen`); held OK on video opens no sheet, so there is no second place for it |
| J10 | Inställningar, Logga ut | Inställningar, Logga ut, ↑ (Avbryt → Logga ut), OK | 184, 188, 189-login-after-logout | Confirmation with `Avbryt` focused and a subtitle (188 shows Logga ut focused after ↑); OK logs out and the login screen returns with a new code (189, `expect:login`) |
| J13 | Nästa avsnitt (REVIEW-R2 X4) | Min lista → Storage Wars, OK (A11 resumes), Back, ↓ ↓ ↑ (A10), OK, end | 64–69, 67-episode-ten-focused, 67-episode-ten-playing | The shots show which episode plays (A10, `expecttitle`) before the card offers the next one (`expectrow:Avsnitt 11`, `expectnorow` of A10's title, R5) |
| J11 | Errors, loading, empty | (fixtures) | 19, 20, 31, 32, osd-channel-failed, osd-channel-norewind, osd-text | A loading list is a note, not a focused card (32); error rows carry the retry in the footer (19, 31); a failed tune keeps the banner with the reason; raw error codes are gone |
| J12 | Stressed fixtures | every tour twice | `build/shots-stress/` | 0 geometry differences, rail included (`CHECK geometry`) (`CHECK geometry` in the test output) |
| J15 | The rail (U10): sections, ← from lists, details and the keyboard, Back one step out | OK OK, ←, →→, ←, ↓ ×4, ↑, →, ←, Back ×3 | 100–109, 06-rail-from-guide, 07-rail-kanaler, 14-rail-sok-kept, 22-rail-from-keyboard, 60-min-lista-rail, 196-rail-from-keyboard | The rail is on every panel shot and marks the section; ↓ shows the next section at once with the focus on the rail (102–105); the end stops without wrap (105); ← from a Serie's first pill reaches the rail and → returns to the Serie as it was (106, 107); Back goes sheet, detail, list, rail, closed (108, 109, 104-rail-third-time). Found and fixed: a panel opened from the controls' pill took the rail's focus (the hidden controls hand focus to the rail first), so Kanaler from catch-up had no focused row; the NU row lost its `–21:00` to the rail's first layout and got it back with the owner's width decision B; "Inställningar" touched the rail card's edges at 14 sp (13 sp now) |
| J14 | F17: an accidental ↑ ↓ never loses your place | ← ×25 on SVT1, Back, ↑, ↓, ↓, ↑; Min lista → Storage Wars A10, ↑, ↓, OK, ↑; ← ×25, Back, ↑, 31 min, ↓ | 200–207 (`Screens.places`) | Rewound 12:30 into Fråga Lund, ↑ zaps at once and the next banner carries `[svt1] väntar på −12:30 · ↓ tillbaka` on its own line (201); ↓ resumes the exact content moment (`expectmark`: 0 ms off) in the ordinary delayed frame, no mode (202); a second zap away keeps the place again, the arrow now ↑ (203); on an episode ↑ ↓ show its banner, and with the overlay focused they move between its rows (204, 205); a zap needs it hidden first (Back); after 31 min the channel plays live with `Fråga Lund har slutat · Direkt` on the status line and the line is gone from the next banner (206, 207). Found and fixed: the harness could not rewind at all (`seek` needed a player), so the live-buffer path now runs without one; the expiry walk moves the app's clock (`clock:` step) because a 31 min `wait:` would run 1,860 ticks |
| J16 | U11, A2-A4, F18: the one overlay, the row, the peek, Pause on live | → ← OK ↓ ↑ Back; OK ←; ← OK ↓ →→ OK; OK, OK (Paus); catch-up OK, ↑, OK; ↑ on a channel without start-over, OK OK, media key | 210–223 (`Screens.overlay`), osd-channel, osd-channel-actions, osd-timeline, osd-episode, osd-channel-waiting | → on live opens the overlay on the bar (the playhead ringed, no peek), ↓ moves to the pills with Paus focused and the peek showing, ↑ goes back and never zaps, Back hides it (210–212); ← on the first pill opens the rail (213); ← rewinds and Direkt joins the row, OK on Direkt is live again (214–216); OK, OK on SVT1 pauses on the start-over stream at the live position within one 3.84 s segment (`expectclose`), the row reads Spela and stays up past 5 s (219), Spela continues delayed (220); a catch-up with Direkt (217, 218); a channel with only the live buffer has no pill at all: OK on the empty row opens the rail, and the media key says `Går inte att pausa` (221–223) |

## Annoyances found in the walkthrough

1. Start-over from the panel crashed in render-only mode (`player` null in `liveNowMs`). Fixed; the TV was
   never affected, the harness was.
2. `Föregående kanal` cut in the Alternativ sheet once the logo plate took its place. Fixed by widening the
   sheet to 360 dp (65-alternativ-with-previous); the video stays 62 % visible. Round 1's logo column
   (M8) took it to 400 dp (58 % visible), so `Bildformat: Hela bilden` stays on one line.
3. A fully hidden first row (bottom at the list's top edge) counted as cut by C1 after four ↓ presses in Sök.
   `SnapList` and the check now look at the first row that shows at all (94-first-result).
4. Held OK on a search result opens it like OK. Not a defect today (no row action exists yet); F15 adds
   `Lägg till i Min lista` there, and the footer must say `Håll OK Mer` only then (C3).
5. Review round 1 (REVIEW-R1.md): J9 and J10 had never run, because Meny restores its last focused row
   and the journey counted ↓ presses from Kanaler. R1 (no identical frames) and R2 (the surface each step
   expects) now fail such a journey; every journey walks by row label where the row count can change.
6. In F16's Serie journey, ↑ from an episode row does not return to the season chips (it walks the rows
   above), so shots 85 and 86 were one frame (R1). The journey walks ↑ through the rows; whether ↑ from
   the first episode should jump to the chips is a design question left to the F16 owner.
7. The framework's top fading edge never drew once the list was scrolled by whole rows (strength 1.0,
   nothing on screen), while the bottom edge drew. `SnapList` now draws both fades itself (DST_OUT in a
   40 dp strip), so "more above" and "more below" look the same.

## Only the TV can verify

The full list with tick boxes is `TV-CHECKLIST.md`; this is the summary.

- The banner's hold until the first picture and the 4 s countdown from it; the failed-tune banner on a channel
  outside the subscription; `Programmet gick inte att starta.` when a ticket fails.
- The timeline during a real start-over, F14 moving to the next programme (no chip),
  and the catch-up bar (`12:30 / 45:00`) appearing at the first tick.
- F17: that zapping back lands on the same picture it left (the start-over seek to programme start + saved
  position, within a segment), and that the content position read off a real live window and a real
  start-over window agree.
- The bottom fade and the top fade at the end of a list on the TV's panel (Robolectric draws them, the device
  composites them).
- Voice (Röst) and the system search hand-off.
