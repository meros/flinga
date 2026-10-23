# Flinga: UI language

An independent, lightweight Android TV client. The design system is small on purpose: a few tokens,
a handful of components, all platform Views drawn in code. Implementation lives in
`app/src/main/java/se/lite/play/ui/` (`Theme`, `Ui`, `RowView`, `RowAdapter`, `OsdView`, `TabsView`,
`CatalogueView`, `PickerView`, `KeyboardView`, `HeroView`, `LoginView`, `Snack`). Behaviour (keys, navigation,
wording) is in `UX.md`; this file owns the look. Change a value here first, then in `Theme.java`.

> **Clean room (2026-10-18, as built 2026-10-19).** The rail, the panel and its sheets are gone. The
> surfaces are the video with one unified strip, the browser (the tab row over the guide grid or the
> catalogue lens, with the in-app keyboard) and the picker (one 400 dp sheet). The `RAIL_*`, `PEEK_*` and
> `PANEL_*` tokens (except `PANEL_DETAIL_*`, the Serie detail) are removed. New tokens: `TAB_H` 56 and
> `TAB_PAD` (the tab row), `FIELD_H` 52 (the search field), `KEY_W` 52 (six keyboard columns are the keyboard's
> 312 dp column), `POSTER_W`×`POSTER_H` 224×126 with `POSTER_CAPTION` 44 (browse cards), `SEARCH_POSTER_W`×
> `SEARCH_POSTER_H` 168×94 with `SEARCH_POSTER_CAPTION` 40 (search results), `PICKER_*` (the sheet). A card's
> geometry comes from its layout, never from its title (E6). Focus on a lens: the card or field ring, drawn
> only while the lens has the focus; the active tab is raised (`SURFACE_RAISED`) while the focus is below it
> and takes the focus fill when the tab row has it. A progress lane on a thumbnail row lies along the
> thumbnail's bottom edge, never across the text. Paragraphs below that name the rail, the panel or its
> Alternativ sheet describe the old look; treat the token values as current and the rail/panel layout as retired.

## Principles

1. Video is the hero: overlays are dark translucent scrims, the panel covers 48 % of the width plus the rail (57.6 % at 960 dp, B3).
   Sök is the one exception (80 %: keyboard column + list column), a row's Alternativ sheet is 400 dp on the right.
   Serie is the one screen where the title's own art takes over (F16): its hero fills the screen behind a
   50 % panel.
2. Speed is a feature: one View per list row, no blur, no shadows, no running animations. Images where they
   add meaning, within the image budget below: one hero per screen, small thumbnails, never a poster wall.
3. Focus is unmistakable: the focused row or key inverts to a light fill with dark text, readable at 3 m.
4. Purple marks state, not decoration: live, progress, the playing channel. Everything else is neutral.
5. Swedish, short and literal: "Kanaler", "Sök", "Tablå", "NU", "DÄREFTER", "min kvar".
6. Stable geometry: nothing moves because a string changed (see below).
7. Never a dead end: a row that cannot play says why ("Inte i Telia Play") and offers the way out.

Independence: purple `#990AE6` is the only borrowed element. No Telia logo, wordmark, imagery, icon set
or layout (no top tab bar, no poster carousels, no rotating hero banners). Channel logos are the channels' own
(from the API), shown as channel identity only; series and episode art is the title's own (from the API),
shown as that title's identity only. The product name is the launcher's alone (B8, owner: "Skippa namnet flinga i appen"): no string inside the app names it.
## Color tokens

| Token | Hex (ARGB) | Use |
|---|---|---|
| `BG` | `#0B0B10` | Opaque app background (login, before first frame) |
| `SCRIM_PANEL` | `#EB0E0E14` (92 %) | Menu panel over video |
| `SCRIM_OSD` | `#E6000000` (90 %) | Bottom stop of the OSD gradient (top stop transparent) |
| `SURFACE` | `#1C1C25` | Search field, login code box |
| `SURFACE_RAISED` | `#262630` | Toast, focused search field |
| `FOCUS` | `#F5F3F8` | Focused row fill, focused field outline |
| `ON_FOCUS` | `#14121A` | Title text on `FOCUS` (17:1) |
| `ON_FOCUS_2` | `#55505F` | Secondary text on `FOCUS` (7:1) |
| `TEXT` | `#F5F5F7` | Primary text on dark |
| `TEXT_2` | `#B0AFBA` | Secondary text, meta, times (8:1 on panel) |
| `TEXT_3` | `#85848F` | Dim: not playable, upcoming, hints (4.6:1 on the panel) |
| `ACCENT` | `#990AE6` | Fills only: NU pill, live and playing bars, progress on `FOCUS` |
| `ACCENT_ON_DARK` | `#C27CF5` | Accent text/icons/progress on dark (7:1) |
| `TRACK` | `#40FFFFFF` | Progress track on dark |
| `TRACK_ON_FOCUS` | `#2614121A` | Progress track on `FOCUS` |
| `HAIRLINE` | `#1FFFFFFF` | 1 dp resting outlines |
| `SUCCESS` | `#4CD787` | Toast dot: done |
| `ERROR` | `#FF6B6B` | Toast dot, login status error |

