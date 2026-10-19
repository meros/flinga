# Flinga: interaction model (UX spec)

Target model for every feature in `REQUIREMENTS.md` plus the next three (Appar, Fortsätt titta, Favoriter).
`DESIGN.md` owns the look (tokens, row anatomy, performance budget); this file owns behaviour. Where they
disagree, change `DESIGN.md` in the same change. Pixel sizes below are 1920×1080 px (= dp × 2).

> **Clean room (2026-10-18, as built 2026-10-19).** The rail, the panel, the separate Kanaler list and the
> nested sheets are gone. The model is two surfaces and one sheet, as `docs/UX-CLEANROOM.md` specifies (its
> section 7 lists where the build went beyond it): the video with one unified strip, the browser (Tablå and
> Katalog & Sök), and the picker. Sections 2, 3.2 and 7.1–7.8 below, and principle 0's two overlay states
> and rail peek, still describe the old model; the rules in this banner supersede them.
>
> - **Keys on video.** ↑/↓ zap. ←/→ scrub the channel's timeline (below). **OK** enters the strip's pills.
>   **Back** hides the strip, then opens the browser in Tablå (a second Back within 0.7 s leaves the app).
>   **Guide** opens Tablå; **Menu** toggles the browser; **Search** opens Katalog & Sök on the field.
> - **The strip** draws its pills in every state: `Paus/Spela · Från början · Hoppa över · Direkt ·
>   Avsnitt · Tablå · Ljud och text · Alternativ` (only what applies; Tablå and Alternativ stay). Dim with no
>   focus, the focused one in the focus look. No "OK Visa knappar" hint.
> - **One timeline per channel (U12).** ←/→ move a cursor along the channel's broadcast time and do nothing
>   else: across programme edges, over live, start-over and catch-up alike. The strip shows the programme
>   the cursor is in, at once and without the network. The stream that holds the moment loads when the keys
>   stop (1 s) or on OK: a seek inside the stream that plays, else the programme's catch-up or start-over at
>   that moment. A neighbour with nothing to play stops the cursor at the edge (`Går inte att spola vidare
>   här`, once); nothing passes live. Back drops the cursor. While the new stream loads the bar keeps its
>   head at the moment asked for. Held keys accelerate (30 s, 2 min, 10 min). A film or an episode seeks
>   inside its own stream.
> - **The browser** is a tab row (`Tablå`, `Katalog & Sök`) over one lens. **Back from the lens steps out to
>   the tab row, in one press from any row (U13); Back on the tab row closes the browser**; Menu and Guide
>   close at once. ← → on the tab row change the lens and the focus stays on the row; ↓ enters the lens. The
>   lens under the tab row shows no focus look while the row has it.
> - **Tablå** is the guide grid (← → time, ↑ ↓ channels, ↑ from the top row reaches the tabs; OK on the Tablå tab resets the grid to now).
> - **Katalog & Sök** has two layouts (U14). *Browse:* the field is one row under the tabs; each shelf is one
>   row of 16:9 cards that scrolls sideways (Min lista first, then Fortsätt titta, Just nu, Senaste sökningar);
>   ↓ from the field enters the shelves, OK on the field opens the keys. *Search:* a narrow keyboard column
>   at the left with the field above it, the results as a wrapping grid of compact cards at the right
>   (Kanaler, Just nu, Serier, Filmer, Avsnitt och program). The D-pad moves by geometry; → out of the keys'
>   last column goes to the results, ← out of the grid's first column goes back to the keys; Back puts the
>   keys away (browse again), the next Back steps out to the tab row.
> - **The picker** is one 400 dp sheet at the right: Alternativ (Från början, Undertext, Ljudspråk,
>   Bildformat, Lägg till i Min lista for a title, **Logga ut**), Ljud och text, Nästa avsnitt, the grid's
>   held-OK sheet and the recent searches. A value row steps with OK or ← →, an action row runs on OK, Back
>   closes. Logga ut asks once in the same sheet, with Avbryt focused; it ends the session and shows the QR login.
> - **The Serie detail** is the browser's one level: a card opens it over the video; Back returns to the lens.
> - **Held OK** on a result card or a grid block opens the picker; held OK on video is OK.
> - **Inställningar and the Min lista section are gone.** Min lista is the first shelf of Katalog & Sök;
>   Bildformat, Undertext, Ljudspråk and Logga ut live in Alternativ.

## 0. The remote decides the model

The target remote (Chromecast Voice Remote, 2020) has: D-pad + OK, Back, Home, Assistant, Mute, volume,
YouTube, Netflix, Input, Power. **No channel keys, no number keys, no Menu, no Guide, no play/pause, no
media keys** [1]. Assistant and Home belong to the system [2]. So the whole app must work with
**↑ ↓ ← → OK, long-press OK and Back**. Every other key (CH±, 0–9, Menu, Guide, Search, Play/Pause,
FF/RW, Last channel, from a CEC TV remote or a Bluetooth keyboard) is an accelerator that maps onto an
action already reachable with the D-pad [3].

## 1. Principles

