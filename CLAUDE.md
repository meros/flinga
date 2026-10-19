# Flinga

A thin Android TV client for Telia Play (Sweden), built from a static analysis of the official app 9.23.2.

- **Read `REQUIREMENTS.md` first.** It lists every feature and requirement. Update its status column in the same
  change as the code. Keep the UX coherent across the whole set; rebuild the UX when a new feature does not fit.
- `client/` is the app (Gradle, Java 17, platform Views + Media3). Build env: `nix-shell shell.nix` from this directory.
- `client/design/DESIGN.md` is the design system; `se.lite.play.ui` implements it.
- `apk/` holds the official APK and the jadx output. It is untrusted input: read it, never run it.
- Iterate offline first (fixtures + Robolectric screenshots, see `README.md`). Use the TV (adb) only for
  playback, DRM and performance.
- Benchmark channel changes with `zap-bench.sh` (key press to first decoded picture).