Rule: `ACCENT` never carries small text on dark (5.9:1 with white on top of it is fine, purple text
on near-black is not). Use `ACCENT_ON_DARK` for that.

## Type scale

System Roboto via `Typeface`, no font files. Sizes in sp; at density 2.0 a 16 sp line is 32 px on a 1080p panel.

| Token | sp | Face | Use |
|---|---|---|---|
| `TEXT_DISPLAY` | 32 | Medium | OSD channel name, login heading |
| `TEXT_TITLE` | 24 | Medium | Panel title, OSD now-title, OSD channel number |
| `TEXT_BODY` | 20 | Medium (rows) / Regular | Row title, search input |
| `TEXT_LABEL` | 16 | Regular, tabular digits | Row secondary line, meta, times, OSD next |
| `TEXT_CAPTION` | 14 | Bold, uppercase | Pills (NU), eyebrows (DÄREFTER, FLINGA, SE I EFTERHAND · TV3), key hints (key bold TEXT_2, action regular TEXT_3) |
| `TEXT_CODE` | 40 | Monospace bold, +0.12 em | Login code |

Nothing below 14 sp. Times use tabular figures (`tnum`) so columns do not jitter.

## Spacing, radii, sizes

- Spacing scale (dp): `4 · 8 · 12 · 16 · 24 · 32`. Safe area: `48` horizontal, `27` vertical.
- Radii (dp): `4` pills, bars and logo plates, `8` rows, keys and fields, `12` toast, `16` QR card. The panel has none (flush to its edge).
- Rows: `56` short, `72` tall, `40` group header (`24` as the first row: the list's inset is its air,
  so a section eyebrow at the top sits where a panel eyebrow sits); lead (time) column `64`; meta slot `96` (right-aligned); trailing icon `12`; row progress `3`; OSD progress `4`; live bar `4`, inset by the card radius so it never crosses a rounded corner.
- Lists (`SnapList`): the focused row, and the group eyebrow right above it, are moved clear of both fade zones first (R3), then whole rows at the top (the list snaps after every layout and key scroll; at the very end of a list the top fade carries the cut). The list draws its own `40` dp fades at both ends (DST_OUT strips; the framework's top edge does not draw after a whole-row scroll), so a row cut there reads as "more above/below". While rows are scrolled out above a grouped list, the top `40` dp are the eyebrow band: its upper `24` dp are erased whole and carry the eyebrow of the first visible row's group with its count (the day in Tablå, `SERIER  8` in Sök), drawn where a header row would stand; its lower `16` dp fade that first row, which is snapped so that its text centre sits `6` dp above the band's edge and slides out under the eyebrow, the mirror of the bottom end. So a scrolled list always shows its cue, nothing moves, and no row is orphaned from its eyebrow; a header is never the cut row (REVIEW-R2 Y1, Y2; REVIEW-R3 M-a, M-b). A list reserves the progress lane in every row when any of its rows has progress; rows of one kind share one height, and a one-line kind in a two-line list (`Item.compact`: the rail sections' plain rows, search suggestions) is `56` dp; a row without a subtitle centres its title. One text column per list (Y3): every row's text starts after the widest logo or thumbnail slot of any row in the list, whether the row has a slot or not; Sök's column is fixed at the `96` dp thumbnail slot in every state, so nothing jumps when the first result lands (M-c). A sheet with a logo on any row, or about a channel, keeps the `56 × 32` logo column on every row; list rows keep the `80 × 45` slot.
- Title priority (VISUAL-QA C4): slots are fixed, but inside them the title runs up to the meta text, and the NU pill takes the meta slot when the row has no meta. Only text moves.
- OSD playhead radius `4 × bar height` (32 px across at 1080p); the bar's numbers sit in slots of `00:00:00` at both ends, the track is inset by them; the live tick is `4` wide and as tall as the playhead; the track is inset by the playhead radius so the playhead stays in the safe area.
- Timing (`Theme`): banner 4 s from the first picture (hold at most 15 s while loading), timeline 3 s, toast 3 s, error toast 6 s.
- Logo slot: `80 x 45` in rows and panel headers, `112 x 63` in the OSD (16:9). Server-scaled request `224 x 126` px.
- Image slots (F16, 16:9): search result `96 x 54`, episode `88 x 50` in a `56` row (B5); hero = the screen.
  Pills (Serie actions, season chips): `40` high in a `48` row, fixed widths: primary `232`, Min lista
  `168`, chip `112`. Serie meta line: one line (source, genre, seasons, age rating), no description. The
  hero band (title, meta line and pills: `108` dp, 19.9 % at 1080p) is 22 % of the screen height at most.