0. **Seamless, as few modes as possible (owner's rule, ranks above the rest).** One overlay, one key meaning, one look for every kind of playback; technical switches (start-over stream, catch-up, VOD) never show as a mode. One overlay with two states: passive (every start, no pills, no focus) and interactive (OK, ← →). OK from clean video focuses the first pill, ← → focus the bar and scrub; ← on the first pill opens the rail, which peeks out at the left edge while the pills have the focus (U11, A2, A4, 3.2). Held OK on video is OK; the one deeper context menu is held OK on a list row (B7).

1. **The TV is always on.** Home is live video of the last channel, full screen. Every surface is a layer
   over playing video; nothing replaces it except login and fatal errors.
2. **One press clears the picture.** From any playback, Back only hides what is on screen (the overlay,
   a previewed seek, the banner) and never moves playback (F12c); on clean live video it leaves the app. From
   any panel surface, one long-press Back closes everything; short Back is one step out (sheet, detail,
   section list, rail, closed), so the deepest place (a sheet over a detail) is four presses from video.
   Verify on the device that a held Back reaches the app (`startTracking` + `onKeyLongPress`); if Google
   TV swallows it, the four-press bound still holds.
3. **Instant, then correct.** Every key shows its result within one frame from cached data; network work
   follows. Nothing waits on the network to move focus or draw a row. Targets: feedback < 100 ms,
   zap < 1 s key-to-picture (users tolerate about 1.5–2 s) [4][5].
4. **One axis, one meaning.** ↑↓ moves within a thing (channels, rows). ← goes out toward the rail, → goes
   in toward detail. OK does the obvious thing; long-press OK offers the rest. Back undoes your last step.
5. **Never a dead end.** Every row the user can reach either plays, opens something that plays, or says
   in plain Swedish why not and what to do instead.
6. **Light by construction.** Images where they add meaning, within a strict budget (`DESIGN.md`, "Image
   budget"): one hero per screen and small thumbnails, each server-scaled to its slot, decoded RGB_565 into
   a byte-capped cache that empties when the panel closes. No blur, no running animation, no poster walls.
   2 GB RAM is shared with the system; a heavy app slows the whole TV [6][7].

### Mental model

> "Det är en TV. Menyn ligger till vänster om bilden, och ← tar mig dit. Tillbaka tar mig alltid närmare TV:n."

Three cues tell the user where they are: the **rail highlight** for a rail destination (it has no panel title,
B1), the **panel title** with its parent as an eyebrow on a detail (`KANALER` over the channel's logo, `BONDE
SÖKER FRU` over `Säsong 18`), the **video behind the panel**, and on video the **OSD eyebrow**
(a channel's logo or name alone, catch-up included, `AVSNITT · TV4 PLAY · STORAGE WARS`: a channel is
never labelled with a mode, a title with its kind and source) (there is no offset chip: owner, "Ta bort den gråa rutan som syns vid spolning top/right"; the bar's labels carry the
state, and otherwise the picture stays clean, "Du kan inte visa offset hela tiden i timeshift"). Catch-up and on-demand have
no live edge; any key brings the banner back.

## 2. Information architecture

Flat and direct (U10, owner: "Tänk aningen plattare, mer direkt. Ej invecklad navigering"): a rail of five
sections at the left of the panel, each a list with at most one level of detail below it.

```
Live video (home)  ── always underneath ─────────────────────────────────────────────
 ├─ The player overlay (U11, one frame for every kind): eyebrow, title, the bar with its numbers, pills
 │    ├─ light, passive (A2): every start, zap and return to live, 4 s; no pills, no focus, hint `OK Visa knappar`
 │    ├─ light, scrubbing: ← → from clean video move the playhead on the bar; hint `OK Spela härifrån  ↩ Dölj`
 │    └─ full, focus on the pills (Paus · Från början · Direkt · Avsnitt · Ljud och text): OK from the light overlay; the rail
 │         peeks out of the screen's left edge and ← on the first pill opens it on Kanaler (A4)
 └─ Panel (left: rail + content column, focus)
     Rail (always on screen with the panel; ↑ ↓ show the section at once)
      ├─ Kanaler       → Kanaler list (held OK: Visa i tablå)                          → plays
      ├─ Tablå         → one line beside the rail; OK or → opens the full-screen grid (7.14) → plays
      ├─ Sök           → keyboard + list           → Serie (level 2)                  → plays
      ├─ Min lista     → FORTSÄTT TITTA (+ Visa alla, in place), MIN LISTA → Serie (2) → plays (7.13)
      └─ Inställningar → Bildformat (a persisted setting, A5), Logga ut (confirm sheet)
     (Appar, F10, is a sixth rail item when it is built.)
Playback of catch-up / start-over / VOD: video layer
Login (full screen) · Fatal error (full screen) · Nästa avsnitt (countdown card, focus)
```

Each section keeps its own stack (at most two surfaces: the list and one detail), kept while the panel is
closed and re-entered as it was (F17); Kanaler alone is rebuilt around the channel playing. The old root
Meny and the separate Fortsätt titta screen are gone: Fortsätt titta is the top group of Min lista.

Entry points: the key map (3.2) covers the D-pad keys. Accelerators from other remotes land on the same
surfaces: Menu → the panel at the last section (Kanaler the first time), Guide → the Tablå grid, Search → Sök, CH± → zap, 0–9 → number entry, Last channel → Föregående kanal, Play/Pause and
FF/RW → pause and seek. The Assistant button stays with the system; how voice reaches the app is in 5.1.

**Navigation rule.** → / OK go into a detail, **Back** is one step out in this order: sheet, detail,
section list, rail, closed. **←** from any content list reaches the rail in one press (from a detail it
leaves the detail on its stack: → comes back to the row it left); in Sök the list's left neighbour is the
keyboard, so the rail is two presses from the list and one from the keyboard's left column. A pill row
(Serie actions, season chips) leaves from its first pill. In the rail ↑ ↓ change the section, → or OK enter
its content on the row last focused, Back closes the panel. Opening from video: ← on the overlay's first
pill or Menu focuses the rail (on Kanaler), Guide and Search focus their content. This satisfies the Android rule "Back takes the user to the
previous destination … never a toggle, always ends at Home" [2][3].

**Width (owner decision B, 2026-10-16; widened by B3, 2026-10-16).** The rail is added to the panel: the panel
is 48 % of the screen plus the rail (92 dp: 84 item + 8 gap), 57.6 % or 553 dp at 960 dp (it was 40 % plus the
rail, 49.6 %). The content column is 461 dp, so a title is cut less than it was. Sök is 80 % plus the rail,
Serie 50 % plus the rail. A row's Alternativ sheet and Nästa avsnitt stay on the right without a rail (a sheet is a question, not a
place).

## 3. Interaction spec

### 3.1 Focus

- One focus target: video (none), the rail, the panel list, the Sök keyboard, a row's Alternativ sheet, or
  the Nästa-avsnitt card. Banner, timeline and toast never take focus.
- Default focus per surface: **Kanaler** current channel, centred · **Tablå** NU row, centred ·
  **Rail** the section shown, only when reached with ← or Back (a panel opened from video focuses its
  content) · **Min lista** the first starred title (B6; with nothing starred, the first Fortsätt titta row) · **Sök** key `a`, or the last key used (5.1) ·
  **Serie** the primary action, which plays the episode in Fortsätt titta, else Telia's
  `suggestedEpisode`, else the newest playable episode (its label names it: `Fortsätt titta · S21 A3`);
  ↓↓ from there lands on that same episode in its season (F16: the action replaced focusing the episode
  row itself, so the hero band stays in view when the screen opens) · **Appar** most recently
  launched · **Alternativ** first row.
- Returning to a surface restores its last focused row and query (tvOS: remember focus; never move focus
  without user input) [9]. Data refreshes never move focus: a refreshed list keeps the focused item by id.
- Lists do not wrap. Zapping wraps.
- Non-playable rows stay focusable and dim; the key-hint line explains them (see 6).

### 3.2 Key map (state × key → result)

`—` = ignored (no feedback needed). "Banner" = Kanalbanner for the current channel.

| State | ↑ / ↓ | ← / → | OK | long OK | Back | long Back |
|---|---|---|---|---|---|---|
| Live, nothing shown | zap ∓1, the passive overlay | scrub on the light overlay, step ∓30 s (start-over invisibly) | the full overlay, first pill | as OK (A5) | exit to launcher | — |
| Live, the light overlay shown | zap ∓1 | scrub, step | the full overlay, first pill | as OK | hide it | — |
| Behind live on a channel (time-shift, the start-over stream alike) | zap ∓1, instant; the channel left keeps its place, silently (F17, section 4) | scrub on the light overlay, step ∓30 s; → to the edge returns to live | the full overlay, first pill | as OK | hide the overlay, else as clean live video (leaves the app) | hide everything, playback stays |
| Catch-up / VOD (a title started by hand) | banner of the title, never a zap (F17) | scrub, step | the full overlay, first pill | as OK | hide the overlay, else as clean live video (leaves the app); never a jump to live | live, no panel |
| Scrubbing (the light overlay with the playhead moving) | nothing (never a zap) | scrub on: step ∓30 s; held: 30 s → 2 min → 10 min | **play from the playhead now** (hint `OK Spela härifrån`) | as OK | **cancel**: the playhead goes back, nothing seeks, the overlay hides (hint `↩ Avbryt`) | live |
| Overlay, bar focused from the pills (the pills stay up, unfocused) | ↓ back to the pill that was focused (never a zap) | scrub as above | **play from the playhead now** | as OK | **cancel** (playhead back, nothing seeks) and hide | live |
| Overlay, focus on the pills | ↑ to the seek bar (never a zap) | move between the pills; ← on the first pill: the rail (A4) | perform the focused pill (on an empty row: the rail) | as OK | hide the overlay | live |
| Rail | ↑ ↓ the previous or next section, shown at once; nothing past the ends | → into the content, on its last row; ← — | the same as → | — | close panel | close panel |
| Panel list (a section's root) | move | ← the rail · → child (chevron rows) | primary action | Alternativ for the row | the rail | close panel |
| Panel detail (Serie) | move | ← the rail, the detail stays on its stack · → child | primary action | Alternativ for the row | pop to the section list | close panel |
| Serie: actions / season chips | move (↓ from the chips: the season's default episode) | move inside the row; ← from the first pill: the rail; nothing at the right end | act / go to the season's episodes | Alternativ for the series | pop | close panel |
| Sök: tangentbord | move in grid; ↑ from row 0 = — | move in grid; → from right column = list; ← from left column = the rail | type key | ⌫ held: repeat | the rail (query kept 10 min) | close panel |
| Sök: förslag/resultat | move | ← back to the key you left | run suggestion / open result | Alternativ for the row | pop to the keyboard's section root, then the rail | close panel |
| Alternativ sheet of a row (no rail) | move | — | act, close | — | close (back to the section below it) | close |
| Nästa avsnitt card | — | — | play now | — | close the card; the title stays on screen | close the card |
| Login | — | — | — | — | exit app | — |

**Back on video** hides what is on screen and nothing else (F12c, owner, 2026-10-16: "inte bakåt för att
gå tillbaka"). *Open question, answered:* on clean video during catch-up or on-demand playback, Back
leaves the app, exactly as on clean live video. There is no jump to live and no stop: the title keeps
playing until the app is left, and a held Back is the way to live (it closes every surface and returns
on-demand playback to live; a rewound channel stays where it is). The key glyph is `↩` (the remote's Back
arrow, `Theme.BACK`) in every hint; `‹ ›` are gone, `← →` are the D-pad.

**One overlay, whatever plays (U11, owner: "Jag gillar inte att det finns flera OSD-lägen. Kan vi slå
ihop skrubba med OK-menyn?").** Live, delayed live, catch-up and VOD draw the same frame: the eyebrow
(the channel's logo, else its number and name; an episode's or film's kind and source), the title across
the full width, the bar with its numbers, the row of pills. It is one component in two states, **passive**
(no pills, no focus) and **interactive** (the pills too); there is no separate banner, seek, timeline,
controls or hint layout, and no wall clock, programme range or "N min kvar" anywhere.

**The bar holds the numbers (A1, owner: "Jag gillar inte den flytande 07:06", "Gillar inte 07:11, 06-10. 168
min kvar ... löst hängande och svåra att tolka").** One size, on the bar's own lines, nothing floating:
the programme's start time at the bar's left end and its end time at the right end; under the playhead
one label with the wall-clock time of the picture on screen (`06:48`); the live edge carries the word
`DIREKT` above its tick. A catch-up reads like a channel (its programme's times, no live tick: live is
past its end). On demand reads `0:00`, the total and the elapsed time (`0:42` under the playhead, `1:30:00`
at the end). The time behind live is the distance between the tick and the playhead (F12b, no chip).

**Passive (A2, owner: "Ta bort den där fula bannern. ... visa en passiv OSD en stund när man byter kanal?
Så man ser var man är i programmet, vilken kanal etc.").** Every start shows it for 4 s: a zap, Från början,
a Tablå start, a Sök or Fortsätt titta start. Logo (or name), title, the bar; the pills slot is reserved
and empty. OK or ← → turn it into the interactive overlay, never a second banner. The separate asset
banner (the `SE I EFTERHAND` eyebrow, the facts line) is gone.

- *Scrubbing needs no full overlay (owner, TCL TV: "Spola bör man kunna göra utan fulla menyn"; "Det är lätt att tro att man
  ska trycka OK").* ← → from clean video start a **scrub on the light overlay**: the logo, the title and the bar
  with its numbers, bottom aligned, the playhead moving and ringed; no pills, no peek. While it lasts, from the first ← →
  until it commits, the hint slot reads `OK Spela härifrån  ↩ Dölj`. The picture seeks when the keys have
  stopped for 1 s (steps accumulate and commit as one seek, 3.3; held keys accelerate as before), or at once on OK.
  Back is "↩ Dölj" in every state of the overlay (owner: the same wording, above all while scrubbing with the pills up); during a scrub it also drops the pending seek, silently: the playhead returns to where it was, nothing seeks, the overlay hides (Back never jumps to live).
  A paused picture previews the same way and stays paused after the commit. After the commit the light overlay
  stays its usual 4 s with the usual hint `OK Visa knappar`; OK then opens the full overlay. So OK means "confirm what
  I am doing now" in both states, and the hint always says which.
- *Entry to the pills.* OK (short or held, A5) from the light overlay or from clean video opens the full overlay with
  the focus on the **pills**, on the first one. The light overlay's hint slot says `OK Visa knappar` (`OK Meny` on a
  channel with no pill).
- *Moving.* ↑ ↓ never zap while the overlay has the focus (a zap needs it hidden: Back, or 5 s). ↑ from any pill puts
  the focus on the seek bar (owner: "Man kan inte gå upp från play/pause-knappen till seek"): the playhead is ringed, the
  pills stay on screen unfocused, ← → scrub with the same rules as from clean video (OK commits at once, Back cancels,
  1 s idle commits), and after a commit the focus returns to the pills. ↓ from that bar returns to the pill that was
  focused. A scrub started from clean video has no pills, so ↓ there does nothing. On the
  pills ← → move, OK performs the focused one and hides the overlay (Paus/Spela keeps it up: only its label changes,
  nothing hides, fades in or is rebuilt, and the 5 s timer restarts; the cause of the blink the owner saw was that
  every pill hid the overlay first, so Paus/Spela made it disappear and fade in again).
- *The rail peeks (A4, owner: "skippa kanaler/tablå/sök/alternativ och istället ha vänster som meny, och
  visa en hint ... låta menyn kika fram"; "Right now it feels as if I should move up to select things").* While
  the pills have the focus, a dark strip peeks out of the **screen's left edge**: from x = 0, the full height, 48 dp
  wide, and the overlay's own side margin is 96 dp (strip plus an equal 48 dp gap, owner: the hint and the strip sat too
  close to the logo, title and pills; no overlay element is nearer than 48 dp to the strip in any state), with the rail's five icons dimmed at the open
  rail's own y and the hint `‹ Meny` under them (7.1). It reads as the side menu sliding in, not as a row above the
  pills, and it has no vertical-move cue. It shows whenever the pills do (owner: "Om knapparna syns bör också menypeaken
  synas"): on a pill, on the bar with the pills up (↑ from a pill, scrubbing there too), and on an empty row; it is hidden on
  the light overlay (passive, a scrub from clean video). ← on the bar scrubs; ← on the first pill opens the rail. ← on the first pill opens the rail on Kanaler. On a channel with no pill (no
  start-over) the row is empty and OK on it opens the rail too: never a dead end (U3). While scrubbing the peek is
  not shown.
- *Leaving.* Back hides it and playback stays. It hides by itself 5 s after the last key, never while paused.
- *The row, one list in one order for every kind, omitting only what cannot apply:* `Paus`/`Spela`,
  `Från början HH:MM` (on a channel, when the programme can be rewound into; `Från början` on a title),
  `Direkt` (A3, owner: "vi kanske behöver en direktknapp också, likt från början?"; only when the picture
  is behind live or in catch-up; OK tunes the channel live), `Avsnitt` (an episode of a series only). No
  `Kanaler`, `Tablå`, `Sök` or `Alternativ` pill: the rail is one ← away, and what Alternativ held has a
  home: Bildformat in Inställningar (persisted), Föregående kanal as the first row of Kanaler (A5, owner:
  "Tablå/från början etc känns lite duplicerat? Alternativ kan nog vara lite mer dolt som inställningar i
  menyn istället"). The first pill is the default focus.
- *Paus on live (F18).* With a programme that can be rewound into, Paus is the first pill on live. It (or
  the media key, or OK on the bar) switches invisibly to the start-over stream at the live position and
  pauses; nothing says so (F12c). Spela continues delayed, and → to the edge returns to live. Without
  start-over (the 61 s buffer only) there is no Paus pill on live, and the media key answers
  `Går inte att pausa` on the overlay's status line.

**Hold-OK, the one deeper pattern (B7, owner: "Bra, håll-ok som UX-mönster där vi behöver djupare kontextuell
meny").** Held OK on a *list row* (a Fortsätt titta or Min lista title, a search result, an episode, a recent
search) opens that row's sheet on the right, eyebrow `Alternativ`, title the row (7.3). Held OK on video is
OK (A5): the video has no context menu, the overlay and the rail are its menu. The Serie screen's two pill rows
(actions, season chips) use it as a row does (Min lista on/off). No other surface uses held OK; every row that
does says `Håll OK Mer` in its footer and opens a sheet whose eyebrow is `Alternativ` (harness check Y7).

Accelerators: CH± = ↑↓ on video (and page ±1 screen in panel lists). 0–9 on video = number entry:
banner shows `1_` then `12`, tunes 1.5 s after the last digit or at once when the number is unique. Menu
toggles the panel (Menu is not Back, so toggling is allowed). Guide toggles Tablå for the current channel.
Play/Pause = the Paus pill: pause/play on any title, and on live when it can be rewound into (F18). FF/RW = → / ←.

### 3.3 Timing

| What | Value |
|---|---|
| Overlay with focus | 5 s after the last key, never while paused (`Theme.OSD_CONTROLS_MS`) |
| Kanalbanner and asset banner (the overlay without focus) | 4 s after the last key, counted from the first picture: while a tune or a ticket loads the banner stays (at most 15 s), so a slow start never leaves a black screen with nothing on it; 6 s on cold start; stays while a tune fails (`Theme.OSD_BANNER_MS`, `OSD_HOLD_MS`) |
| Zap coalescing | first press tunes at once; presses within 400 ms of the previous one only update the banner, and the tune fires 250 ms after the last press; a held key scrolls the banner at key-repeat rate and tunes on release |
| Seek commit | steps accumulate on the timeline marker; one `seekTo` 500 ms after the last ← → (no rebuffer per press) |
| Long-press | 500 ms (`ViewConfiguration.getLongPressTimeout`); the press-down shows nothing, release before 500 ms = short press |
| Number entry | 1.5 s |
| Search debounce | 300 ms after the last character; at least 2 characters |
| Toast | info/success 3 s, error 6 s |
| Nästa avsnitt | 10 s countdown; no countdown (waits for OK) after 90 min without a key |
| Progress to Telia | on pause, on leaving the title, on leaving the app, and every 60 s while playing (or the ticket's `progressInterval` if longer) |
| Fortsätt titta / Min lista refresh | at start, and when Min lista opens if older than 60 s |

### 3.4 Zero-wait rules

- **Cold start:** draw the last channel's banner from disk cache (channel list + now/next, stale up to
  24 h) and tune the last channel before refreshing anything. Refresh now/next in the background.
- **Now/next:** refresh at every programme boundary (earliest `endMs` among all channels, +5 s) and on
  resume, not on a fixed 5 min timer. NU must never show a programme that has ended.
- **Guide:** after a channel has played 2 s, prefetch its guide; after 5 s, prefetch ±1 channels. Keep
  guides in an LRU of 8 channels, valid until midnight or for 30 min for the NU part.
- **Tickets:** if the API allows reuse (verify TTL), prefetch the playback ticket of ±1 channels and the
  previous channel; hold at most 60 s. This is the biggest lever on zap time after the player itself.
- **Optimistic UI:** zap updates the banner and saved channel before the ticket; a failed tune keeps the
  banner up with the error line (see 6). Lists render from cache and patch in place by id.
- **Never block focus on I/O.** A surface whose data is not cached opens at once with its title and one
  dim row `Laddar …`; focus is on the list so Back works immediately.

## 4. Live TV

**Zapping.** ↑ = previous number, ↓ = next (as today; matches the list order on screen). With
"Zappa bland: Favoriter" set, ↑↓ cycle favourites only; the banner shows `★`. The banner shows the
target channel's NU/SEDAN from cache immediately, so a fast burst reads like a mini-guide without
retuning every step (coalescing, 3.3).

**Browse without retuning.** OK opens Kanaler: moving focus changes nothing in playback; → shows that
channel's Tablå is gone (the grid is the one Tablå, 7.14); OK on a channel tunes and closes the panel, held OK
offers `Visa i tablå` (the grid on that channel at now). This list is the fast list for zapping.

**Föregående kanal.** The first row of Kanaler (A5, `Föregående kanal` as its second line, the channel's own
programme and logo), so OK, ←, OK flips back; it is a zap, so a place that channel kept is resumed (F17).
KEYCODE_LAST_CHANNEL does it in one press.

**Time-shift and the live edge.** Live is a window ending at the live edge (DASH time-shift buffer).
The overlay's bar shows the current programme as the track, the window start as a dim segment, the playhead,
and the live edge as an `ACCENT` tick labelled `DIREKT`.

- → can never pass the live edge. A step that would reach it snaps to the edge, the playhead sits on the
  tick, the label reads `Direkt` and further → presses do nothing (no toast, no jump). This replaces
  "fast-forward beyond NOW".
- The time behind live is never on the clean picture and never a number of its own: there is no offset chip (the
  grey `−33:04` box top right was removed, owner: "Ta bort den gråa rutan som syns vid spolning top/right"); the bar's
  labels and the DIREKT tick say it. → up to the edge is the only way back to live:
  Back never jumps (owner, 2026-10-16: "inte bakåt för att gå tillbaka ... Man spolar helt enkelt framåt
  till live igen!"); Back hides the banner when one is up, otherwise it does what it does on clean live video.
- ← at the window start stops there; if the programme started before the window and has start-over,
  the next ← continues into start-over (F9). On a channel whose stream is only a live buffer (F8), ←
  goes straight into start-over when the programme has it; otherwise the banner says
  `Går inte att spola tillbaka` on its status line.
- Pausing live is F18 (3.2): where the programme can be rewound into, Paus continues from the live
  position on the start-over stream; the delay then shows in the bar's label while the overlay is up.
- Zapping from behind live is instant and goes to the new channel's live edge; the channel left keeps
  its place (F17, below).

**F17: a channel left behind live keeps its place (owner, 2026-10-16: "Man tappar lätt bort sin
plats/serie om man råkar trycka upp eller ner … Bakåtspolad live är lurigare").** Two rules, no new
mode (U0):

- *A title started by hand* (an episode, a film, catch-up from Tablå or Sök) is not a channel: ↑ ↓ show
  its banner, exactly as OK would, and never zap; with the overlay focused ↑ ↓ move between its rows.
- *A channel watched behind live* (the live window or the start-over stream alike) zaps as fast as
  live. The channel left keeps its **content position**: the programme and the position within it,
  not the offset, because live moves on while it waits. It keeps it while the programme lasts and for
  at most 30 min after leaving; five channels at once, the most recently left first (`PlaceStore`).
  The kept place is **silent** (owner: "Skippa det här med 'väntar' och 'tillbaka'"): the new channel's banner is
  the ordinary zap banner, with no line about the channel left. Zapping back (↑ ↓, Föregående kanal, Last channel) resumes the exact content
  moment on the start-over stream (programme start + saved position): the usual delayed frame
  (`SVT1`, the bar with `DIREKT` and the playhead), nothing announces it. Choosing the
  channel any other way (Kanaler, Tablå's NU row, Sök) is `Titta direkt`: live, and the place is
  dropped. When the place has expired, its programme has ended, or the programme cannot be rewound
  into, the channel plays live, silently; with nothing kept, a zap is a zap.

**Rewinding is rewinding (owner, 2026-10-16: "Jag vill inte ha distinktion börja om för bakåt (det är
bara spola bakåt för användaren)").** When the programme on air can be rewound into (Telia's start-over
stream), ← on live video simply rewinds; the switch from the live window to the start-over stream is
invisible: the OSD is the delayed channel's frame (`SVT1` (the channel alone), the programme, the bar with the
`DIREKT` tick and the playhead), ← on the overlay's bar is just the step, and no mode is named. The
explicit jump stays as a shortcut named `Från början 20:00` (Tablå NU section, its own row below NU; the
overlay's pills) and lands in exactly that frame; `Direkt` is its counterpart on the way back (A3). → up to the live edge switches
seamlessly to live (`Direkt`, banner of the channel), the only way back. When the programme cannot be
rewound into: banner status `Går inte att spola tillbaka`.

**Catch-up.** Reachable from Tablå past rows (play icon), Sök results (`Sändes tors 20:00 · TV3`),
Fortsätt titta. Plays with the chip `Play`. ← → seek within the asset. A catch-up started from Tablå is
the channel watched with a delay: at its end the next programme of the same channel follows (F14), never
"next episode". A film or episode started from Sök, Serie, Fortsätt titta or Min lista: at the end the
next episode of the same series if there is one (Nästa avsnitt, 7.10), else back to live with the toast
`Slut. Tillbaka till direkt.`

**F14: the programme boundary in delayed viewing (owner decision, 2026-10-15).** When a programme
watched behind live ends, whether in start-over or in catch-up from the guide, the next programme on
the same channel continues in the same delayed mode: catch-up if it has already ended, start-over
from its start if it is on air, and live once the delay has caught up with the broadcast. The viewer
keeps their delay; nothing jumps to live on its own. On-demand episodes and films are not affected.

What the user sees at the boundary: the passive overlay of the next programme, exactly like a zap
(the channel alone, the new title, its times on the bar), shown for the banner time; no chip, no toast. When the delay reaches live, the banner is the channel's live banner.
What the playback agent needs from the OSD: the frame of the next programme (`showTimeline`, on the
ticks too) with the row, flashed for `Theme.OSD_BANNER_MS`.

**Return to live.** Back never returns to live from playback (F12c): on a rewound channel, in catch-up
and on demand it hides what is on screen and, on clean video, leaves the app. The ways to live are → to
the live edge on a rewound channel, held Back (every surface closes; on-demand playback tunes live, a
rewound channel stays), and OK on a Tablå NU row or `Tillbaka till direkt`. Position is saved to Fortsätt
titta on pause, on leaving the title and on leaving the app. On resume from the Android home screen: live
re-tunes; non-live resumes paused with the overlay shown.

## 5. Search and catalogue

### 5.1 Sök: in-app keyboard, completions, voice

The Google TV system keyboard only says `Använd tangentbordet på din mobila enhet`, so Sök draws its own
keyboard. No `EditText`, no IME: the query field is a drawn, non-focusable label.

**Layout: alphabetical, 6 columns, action row on top.** On a D-pad, presses depend on grid distance,
not finger memory. A count over 1,000 film titles found 3.32 presses per character on Netflix's 6-wide
alphabetical grid against 4.01 on a 3×11 QWERTY grid, about 17 % fewer [14]. QWERTY wins only for touch
typists, who are not using a D-pad. Swedish order puts å ä ö after z.

```
row 0  [ 🎤 Röst      ][ Mellanslag   ][ ⌫ ][ ✕ ]     (2 + 2 + 1 + 1 columns)
row 1    a   b   c   d   e   f
row 2    g   h   i   j   k   l
row 3    m   n   o   p   q   r
row 4    s   t   u   v   w   x
row 5    y   z   å   ä   ö   -
row 6    1   2   3   4   5   6
row 7    7   8   9   0   .   &
```

Keys are 104×72 px, label TEXT_BODY; focused key = FOCUS fill, radius 8 (same as rows). No shift: search
is case-insensitive and ignores punctuation. ⌫ deletes one character (held: repeats at key-repeat rate);
✕ (`Rensa`) empties the query.

**Two columns.** Sök is the one surface wider than 40 %: keyboard column 768 px on the left, list column
768 px next to it, 384 px of video stays visible. The rail is added to both widths (2).

**Focus.** Opening Sök focuses `a` (empty query) or the last key used. The focused key stays where it
was after typing (no reset to `a`). Grid movement does not wrap; ↑ in row 0 does nothing, and ← in the
left column reaches the rail in one press (the query is kept; typing never leaves the surface by accident,
the rail is a deliberate key). → from the right column moves
to the list: to its last focused row if the list has not changed, else its first row. ← from anywhere
in the list returns to the key it came from. Moving into the list does not change the query.

**The list column** shows, top to bottom:

1. Query empty: `SENASTE SÖKNINGAR`, up to 8 recent queries (OK runs it). None yet: one dim row
   `Inga tidigare sökningar`.
2. Query of 1+ characters: `FÖRSLAG`, up to 4 completions, typed part in TEXT and the completion in
   TEXT_2 (`bon`·`de söker fru`). OK sets the query to the suggestion, runs it and moves focus to the
   first result.
3. Query of 2+ characters: the result groups below, patched in as they land.

**Completions are local and instant** (no network, computed per key press): channel names, now/next
titles of all channels, cached guide titles, Fortsätt titta, Min lista, recent queries and titles from
earlier search results (kept, max 2,000 strings, on disk). Match: case-insensitive word prefix,
punctuation ignored; å ä ö are distinct letters (`a` does not match `å`). Rank: title starts with the
query, then word-prefix; within that Fortsätt titta and Min lista, then on air now, then recent, then the
rest. Network search: 300 ms after the last key, 2-character minimum; a newer query cancels an older one.

**Recent searches**: saved when the user opens or plays a result, or after 3 s idle on a query with hits;
newest first, 8 kept, stored locally. long-press OK on a recent row: `Ta bort` and `Rensa alla`.

**Voice.**
- The `Röst` key starts `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `EXTRA_LANGUAGE = "sv-SE"`,
  `LANGUAGE_MODEL_WEB_SEARCH`, `EXTRA_PROMPT = "Säg vad du vill se"`, via `startActivityForResult` [15].
  The first result replaces the query, runs at once, and focus moves to the first result. If no
  recognizer exists (`ActivityNotFoundException`), the key is drawn dim and OK shows the hint
  `Röstsökning finns inte på den här enheten`.
- KEYCODE_SEARCH (remotes that have it) opens Sök and starts voice immediately.
- Assistant button: owned by the system. Declare the activity searchable and handle `ACTION_SEARCH`
  (`SearchManager.QUERY`), so "Sök efter Bron i Telia Lite" or a Google TV search hand-off opens Sök with
  the spoken query already run [16]. Verify on the device which phrasings Google TV routes to the app;
  the in-app `Röst` key is the guaranteed path.

**Strings** (exact): title `Sök` · empty field `Program, serier, filmer, kanaler` (the longer
`Sök program, serier, filmer och kanaler` does not fit the field at TEXT_BODY) · keys `Röst`,
`Mellanslag`, `⌫` (content description `Radera`), `✕` (`Rensa`) · eyebrows `SENASTE SÖKNINGAR`,
`FÖRSLAG`, `KANALER`, `JUST NU`, `SERIER`, `FILMER`, `AVSNITT OCH PROGRAM` · while the network search
runs, the results eyebrow reads `Söker …` · hint in keyboard `OK Skriv   → Förslag   ↩ Meny` · hint in
list `OK Öppna   ← Tangentbord   ↩ Meny` · voice errors `Jag hörde inget. Försök igen.` and
`Röstsökning finns inte på den här enheten`.

### 5.2 Results and catalogue

**Result grouping**, in this order, each group under an eyebrow row (TEXT_CAPTION, not focusable), max 6
rows per group plus a `Visa alla 23` row:

| Group | Source | Row | OK |
|---|---|---|---|
| KANALER | local channel names | `12  SVT1` · now title | tune, close |
| JUST NU | cached now/next titles | title · `SVT1 · 38 min kvar` | tune, close |
| SERIER | catalogue | thumbnail · title · `Serie · SVT Play` | open Serie |
| FILMER | catalogue | thumbnail · title · `Film · 2007` | play |
| AVSNITT OCH PROGRAM | catalogue | thumbnail · title · `Sändes tors 20:00 · TV3` | play |

Local groups appear instantly (no network); catalogue groups patch in when they land. Every catalogue row
has a 16:9 thumbnail slot (96 × 54 dp): Telia's search poster (`search2.posters.image`, the key art with
the title and the provider's logo composed in), so a series is recognised before its name is read. The
slot is there with or without an image; a missing or failed image leaves its placeholder (F16). Every row
of the column, completions and channels included, starts its text after that slot in every state, before
any result lands: one text column that never jumps. When
the column is scrolled past a group's eyebrow, the eyebrow of the first visible row's group, with its
count, stays pinned in the list's top band (`FÖRSLAG`, `SERIER  8`), and that row slides out under it
through the band's fade: the cue that more is above, and no row orphaned from its group. No
backdrop behind Sök: the 80 % panel leaves a 384 px strip of video, and a 960 × 540 hero per focus change
(about 330 kB each) is not worth a strip.

**Serie: a detail screen (F16).** One level, as before, but immersive: the series' key art
(`images.backdrop16x9`) fills the screen behind a half-width panel, with a left-to-right scrim (the panel
scrim tinted with the art's own `muteBgColor`) so the text reads on it; the right half shows the art.
Top to bottom, in one list that scrolls:

- **Hero band (B5, owner: "Onödigt lite plats för episoderna. Relayout?"):** the title, the meta line and the
  actions, 22 % of the screen height at most (19.9 % at 1080p). Title = the series name, one line (Telia's API
  has no transparent title logo: the app's queries carry no `brandLogo`, and `showcard16x9` has the art and
  the provider's logo composed into one card). There is no eyebrow: the source is the first word of the meta
  line.
- **Meta line** (not focusable, one line): `TV4 Play · Reality-TV · 3 säsonger · 0+` (source, genre, seasons,
  age rating; series have no production year in the API). The description is gone: the episodes need the room.
- **Actions** (one row, ← → between them): the primary action `Fortsätt titta · S21 A3` (started), else
  `Spela S21 A4` (Telia's suggestion or the newest playable); then `☆ Min lista` / `★ I Min lista` (the
  F15 toggle, optimistic). Nothing playable: `Öppna SVT Play` when the app is installed, else
  `Tillbaka till sökningen`.
- **Season chips** (one row, newest first, ← → move and show that season below at once, from cache): only
  whole chips draw, and a small `›` or `‹` at the edge says that more wait there (B5: no cut chip); the
  chosen season keeps a raised fill; a season with no playable episode is a dim chip, and choosing it
  shows `Säsong 20 finns på TV4 Play men går inte att spela upp här.` above its dim episodes; a season
  whose list failed shows `Kunde inte hämta listan · OK Försök igen`. OK or ↓ on a chip goes to the
  season's default episode.
- **Episodes** of the chosen season, ascending, 56 dp rows: at least 5 whole rows fit under the band and the
  chips at 1080p (harness `expectepisodes:5`). Thumbnail (88 × 50 dp, `images.screenshot16x9`), `3. Dags
  att välja`, `S21 · A3` (plus `12 min kvar` when started, or the real length once this TV has played it: Telia's nominal duration can be twice the real one, so it is not shown), play icon, progress lane. Unplayable: dim (thumbnail too), `Inte i
  Telia Play`.
- **No playable episode at all:** the search row is already dim with `Inte i Telia Play · SVT Play`; the
  info line says `Inte i Telia Play · finns på SVT Play`, every chip is dim, the chosen season shows the
  explanation, and the primary action is the way out. Never an empty list.
- The old filter row (`Visa bara de 4 du kan se`) and the season eyebrows are gone: the chips replaced
  them, so dim episodes only show for the season the user chose.
- Started episodes: progress lane and `S13 · A11 · 12 min kvar`; finished ones `Sett`.
- long-press OK on an episode: `Spela från början` (when started), `Lägg till serien i Min lista` /
  `Ta bort serien från Min lista`. On a series row in Sök: `Öppna serien`, `Lägg till i Min lista`.
  A saved series shows `★ I Min lista` on its Serie action and `★` before its secondary line in Sök.

## 6. States and wording

All strings exact. Never show exception text or error-code names to the user; log them.

| Situation | Where | Text |
|---|---|---|
| Loading a list | one dim row | `Laddar …` |
| Empty list | one dim row | `Inget att visa här just nu` |
| Load failed | one row, focusable | `Kunde inte hämta listan` · secondary `OK Försök igen` |
| Search < 2 chars | note row in the list column, below any completions | `Skriv minst två bokstäver för att söka` |
| No hits | one note row | `Inga träffar för ”bron”. Prova en del av titeln.` |
| No recent searches | one note row | `Inga tidigare sökningar. Skriv med tangentbordet till vänster.` |
| Search failed | one row | `Sökningen fungerade inte` · `OK Försök igen` |
| Offline | sticky toast, error dot | `Ingen internetanslutning. Försöker igen …` (auto-retry every 5 s, resume on reconnect, toast `Ansluten igen`) |
| Tune failed | banner status line (error colour), stays | `Kanalen gick inte att starta.` (licence: `Kanalen gick inte att starta (licens).`) · hint `↑ ↓ Byt kanal   OK Kanaler` (OK keeps one meaning on video; OK on the same channel in Kanaler is the retry) |
| Catch-up or VOD failed to start | toast error, then live | `Programmet gick inte att starta.` |
| Programme could not be fetched (search result) | toast error | `Det gick inte att hämta programmet. Försök igen.` |
| ← on a channel that cannot be rewound into | banner status line | `Går inte att spola tillbaka` |
| Paus on a live channel that cannot be rewound into (F18) | banner status line | `Går inte att pausa` |
| Not in subscription | toast info | `Ingår inte i ditt abonnemang` |
| Catch-up expired | toast info | `Programmet går inte längre att se i efterhand` |
| DRM/licence denied | toast error | `Det gick inte att spela upp (licens).` |
| Playback stalled > 10 s | banner | `Bilden har stannat.` · `OK Försök igen` |
| Upcoming programme (OK) | hint line | `Börjar 21:00` |
| Past without catch-up (OK) | hint line | `Går inte att se i efterhand` |
| Login expired / refresh failed | login screen status | `Du har loggats ut. Logga in igen för att fortsätta titta.` |
| Login code expired | login status, error | `Koden har gått ut. Hämtar en ny …` (fetch a new code, no restart) |
| Logged in | toast success | `Inloggad` |
| No channels | fatal | `Inga kanaler i abonnemanget.` · `OK Försök igen` |
| Fatal start error | fatal (BG, OSD text) | `Det gick inte att starta.` · `OK Försök igen  ↩ Avsluta` |
| Logga ut | Alternativ-style confirm | `Logga ut?` · rows `Logga ut` (`Du loggar in igen med QR-koden`), `Avbryt` (`Stanna inloggad`, focused) |
| End of an on-demand episode with no next | toast | `Slut. Tillbaka till direkt.` (a broadcast continues, F14) |

The overlay's bottom line is its pills (7.1); the only hint after them, while the overlay has the focus, is
`↩ Dölj`. There is no hint text for the keys of the bar and the row: they are the same for every kind, and
the pills name what they do. The text layout (a fatal error before any video) keeps `OK Försök igen   ↩ Avsluta`.
A Back hint names where Back lands (`↩ Dölj`, `↩ Meny` = the rail, `↩ Kanaler`, `↩ Stäng`), never
`Tillbaka`, and never `Direkt` or `Till direkt` (Back does not jump, F12c). With the rail focused the
footer is `OK Öppna   ↩ Stäng` whatever the section.

Keyboard hints name the key under focus (they change as focus moves between keys) and the column to the
right: `OK Skriv`, `OK Tala` (Röst; a dim Röst key says `Röstsökning finns inte på den här enheten`),
`OK Mellanslag`, `OK Radera`, `OK Rensa` (no OK pair on ⌫ or Rensa while the field is empty), then
`→ Förslag`, `→ Senaste`, `→ Resultat` or `→ Försök igen` only when that column has a row to select. The
keyboard footer holds key–action pairs only; a column with nothing to select explains itself in its own
note row (section 6), never in the footer. A failed search is the row
`Sökningen fungerade inte · Kontrollera anslutningen` with `OK Försök igen`. A toast while a panel is
open is the panel's footer line for its duration (`Tillagd i Min lista`), never a box over the rows.

Key hints (bottom line of the panel) change with the focused row: `OK Titta  Håll OK Mer  ↩ Meny`,
`OK Spela  Håll OK Mer` (long-press OK is always written `Håll OK`), and the dim-row explanations above.
Hints follow the state, never a template (VISUAL-QA C3): `→ Förslag` only when the list column has a row to
reach, `OK …` only when the focused row acts, and a list with nothing to select (loading, a note) shows only
the explanation its row carries. Glyphs, one meaning each: `↩` is Back, `›` is the child level (the panel), `← →` are the D-pad keys
themselves (the keyboard column, seeking, the rail).

## 7. Wireframes (1920×1080; 1 char ≈ 20 px; panel = 952 px: the rail 184 + 768; safe area 96/54 px)

`░` video · `▒` panel scrim · `[ ]` focused row (FOCUS fill) · `▌` live bar

### 7.1 The overlay on live: passive (the zap banner) and interactive

```
+--------------------------------------------------------------------------------------------------+
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|  (no offset chip here: removed, A1)
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ gradient to SCRIM_OSD ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|     [svt1]                                                                                        |  eyebrow line: logo slot 112×63 dp (or "1 SVT1")
|     Fråga Lund                                                                                    |  DISPLAY title, the full width
|                         DIREKT                                                                    |  CAPTION above the live tick
|     20:00  ●━━━━━━━━━━━━━━━━━━━━|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    21:00  |  start, bar, end (or the status line: "Kanalen gick inte att starta.")
|                         20:18                                                                     |  the picture's time under the playhead
|     OK Visa knappar                                                                               |  the hint slot, bottom aligned like the pills below; while scrubbing: OK Spela härifrån  ↩ Avbryt
+--------------------------------------------------------------------------------------------------+

 interactive (OK): the rail peeks at the left edge, the pills show
+--+---------------------------------------------------------------------------------------------+
|▒▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|▒▭|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|  five dimmed icons, 56 dp wide, on a dark plate, hint under them
|▒◷|                                                                                               |
|▒⌕|                                                                                               |
|▒☆|                                                                                               |
|▒≡|                                                                                               |
|▒‹ Meny                                                                                           |
|     [svt1]                                                                                        |
|     Fråga Lund                                                                                    |
|                         DIREKT                                                                    |
|     20:00  ●━━━━━━━━━━━━━━━━━━━━|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    21:00  |
|                         20:18                                                                     |
|     [Paus] [Från början 20:00]   ↩ Dölj                                                          |  pills 36 dp; focused = FOCUS fill; "↩ Dölj" only with focus
+--------------------------------------------------------------------------------------------------+
```

Implemented in `OsdView` (`showChannel`, `showTimeline`, `showSeek`, `setLogo`, `status`, `actions`, `focusRow`;
the pills are drawn only while the focus is not none) and `RailView.setPeek`; `OsdShots` renders every kind to
`shots/osd-*.png`, `OsdLayoutTest` holds the E6 geometry and that every playback kind draws the same rects,
`PassiveOverlayTest` that the passive overlay draws no pills and no number floats free.

### 7.2 The same frame behind live, in catch-up and on demand

```
|     [tv3]                                                                                         |  a channel behind live: its logo; no mode word
|     Efterlyst                                                                                     |
|                                 DIREKT                                                            |
|     20:00  ████████████████████████◉━━━━━━━|━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━    21:00  |  scrubbing (light overlay, no pills): the playhead is ringed
|                                 20:47                                                             |  the picture's own time, not the offset
|     [Paus] [Från början 20:00] [Direkt]   ↩ Dölj                                                  |  Direkt: only behind live or in catch-up (A3)

|     [tv3]                                                                                         |  a catch-up is its channel's frame (A2): no "SE I EFTERHAND"
|     Spin City                                                                                     |
|     18:30  ████████●━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   19:00  |  no live tick: live is past the programme's end
|                    18:42                                                                          |
|     [Paus] [Från början] [Direkt]                                                                 |

|     AVSNITT · TV4 PLAY · STORAGE WARS                                                             |  an episode or film: its kind and source
|     A Plane Flew Over The Cuckoos Nest                                                            |
|     0:00  ████████●━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   1:30:00  |  0:00, the total, elapsed under the playhead
|                    0:42                                                                           |
|     [Paus] [Från början] [Avsnitt]                                                                |  Avsnitt: an episode of a series only
```

The frame is the same at the start of catch-up or an episode, before the player reports a position: the
bar spans the programme (Tablå's start and end) or Telia's duration for a film or episode, with the
playhead at the start or the resume position (F12: the bar shows whenever the overlay does). When the
player's own position arrives nothing moves. There is no offset chip. The toast sits in the top-right
safe corner. A channel behind live shows its logo or name alone
in the eyebrow: the bar says the rest, no mode word (U0). The eyebrow's element, title, bar and pills sit at
the same rects for every kind (`OsdLayoutTest.everyPlaybackKindDrawsOneFrame`; `Screens.stable` replays
them stressed). The playhead's label moves with the playhead in a slot of fixed width, like the playhead.

### 7.3 Alternativ: held OK on a list row (the one deeper pattern, B7)

```
+--------------------------------------------------------------------------------------------------+
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  ALTERNATIV                      ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Storage Wars                    ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒ [ Spela från början            ] ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒   Öppna serien               > ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒   Ta bort från Min lista       ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒   OK Välj   ↩ Stäng            ▒|
+--------------------------------------------------------------------------------------------------+
```

Right-hand sheet (400 dp, 800 px), so it never looks like the navigation panel. There is no such sheet on
video (A5): held OK there is OK, Bildformat lives in Inställningar, Föregående kanal in Kanaler, Från
början and Direkt on the overlay, Tablå and Sök on the rail. Rows that do not apply are left out, not
dimmed. One header convention for every held-OK sheet (a row, a recent search): the eyebrow says
`Alternativ` (`Alternativ · Storage Wars` for an episode), the title is the context (the row, the query).
A sheet with a logo on any row keeps a 56 × 32 dp logo column on every row, so the titles share one left
edge.

### 7.4 The rail (U10)

Every section list opens in a panel with the rail at its left: icon over a 13 sp label, five items of
84 × 72 dp, the section shown marked with the accent bar (and the FOCUS card while the rail has focus).
The content column has no header on the rail itself: the section's own title is its header (`Kanaler`,
the channel's logo for Tablå, `Sök`, `Min lista`, `Inställningar`) and a detail's eyebrow names where it
came from.

```
+----------------------------------------------------+---------------------------------------------+
|▒  [▭] Kanaler  ▒  Kanaler                          ▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|▒▌[ ◷ Tablå   ]▒                                    ▒|░░░ live video keeps playing ░░░░░░░░░░░░░░|
|▒  [⌕] Sök      ▒▌ [svt1]  Fråga Lund             > ▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|▒  [☆] Min lista▒   [svt2]  Spårlöst försvunn…     > ▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|▒  [≡] Inställn.▒   [ 3 ]   Efterlyst              > ▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
|▒               ▒  OK Öppna   ↩ Stäng                ▒|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░|
+----------------------------------------------------+---------------------------------------------+
```

`[ ]` = focused (the rail focused on Tablå), `▌` = the section shown. ↑ ↓ in the rail show the next
section's content at once while the focus stays on the rail; → or OK moves into it. Min lista is the
home of Fortsätt titta (7.13): MIN LISTA first, the starred titles (B6, owner: "Fokus på stjärnor först"; empty:
`Inget sparat ännu`), then FORTSÄTT TITTA (a few rows, `Visa alla N` unfolds the rest in place, never a second
level), both last watched first. A section opens at its top with
focus on its default row (3.1), never on a Fortsätt titta row from Kanaler or Tablå: it is offered, not
pushed.

The rail also peeks out of the screen's left edge over the video (A4, 7.1): a full-height strip, 48 dp wide, on a
dark plate, with the same five icons dimmed at the open rail's y and the hint `‹ Meny`, while the player overlay's
pills have the focus.

### 7.5 Kanaler (mini-guide)

```
|▒▌ [ [svt1]  Fråga Lund           > ]▒|  no title: the rail names the section (B1); ▌ = playing now
|▒▌ [         21:00 Vår älskade       ]▒|
|▒▌ [         ███████░░░░░░░░░░░░░░░░ ]▒|
|▒    [svt2]  Spårlöst försvunnen    >  ▒|
|▒    [TV Malmö] Inget program just nu   ▒|  a gap in the schedule: the chevron shows when the guide has programmes (B2); none: no chevron, → does nothing
|▒  OK Titta   Håll OK Mer   ↩ Meny     ▒|
```

### 7.6 Tablå

Gone as a list: Tablå is the guide grid (7.14). Beside the rail it shows one line, `OK Öppna tablå`.

### 7.7 Sök (keyboard column + list column, 80 % + rail)

```
+---------------------------------------+---------------------------------------+------------------+
|▒ ┌───────────────────────────────────┐▒▒  FÖRSLAG                             ▒░░░░░░░░░░░░░░░░░░|
|▒ │ bon▏                              │▒▒    bon de söker fru                  ▒░░░░░░░░░░░░░░░░░░|
|▒ └───────────────────────────────────┘▒▒    bon des längtan                   ▒░░░ live video ░░░|
|▒ [🎤 Röst  ][Mellanslag][ ⌫ ][ ✕ ]   ▒▒  JUST NU                             ▒░░░ 384 px ░░░░░░░|
|▒   a    b    c    d    e    f         ▒▒    Bonde söker fru  TV4 · 22 min kvar ▒░░░░░░░░░░░░░░░░░░|
|▒   g    h    i    j    k    l         ▒▒  SERIER                              ▒░░░░░░░░░░░░░░░░░░|
|▒   m  [ n ]  o    p    q    r         ▒▒    Bonde söker fru  Serie · TV4 Play >▒░░░░░░░░░░░░░░░░░░|
|▒   s    t    u    v    w    x         ▒▒    Hundra procent bonde            > ▒░░░░░░░░░░░░░░░░░░|
|▒   y    z    å    ä    ö    -         ▒▒    Inte i Telia Play · SVT Play      ▒░░░░░░░░░░░░░░░░░░|
|▒   1    2    3    4    5    6         ▒▒  FILMER                              ▒░░░░░░░░░░░░░░░░░░|
|▒   7    8    9    0    .    &         ▒▒    En bondes längtan  Film · 2019  ▶ ▒░░░░░░░░░░░░░░░░░░|
|▒  OK Skriv   → Förslag   ↩ Meny      ▒▒                                      ▒░░░░░░░░░░░░░░░░░░|
+---------------------------------------+---------------------------------------+------------------+
```

Query field: SURFACE, 1 dp hairline, drawn caret; not focusable. `[ n ]` = focused key. Suggestions draw
the typed part (`bon`) in TEXT and the rest in TEXT_2. Empty query: the list column shows
`SENASTE SÖKNINGAR` instead of FÖRSLAG and results.

### 7.8 Serie (detail screen, F16, relaid out by B5)

```
+------------------------------------------------+-------------------------------------------------+
|▓  Bonde söker fru                              ▓▒      hero: images.backdrop16x9, 960 x 540,      |
|▓  TV4 Play · Reality-TV · 3 säsonger · 0+      ▓▒      drawn full-bleed at 2x                     |
|▓ [▶ Spela S21 A4          ] ( ☆ Min lista  )   ▓▒  ▓ = scrim, panel colour tinted with the art's  |
|▓  (Säsong 21) ( Säsong 20 ) ( Säsong 19 )  ›   ▓▒      muteBgColor, 94 % up to 42 % of the width, |
|▓  ┌─────┐ 1. Ett första steg mot kärl…         ▓▒      75 % at the panel edge, clear at 72 %      |
|▓  │thumb│ Inte i Telia Play                    ▓▒  dim row, dim thumbnail; 88 x 50 dp, 56 dp row  |
|▓  └─────┘                                      ▓▒                                                 |
|▓  ┌─────┐ 3. Dags att välja              ▶     ▓▒  the band above is 19.9 % of the height (title,  |
|▓  │thumb│ S21 · A3 · 12 min kvar               ▓▒  meta line, pills); 5 or more episode rows      |
|▓  └─────┘ ████████░░░░░░░░                     ▓▒  fit under the chips                            |
|▓  OK Spela   Håll OK Mer   ↩ Sök               ▓▒                                                 |
+------------------------------------------------+-------------------------------------------------+
```

Panel: the rail plus 50 % of the width (480 dp), no panel scrim of its own (the hero's scrim is it). Default focus:
the primary action (3.1). From the player's `Avsnitt` pill (U11) the playing episode's row is selected instead, in its
season. Scrolling keeps whole rows (C1): an episode below the chips keeps them whole at the top while it
fits under them; further down they leave as one row, never half (room below the last episode makes that
possible at the end of a short season). Loading: the screen opens at once with the header, `Laddar …` and the hero's
placeholder colour; the art fades in (120 ms) when it lands. Back pops to Sök with its row focused; the
hero bitmap leaves the screen with the surface, and every decoded image goes when the panel closes.

No playable episode (Hundra procent bonde, only on SVT Play):

```
|▓  Hundra procent bonde                         ▓▒
|▓  Inte i Telia Play · finns på SVT Play        ▓▒
|▓ [  Öppna SVT Play         ] ( ☆ Min lista  )  ▓▒   else "Tillbaka till sökningen"
|▓  (Säsong 34) ( Säsong 33 ) ( Säsong 15 )      ▓▒   all dim
|▓  Serien finns på SVT Play men går inte att    ▓▒   TEXT_2, not focusable
|▓  spela upp här.                               ▓▒
```

### 7.9 Appar

```
|▒  Appar                                ▒|
|▒ [ ▣ SVT Play                        ↗ ]▒|  ▣ = app icon 48 px, loaded lazily, cached in memory
|▒    ▣ YouTube                       ↗  ▒|
|▒    ▣ Netflix                       ↗  ▒|
|▒    ▣ TV4 Play                      ↗  ▒|
|▒  OK Öppna   Håll OK Flytta först   ↩ Meny ▒|
```

Leanback launcher apps (`CATEGORY_LEANBACK_LAUNCHER`), sorted by last launched from here, then A–Ö;
long-press OK = `Flytta först` / `Dölj`. Needs a `<queries>` intent declaration for the launcher
intent [10]. Launching pauses playback; returning re-tunes live (3.2). Icons: at most 12 decoded,
`RGB_565`, released when the panel closes.

### 7.10 Nästa avsnitt (end of an episode)

The Alternativ sheet (no new component), shot 68:

```
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  NÄSTA AVSNITT OM 7 S               ▒|
|░░░░░ last frame of the episode ░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Storage Wars                       ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒ [ Spela nu                       ▶ ] ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒ [ Avsnitt 11 · Special (Welcome …  ] ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒   Tillbaka till direkt             ▒|  tunes live, as it says
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒   OK Välj   ↩ Stäng             ▒|
```

Shown only when the episode really ended (STATE_ENDED) and the next episode is known and playable (same
season, else the first playable one of the next season). The eyebrow counts down once per second (text
only, no bar, no per-frame work); at 0 the next episode plays. Back only closes the card (the title stays on screen); `Tillbaka till direkt` = live. After 90 minutes without a key press it does not count
down; it waits for OK (Netflix asks after 3 episodes or 90 minutes [20]). Never shown for catch-up from
Tablå (F14) or for films.

### 7.15 Ljud och text (F19)

The pill `Ljud och text` is the last of the overlay's row (after Avsnitt), there only when the stream reports more
than one audio track or at least one text track; the row is otherwise as before (U0: no empty mode). OK opens the
one sheet component on the right (400 dp, the Nästa avsnitt look), shot 402:

```
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  LJUD OCH TEXT                       ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  SVT1                                ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Ljud                                ▒|  group header, left out with one audio track
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒ |Svenska                            ▒|  accent bar = what plays
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Engelska                            ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Undertext                           ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Av                                  ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Svenska                             ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  Engelska / Hörselskadade/SDH         ▒|
|░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░▒  OK Välj   ↩ Stäng                    ▒|
```

- Names are Swedish language names (`Svenska`, `Engelska`); a region or script only when two tracks share the language
  (`Kinesiska (förenklad, Kina)`). An audio description is `Syntolkning` on the second line. When tracks share a
  language the second line also gives channel layout and codec (`5.1 · E-AC-3`, `Stereo · AAC`).
- OK on a row applies it at once and the accent bar moves; the sheet stays so the viewer can try the other group.
  Back closes the sheet; the overlay returns with the focus on `Ljud och text`.
- The choice holds for the session. Choosing a language also sets the remembered language (Inställningar shows
  `Ljudspråk: Engelska` after English was picked); choosing `Av` or a track in Automatisk never changes the
  setting `Undertext`. Changing a setting in Inställningar drops the session's pick and decides again at once.
- Inställningar: `Bildformat`, `Undertext` (Automatisk, Alltid på, Av), `Ljudspråk` (Automatisk, Svenska, Engelska,
  Original), `Logga ut`; OK on a value row turns it to the next value (the Bildformat pattern).
- Subtitles: one box, centred at the bottom, the system caption style when the viewer set one; while the overlay is up
  the box stands above the overlay's first line (logo, eyebrow), the overlay never covers it (shot 401). A sheet
  from the pill hides the overlay, so the caption drops back to the bottom.
- The audio description never plays unless picked, also not when the stream lists it first.

### 7.16 The system around the player (F20-F23)

Nothing here adds a screen, a pill or a key: the system reaches the app through the places it already has.

| From | What it does |
| --- | --- |
| Watch Next row on the TV's home screen (Fortsätt titta) | `flinga://title/<id>?play=1`: plays the title where it stopped, like OK on its row in Min lista. A series row plays the episode in progress or the next one |
| `flinga://title/<id>` | a series opens its Serie page, a film plays |
| `flinga://channel/<id>` | tunes the channel (the guide and the panel close); the one already playing is left alone |
| `flinga://guide` | the guide grid |
| `flinga://search?q=text` | Sök with the text typed and run |
| the Assistant: "sök efter Bron" (ACTION_SEARCH) | Sök with `Bron` run; the results are the answer |
| the Assistant: "spela SVT1" (MEDIA_PLAY_FROM_SEARCH) | a channel whose name is the query tunes at once, no Sök |
| the Assistant: "spela Rapport" | Sök with the query run; if exactly one result is titled `Rapport` a film or episode plays and a series opens; with two or none the results stay on screen |
| the Assistant or a remote: "pausa", "spela", "spola framåt 30 sekunder", "nästa" | the Paus pill, the Spela pill, → ×1 (`scrub`, the light overlay), the next episode; each is the key's own code, so the overlay shows what happened |
| another app takes the sound, the headset is pulled | the picture pauses and the pill reads Spela; Spela resumes |
| Home | playback pauses and the system's session ends; coming back resumes |

Unknown or broken links, and a channel the subscription lacks, change nothing: the app opens as from its icon. The activity
is never recreated, so a playing session is never interrupted by a link to the place it already is. Spoken text arrives in
Swedish, so a title is matched without case, accents or punctuation (`SVT 1` is `SVT1`). The system's now-playing surface
shows the programme (or episode) and the channel with its logo; for a live channel without start-over it offers no pause or
seek, matching the key (`Går inte att pausa`).

### 7.11 Login and fatal error

Login stays as in `DESIGN.md` (QR + code). On code expiry it fetches a new code itself. Fatal error:
BG, OSD text mode, centred-left, with the two key hints from section 6; OK retries.

## 7.12 Apps integration (F10, parked; plan in `docs/LAUNCHER.md`)

The aim: the other apps on the TV are one step away, without making Telia Lite a heavy home screen.
Build it in three steps; each step works on its own, and each later step only adds.

**Step 1: Appar inside Telia Lite (no system change).** The `Appar` surface (7.9) lists the installed TV
apps. It is a rail item between `Min lista` and `Inställningar`.
- Rows: app icon (48 px, `RGB_565`, at most 12 decoded, released when the panel closes) and the app name.
- **Order and visibility are the user's (owner requirement).** Default order: last launched from here, then A–Ö.
  Held OK on an app opens `Flytta först`, `Flytta upp`, `Flytta ner`, `Dölj`. While `Flytta` mode is on
  (`Ordna appar` in the sheet), ↑↓ moves the focused app and OK drops it; the hint reads `↑↓ Flytta   OK Klar`.
  Once the user has moved an app, the order is fixed and no longer follows use. Hidden apps collect in a last
  row `Dolda appar (3)`; inside it, OK on an app offers `Visa igen`. New apps appear last and visible.
  Store by package name.
- OK launches the app (`getLeanbackLaunchIntentForPackage`). Playback pauses; returning to Telia Lite
  re-tunes live (3.2). The hint line reads `OK Öppna   Håll OK Flytta först   ↩ Meny`.
- **Contextual hand-off instead of a separate launcher:** wherever Telia cannot play something that
  another installed app can, offer that app at the point of need, not only in Appar:
  - Serie with no playable episode: `Öppna SVT Play` (7.8).
  - Search result from another provider: secondary text `Inte i Telia Play · SVT Play`. OK on it opens the
    Serie surface with the `Öppna SVT Play` row.
  - Deep links into a title (Netflix `netflix://title/<id>`, YouTube `watch?v=`) are unofficial. Only
    use them when the app's own search result gives an id; otherwise open the app's start page.
- Strings: title `Appar` · hint as above · empty `Inga andra appar installerade` · app missing
  `Appen finns inte längre på TV:n`.

**Step 2: Telia Lite as home app (opt-in, reversible).** `Inställningar › Använd som startskärm` (off by
default) enables a HOME activity alias. The user (or adb) then makes Telia Lite the home app.
Behaviour changes when Telia Lite is home:
- **Home key:** closes every surface and shows live video of the last channel with the Kanalbanner. A
  second Home press within 2 s opens the panel (Home is never a toggle back to the previous surface).
- **Back at the root:** does nothing, instead of leaving the app. There is nothing below home.
- **Assistant and Search keys:** pass through to the system. Never swallow them.
- **Inställningar gains `Inställningar för TV:n`:** it opens the system settings (`Settings.ACTION_SETTINGS`).
- **Boot:** show the last channel's banner from the disk cache at once. If the network is not up yet,
  show `Väntar på internet …` in the banner, and tune as soon as it connects. Never show the login
  screen only because the network is missing.
- **Appar becomes the app grid of the TV.** The same surface; no second launcher UI.

**Step 3: Turn off the stock launcher (owner's decision, adb only).** No UI in the app. The commands and the
rollback are in `docs/LAUNCHER.md`. Known loss: the YouTube button on the remote. Telia Lite must then
pass the full step 2 checklist on the device first.

**Limits that shape the design:** Telia Lite cannot read other apps' "continue watching" rows, because
the Watch Next provider is closed to non-system apps. So Fortsätt titta only holds Telia content. Do
not design an "all my apps' progress" row.

## 7.13 Fortsätt titta, Min lista and resume (F15)

**One model: Telia's lists, shown where the user already is, never in the way.** The official app keeps
both lists on Telia's servers, and this client uses the same lists, so a film paused on the phone shows
up here and the other way round. Nothing in F15 opens by itself or asks a question before playback.

**What the server offers** (static analysis of 9.23.2, checked against the server 2026-10-15):

| Need | Telia API | Notes |
|---|---|---|
| Fortsätt titta | `panelX(id: "known-panel\|container-continue-watching")` (getPanelPanelX) | Newest first. A series poster links to the series, `progress.id` is the episode to play. `position` is in ms. `percent` is rounded to 5. `timeLeft` reads "12 min återstår". Position 0 on a series means that Telia has moved on to the next episode |
| Min lista | `panelX(id: "known-panel\|container-my-list")`; `series.userData.favorite`, `media.userData.favorite` | |
| Add or remove | `addToMyList2` / `removeFromMyList2(input: {id, type: SERIES\|MEDIA})` | `ResultOk`; one round trip with Storage Wars, undone at once |
| Hide from Fortsätt titta | `removeListFromContinueWatchingV2(input: {ids})` | From the app's code; not run against the server |
| Next episode | `series.suggestedEpisode` | Telia's own logic: the episode in progress, else the next one |
| Report progress | POST `playbacktrackinggateway…/event` `{sessionId, type: SESSION_START\|PROGRESS\|SESSION_END, time, position, duration}` (ms), header `Client: telia-play-android`; DELETE `…/streamingticket/{type}/{id}?position=<ms>&sessionId=…` at the end | `sessionId` and `progressInterval` come from the ticket's `tracking.PLAYBACKTRACKING`. Only on the TV: whether the ticket has that block, and how long Telia takes to show a new position |

**Rules** (from Android TV's Watch Next guidelines [17] and the apps in [18]–[22]):

- *In and out of Fortsätt titta.* Telia decides. Locally (shown until the next refresh), a title is
  added after 2 min watched. It is finished with less than 3 min left or at 95 %: a film leaves the
  row, and a series moves on to its next episode (`Nästa avsnitt`) or leaves the row. This matches
  [17]: 2 min to start, credits or < 3 min left to finish, one entry per series.
- *Resume, not ask.* OK plays from where the user stopped, with no modal [17][19]. From 1 min watched,
  the bar's label says where (`10:05 / 30:00`); nothing else announces it (REVIEW-R4 A6).
  `Från början` is a pill while the title plays. `Spela från början` is on the row's
  held-OK sheet.
- *Next episode.* Serie opens on the episode to continue with. Fortsätt titta points to it. The
  Nästa avsnitt sheet (7.10) offers it at the end. A delayed channel (F14) is never "next episode".
- *Saving with a D-pad.* Held OK on any title row opens a sheet that names the state:
  `Lägg till i Min lista` or `Ta bort från Min lista`, as Google TV's long press and toggle label do
  [21]. Every such row has `Håll OK Mer` in its hint line. A saved title shows `★` before its
  secondary line and `★ I Min lista` on the Serie action. Changes are optimistic, with the toast
  `Tillagd i Min lista`. If Telia refuses, the change reverts with `Min lista kunde inte sparas.
  Försök igen.`
- *Where.* The Min lista section of the rail (B6, owner: "Fokus på stjärnor först! Fortsätt titta sekundärt, ...
  senast tittat först"): MIN LISTA first, the starred titles, and the initial focus is on the first of them (with
  nothing starred, on the first Fortsätt titta row); then FORTSÄTT TITTA, a few rows and then `Visa alla N` (which
  unfolds in place). Both groups are last watched first. Telia's panel rows carry no timestamp (the recorded
  fixtures hold position, percent and time left only), so "last watched" is the order of Fortsätt titta: Telia's
  own list, newest first, and a title played on this TV moves to its top (`Library.played`). The starred titles
  follow that order (`Library.starred`: the watched ones in Fortsätt titta's order, then the never watched ones
  in the order the API returned them). Live TV stays home (1): Fortsätt titta never opens on its own.

**Rows** (shots 60–71):

| Row | Title | Secondary | Lane | OK | → | Held OK |
|---|---|---|---|---|---|---|
| Fortsätt titta | series or film | `22 min kvar · SVT Play` (resumes), `4 min · SVT` (under 1 min watched: it plays from the start, so the row says the length, the same number the banner's bar starts with: position + Telia's minutes left, never Telia's nominal duration, M-d), `Nästa avsnitt · SVT Play` | only when it resumes: position / (position + minutes left), the same pair as the text | plays at the position | Serie (series) | Spela från början, Öppna serien, Min lista on/off, Ta bort från Fortsätt titta |
| Min lista | title | `Serie · TV10`, `Film · SVT Play` | – | Serie / plays | Serie | Öppna serien, Ta bort från Min lista |
| Episode in Serie | `11. Special …` | `S13 · A11 · 12 min kvar`, `Sett`, `30 min` | when started | plays (resumes) | – | Spela från början, Lägg till serien i Min lista |

**Cache (E6, 3.4).** `Library` keeps both lists and this TV's positions in `files/library.json` (a few
kB). The menu draws from it on a cold start. A refresh replaces it, apart from positions saved after the
refresh began. Refresh happens at start, and when Min lista opens if the data is more than
60 s old. Rows patch in place by key, so focus never moves.

### 7.14 Guide grid (B4)

Owner (TV feedback round 1, shot 45): "Jag tycker denna tablå bör vara en grafisk lanes-tablå med alla kanaler och
tablåer! Möjlighet att starta live eller i efterhand starta program med ett OK-tryck. Egen helsida typ". The rail's
**Tablå** (OK or → on it) and the Guide key open a full-screen page, drawn by one custom view (`GuideGridView`). The
grid is the only Tablå (owner, on the TCL TV: "Tablå har nu en sido-meny som inte går att använda ... Den stora är nog den vi
vill ha kvar"; U0: fewer modes): there is no per-channel list and no preview. While the rail is browsed past Tablå the panel
shows one quiet line, `OK Öppna tablå`. Kanaler stays the fast list for zapping; its held OK offers `Visa i tablå`.

```
+--------------------------------------------------------------------------------------------------+
|  TORS 15 OKT  19:30      |20:00   ●    |20:30      |21:00      |21:30      |22:00                  |
|  [svt1]   | Rapport |   | Fråga Lund ▓▓▓▓▓░░░░░ | Vår älskade låtskatt  | Lokala | Hocke…         |
|  [svt2]   | Sverige rom… | Spårlöst försvunnen | Jakte… | Aktuellt            | Sp… | I'm Chevy… |
|  [tv3]    | Lyxfällan    | Efterlyst           | Svenska fall                    | Confessions…   |
|   ...                                  ▏now-line over every row                                  |
|  OK Titta direkt   Håll OK Mer   ↩ Stäng                                                         |
+--------------------------------------------------------------------------------------------------+
```

- *Layout.* No rail, no panel, the video's audio plays behind a `BG` page. Channels are rows in Kanaler's order, 56 dp
  high, whole rows only (E5): 7 fit at 1080p, the first visible row moves a row at a time. The logo has a fixed 96 x 45
  dp slot at the left (the same logo rules as everywhere, F13: dark variant, else the light one as a silhouette, else
  the number and name, never cut). Time is the columns: 3 h across, a block's width comes from its duration on that
  scale and never from its title, which is cut with an ellipsis inside the block (E6); a block cut by the left edge
  starts its title at the visible edge. The ruler runs along the top: a tick and an `HH:MM` label every 30 min, the day
  at its left (`TORS 15 OKT`). **The now-line** (a thin accent line over every row with a dot on the ruler) is the one
  mark of "now"; the programme on air also has the progress lane. There is no NU badge and no end time (B3).
- *Focus and keys.* The focus stands on a **time cursor**. ↑ ↓ move across channels (no wrap) onto the programme that covers
  the cursor, else the nearest one by distance (a tie goes to the later), and **never move the time window** (owner, on the
  TCL TV: switching channel could switch the time): a block cut by the window's edge is drawn on its visible part, the title
  stays on that part and a `‹` or `›` cue says the block goes on. ← → one programme per press; they are the only keys
  that scroll time, and just as far as it takes to show that programme whole (a longer one: its start), and the cursor
  becomes the start of the programme they land on (clamped into the window), so the next ↑ ↓ lands near it. OK on the **programme on air**
  plays it live (Titta direkt: `tune`); OK on a **past block with catch-up** plays it from its start, the same path
  the way catch-up has always started (F14 continues into the next programme); OK on a **future block** and on a past one without
  catch-up does nothing, and the footer already says so (`Sänds 21:00`, `Går inte att se i efterhand`, U3). **Held OK** is
  the one deeper pattern (B7): a sheet `Alternativ` over the dimmed grid with `Titta direkt` and `Från början HH:MM` (when
  the programme can be rewound into) on air, `Spela från början` in catch-up; Back closes it and the grid has the focus again.
- *The way back to now (the Tablå tab).* There is no `NU` chip (owner, 2026-10-19: "The nu button feels reversed ... maybe we
  should just reset nu on tab click instead"; it was accent while away from now, which read backwards). The ruler row holds the day
  (`15 OKT`) at the left. ↑ from the top channel row focuses the tab row. OK on the Tablå tab puts now about a fifth in from
  the left of the window and focuses the playing channel's programme on air, that row centred (the same as opening the grid), and
  enters the grid. Held OK on a block also offers `Gå till nu` as the sheet's first row while the window is away from now (the
  sheet then opens even on a future block).
- *Opening and leaving.* It opens on the playing channel's programme on air, that row vertically centred (the first rows
  stay at the top). Back closes it to the video and never leaves the app from here. ← at the first column (the first
  programme of the row, or one starting 6 h back, or a row without data) hands over to the rail on Tablå (U10). The grid
  scrolls in time through the whole guide history the fetch returns (24 h back) and ahead as far as any loaded guide goes
  (at least 6 h), so earlier catch-up stays reachable. A row waits as one `Laddar …` block and, when its guide is empty or failed, shows `Tablå saknas`.
- *Data.* One guide fetch per channel, cached (`Api.guide`, `guideCache`): the rows on screen and three either
  side are asked for, nearest the focus first, at most two in flight on the one I/O thread (the 80/min limit analog); no
  request per block, no images beyond the logos (F13, `Logos` as ever).
- *Offline check.* `Screens.grid` (shots 300-309: on air, a future block, a past block and its catch-up, rows with logos,
  a gap row, a row without a guide, the hold-OK sheet, ← to the rail), harness check B4 on every shot (blocks inside the
  grid and never overlapping, logo slots whole, the now-line inside, ruler and footer present), the stressed replay
  (long titles, 3-digit numbers: 0 geometry differences) and `GuideGridViewTest`. `Screens.GRID_TIME` (shots 312-315) checks that ↑ ↓
  leave the window where it was (`markgrid`, `expectgridwindow:same`), ← → move it, the chip state (`expectchip`), the sheet's `Gå till nu` and OK on the chip.

## 8. Gap list (current code and shots vs this spec, by user impact)

| # | Today | Change |
|---|---|---|
| 1 | Tidslinje: each ← → press calls `seekTo` (rebuffer per press); the only time-shift cue is a 1.5 s text OSD; nothing persistent says "not live"; no live-edge marker (`seek()`, shot 01) | Accumulate steps, one seek 500 ms after the last press; draw the timeline with the `DIREKT` tick and clamp there; the `−mm:ss` chip after a delay change; OK pauses while the bar has the focus (U11, F18) |
| 2 | OK on video pushes Meny and Kanaler, so Back from Kanaler lands in Meny, two presses to video (`dispatchKeyEvent` OK branch) | Open Kanaler alone; Back closes; ← reaches Meny (navigation rule, 2) |
| 3 | Series with only SVT Play episodes: a season list (34, 33, 15) then 10 dim `ej tillgänglig` rows, focus on a dead row (shots 09, 10) | Seasons inline; unplayable seasons collapsed; filter row; explanation + `Öppna SVT Play`; mark the series dim in search |
| 4 | Raw errors on screen: `Uppspelningsfel: ERROR_CODE_…`, `Kunde inte ladda: <exception>`, `title: <exception>` | Map to section 6 strings; log the detail |
| 5 | Every zap press retunes at once; bursts fetch tickets for channels the user skips | Coalesce (3.3); prefetch ±1 tickets if TTL allows; measure with `zap-bench.sh` |
| 6 | Now/next refreshes every 5 min, so NU can be up to 5 min stale at boundaries | Refresh at the earliest programme end; cache to disk for cold start |
| 7 | Tablå NU row: OK starts start-over when available, else tunes; hint says `OK Titta` either way | NU row = watch live; separate `Börja om från 20:00` row |
| 8 | Back from catch-up tunes live and drops the user's place in Tablå/Serie | Live under the reopened source list, focus on the played item; long-press Back = live only |
| 9 | No previous channel; 0–9 and LAST_CHANNEL unhandled; Guide opens the root menu, not the guide | Alternativ sheet with `Föregående kanal`; number entry; Guide → Tablå |
| 10 | Search uses the system IME, which on Google TV only says `Använd tangentbordet på din mobila enhet`; no completions, no recent searches, no voice; no `Inga träffar`, no 2-letter hint, failures as toast, results ungrouped, channels and live programmes not searchable | In-app alphabetical keyboard, local completions, recent searches, `sv-SE` voice key, `ACTION_SEARCH` hand-off, grouped results (5.1, 5.2, 7.7) |
| 11 | `Logga ut` sits in the root list one press from `Bildformat`, no confirmation | Move to Inställningar with confirm; Bildformat to Alternativ and Inställningar |
| 12 | Panel titles truncate (`Hundra procent bonde · S…`, shot 10); guide rows repeat the full date on every row (shot 05) | Eyebrow + title; day eyebrow rows in Tablå |
| 13 | Login code expiry asks the user to restart the app | Fetch a new code automatically |
| 14 | Loading pushes an empty screen whose hint says `Laddar …`; failure appends a dead row | Cached-first rendering; `Kunde inte hämta listan · OK Försök igen` row that retries |
| 15 | Missing: Favoriter (channels), Appar | Build per 7.5, 7.9. Fortsätt titta, Min lista and Nästa avsnitt are built (F15, 7.13); Inställningar exists |

## Sources

1. Google, "Meet the Chromecast Voice Remote", support.google.com/chromecast/answer/10116742 (undated; read 2026-10-15).
2. Android Developers, "Navigation on TV", developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv (updated 2025-05-09): Back to previous destination, no exit gating, Home always to launcher, long-press Select for more options.
3. Android Developers, "Manage TV controllers", developer.android.com/training/tv/get-started/controllers (read 2026-10-15): operable with up/down/left/right/select/Back/Home; "The Back button must never act as a toggle".
4. "Zap time", en.wikipedia.org/wiki/Zap_time, and Wiley, *IPTV Delivery Networks* (2018), ch. 6: zapping under 2 s for acceptable QoE; informitv (2008-10-13): average about 2 s across 30 operators, 0.6 s on Mediaroom.
5. J. Nielsen, *Usability Engineering* (1993), response-time limits 0.1 / 1 / 10 s; Doherty & Thadani, IBM (1982), 400 ms, via lawsofux.com/doherty-threshold (read 2026-10-15).
6. Samsung Developers, "Web App Memory Optimization Guide" (read 2026-10-15): one app's memory use slows app switching and system animation on the whole TV.
7. Netflix TV UI on low-end devices: animations dropped when the platform is busy (J. Husain, Frontend Masters, "Advanced Asynchronous JavaScript"; M. Seeley, HTML5 Dev Conf 2011).
8. Android Developers, "On-screen keyboard and text input" (updated 2026-09-03): Gboard on TV and its speech-to-text. On the owner's Google TV it shows only a phone prompt, hence the in-app keyboard.
9. Apple, Human Interface Guidelines, "Focus and selection" (tvOS); the page did not render for fetching on 2026-10-15, so the rules cited (remember focus, never move focus without user input) are from the published guidance as previously known, not re-read.
10. Android Developers, "Declaring package visibility needs" (read 2026-10-15): `<queries>` for apps targeting API 30+.
11. Google TV Live tab refresh, droid-life.com (2023-04-11) and 9to5google.com YouTube TV guide redesign (2023-08-21): favourites pinned at the top, Recents category.
12. Channels DVR community, "Quick Guide – Recent Previous Channels" and "Last Channel" threads (community.getchannels.com, read 2026-10-15): last-channel key, Menu/Guide toggles the in-player guide, up/down zaps in the player.
13. Allente Android release notes via apkmirror (read 2026-10-15): 7-day guide, favourites, start-over from the guide.
14. R. Wicklin, "Interfaces for entering text with a remote control", SAS Blogs (2018-09-12): 1,000 film titles; Netflix 7×6 alphabetical grid 50.9 presses per title (3.32 per character), Amazon 3×11 QWERTY grid 61.4 (4.01).
15. Android Developers, `RecognizerIntent` reference (read 2026-10-15): `ACTION_RECOGNIZE_SPEECH` returns results only via `startActivityForResult` or a PendingIntent; catch `ActivityNotFoundException` when no recognizer is installed.
16. Android Developers, "Make TV apps searchable" / in-app search, developer.android.com/training/tv/discovery/in-app-search (read 2026-10-15): system search reaches apps through the searchable configuration and `ACTION_SEARCH`.
17. Android Developers, "Watch Next guidelines for app developers", developer.android.com/training/tv/discovery/guidelines-app-developers (updated 2026-09-03), and "Add programs to Watch Next" (updated 2026-10-02):
    - A title counts as started after 2 min (a film also at 3 %), and as finished when the credits start or when less than 3 min of an episode is left.
    - A title goes to Watch Next when the user exits or pauses for 5 min.
    - Watch Next keeps one entry per series and points it at the next episode.
    - A film leaves the row once it is finished.
18. SVT, "'Fortsätt titta' på SVT Play", svt.se/kontakt/ta-bort-program-fran-fortsatt-titta-pa-svt-play (undated, read 2026-10-15):
    - A title is added on play and removed once it is watched to the end.
    - The row holds about 24 titles.
    - On Android TV, a long press shows "ta bort från fortsätt titta".
19. Netflix Help, "How to remove titles from the Continue Watching row", help.netflix.com/en/node/115312 (undated, read 2026-10-15). A title is removed from the detail page; playback resumes silently. Not verified: a "Start over" button on the title page (common pattern, no help page found).
20. Netflix Help, "Are you still watching", help.netflix.com/node/114059 (quoted from search results, page not opened): Netflix asks after 3 episodes or 90 min with no interaction. Netflix's countdown length now is unverified (15 s at launch in 2014, MacRumors 2014-07-21); 10 s is our choice.
21. Google TV Help, "Watchlist", support.google.com/googletv/answer/10059391 (read 2026-10-15):
    - Long press Select on a tile to add it to the watchlist.
    - On the detail page, the button is a toggle whose label shows the current state ("Watchlisted").
22. Android Headlines (2021-07), "Google TV now lets you remove titles from the Continue watching row": a long press offers "Hide". Telia Play, Tele2 Play and TV4 Play: no public help pages on these features were found.
