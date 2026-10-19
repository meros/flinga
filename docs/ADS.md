# Skipping ads (parked 2026-10-16)

Owner idea: skip commercials automatically in non-live playback. Parked by the owner ("skippa den här idén for now").

## Findings (TCL TV, probe build on worktree branch `worktree-agent-ae4df9004e2e02afa`, commit 21ce502, logging only)

- **Catch-up / start-over / recordings of linear channels** (Ensi-ilta S8E10 from the guide; Reza och Katti i Stockholm S32E1 from a `recordings/…/manifest.mpd`): ticket `adType = NONE`, `timeLine = []`, `playbackRestrictions` all false, one DASH period, no event streams, no timed metadata, `isPlayingAd` never true. The commercials are part of the recording. **No signal exists** in the stream or the ticket.
- **Live**: dynamic DASH, 61 s window, no event streams, no metadata during an ad break. No signal.
- **Play titles with server-side ads**: the official app (jadx) knows `adType` `YOSPACE` (live) and `YOSPACE_VOD`, Yospace timed metadata `urn:yospace:a:id3:2016`, and the ticket `timeLine` (credits, recap only). Not measured: no title with that adType was found in the test. The official app blocks fast-forward through its own `playbackRestrictions` flags and breaks it plays itself; Flinga ignores both.
- **Open idea**: detect breaks from audio (silence runs and level shifts) in catch-up. Untested; PCM access on the TV is unknown (passthrough may hide it). Reference point recorded: a break near 15:22 into Reza och Katti S32E1.

## Not done

No skipping code exists in Flinga. The probe branch is not merged. Nothing here is a requirement.