- Keyboard: 6 columns, 8 rows of `36`.
- Panel (U10, owner decision B: the rail is added, never taken out of the column): rail `92` (item `84` x `72`, gap `8`) plus `min(48 %, 504 dp)` = `553` at 960 dp (B3; it was 40 %, `476`), so the content column is `461`; Sök rail plus `min(80 %, 840 dp)`; Serie rail plus `min(50 %, 520 dp)` over the hero; right padding `SP_XL` as before; Alternativ sheet `400`, on the right, no rail (the logo column plus `Bildformat: Hela bilden` on one line; the video stays 58 % visible). Full height.
- Chip rows (`PillsView`): only whole chips draw (B5, Y8); when more wait beyond an edge, a `24` dp slot there holds a small `›` or `‹` and the chips that do not fit wait out of sight. The focused chip is always shown.
- Rail destinations (Kanaler, Sök, Min lista, Inställningar) have no panel title (B1): the list starts at the panel's top. A detail keeps its header: a Serie (its title alone, in the hero band).
- A chevron marks a row that opens something (B2): a channel row of Kanaler has none (OK plays). The guide grid has no NU badge and no end time (B3): its now-line and the progress lane say now.

## Stable geometry (owner rule E6)

The geometry of every component comes from its container and fixed slots, never from the content of
any field that can change. That covers titles, channel names, subtitles, meta/status labels, pills (NU),
chips, key hints, toasts, query text, suggestion text, and elements that appear or disappear.

- Each text field has a fixed slot and a fixed line count (single line, or a fixed max with the height
  reserved); overflow ellipsizes inside the slot (`Ui.ellipsize`, which measures with the paint).
- Optional elements reserve their space or overlay; never let one appearing push others (the NU pill
  appearing does not move the meta text or the chevron; a row without a subtitle has the same height
  as the other rows of its list: one row height per list). Use INVISIBLE, not GONE, for in-place toggles
  (the panel eyebrow).
- Hints and toasts change text in place without resizing their container (`Snack`: fixed width, 2 lines).
- Digits are tabular (`tnum`) everywhere.

Rows: the trailing icon, the meta slot (right-aligned text), the NU pill slot, the lead column, the logo
slot and the progress lane sit at fixed x; the progress lane is always reserved, and a tall row places
its title as if the subtitle were there. The panel header is an eyebrow line (always reserved) and a
one-line title of fixed height (it fits the logo plate). The OSD has one fixed height with every
optional line reserved, the same for every kind of playback.

Checked by the harness: every tour runs twice, with the recorded fixtures and with stressed ones (every
title, name and season three times longer with å ä ö, three-digit channel numbers); the bounds of every
view and the drawn rects of row and OSD parts must be identical in every shot (`Screens.stable`).
`OsdLayoutTest` checks the OSD with short and long texts and that every playback kind (live, behind
live, catch-up, VOD), passive or not, draws the same rects; `PassiveOverlayTest` that the passive overlay
draws no pills and that no number floats free; `RailPeekTest` the peek.

## Channel logos (F13)

The logo is the channel's identity: number and name are not drawn next to it, only as its fallback.

- The slot is invisible (no plate, no fill): `80 x 45` dp in rows, `56 x 32` dp in panel headers (the title's
  line height: the logo reads as a title, not a hero), `112 x 63` dp in the OSD.
  The PNGs have alpha; they are decoded `ARGB_8888` and drawn with a filtered paint straight on the surface.
- Variant by surface: the dark-background artwork on dark surfaces (rows, headers, OSD), the
  light-background artwork on the light focused card. Rows hold both, so the swap on focus is instant.
  A missing variant is drawn as a silhouette in the surface's text colour (`TEXT` / `ON_FOCUS`).
