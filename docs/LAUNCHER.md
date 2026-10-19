# Telia Lite as launcher: parked

Status: **parked by the owner on 2026-10-15.** Build it later. Requirement F10 in `REQUIREMENTS.md`.

Goal: open the other installed apps (Netflix, Disney+, SVT Play, YouTube, Spotify, Plex, Prime Video) from Telia
Lite, and optionally make Telia Lite the home screen of the Chromecast with Google TV (sabrina, Android TV OS 14).

## Recommendation

Do **A** first, then **B** as a reversible test. Use **C** only if B does not take effect on sabrina.

| Option | What the owner gets | Work | Risk |
|---|---|---|---|
| **A. "Appar" row in Telia Lite** | Other apps with their banners, launched from the menu. Home still opens Google TV. | About 1 day, 150–200 lines | None |
| **B. Telia Lite holds the HOME role** | The Home key opens Telia Lite | 1–2 days more (60–100 lines) | Low. Unverified whether Google TV keeps the role; test first |
| **C. Disable the stock launcher with adb** | What FLauncher and Projectivy users do on this device | adb commands only | The YouTube remote button stops working; recovery needs adb; soft lock if Telia Lite fails at boot |

## A. "Appar" row (no launcher change)

- **Manifest:** add `<queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent></queries>`.
  Do not use `QUERY_ALL_PACKAGES`. Package visibility applies, because the target SDK is 35.
- **List:** `pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)`.
- **Banners:** `ActivityInfo.loadBanner(pm)`, falling back to `loadIcon(pm)`. Load them on the io executor, scale to about
  160×90 and cache. A dozen banners cost a few MB.
- **Launch:** `pm.getLeanbackLaunchIntentForPackage(pkg)` with `FLAG_ACTIVITY_NEW_TASK`.
- **Refresh:** reload the list in `onStart`, or on PACKAGE_ADDED/REMOVED from a receiver registered at runtime.
- **Order and visibility are configurable (owner requirement).** The user can move each app (to the top, up,
  down) and hide or show it. Store the order and the hidden set by package name in SharedPreferences. A new
  app appears at the end and is visible. A removed app drops out; keep its saved position in case it returns.
  Hidden apps stay reachable in a `Dolda appar` row at the end of the list, so nothing is ever lost.
- **Code:**
  - new `se.lite.play.Apps` (about 80 lines)
  - an "Appar" item in the menu, opening a screen of app rows
  - a banner variant of `RowView` (about 40 lines)
  - fake `ResolveInfo`s in the Robolectric harness
- **Follow `client/design/UX.md`** for where "Appar" sits in the information architecture.

## B. HOME role (reversible)

- Add a disabled-by-default `<activity-alias android:name=".Home" android:targetActivity=".MainActivity">` with
  MAIN + HOME + DEFAULT.
- Add a menu toggle "Använd som startskärm" that calls `setComponentEnabledSetting`.
- Set the role with `adb shell cmd role add-role-holder android.app.role.HOME se.lite.play`, or through the chooser.
  Google TV has no home-app setting in its UI.
- **Keys when Telia Lite is home:**
  - Home arrives as `onNewIntent` with CATEGORY_HOME (the activity is `singleTask`). Use it to close the menu and return to live TV.
  - Back at the root must do nothing. Today it falls through to `super` and would exit to an empty screen.
  - Do not swallow `KEYCODE_SEARCH`; the Assistant key must reach the system. Today `MainActivity` swallows it.
- **Boot:** a home app starts at boot, possibly before the network is up. Login and the channel list must handle
  "no network yet".
- **Settings:** add a row that fires `Settings.ACTION_SETTINGS`.

## C. Disable the stock launcher (last resort)

From the FLauncher README; still the reported method in March 2026:

```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith   # otherwise re-enables launcherx
```

Undo:

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

- **Never** `pm uninstall` either package.
- Before you disable anything, confirm that `adb connect` works.
- Last resort: a factory reset with the button on the back of the Chromecast. It also re-enables the stock launcher.
- An OTA update can undo B or C. In March 2026 a firmware update silently dropped Projectivy's override.

## Limits

- **No "continue watching" from other apps.** AOSP TvProvider limits Watch Next queries to the caller's own package
  unless the caller holds `ACCESS_ALL_EPG_DATA`, which is `signature|privileged`.
- **Deep links are per app and unofficial.**
  - Netflix: `netflix://title/<id>`.
  - YouTube: probably a VIEW of `https://www.youtube.com/watch?v=…`, not confirmed.
  - SVT Play: read the package name from `pm list packages`.
  - For a first version, open each app's front page.
- **Memory gain unknown.** No published RAM figure for launcherx on sabrina exists. Measure
  `adb shell dumpsys meminfo com.google.android.apps.tv.launcherx` and the totals before and after C. Expect tens to
  low hundreds of MB. The system already kills a background launcher under memory pressure.

## Verify on the device

1. **A:** every installed app appears with a banner, launches, and Back returns to Telia Lite.
2. **B:** whether the role (or the chooser) makes Home open Telia Lite, after a reboot and after a day of use.
3. **C:** whether the YouTube button, Assistant, the notification panel, ambient mode and Settings still work.
4. Memory and start time, before and after.
5. Telia Lite's behaviour when the network is missing at boot.

## Sources

- Android TV app setup, updated 2026-09-28: https://developer.android.com/training/tv/get-started/create
- Package visibility, updated 2026-10-01: https://developer.android.com/training/package-visibility/declaring
- Watch Next, updated 2026-10-02: https://developer.android.com/training/tv/discovery/watch-next-add-programs
- AOSP TvProvider, main branch, read 2026-10-15: https://android.googlesource.com/platform/packages/providers/TvProvider/
- FLauncher README: https://github.com/svrooij/flauncher
- Phonandroid, 2026-03-17: https://www.phonandroid.com/?p=2687694
- Google TV customization notes, 2025-04-05: https://til.arktronic.com/2025/04/05/google-tv-customization.html
- Notebookcheck on launcher replacement: https://www.notebookcheck.net/The-Google-TV-launcher-is-mess-of-adverts-but-this-replacement-is-a-clean-beautiful-ad-free-alternative.1018849.0.html
- 9to5Google, 2022-08-22: https://9to5google.com/2022/08/22/chromecast-google-tv-performance-storage-update/
