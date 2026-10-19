# Flinga: what only the TV can verify

The Robolectric harness renders every surface and runs the checks (E5, C1–C5, E6 stress replay) on each
shot, but it has no player, no network and no compositor. The items below were designed and reviewed on
shots only and need one session on the Chromecast (2020, 2 GB) before the build counts as done. Tick each
line with the date; a failure goes to a POLISH.md row with the shot or clip that shows it.

## The player overlay (U11, A1-A5) and Pause on live (F18)

- [ ] **A1, the bar's numbers.** No wall clock, no range at the title's right, no "N min kvar", no "−1:22:15"
      text. The programme's start sits left of the bar, its end right of it, the picture's own time under the
      playhead (06:48), DIREKT above the live tick. All in one size, readable from the sofa (3 m); the
      numbers do not touch the bar and the playhead's label does not touch the end labels at either end.
      On an episode: `0:00`, the total, the elapsed time.
- [ ] **A2, passive overlay.** A zap, `Från början`, a Tablå start, a start from Sök and `Fortsätt titta` each
      show logo or name, title and bar for 4 s from the first picture (never longer than 15 s while it loads),
      with no pills and no focus; OK or ← → turn that very overlay interactive. A catch-up shows its channel's
      logo and the programme's times, no `SE I EFTERHAND` banner.
- [ ] **A4, the rail's peek.** OK on live video opens the overlay with the first pill focused (`Paus` on a
      programme that can be rewound into) and the five rail icons show dimmed at the left edge with `‹ Meny`;
      the strip ends above the overlay's logo and the dimmed icons are legible over a bright picture. ← on the
      first pill opens the rail on Kanaler, its icons in the same column; Back from it lands on the video (one
      step, U1). On a channel without start-over the row is empty, the peek is still there and OK opens the
      rail. While scrubbing (← → from clean video) there is no peek and ← still rewinds.
- [ ] ← → from clean video open the overlay on the bar and the first press already steps 30 s; ↓ moves to
      the pills, ↑ back; ↑ ↓ never zap while it is up; Back hides it and playback stays.
- [ ] The overlay hides 5 s after the last key and never while paused.
- [ ] **A3, Direkt.** After a rewind (the live window or the start-over stream) and in a catch-up from Tablå
      the row has `Direkt` after `Från början`; OK tunes the channel live. On clean live video there is none.