- Optical normalisation, once per decoded bitmap: trim to the visible pixels (alpha > 8), then scale so
  that sqrt(w x h) = 0.9 x slot height, clamped to the slot (`LogoFit`). Wordmarks and round logos get the
  same visual weight.
- Alignment: in rows centred on the slot's axis and vertically on the text block (title + secondary
  line), always inside the row and its focus card; in headers and the OSD left-aligned in the slot, so
  the title starts at a fixed x.
- No logo: "12 SVT1" as plain text in the slot (`TEXT_2`), same alignment, never ellipsized (X1, `LogoText`):
  at the largest size of title / body / label (OSD) or body / label / caption (rows) that fits on one line,
  else wrapped onto two lines ("87 SVT" over "Barn/SVT24"), else the channel number alone. The harness
  fails any shot that draws a cut channel name.

## Focus model

- Exactly one focus target at a time: the panel list, the Sök keyboard, or nothing (video). The OSD,
  chip and toast never take focus; the query display is not focusable and never draws the focus look
  (1 dp hairline on `SURFACE`, on `SURFACE_RAISED` while the keyboard types into it, caret in
  `ACCENT_ON_DARK`). Exactly one focus look is on screen at a time (VISUAL-QA C2).
- **Focus indicators are drawn inside the view's bounds; nothing may rely on drawing outside the view or
  on a parent's clipChildren=false.** No focus scale. Rows inset their fill by 2 dp; keys by half a gap;
  a stroke is inset by half its width (GradientDrawable does that). The text sits at the same place
  focused and unfocused. The harness checks every shot: the focused view (and the selected row of a
  focused list) must be fully inside the visible rect of all its ancestors.
- Focused row: full-width `FOCUS` fill, radius 8, text inverts to `ON_FOCUS`/`ON_FOCUS_2`, icons and
  progress switch to `ACCENT`. Focused key: the same fill on the key. A luminance flip plus a shape.
  At rest the trailing icon (play, chevron) is `TEXT_2`: purple marks state, not every row.
- The ListView selector is transparent; rows draw their own focus from `isSelected()`.
- Group headers and note rows are not selectable (the adapter reports them disabled). Dim rows stay
  focusable and the footer says why ("Går inte att se i efterhand", "Inte i Telia Play").
- Keys follow `UX.md` 3.2: up/down move, OK acts, held OK on a row = that row's Alternativ sheet (B7; on video it is OK, A5), → child, ← the rail (in a Sök
  list: back to the keyboard first), Back one step out (sheet, detail, section list, rail, closed), held
  Back closes everything. Focus is restored to the row you came from.
- Motion (optional, `Theme.animations`): 120 ms alpha in for OSD and toast. No row animation, no animated
  scroll, no spinners, no shimmer.
