# Ideas (brainstorm 2026-10-16)

Not requirements. Move an idea to REQUIREMENTS.md when the owner picks it. Feasibility against Telia's API is not checked unless it says so.

## Picked

- **Audio and subtitle selection** (owner: "Lägg till ljud och undertext"). In progress, see REQUIREMENTS.
- **Android TV integration** (owner: "Gör en state-of-the-art Android TV integration"): Watch Next, deep links, voice search, MediaSession, frame-rate matching, lifecycle. In progress.

## Proposed first (not yet picked)

1. **Skip intro and recap — built, 2026-10-17.** The ticket's `timeLine` is parsed (`Api.parseTimeline`, OPENING_CREDITS/CLOSING_CREDITS/RECAP with offset+duration) and a `Hoppa över intro`/`Hoppa över recap` pill shows while the playhead is inside the span (after Från början, before Direkt); OK jumps past it. Every ticket measured so far carries an empty `timeLine`, so the pill has never shown yet; a series episode on the TV settles whether Telia fills it (the log line `timeLine of …` fires on the first non-empty one).
2. **Favourite channels, hidden channels, own order.** Local setting, no Telia call. The list has over 100 channels.
3. **Direct channel entry with digits** (type 4 or 12 on the remote). Check first whether it already exists.
4. **Rewind/ffw across programme boundaries** (owner, 2026-10-17: "rewind/ffw to next/prev program as well, not only within one program"). **Built this session, hard boundaries**: ← past a programme's start jumps into the previous programme's catch-up near its end (where the playhead was heading); → past a catch-up's end jumps into the next programme (catch-up from its start, start-over when on air with start-over); a neighbour without an option is a hard boundary: the seek stops at the edge and the overlay says "Går inte att spola vidare här" once. Back cancels a pending jump. The neighbours come from the channel's guide (the one cache, X3); `BoundaryJumpTest` drives the real scrub/commit paths through a `SimpleBasePlayer` stub. Not yet on the TV.
5. **Programme metadata the official app reads and Flinga doesn't**: the official app's media/series queries carry `description`, `genre`, `yearProduction`, `ageRating`, `soundQuality` (Dolby), `imdbRating`, `originalAirDate`, `availability { to }`, `directors`, `actors`, `provider { name icons }` and `series { numberOfEpisodes numberOfSeasons }` (jadx `p247li/A2.java`, `getMobileSeries`). Flinga reads title, images, duration, progress. The official Android TV app's EPG row (`EpgMedia`, `p390se/tv4/shared/androidtv/timeline/epg/...`) also carries `recordingsType` (the PVR signal), `isSeriesRecordable` and `cdpCapabilities` — the on-screen record button's data. Feasibility unchecked; the guide query could ask for `description` and `genre` cheaply.
6. **Recordings (NPVR) — partially built, 2026-10-17.** `play.npvr { item startover }` is asked for and parsed as a catch-up fallback (a recording plays like one); the Serie query reads `isRecordable`, and a `Spela in` row in the series' Alternativ sheet runs `scheduleRecording2` (SERIES type). Untested against the server: no fixture records a recording, so both paths fire only on the TV. Still open: a recordings list query (none found in the TV app's own set) and `userData.npvrInfo { episodes { scheduled recorded } }` for the recorded state.
7. **Two streams at once (PiP) — idea, 2026-10-17, not built.** A second live stream as a corner chip: browse without losing the picture, live previews in the grid, a visible handover when a programme switches, real two-tuner watching. Feasibility: player side yes (Media3 multi-instance + separate SurfaceViews compose fine); decoder side two 1080p streams is the realistic ceiling on the CCwGTV, not a tile wall; server side unknown — the subscription's concurrent-stream limit is the deciding fact. The official app's PiP code is mobile-only, so this would be unique in its class. Three probes settle it: (1) a second live ticket on the same subscription — accepted or refused; (2) two concurrent decoders — codec init ok; (3) catch-up while live plays — do they count as two streams. UX cost is zero surfaces: the chip is a fourth state of the strip; the clean-room model (docs/UX-CLEANROOM.md) absorbs it unchanged.

## Later

- Sleep timer ("Stäng av om 30/60 min").
- Guide: jump one day forward or back, shortcut to 20:00.
- Type in Sök from the phone (a small web page on the LAN).
- Reminder "Sänds om 5 min" for a marked programme.
- Recordings: one stream was served from `recordings/…`; there may be a recordings list at Telia that Flinga does not read.
- Diagnostics view (version, buffer, bitrate) behind a long Back.
- Bitrate cap for a weak connection.
- In-place update for the family's other TVs (today the app is installed with adb).
- Preview channel "Direkt nu" on Android TV home (only if cheap); Engage SDK for Google TV.

## Parked by the owner

- Skip ads: `docs/ADS.md`.
- Launcher / app start order: `docs/LAUNCHER.md`.
