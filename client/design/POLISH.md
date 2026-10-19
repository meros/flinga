# Flinga: polish list (2026-10-15)

One pass over the whole product as the user meets it: live TV with the channel banner and the
timeline, start-over, catch-up, episodes and films, the menu, guide, search, series, login, errors,
and the parked Appar. Ranked by what the viewer notices first from the sofa. Each item names the
surface, the problem and the screenshot that shows it, the change, and who does it: **done** (this
branch), **implementer** (already in the UX implementer's branch, verify after the merge), or
**after merge** (open, for phase 2).

Screenshots: `client/shots/*.png` are the tour on `main` before this branch, `client/shots/osd-*.png`
are the OSD states rendered by `OsdShots` on this branch. The "before" OSD renders are in the
report, not in the repo.

## Changes to the design documents

The owner's requirements win over both design files. What changed and why:

- **UX.md 1 and 4: the status chip is the time behind live, nothing else.** UX.md had three chip
  texts (`−12:30`, `Börja om`, `Play`). F12 says the chip is a fixed-width `−mm:ss` while behind
  live, in time-shift and start-over alike. Catch-up and on-demand have no live edge, so they have
  no chip: the asset banner (eyebrow `SE I EFTERHAND · TV3`) says where you are, and it comes back on
  any key. A persistent overlay through a 45 min episode would be burn-in and distraction for no
  information. `Play` was not Swedish either.
- **UX.md 4: F14, the programme boundary in delayed viewing (owner decision, 2026-10-15).** The
  code returned to live at the end of a start-over programme; UX.md said the next programme
  continues. The owner decided: the next programme on the same channel continues in the same
  delayed mode. Written up as F14 with the banner and chip behaviour; the playback agent implements
  it (what it needs from the OSD is listed under F14).
- **UX.md 3.3 and 6, DESIGN.md: one name for start-over: `Börja om`.** The code said
  `Från början · −12:30` on the timeline, `Pågår, OK = från början` in the guide and
  `<title> från början` as the asset title. The guide row, the OSD eyebrow (`BÖRJA OM · SVT1`), the
  live hint (`← Börja om`) and the status (`Går inte att börja om`) now share the one word.
- **UX.md 6: new strings** for what the code says today without a string: `Går inte att spola
  tillbaka`, `Går inte att börja om`, `Programmet gick inte att starta.`,
  `Kanalen gick inte att starta (licens).`, `Det gick inte att hämta programmet. Försök igen.`.
  The tune-failed hint is `↑ ↓ Byt kanal   OK Kanaler`, not `OK Försök igen`: OK means Kanaler on
  video, one key one meaning, and OK on the same channel in Kanaler is the retry.
- **UX.md 3.3: banner timing.** `4 s after the last key` stays, with the addition the code needed:
  the banner waits for the first picture (at most 15 s) and counts the 4 s from there, so a slow
  tune never leaves a black screen with nothing on it. Timeline 3 s unchanged.
- **UX.md 5.1: the empty query placeholder** `Sök program, serier, filmer och kanaler` does not fit
  the field at TEXT_BODY (cut to `…och ka…` in the implementer's shot 08-search-empty). Now
  `Program, serier, filmer, kanaler`.
- **UX.md 7.1, 7.2 and DESIGN.md "OSD": the OSD anatomy** is now one frame for every mode (eyebrow
  line with the clock, title line, bar or meta line, hint line), and the chip and toast stack in
  the top-right corner instead of sharing it.
- **DESIGN.md: product name.** `Telia Lite` became `Flinga` on main (commit 2de598e); the design
  files still said Telia Lite. Updated where the name is user-visible (eyebrow `FLINGA`, strings
  in UX.md 6). The implementer's branch was cut before the rename and writes `TELIA LITE`; the
  merge must keep `FLINGA`.
- **DESIGN.md: login code expiry** no longer asks the user to restart the app (UX.md 6 and the
  implementer's branch fetch a new code).
- **DESIGN.md: toast type size** is TEXT_BODY (it was 18 sp, off the scale).

## Ranked list

| # | Surface | Problem (shot) | Change | Owner |
|---|---|---|---|---|
| 1 | OSD, every playback mode | The viewer never learns what mode they are in or which keys work: the time-shift and start-over bar has no label beyond `Från början`, catch-up starts with a bare title in plain text, and no OSD shows key hints (before: `osd-timeline`, `osd-startover`, `osd-text` in the report; `01-live-osd`) | One OSD anatomy: eyebrow (`BÖRJA OM · SVT1`, `SE I EFTERHAND · TV3`, `AVSNITT · TV4 PLAY`) with the clock, the title in display size, the bar or one line of facts, and a hint line that only names keys that work (`OK Kanaler  ↑ ↓ Byt kanal  ← Börja om`, `← → Spola  ‹ Direkt`). Asset banner and bar share one frame, so nothing jumps when the position arrives (`osd-channel`, `osd-startover`, `osd-asset`, `osd-seek`) | done |
| 2 | Errors on video | Raw strings reach the screen: `Uppspelningsfel: ERROR_CODE_…`, `<title>: <exception message>`, `Fel: <message>`, `Direktsändning – kan inte spolas tillbaka`; a failed tune shows a toast and then black video with nothing on it | Playback errors use the UX.md 6 strings. A failed live tune keeps the banner up with the reason on the bar's line and the keys that help (`osd-channel-failed`); a failed catch-up or VOD start toasts `Programmet gick inte att starta.` and returns to live. `Går inte att spola tillbaka` and `Går inte att börja om` are banner status lines, not toasts (`osd-channel-norewind`). The start-up and search errors are in the implementer's branch (`Flinga kunde inte starta.`, `Kunde inte hämta listan`) | done (playback); implementer (start, lists, search) |
| 3 | Waits | The banner hid after 3.5 s whether or not the picture had arrived, so a slow tune or a slow ticket ended in a black screen; cold start was black until the channel list loaded | The banner holds until the first frame (at most 15 s), then counts its 4 s. `Startar …` at cold start is in the implementer's branch | done; implementer (cold start) |
| 4 | Chip and toast | Both sat in the same top-right corner; a toast while behind live covered the chip | The toast sits below the chip slot, always (stable, E6) (`osd-chip-toast`) | done |
| 5 | Start-over naming | Four names for one feature: `Från början`, `från början` (asset title suffix), `Pågår, OK = från början` (guide), `Börja om` (UX.md) (`04-guide`, before `osd-startover`) | `Börja om` everywhere: guide row `Börja om från 20:00` (implementer), OSD eyebrow `BÖRJA OM · SVT1`, live hint `← Börja om`, status `Går inte att börja om` | done (OSD, playback); implementer (guide row) |
| 6 | Programme boundary in delayed viewing (F14) | Start-over ended by jumping to live, dropping the viewer's delay; UX.md said the opposite | Documented under F14 in UX.md 4 and REQUIREMENTS: the next programme continues in the same delayed mode, the banner shows it like a zap, the chip keeps counting. Playback implementation is the playback agent's; what it needs from the OSD is listed there | after merge (playback agent) |
| 7 | Merge of the implementer's branch | Its `OsdView` was rewritten from a base before F12: it removes the timeline mode, `LIVE_SLACK_MS`, `debugRects()` and the chip, and its `LoginView` writes `TELIA LITE`. Taking it would silently drop F12 | Keep this branch's `OsdView`, `Chip`, `Snack`, `Theme` and the playback code; this `OsdView` already offers the API the implementer's `MainActivity` calls (`showAsset(eyebrow, title, meta)`, `setLogo(Bitmap)`, `drawnRects()`, `showText("title\nbody")`). Keep `FLINGA`/`Flinga` in `LoginView` and the menu eyebrow. Replace its `Theme` additions by adding its new tokens (`ROW_H_HEADER`, `KEY_H`, `ROW_META_W`, `PANEL_WIDE_*`, `SHEET_W`) to this `Theme`; `LOGO_W`/`LOGO_H` are already here with the same values | after merge (main session) |
| 8 | Back on live video | Back with the banner showing exits the app at once (`dispatchKeyEvent` falls through to `super`); UX.md 3.2 says Back hides the banner first, and exit is the second press | Banner visible: Back hides it. Nothing shown: Back exits (Android TV rule) | after merge (implementer owns Back) |
| 9 | OK on live video | Opens Meny and Kanaler on the stack, so Back from Kanaler lands in Meny: two presses to the video (`02-channels`) | Open Kanaler alone; Back closes; ← reaches Meny (UX.md navigation rule, gap 2). Superseded by U9: OK opens the player controls in every mode and Kanaler is their first action, so the surface stays one Back from the video | done |
| 10 | Login | `Skanna QR-koden med mobilen,` wraps because the column is as wide as the URL; the URL shows `https://`; the code is six digits without grouping (`00-login`) | Fixed column width, host only (`login.teliaplay.se`), code grouped `766 162`. All three are in the implementer's branch; keep the `FLINGA` eyebrow when merging. Open: the code box is the only element with a hairline, so it reads as an input field; consider dropping the stroke and letting the SURFACE fill carry it | implementer; after merge (code box) |
| 11 | Guide | Every row repeats its date (`torsdag 15 okt.`), the NU row says `Pågår, OK = från början`, past rows without catch-up and upcoming rows look the same (`04-guide`, `05-guide-past`) | Day eyebrow rows, NU row = watch live with a separate `Börja om från 20:00` row, dim rows explain themselves in the hint line (`Går inte att se i efterhand`, `Börjar 21:00`) | implementer |
| 12 | Series and episodes | A series with no playable episode shows a season list and then ten dim `ej tillgänglig` rows with focus on a dead row; the title truncates (`09-series`, `10-episodes`) | Seasons inline, unplayable seasons collapsed, `Inte i Telia Play` explanation, eyebrow + title | implementer |
| 13 | Search: two focus indicators | In the implementer's shot `18-search-suggestions` the query field draws the white focus ring while the ✕ key is focused: two things look focused | The query field is not focusable and never uses the focus style: hairline on SURFACE (DESIGN.md), caret in ACCENT_ON_DARK | after merge (implementer) |
| 14 | Search: placeholder | `Sök program, serier, filmer och kanaler` is cut to `…och ka…` at TEXT_BODY in the field (implementer's `08-search-empty`) | `Program, serier, filmer, kanaler` (UX.md 5.1 updated) | after merge (implementer) |
| 15 | Timeline marks at 3 m | Playhead 20 px across and a 6 px live tick were hard to see from the sofa; the playhead at position 0 sat outside the safe area (before `osd-startover`) | Playhead 32 px, tick 4 dp wide and as tall as the playhead, track inset by the playhead radius | done |
| 16 | OSD scrim | The three-stop gradient reached 60 % black at its midpoint, a visible onset above the first line (`01-live-osd`) | Four stops over a 72 dp lead-in, so the darkening starts gently | done |
| 17 | Toast | 18 sp, not on the type scale; the dot is the only kind cue, fine | TEXT_BODY | done |
| 18 | Build | The launcher icon and banner PNGs were ignored by `*.png`, so a fresh clone or worktree failed at `processDebugResources` | Tracked (`!client/app/src/main/res/**/*.png`) | done |
| 19 | Menu root | Title `Flinga` with no screen name; mixed 56/72 dp rows (`Tablå` has a subtitle, the others not), so the rhythm stumbles (`06-menu`) | Eyebrow `FLINGA`, title `Meny`, single-line rows (`Tablå: SVT1`); `Logga ut` under `Inställningar` with a confirmation | implementer (keep FLINGA) |
| 20 | Channel list meta | `−21:00` reads as a negative number at a glance (`02-channels`). It is the end time; the progress lane already shows how far the programme has come | Keep the convention (TV4 Play, Allente use it) but set it in TEXT_3 so the title and the lane lead; or `till 21:00`. Decide after the logos land, the row is changing anyway | done: no meta on channel rows (the next line starts with the end time); the Tablå NU row keeps `–21:00` left of the pill (REVIEW-R1 M7) |
| 21 | Appar (F10, parked) | Nothing to polish yet. When built: the row's icon slot follows the logo slot rule (fixed 64×36 dp, name in the slot when the icon is missing), and the hint `OK Öppna  Håll OK Flytta först  ‹ Meny` only once long-press OK exists | parked |

## What was verified and what was not

- `gradle testDebugUnitTest` is green: `OsdLayoutTest` (E6 for every OSD mode, including the status
  line, the asset banner and the logo slot), `OsdShots` (renders the states above), the tour.
- Playback transitions ran only in the render-only harness. The hold-until-first-frame banner,
  the failed-tune banner and the F14 boundary need the TV: zap to a channel outside the
  subscription (expect the banner to stay with `Kanalen gick inte att starta.`), pull the network
  during a catch-up start (expect `Programmet gick inte att starta.` and live), and rewind on SVT1
  (expect `BÖRJA OM · SVT1` on the timeline and `−0:30` in the chip).

## Status after the merges (same evening)

Main took the implementer's navigation, keyboard, Serie surface, error strings and logos, and the
playback agent's F14; this branch merged them twice. Of the list above: 1–5, 15–18 done here; 6
implemented by the playback agent (`continueDelayed`, the OSD side is this branch's `showAsset`); 7
settled by the merges (this `OsdView` kept, `FLINGA` kept); 8–14 and 19 are in the implementer's
work and verified in the tour shots; 10's open point (the code box stroke) stays open; 20 resolved
by dropping the end time from Kanaler (the lane shows the progress, Tablå has the times); 21 parked.
The reviewers' list in `VISUAL-QA.md` carries the second round with its own status column.