- Progress that follows the clock (U16: the live strip's playhead, the Tablå lanes and now-line, the Just nu cards) is
  computed at draw time and the view draws again when the fill would move one pixel or the slot ends
  (`ui.Progress.redrawDelay`, 100 ms to 30 s): smooth on the pixel grid without a running animation or per-frame work, and
  nothing is scheduled while the view is hidden. A bar never passes 100 %.

## The app on the TV (F20, F23)

- Launcher: a 320 x 180 dp banner (`drawable-xhdpi/banner.png`, 640 x 360 px) and the adaptive icon, both from
  `branding/generate_icon.py`; they carry the launcher label `Flinga` and no other surface does (B8).
- Home screen row (Watch Next): the title, `N min kvar · <source>` or `Nästa avsnitt · <source>`, the Telia poster at 16:9
  (480 x 270 through the image proxy), a progress bar the launcher draws from the position and length. The app draws
  none of it.

## Components and states

| Component     | Built by                                           | States                                                                                                                       |
| ------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Panel         | `Ui.panel`, `Ui.panelParams`                       | hidden, shown (left rail + 48 %, Sök rail + 80 %, sheet right, no rail)                                                                           |
| Rail          | `RailView`                                         | five items, icon (vector, drawn in code, 24 dp, 2 dp strokes) over a 13 sp label; the section shown has the accent bar; rail focused = FOCUS card, ACCENT icon; ↑ ↓ move without wrap |
| Panel header  | `Ui.panelEyebrow`, `Ui.panelTitle`, `Ui.setHeader` | eyebrow (parent, caps) + title; title with a 56 × 32 dp logo (Tablå, Alternativ); none on the rail's roots (the section is the title)  |
| Key hint      | `Ui.panelHint`, `Ui.hints`                         | per focused row; key bold `TEXT_2`, action `TEXT_3`; always ends with "↩ Parent", "↩ Meny" (the rail) or "↩ Stäng"                              |
| List          | `Ui.list` / `Ui.styleList`                         | focused, unfocused                                                                                                           |
| Row           | `RowView` via `RowAdapter`                         | rest, focused, dim, live (bar + NU), playing (bar), progress, play / chevron, logo, completion highlight, group header, note, thumbnail (image / placeholder, dim), info block |
| Pills         | `PillsView` via `RowAdapter.setPills`              | row focused (focused pill FOCUS fill) or not (chosen pill raised); dim pill; icon play / star / filled star; scrolled to the focused pill; a cut pill fades at the edge |
| Hero          | `HeroView`                                         | placeholder (the art's muteBgColor, else SURFACE), image (fades in 120 ms), with the left-to-right scrim; hidden off Serie |
| Query display | `Ui.searchField`, `Ui.setSearchActive`             | empty (placeholder), typed (caret), active while the keyboard has focus                                                      |
| Keyboard      | `KeyboardView`                                     | key focused, Röst dim when no recognizer                                                                                     |
| Logo plate    | `RowView.logo`, `LogoPlate`, `OsdView.setLogo`     | logo, or "12 SVT1" in the same plate                                                                                         |
| OSD           | `OsdView`                                          | the one overlay frame (eyebrow, title, bar with its numbers, pill row) for live, behind live, catch-up and VOD: light (passive, and scrubbing: no pills, a hint for OK) or full (pills and the rail's peek); status line; text (heading + body) for a fatal error only; hidden |
| Rail peek     | `RailView` (peek mode)                             | hidden, or a full-height strip at the screen's left edge with five dimmed icons and `‹ Meny` while the overlay's pills have the focus |
| Caption       | `CaptionView`, `CaptionLook`                       | hidden (no cue), or one box centred at the bottom: the viewer's system caption style, else white on `0xB3000000`, 5.5 % of the height, 80 % of the width at most; above the overlay's content while the overlay is up |
| Guide grid    | `GuideGridView`                                    | hidden, shown full screen (B4): ruler (30 min ticks, day), one 56 dp row per channel (logo slot 96 x 45, blocks by duration, 3 h window, the focused block `FOCUS`, the one on air with a progress lane, past without catch-up `TEXT_3`, a placeholder block `Laddar …` / `Tablå saknas` / `Kunde inte hämta listan · OK Försök igen`), the now-line (`ACCENT_ON_DARK` 70 %), the footer hints, the `NU` chip over the logo column (plain, accent when the window excludes now, `FOCUS` when focused); a cut focused block shows `‹` `›`; dimmed to 30 % under a sheet |
| Login         | `LoginView`                                        | waiting, new code, error status                                                                                              |
| Toast         | `Snack`                                            | info, success, error; auto-hide or sticky                                                                                    |

Row anatomy (one View, drawn; all slots fixed):

```
| [lead][logo]  Title ......................  [NU][    meta][>] |
|               secondary line                                   |
|               ===========-----------------------------         |  <- progress lane, always reserved
```

Row semantics by screen:

| Screen                         | lead    | logo                       | title                                                                                                               | secondary                                            | meta     | trailing          | flags                                |
| ------------------------------ | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- | -------- | ----------------- | ------------------------------------ |
| Inställningar                  | -       | -                          | "Bildformat", "Undertext", "Ljudspråk", "Logga ut"                                                                   | "Hela bilden", "Automatisk", "Automatisk", "Kopplar bort den här TV:n"                        | -        | chevron on Logga ut | -                                  |
| Kanaler                        | -       | channel                    | now title                                                                                                           | "21:00 Next title"                                   | -        | none (B2: nothing opens from the row; held OK offers Visa i tablå) | progress; bar on the playing channel |
| Sök, recent / suggestion       | -       | -                          | query / completion (typed part `TEXT`, rest `TEXT_2`)                                                               | -                                                    | -        | -                 | -                                    |
| Sök, KANALER / JUST NU         | -       | channel                    | now title                                                                                                           | - / "38 min kvar"                                    | -        | -                 | progress (JUST NU)                   |
| Sök, SERIER / FILMER / AVSNITT | -       | thumbnail 96 x 54 (poster) | title                                                                                                               | "Serie · SVT Play" or "Inte i Telia Play · SVT Play" | -        | chevron / play    | dim when known unplayable            |
| Serie, meta line               | -       | -                          | "TV4 Play · Reality-TV · 3 säsonger · 0+" (one line)                                                                | -                                                    | -        | -                 | info, not focusable                  |
| Serie, actions / chips         | -       | -                          | pills: "Fortsätt titta · S21 A3" / "Spela S21 A4", "Min lista"; "Säsong 21" ...                                     | -                                                    | -        | -                 | pills; dim chip = nothing playable   |
| Serie, episode                 | -       | thumbnail 88 x 50          | "3. Avsnittets titel"                                                                                               | "S21 · A3" / "S13 · A11 · 12 min kvar" / "Sett" / "Inte i Telia Play" | -        | play              | progress when started; dim when unplayable |
| Min lista, FORTSÄTT TITTA          | -       | -                          | series or film                                                                                                      | "22 min kvar · SVT Play" (resumes) / "5 min · SVT" (under 1 min watched: plays from the start, so the length) / "Nästa avsnitt · SVT Play"; "★ " first when in Min lista | -        | play              | progress only when it resumes: position / (position + minutes left), the same pair as the text (X2) |
| Min lista, Visa alla (in place)   | -       | -                          | "Visa alla", "Min lista"                                                                                            | -                                                    | count    | chevron           | -                                    |
| Min lista                      | -       | -                          | title                                                                                                               | "Serie · TV10" / "Film · SVT Play"                   | -        | chevron / play    | -                                    |
| Serie, nothing playable        | -       | -                          | note: "Serien finns på SVT Play men går inte att spela upp här." then "Öppna SVT Play" or "Tillbaka till sökningen" |                                                      |          |                   | note                                 |
| Loading / empty / error        | -       | -                          | "Laddar …" / "Inget att visa här just nu" / "Kunde inte hämta listan"                                               | - / - / "OK Försök igen"                             | -        | -                 | dim / dim / focusable                |

## Sketches

Screen is 960 x 540 dp. `░` = video, `▒` = scrim.

### The player overlay (U11): one frame for every kind of playback, in two states

```
|▒▒▒ ┌────────┐                                                               ▒▒▒|
|▒▒▒ │  svt1  │                                                              ▒▒▒|   eyebrow line: logo plate 112 x 63 ("1 SVT1" when no logo)
|▒▒▒ └────────┘                                                               ▒▒▒|
|▒▒▒ Fråga Lund                                                                ▒▒▒|   DISPLAY title, the full width
|▒▒▒                  DIREKT                                                   ▒▒▒|   CAPTION, ACCENT_ON_DARK, clear of the focus ring
|▒▒▒ 20:00 ●━━━━━━━━━━━━|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  21:00 ▒▒▒|   start, track, watched part, live tick, playhead, end (A1): TEXT_2, label size
|▒▒▒                20:18                                                      ▒▒▒|   the picture's time under the playhead, TEXT, in a slot of fixed width
|▒▒▒ [Paus] [Från början 20:00] [Direkt]  ↩ Dölj                               ▒▒▒|   pills (interactive only), one list in one order; "↩ Dölj" only with focus
```

An episode's or film's eyebrow line holds its kind and source in `ACCENT_ON_DARK` caps (`AVSNITT · TV4 PLAY ·
STORAGE WARS`) where the channel's logo plate is; a catch-up has its channel's logo plate like a channel behind
live. The title, bar numbers and pills are at the same rects (E6, `OsdLayoutTest.everyPlaybackKindDrawsOneFrame`).
No kind has its own layout or a mode word.

- **The bar's numbers (A1)**: one font size (`TEXT_LABEL`), aligned to the bar: the start at its left end and the
  end at its right end, each in a slot of the widest value (`00:00:00`) at the safe edges, so the title and the
  start share one left edge; the picture's time centred under the playhead in a slot of that width, clamped
  between the two ends; `DIREKT` above the live tick. No wall clock, no range at the title's right, no
  remaining-time or offset text. On demand: `0:00`, the total, the elapsed time.
- **Passive** (no focus; a zap or any start): the same frame, bottom aligned: the footer slot (`ACTION_H`,
  36 dp, the slot the pills use) holds the hint `OK Visa knappar` (`OK Meny` with no pill), in the hint style
  below (key bold `TEXT_2`, action `TEXT_2`), so the overlay's lowest element ends at the same bottom margin as
  the full overlay's pills. 4 s from the first picture, then hidden. The status line (`Går inte att spola
  tillbaka`, `Går inte att pausa`, or a failed tune in `ERROR`) takes the bar's line. A channel with no programme
  on air has its logo, no title and no bar (the slot stays).
- **Scrubbing** (← → from clean video): the passive frame with the playhead moving and a 3 dp `ACCENT` ring on it
  (the one focus look of the bar, check C2), no pills, no peek; the hint slot reads `OK Spela härifrån  ↩ Dölj`.
- **Focus on a pill**: `SURFACE_RAISED` pills, the focused one `FOCUS` with `ON_FOCUS` text, `SP_L`
  padding, `SP_S` apart, then `↩ Dölj` after the pair gap. The row is `ACTION_H` (36 dp) tall and is the
  same slot the text layout's key hints use. The rail peeks out (below).
- A pill that does not fit the width is dropped, never cut; with at most four pills nothing is.
- 5 s after the last key the overlay hides, never while paused; `setOnHide` tells the activity so its model follows.

**The rail's peek (A4)** is `RailView` in peek mode: a `PEEK_PLATE` (60 % black) strip from x = 0 over the full
height of the screen, `48` dp wide (`RAIL_PEEK_W`; the overlay's side margin is `OSD_SIDE` = `96`, the strip plus a `48` dp gap, so nothing of the
overlay comes nearer than `48` dp to it), with the five icons (`TEXT_2`) at the open rail's own y (`SAFE_Y + SP_S` plus the rail's item height times
the index) and the hint `‹ Meny` (13 sp, `TEXT_2`) in a `32` dp row under the last icon. It reads as the side menu
sliding in; nothing in it suggests a vertical move. It shows exactly while the pills have the focus, takes no
focus, and the panel hides it.

The status line is part of this frame, not a surface of its own. The passive overlay waits for the first picture before it counts down, so a slow start is never
a black screen. The seek bar (VOD), the timeline (behind live on a channel, the live window or the
start-over stream alike, and catch-up of a channel's programme: programme as the track, `DIREKT` tick when
live is inside it, playhead; one frame, no mode for the stream switch) are the same bar; with no span
known its numbers are empty and the frame stays, so nothing jumps when the position arrives (UX.md 7.2).

### The rail (U10)

```
▒                                           ▒
▒  [▭]   Kanaler                            ▒   ▌ = section shown (ACCENT bar, 4 dp, inset by the card radius)
▒ ▌[ ◷ ]  Tablå                             ▒   [ ] = the rail's focus card (FOCUS fill, ON_FOCUS label)
▒  [⌕]   Sök                                ▒   item 84 x 72 dp, icon 24 dp over a 13 sp label
▒  [☆]   Min lista                          ▒   "Inställningar" is 71 dp: 6 dp of air each side
▒  [≡]   Inställningar                      ▒
▒        OK Öppna   ↩ Stäng                 ▒   one hint in the rail, whatever the section (U0)
```

The rail sits left of the content column, 92 dp with its gap, always as high as the panel. The panel is
48 % plus the rail (B3; 40 % before): the rail is never paid for out of the column, so the row anatomy above is unchanged
(logo slot `80 x 45`, lead `64`; B3 then widened the panel to 48 %). `RailView` is one view with five
drawn items (no child views), so a section switch re-renders the list and nothing else.

### Kanaler

```
▒  Kanaler                         ▒
▒▌ [svt1]  Fråga Lund            > ▒   ▌ = the playing channel
▒          21:00  Vår älskade …    ▒
▒          ██████░░░░░░░░░░░░░░    ▒
▒ ┌───────────────────────────────┐
▒ │[ 3 TV3] Efterlyst           > │   no logo: number and name in the plate
▒ │         21:00  Svenska fall   │
▒ └───────────────────────────────┘
▒  OK Titta   Håll OK Mer   ↩ Stäng ▒
```

### Tablå

The guide grid (the component table above; `UX.md` 7.14) is the only Tablå. Beside the rail the panel shows the single note
line `OK Öppna tablå`; there are no day header rows, NU row or per-channel list any more.

### Sök (80 %)

```
▒  Sök                                                               ▒
▒ ┌────────────────────────────────┐   FÖRSLAG                       ▒
▒ │ bon|                           │     bonde                       ▒   typed part TEXT, rest TEXT_2
▒ └────────────────────────────────┘     Bonde söker fru             ▒
▒ [ Röst    ][ Mellanslag ][ ⌫ ][ ✕ ]   SERIER                   8   ▒
▒   a    b    c    d    e    f            Bonde söker fru          >  ▒
▒   g    h    i    j    k    l            Serie · TV4 Play            ▒
▒   m    n    o    p    q    r            Hundra procent bonde     >  ▒
▒   s    t    u    v    w    x            Inte i Telia Play · SVT Play▒
▒   y    z    å    ä    ö    -          FILMER                   1   ▒
▒   1    2    3    4    5    6            En bondes längtan        ▶  ▒
▒   7    8    9    0    .    &                                        ▒
▒  OK Skriv   → Förslag   ↩ Meny                                     ▒
```

Alphabetical 6-column grid with the action row on top: on a D-pad, presses depend on grid distance, not
finger memory (3.32 presses per character on a 6-wide alphabetical grid against 4.01 on 3x11 QWERTY over
1,000 film titles, UX.md 5.1); Swedish order puts å ä ö after z. Empty query: SENASTE SÖKNINGAR. One
letter: hint "Skriv minst två bokstäver". No hits: one dim row "Inga träffar för ”qzx”". Failure: the row
"Sökningen fungerade inte · OK Försök igen".

### Serie (detail screen, F16)

```
▓  Bonde söker fru                         ▓▒                          hero, full-bleed
▓  TV4 Play · Reality-TV · 3 säsonger · 0+ ▓▒   ▓ = scrim tinted with the art's muteBgColor
▓ ┌─────────────────────┐ ┌──────────────┐ ▓▒   (94 % to 42 % of the width, 75 % at the panel
▓ │ ▶ Spela S21 A4      │ │ ☆ Min lista  │ ▓▒    edge, clear at 72 %); pills, fixed widths
▓ └─────────────────────┘ └──────────────┘ ▓▒
▓  (Säsong 21) (Säsong 20) (Säsong 19)  ›  ▓▒   chosen raised, dim = nothing playable, › = more
▓  ┌─────┐ 3. Dags att välja          ▶    ▓▒
▓  │thumb│ S21 · A3 · 12 min kvar            ▓▒   88 x 50, 56 dp row, 5 or more rows fit
▓  └─────┘ ██████░░░░░░░░                    ▓▒
▓  OK Spela   Håll OK Mer   ↩ Sök          ▓▒
```

Images where they add meaning: the art of a series on its own screen, a thumbnail that identifies a
search result or an episode. Implemented by `Images` (no library) and checked by `ImagesTest`.

| Rule | Value |
|---|---|
| Hero | one per screen (Serie only): `images.backdrop16x9`, requested at half the screen (960 x 540 on 1080p) and drawn full-bleed at 2x with filtering; 1.04 MB decoded |
| Thumbnails | search result `192 x 108` px (`search2.posters.image`, 41 kB decoded), episode `176 x 100` px (`images.screenshot16x9`, 35 kB decoded; the offline fixtures are the recorded 256 x 144 ones scaled down, `record.py` asks for 176 x 100 from now on); requested when the row is bound, which a ListView does only for rows on screen |
| Scaling | always by Telia's image proxy, to the exact pixel size of the slot: `https://director-image.streaming.telia.com/convert/?resize=WxH&shape=nocut&source=<source>` (the official app's `ImageProxyImageUrlTransformation`), `Accept: image/jpeg` (the proxy answers PNG otherwise: 459 kB instead of 332 kB at 960 x 540) |
| Decode | `RGB_565` (the art is opaque): 2 bytes per pixel; sampled down if the server sends more than asked |
| Memory | one LRU of decoded bitmaps capped at 7 MB (`Images.CAP_BYTES`); with the logos about 12 MB in all |
| Release | the panel closing (to video, or to play) empties the LRU and drops the bitmaps from rows and hero; leaving Serie for Sök drops the hero from the screen |
| Loading | two threads, newest request first, at most 24 waiting (older ones are dropped when the user scrolls fast); failures remembered 10 min, no retry storm; disk cache 16 MB, 3 days |
| Placeholder | same size as the image: a `SURFACE` plate in thumbnail slots, the art's `muteBgColor` (else `SURFACE`, never flat black) for the hero. A failed image keeps it; nothing moves when an image arrives (E6, the harness's `same:` step) |
| Motion | the hero fades in over 120 ms (≤ 150 ms, behind `Theme.animations`); thumbnails appear at once |
| Focus | nothing waits for an image: rows, pills and keys move at once; images patch into their slot by key, without a rebind or relayout |