- [ ] **F18:** `Paus` on live switches to the start-over stream within one segment (3.84 s) of where live
      was, the picture freezes with no flash of the live window, the pill reads `Spela`, and `Spela`
      continues delayed (the playhead's time and the gap to the DIREKT tick show the pause length). → to the
      edge returns to live. The play/pause media key does the same; on a channel without start-over it does
      nothing and the overlay says `Går inte att pausa`.
- [ ] **A5.** Held OK on live video, on an episode and on a film does what OK does (the overlay on the first
      pill); no sheet opens. `Föregående kanal` is the first row of Kanaler after a zap and OK on it flips
      back (a place that channel kept is resumed). `Bildformat` in Inställningar toggles `Hela bilden` and
      `Fyll skärmen`, takes effect on the picture at once and is still set after the app is closed and opened
      (verify a 4:3 stream if one turns up; none was found).
- [ ] On an episode, `Avsnitt` opens the series surface with the playing episode's row selected.
- [ ] `Från början` on an on-demand title seeks to 0 and the bar shows `0:00` under the playhead; on
      start-over it restarts the programme at its broadcast start.
- [ ] The pills never shift the bar above them, and the eyebrow, title, bar and pills sit at the same
      place for live, behind live, catch-up and an episode (compare four frames).
- [ ] **B8.** No screen names the app (the login screen's eyebrow reads `TELIA PLAY`, the logout dialog
      `Logga ut?`); the launcher still shows the name and icon.

## Ljud och text (F19)

The track lists in the harness are fabricated. Only the TV shows what Telia's streams carry.

- [ ] **What the streams report.** Play SVT1, TV4, a catch-up, an episode and a film. For each, `adb logcat | grep
      TeliaLite` and the pill tell: more than one audio language? an audio description (`Syntolkning`)? text tracks
      (and in which format: TTML, WebVTT, `stpp`, picture subtitles)? Write the answers here. If a channel has no text
      track the pill must be absent (U0); if the stream has only a second audio track the sheet has only `Ljud`.
- [ ] **The pill and the sheet.** `Ljud och text` is the last pill; OK opens the sheet; a pick changes the sound or the
      text within about a second without a rebuffer; Back returns the focus to the pill.
- [ ] **Audio description.** It never plays on its own (check the stream that lists it first), and picking `Syntolkning`
      plays it. After a zap the session's pick still holds when the next channel has the same track.
- [ ] **Automatisk.** English audio with Swedish subtitles available: Swedish text shows from the first frame or within a
      second, with no flash of another language. Swedish audio: no text.
- [ ] **Alltid på, Av.** `Undertext` in Inställningar changes it at once, also while a stream plays.
- [ ] **Remembered language.** Pick English audio in the sheet, close the app, open another channel with both: English
      plays, `Ljudspråk` reads `Engelska`.
- [ ] **Picture subtitles (bitmap cues).** The caption box draws text cues only. If a stream carries DVB or PGS
      subtitles nothing shows: report the channel here (a bitmap renderer is not built).
- [ ] **Caption look.** Set the system caption style (Settings, Accessibility, Captions: a size, a colour, an edge) and
      reopen the app: the style is used. With the stock style the box is white on semi-transparent black, about 5.5 % of
      the picture height; read it from 3 m. While the overlay is up the caption stands above its first line (logo) and
      is never under the bar or the pills.
- [ ] **Language names.** `Svenska`, `Engelska` and the rarer ones read right in Swedish on the TV's ICU; a track with no
      language shows the stream's own label or `Ljudspår 2`.

## The system around the player (F20-F23)

None of this runs in the harness. Each line names the command that settles it.

- [ ] **Watch Next permission.** `adb shell dumpsys package se.lite.play | grep -i epg`: is
      `com.android.providers.tv.permission.WRITE_EPG_DATA` listed and `granted=true` after a plain install? If not,
      `adb shell pm grant se.lite.play com.android.providers.tv.permission.WRITE_EPG_DATA` and note it here: the install
      then needs a runtime step and the code (it already stays quiet on a refusal) needs a hint. The permission's name is
      from memory of the TvProvider: the SDK has no constant for it.
- [ ] **Watch Next rows.** Play an episode of a series for 3 min, press Home. `adb shell content query --uri
      content://android.media.tv/watch_next_program` lists the title with `watch_next_type=0` (CONTINUE),
      `last_playback_position_millis`, `duration_millis`, `intent_uri=intent://title/s-…?play=1#Intent;scheme=flinga;…`
      and a `poster_art_uri`. The Android TV home shows it in Play next with the poster, `N min kvar · <source>` and a
      progress bar. Does the recorded poster field exist (rows without a poster mean the panel query's `image { source }`
      returned nothing)?
- [ ] **Update pace.** While an episode plays the row's position moves about once a minute, never more often
      (`adb logcat | grep "watch next"` shows no storm); pausing and Home update it at once.
- [ ] **Finished and removed.** Finish a film (or watch an episode to its end: the series row turns NEXT, `watch_next_type=1`);
      remove a title with `Ta bort från Fortsätt titta`; Logga ut. Each leaves or empties the row at the next sync.
- [ ] **Launching from the home screen.** OK on the row opens the app on that title and it resumes where it stopped (the
      silent resume of Fortsätt titta), with the app cold and with it already running (then the same playing session is not
      restarted for a link to what it plays: no black frame).
- [ ] **Links by hand.** `adb shell am start -a android.intent.action.VIEW -d "flinga://channel/<id>"` (an id from the
      logcat of a tune), `-d flinga://guide`, `-d "flinga://search?q=bonde"`, `-d flinga://title/<series id>`,
      `-d flinga://nowhere` (nothing changes), each cold and with the app running; running, `adb shell dumpsys activity
      activities | grep se.lite.play` shows one `MainActivity` record (singleTask) before and after.
- [ ] **The Assistant.** "Sök efter Bron" opens Sök with `Bron` run. "Spela SVT1" tunes the channel without Sök. "Spela
      <a film's exact title>" plays; "spela <a title that two results share>" leaves Sök with the results. Note what the
      Assistant actually sends (`adb logcat | grep -i "MEDIA_PLAY_FROM_SEARCH\|TeliaLite"`): its extras can differ from the
      query extra the code reads.
- [ ] **Media session.** While something plays `adb shell dumpsys media_session` lists `se.lite.play/TeliaLite` active with
      the title, the channel as artist, state PLAYING and the actions; paused: state PAUSED. After Home it is inactive. The
      Assistant's "pausa", "spela", "spola framåt 30 sekunder" and a remote's play/pause key do what the Paus pill and → do;
      the TV's now-playing card (if it has one) shows the title and the channel logo.
- [ ] **Audio focus.** Start music in another app (or `adb shell cmd media_session dispatch pause`-style key events) while
      a channel plays: the picture pauses and the pill reads Spela; ending the music resumes it for a transient loss. With
      Bluetooth headphones, switch them off: the picture pauses (becoming noisy).
- [ ] **The screen.** While playing the TV never dims (`adb shell dumpsys window | grep -i keep_screen`: the flag set); paused
      for the screensaver timeout (Settings, Display) the screensaver starts; Home and back: playing again keeps it on.
- [ ] **Home.** Home while a channel plays: the sound stops, the session ends; opening the app again re-tunes the live channel
      (a stale stream is never resumed) and an episode resumes paused-or-playing as it was.
- [ ] **Frame rate.** Play a 25 fps channel (`SVT1`): `adb shell dumpsys display | grep -i "mode\|refresh"` and
      `adb shell dumpsys SurfaceFlinger | grep -i "refresh\|frame rate"` show 50 Hz (or 25) while it plays, and 60 Hz again
      in the menu, with the TV's Match content frame rate set to Seamless or Non-seamless; with Never it must stay put. If it
      never switches, the stream may carry no frame rate in its manifest (check `Format.frameRate` in the log) or the TV
      needs non-seamless switching, which ONLY_IF_SEAMLESS never asks for (Media3 1.5.1 has no ALWAYS strategy): then a
      setting would not help without a library change.
- [ ] **Launcher.** The banner shows in the Android TV launcher's app row, sharp at 320 x 180 dp; the app is in the TV's
      launcher and not offered on a phone as a touch app (`uses-feature` leanback not required, touchscreen not required).

## Banner timing and the first picture

- [ ] The passive overlay holds until the first picture, then counts 4 s; a slow tune is never a black screen.
- [ ] The failed-tune banner on a channel outside the subscription: `Kanalen gick inte att starta (licens).`
      in the error colour on the bar's line; ↑ ↓ still zap.
- [ ] `Programmet gick inte att starta.` when a ticket fails for catch-up or on-demand.
- [ ] The catch-up and on-demand overlay shows the bar from its first frame (span from Tablå or Telia's
      duration) and nothing moves when the player's own position arrives.

## Time-shift and F14

- [ ] The timeline during a real start-over; no grey offset chip top right at any time (owner: removed), the bar's
      labels (start, end, wall clock under the playhead, DIREKT) say where you are.
- [ ] F14: when a programme watched behind live ends, the next programme on the channel continues in the
      same delayed mode; its banner looks like a zap; no toast.

## F17: a channel left behind live keeps its place

- [ ] **Seek accuracy.** Rewind SVT1 a few minutes (note the picture and the bar's `−mm:ss`), ↑, wait 20 s,
      ↓. Pass: the picture continues within one segment (3.84 s) of where it left, and the bar reads the old
      delay plus the time away (`−12:30` → about `−12:50`), never the old delay. `adb logcat | grep place`
      prints `place kept … at m:ss` and `place resumed … at m:ss` with the same position.
- [ ] **Off the live window.** On a channel whose stream has a real time-shift window (if any; SVT1 has only
      a 61 s buffer), rewind inside the window without reaching start-over, zap away and back. Pass: the same
      content moment resumes on the start-over stream (the window start plus the player position equals the
      broadcast moment; `windowStartTimeMs` must be set in the manifest).
- [ ] **Zap speed.** ↑ from a rewound channel is as fast as from live (`zap-bench.sh`, within the F2 numbers);
      the next banner is the ordinary one: nothing says that a place waits (no `väntar på`, no `tillbaka`).
- [ ] **Expiry on the device.** Leave a rewound channel for 31 min (or until its programme ends), zap back:
      live, silently: no line on the banner or the bar.
- [ ] **A title never zaps.** On an episode, ↑ and ↓ show its banner (the bar at the real position) and the
      channel does not change; with the controls up they stay up.

## Lists on the device

- [ ] The bottom fade and the top fade at the end of a list (Robolectric draws them, the device composites).
- [ ] A burst of ↓ on Kanaler: whole rows after every scroll, no half row under the header.
- [ ] Logo artwork at 3 m: the light and dark variants per surface, the silhouette for light-only art on
      the focused card.

## Input that the harness cannot send

- [ ] Voice (Röst) and the system search hand-off.
- [ ] Number entry 0–9 on video: `1_` then `12`, tune after 1.5 s or at once when unique.
- [ ] Held Back from any depth lands on live with nothing on screen.

## Guide grid (B4)

- [ ] **Scrolling.** Hold ↓ through all 27 channels and ← → through a day on the CCwGTV: focus keeps up with key
      repeat (the view redraws once per key, no per-frame work), `dumpsys gfxinfo se.lite.play` janky frames under 5 %.
- [ ] **Readability at 3 m.** 56 dp rows, 16 sp titles, the 30-minute ruler and the now-line; is 7 channels on a page
      right, and is the day label (`TORS 15 OKT`) enough?
- [ ] **OK on blocks.** On air plays live; a past block with catch-up plays from its start and the next programme
      follows (F14); a future block and a past one without catch-up only show their footer; held OK shows the sheet.
- [ ] **Data.** Rows fill in nearest the focus first; a channel without a guide says `Tablå saknas`; watch the
      request count when holding ↓ (about two in flight at a time, each channel once).
- [ ] **A stable time.** ↑ ↓ across channels never moves the time; a block cut by the edge is shown on its visible part with a
      `‹`/`›`; only ← → scroll. Is the `NU` chip easy to find and use: accent when you are away from now, ↑ from the top row,
      OK, and `Gå till nu` in the held-OK sheet?
- [ ] **Leaving.** Back goes to the video, never out of the app; ← at the first column opens the rail on Tablå and → there
      opens the grid again.

## Overlay round 2 (owner findings on the TCL TV)

- [ ] **Scrub without the full overlay.** ← → from clean video: the light overlay with the playhead moving, bottom
      aligned, no pills, no peek, hint `OK Spela härifrån  ↩ Dölj`. Judge whether it is now clear that OK confirms:
      OK plays from the playhead at once and opens no pills; Back puts the playhead back and nothing seeks; left alone
      for 1 s it commits (is 1 s right?). Afterwards the usual 4 s with `OK Visa knappar`, and OK opens the pills.
      Held ← → still accelerates; a paused picture previews and stays paused.
- [ ] **↑ from a pill goes to the seek bar.** From Paus (or any pill) ↑ rings the playhead, the pills stay up; ← →
      scrub, OK commits, Back cancels, ↓ returns to the pill you left. The channel never changes with ↑ ↓.
- [ ] **The margin.** The overlay's logo, title, bar and pills start 96 dp in from the screen's edge, clear of the
      peek strip (48 dp wide) and its `‹ Meny` hint; judge the wider margin at 3 m.
- [ ] **The Back hint** reads `↩ Dölj` in every state (passive, scrubbing, pills, bar with pills); the peek is up whenever
      the pills are, also while you scrub with them up (↑ from a pill), and gone on the light overlay.
- [ ] **The passive overlay is bottom aligned** and shows `OK Visa knappar` in the slot where the pills will stand;
      on a channel without start-over it shows `OK Meny`.
- [ ] **The rail's peek** comes out of the screen's left edge over the full height, its icons at the same height as the
      open rail's, hint `‹ Meny`; nothing makes you want to press ↑; ← on the first pill opens the rail.
- [ ] **Paus/Spela does not blink.** Press OK on the Paus pill several times, on live (start-over) and on an
      episode: only the label and the bar change, the overlay never disappears or fades in again, and it stays
      its 5 s after the last key.
- [ ] **Inget program just nu.** A channel in a gap of the schedule says so, and its › opens a guide that has
      programmes; a channel with an empty guide has no ›.

## Panels (B1-B3, B5, B6)

- [ ] **B5, Serie at 3 m.** The 88 x 50 dp episode thumbnails identify an episode from the sofa; the title,
  meta line and pills sit in the top fifth and five episode rows show under the season chips. A series with
  more than three seasons: only whole chips show, a `›` at the right edge says more wait, ← → scrolls them.
- [ ] **B3, the 48 % panel.** The panel covers the picture's left 58 %; the live picture still reads next to
  Kanaler, Sök and Min lista.
- [ ] **Earlier catch-up** is reachable in the grid: ← through the day (24 h back) and start a past programme.
- [ ] **B1, B2.** No title over Kanaler, Sök, Min lista or Inställningar; the lists start at the top. No channel row
  of Kanaler has a chevron: OK plays, held OK offers `Visa i tablå`, which opens the grid on that channel. Beside the rail,
  Tablå shows only `OK Öppna tablå`.

## F16: immersive series (images)

- [ ] **Decoded image memory.** Open Sök, search `bonde`, scroll every result group, open Bonde söker fru,
  go through every season chip and scroll every episode list. `adb shell dumpsys meminfo se.lite.play`:
  Graphics + Native heap grow by at most about 8 MB over the app with the panel closed, and fall back
  within a few seconds of closing the panel (the image LRU is released). Pass: PSS stays under 70 MB
  (today 50 MB without images).
- [ ] **Decode time.** `adb logcat` while opening a series: the hero (960 x 540 JPEG, RGB_565) decodes on a
  background thread; no main-thread frame over 32 ms when it arrives (Choreographer "Skipped frames"
  absent). Pass: no skipped frames, hero on screen within 1 s on Wi-Fi.
- [ ] **Scroll smoothness.** Hold ↓ through Storage Wars Säsong 13 (11 episodes with thumbnails) and
  through the film search results. Pass: focus keeps up with key repeat, no visible hitch when a
  thumbnail lands, `dumpsys gfxinfo se.lite.play` janky frames under 5 %.
- [ ] **Hero fade and scrim.** The art fades in (120 ms) without flicker; the text on the left stays
  readable from 3 m on the owner's TV (the scrim is 94 % to 42 % of the width, 75 % at the panel edge).
- [ ] **Image proxy from the TV.** Thumbnails and the hero load over the TV's network (the proxy answers
  JPEG to `Accept: image/jpeg`); a request that fails leaves the placeholder, and is not retried for
  10 minutes (`adb logcat | grep "image "`).
- [ ] **Video under the hero.** Live audio keeps playing while Serie covers the picture; Back to Sök shows
  the video again at once.
