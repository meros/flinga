# UX map: Flinga as built

Every screen, button and flow, read from the code (MainActivity, OsdView, GuideGridView) on
2026-10-17. This is the inventory the clean-room reimagining (`UX-CLEANROOM.md`) starts from.

## 1. Surfaces (everything that can be on screen)

| Surface | What it is | Reached from |
|---|---|---|
| Video | The playing stream, no chrome | start, Back from anything |
| Overlay (light) | Bottom bar: eyebrow, title, programme track with playhead and DIREKT tick; passive or scrubbing | every key on video, fades 4 s |
| Overlay (full) | Light + one pill row (Paus/Spela · Från början · Hoppa över · Direkt · Avsnitt · Ljud och text) | OK on video |
| Grid (Tablå) | Full-screen guide: channel rows × time columns, now-line | rail Tablå, Guide key, held OK Visa i tablå |
| Panel + rail | Left rail (Kanaler, Tablå, Sök, Min lista, Inställningar) + content list | Menu key, ← from video/lists |
| Sheet (Alternativ) | Modal list over whatever is up | held OK on any row |
| Sheet (Nästa avsnitt) | Countdown card when an episode ends | automatic |
| Sheet (Ljud och text) | Audio/subtitle picker | pill |
| Sheet (Logga ut) | Confirm card | Inställningar |
| Login | QR + code | no token |
| Snack | Toast line | errors, notices |

Stack rules: each rail section keeps its own stack (root + at most one detail); a sheet sits on
top and Back pops one level; Back on video only hides overlays and never jumps to live.

## 2. The pill row (overlay, interactive)

One canonical order: Paus/Spela · Från början HH:MM · Hoppa över intro/recap · Direkt ·
Avsnitt · Ljud och text. Each pill appears only when it applies (no empty modes):
- Paus: always except a live channel without start-over
- Från början: a programme with start-over, catch-up, VOD
- Hoppa över: only while the playhead is inside the ticket's intro/recap span
- Direkt: only behind live or in catch-up
- Avsnitt: episodes only
- Ljud och text: only with a choice to make

## 3. Key map (state × key)

| State | ↑/↓ | ←/→ | OK | Back | Menu | Guide | Other |
|---|---|---|---|---|---|---|---|
| Video, clean | banner | scrub ±30 s | open full overlay | exit app | rail | grid | CH±/PREV zap |
| Overlay passive | — | — | → full | hide | rail | grid | media keys = scrub |
| Overlay full | pill ↔ bar | pill move / bar scrub | run pill | hide | rail | grid | |
| Grid | channel row | programme, window follows | play | close | — | close | ↑ the tabs |
| Rail | section | — | open | close panel | — | — | |
| List | row | pills/keyboard | select | out one | rail | — | held OK = sheet |
| Sheet | row | — | select | close | — | — | |

On video: ← crosses the programme's start into the previous one's catch-up near its end; → past
a catch-up's end into the next one; a neighbour without an option holds the seek at the edge
("Går inte att spola vidare här"). One boundary per scrub; Back cancels a pending jump.

## 4. Flows (the walks)

1. Start → login (QR) → channels load → live on the last channel.
2. Live: ↑↓ zap (bursts coalesce); ← rewinds into start-over; OK opens pills; ← on the first
   pill opens the rail; Guide opens the grid.
3. Catch-up: grid → past block → OK plays from its start; the programme ends → the next on the
   channel follows (F14), still delayed, until caught up; then live.
4. Series: Sök → text/voice → Serie page (hero, season chips, episodes) → episode; progress is
   saved and Watch Next rows are written to the launcher.
5. Delayed place: zapping away keeps the content moment 30 min; zapping back resumes it.

## 5. Distilled feature set (what the product IS)

1. Watch live TV, change channel instantly.
2. Watch what is on in catch-up or start-over; continue to the next programme.
3. Find and play anything in the catalogue; series browsed by season.
4. Resume where you stopped, on any device path (Fortsätt titta, Watch Next).
5. Choose audio and subtitles.
6. See the schedule for every channel at once.

Everything else (Min lista, Inställningar, Appar, logout) is support, not product.

## 6. Where the model strains (the clean-room inputs)

- **Depth**: rail → section → detail → sheet is up to four levels; the rail duplicates the
  Menu key, Kanaler duplicates the grid's rows, Tablå in the rail is one hint line.
- **Two overlays + three sheets + a panel** on top of video: five chrome surfaces above one
  stream. Each is honest, but the sum needs Back-as-undo rules (U1) to stay coherent.
- **Key asymmetries**: ← scrubs on video but opens the rail on lists; OK opens pills on video
  but selects on lists; the Guide key only opens/closes the grid.
- **Mode-ish edges**: the overlay's light/full distinction (A2), the sheet as "a question, not
  a place", and the grid being its own full-screen surface while the panel also exists.
- **The delayed-continue (F14) and boundary jumps interact**: a jump lands near a stream's
  end, the stream ends, the auto-continue bounces the viewer forward (fixed 2026-10-17 with a
  20 s hold-off; the interaction is still the model's most delicate joint).
