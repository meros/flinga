package se.lite.play;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import se.lite.play.ui.KeyboardView;
import se.lite.play.ui.Theme;

/**
 * Drives the real MainActivity on the JVM with recorded fixtures and saves 1920x1080 screenshots.
 *
 *   gradle testDebugUnitTest --tests se.lite.play.Screens                      (all tours)
 *   gradle testDebugUnitTest --tests se.lite.play.Screens -Pscenario='key:DPAD_CENTER;shot:x'
 *
 * Steps, separated by ';':
 *   key:<KEYCODE without prefix>[*n]   press (down + up) n times
 *   hold:<KEYCODE>                     long press: down, 700 ms, up
 *   text:<typed>                       type on the in-app keyboard
 *   wait:<ms>                          let I/O finish and advance the main clock
 *   slow:<ms>                          delay every fixture response by ms (0 = off), to see loading states
 *   slowimg:<ms>                       hold every image response while ms > 0 (0 = release them)
 *   same:<shot>                        the geometry of the last shot equals that of an earlier one (F16:
 *                                      images arriving must not move anything)
 *   end                                the on-demand title playing reaches its end (Nästa avsnitt); R5
 *   shot:<name>                        screenshot, after the checks; R1: fails when identical to an earlier shot
 *   repeat:<name>                      screenshot that may repeat an earlier frame on purpose (R1)
 *   expect:<surface>                   R2: "login", "video", "osd:<mode>" or "panel:<screen name>" is on screen
 *   expectgrid:<channel>|<title>       B4: the guide grid is up on this block (off: closed)
 *   expectgridfooter:<text>            B4: the grid's footer hints contain this
 *   markgrid / expectgridwindow:<same|moved>   3c: ↑ ↓ never move the time window, ← → do
 *   expectsection:<name>               U10: the rail marks this section (Kanaler, Tablå, Sök, Min lista, Inställningar)
 *   expectrail:<name>                  U10: the rail has the focus, on this section
 *   expectfocus:<text>                 R2: the selected row's description contains this (stressed names too)
 *   expecttitle:<text>                 R2: the OSD's title is this
 *   expecthint:<text>                  the footer (panel hint, or the OSD hints on video) contains this
 *   expectnohint:<text>                the footer does not contain this ("Direkt" in a rewound channel, F12c)
 *   expecteyebrow:<text>               the OSD's eyebrow is this
 *   expectplayback:<state>             "live", "delayed" (behind live on a channel) or "ondemand" (F12c)
 *   expectrow:<text>                   a row of the surface shown has this in its title or secondary line
 *   expectorder:<a|b|c>                B6: rows containing a, b, c come in this order
 *   expectepisodes:<n>                 B5: room for at least n whole episode rows below the hero band, chips and all
 *   expectband:<n>                     B5: the Serie hero band is at most n % of the screen height
 *   expectnorow:<text>                 no row of the surface shown has this (R5: the episode that ended)
 *   expectstatus:<text>                the overlay's status line contains this
 *   markosd / expectosdkept            blink: the overlay did not hide or fade in again in between, stays opaque and focused
 *   expectscrub                        U11: scrubbing on the light overlay (bar focus, no pills, no peek, hint "OK Spela härifrån ↩ Dölj")
 *   expectpending:<yes|no>             U11: a scrub has a seek waiting to commit
 *   expectbarpills                     U11: ↑ from a pill: the bar has the focus, the pills and the peek stay
 *   mark:<name>                        F17: remember the broadcast moment playback is at (fails when unknown)
 *   expectmark:<name>                  F17: playback is at the moment remembered under this name, exactly
 *   clock:<ms>                         move the app's clock forward by ms (a place expires after 30 min)
 *   pill:<label>                       U11: from clean video OK, → to the pill that starts with <label>, OK (real keys)
 *   menu:<section> / rail:<section>    A4: from video OK, ← on the first pill (the rail opens on Kanaler), ↓ to <section>;
 *                                      menu then → into its content, rail stays on the rail (real keys)
 *   expectpills:<a,b,...>              U11: the overlay's row is exactly these pills (each given as a label prefix)
 *   expectnopills                      U11: the overlay's row is empty (a channel without start-over)
 *   expectpill:<label> / expectnopill  U11: a pill that starts with <label> is / is not in the row
 *   expectpassive                      A2: the overlay is up with no focus, draws no pills and no rail peek
 *   expectpeek:<yes|no>                A4: the rail's peek is on screen (only while the pills have the focus)
 *   expectformat:<contain|cover>       A5: Bildformat is this, in the picture and in the saved setting
 *   expecttext:<text>                  B8: a text on screen contains this
 *   expectosdfocus:<none|bar|actions>  U11: which row of the overlay has the focus
 *   expectfocuspill:<label>            U11: the focused pill starts with <label>
 *   marklive:<name>                    F18: remember the moment live shows now
 *   expectclose:<name>                 F18: playback is within one segment (3.84 s) of the moment remembered under this name
 *   expectpaused:<yes|no>              F18: the player is paused
 *   debug                              print focus state
 *
 * Every shot first checks that the focused view (or the selected row of a focused list) lies fully
 * inside the visible area of all its ancestors: a focus indicator that needs space outside its view gets
 * clipped on the TV (the search-field bug of build e7526d5). A failure names the shot and the view.
 * After the draw, the REVIEW-R2 checks run on what was drawn: R4 (every start-over call site agrees),
 * X1 (no channel name cut in a logo slot), Y1/Y2 (the top fade erases the edge and the sticky group
 * eyebrow names the first visible row's group), Y3 (one text column per list), Y5 (the keyboard footer
 * holds key–action pairs only), Y6 (an asset eyebrow names its mode and source), Y8 (a chip row shows whole
 * pills only), A1 (no offset chip anywhere, no clock, range or remaining-time
 * element on the overlay), A2 (the passive overlay draws no pills), A4 (the rail's peek shows exactly while the
 * pills have the focus), B8 (the name of the app is on no screen). A held OK on a row must open a sheet whose
 * eyebrow is Alternativ (Y7).
 *
 * The video area is painted with a stand-in gradient so overlays can be judged over "video".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Screens {
    /**
     * The clean room's three surfaces: the unified strip, the browser (Tablå and Katalog & Sök) and the
     * picker, with Back as the browser's one-press gateway and the Serie detail as the browser's one level.
     */
    static final String TOUR = String.join(";",
            "wait:300", "shot:01-live-osd",
            "key:DPAD_CENTER", "expectosdfocus:actions", "expectfocuspill:Paus", "expectpills:Paus,Från början,Tablå,Alternativ", "shot:02-live-strip",
            "key:BACK", "wait:300", "expect:video", "shot:03-back-hides-the-strip",
            "key:BACK", "wait:500", "expect:grid", "expectsection:Tablå", "expectgrid:SVT1|Fråga Lund", "shot:04-back-opens-tabla", // Back on clean video: the browser in one press
            "key:BACK", "wait:300", "expectrail:Tablå", "shot:04b-back-to-the-tabs", // Back steps out to the tab row first
            "key:BACK", "wait:300", "expect:video", "repeat:05-back-closes-the-browser",
            "key:DPAD_CENTER", "key:DPAD_RIGHT*2", "expectfocuspill:Tablå", "key:DPAD_CENTER", "wait:300", "expect:grid", "shot:06-tabla-pill",
            "key:BACK*2", "wait:300", "expect:video",
            "key:GUIDE", "wait:300", "expect:grid", "key:DPAD_DOWN*2", "expectgrid:TV3|", "shot:07-grid-rows",
            "key:MENU", "wait:300", "expect:video", // Menu closes it
            "key:MENU", "wait:300", "expect:grid", "key:BACK*2", "key:MENU", "wait:300", "expect:grid",
            "key:SEARCH", "wait:300", "expectsection:Katalog & Sök", "expect:browser:katalog", "expecthint:Katalog", "shot:08-katalog-empty",
            "key:DPAD_CENTER", "text:bonde", "wait:800", "shot:09-search-results",
            "key:DPAD_RIGHT*6", "expectcard:", "shot:10-katalog-shelves", // the keys' last column leads to the results
            "key:DPAD_DOWN", "key:DPAD_DOWN", "key:DPAD_DOWN", "shot:11-katalog-next-shelf",
            "key:GUIDE", "expectsection:Tablå", "expect:grid", "shot:12-katalog-to-tabla",
            "key:BACK", "wait:800", "expect:video", "repeat:13-video-clean",
            "key:DPAD_CENTER", "key:DPAD_RIGHT*3", "expectfocuspill:Alternativ", "key:DPAD_CENTER", "expectpicker:yes", "shot:14-picker",
            "key:DPAD_DOWN*3", "expectpicker:Bildformat", "key:DPAD_CENTER", "shot:15-picker-value",
            // Logga ut is the last row of Alternativ; it asks once, in the same sheet, with Avbryt focused.
            "key:DPAD_DOWN", "expectpicker:Logga ut", "key:DPAD_CENTER", "expectpicker:Logga ut?", "expectpicker:Avbryt", "shot:15b-logout-confirm",
            "key:DPAD_CENTER", "expectpicker:Logga ut", "repeat:15c-logout-cancelled",
            "key:BACK", "expectpicker:no", "expectosdfocus:actions", "expectfocuspill:Alternativ", "shot:16-picker-closed",
            "key:BACK", "wait:4000", "expect:video", "repeat:17-clean-again");

    /** Loading, a series with nothing playable, the search error and the empty query's recent searches, in the catalogue lens. */
    static final String STATES = String.join(";",
            "wait:300",
            "key:SEARCH", "wait:300", "expectsection:Katalog & Sök", "shot:30-katalog-empty",
            "key:DPAD_CENTER", "text:film", "wait:800", "key:DPAD_RIGHT*6", "expectcard:", "shot:31-search-film",
            "key:DPAD_DOWN", "shot:32-katalog-film-shelf",
            "key:GUIDE", "expectsection:Tablå", "expect:grid", "shot:33-tabla-from-search",
            "key:BACK", "key:SEARCH", "wait:300", "expectsection:Katalog & Sök", "shot:34-katalog-again");

    private File shotsDir = new File(System.getProperty("shots.dir", "shots"));

    @Test
    public void tour() throws Exception {
        String scenario = System.getProperty("scenario", "");
        if (!scenario.isEmpty()) {
            run(scenario, true);
            return;
        }
        stable(TOUR, true, "tour");
    }

    @Test
    public void states() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        stable(STATES, true, "states");
    }

    /**
     * Runs a tour twice, with the recorded data and with stressed data (every title three times longer
     * with å ä ö, three-digit channel numbers), and fails when any view's bounds or any drawn non-text
     * element (row icon, pill, meta slot, logo slot, progress; OSD bar, pill, logo) differs in a shot.
     * The geometry must come from containers and fixed slots, never from the strings (DESIGN.md).
     */
    private void stable(String scenario, boolean loggedIn) throws Exception {
        stable(scenario, loggedIn, "stable");
    }

    private void stable(String scenario, boolean loggedIn, String name) throws Exception {
        java.util.Map<String, java.util.Map<String, Rect>> normal = run(scenario, loggedIn, name);
        java.util.Map<String, java.util.Map<String, Rect>> stressed;
        File keep = shotsDir;
        Fixtures.stress = true;
        shotsDir = new File(System.getProperty("shots.dir", "shots"), "../build/shots-stress");
        try {
            stressed = run(scenario, loggedIn, name);
        } finally {
            Fixtures.stress = false;
            shotsDir = keep;
        }
        List<String> diffs = new ArrayList<>();
        for (String shot : normal.keySet()) {
            java.util.Map<String, Rect> a = normal.get(shot), b = stressed.get(shot);
            if (b == null) {
                diffs.add(shot + ": missing in the stress run");
                continue;
            }
            java.util.Set<String> keys = new java.util.TreeSet<>(a.keySet());
            keys.addAll(b.keySet());
            for (String k : keys) {
                Rect x = a.get(k), y = b.get(k);
                if (x != null && y != null && k.matches(".*(OsdView#(playhead|live|liveLabel|focus|labelHead)|GuideGridView#nowLine)") && sameButPlace(x, y)) continue;
                if (x == null || !x.equals(y)) {
                    diffs.add(shot + ": " + k + " " + (x == null ? "absent" : x.toShortString()) + " vs stressed "
                            + (y == null ? "absent" : y.toShortString()));
                }
            }
        }
        int n = 0;
        for (java.util.Map<String, Rect> m : normal.values()) n += m.size();
        System.out.println("CHECK geometry: " + normal.size() + " shots, " + n + " elements compared, "
                + diffs.size() + " differences");
        if (!diffs.isEmpty()) {
            StringBuilder m = new StringBuilder("CHECK geometry depends on text in " + diffs.size() + " place(s):");
            for (int i = 0; i < Math.min(40, diffs.size()); i++) m.append("\n  ").append(diffs.get(i));
            throw new AssertionError(m.toString());
        }
    }

    /**
     * The playhead, the live tick and their caption sit where the clock puts them: the two runs of a tour
     * happen seconds apart, so their x differs by a pixel or two. Their size and line are geometry.
     */
    private static boolean sameButPlace(Rect x, Rect y) {
        return x.width() == y.width() && x.height() == y.height() && x.top == y.top && Math.abs(x.left - y.left) <= 3;
    }

    /**
     * Logos: focused rows use the light-background artwork, others the dark one. Both variants of TV3
     * (id 3) and Sjuan (id 7) fail: the number and name fill the slot. SVT2 (id 2) has only the dark
     * variant: focused, it keeps it on a dark plate.
     */
    static final String LOGOS = String.join(";",
            "wait:300", "shot:50-osd-logo",
            "key:MENU", "wait:800", "expect:grid", "expectgrid:SVT1|Fråga Lund", "shot:51-channels-logos", // the grid's logo column: SVT1's artwork
            "key:DPAD_DOWN", "shot:56-channel-one-variant-focused",          // SVT2: only dark, on a dark plate
            "key:DPAD_DOWN", "shot:52-channels-fallback",                    // TV3 and Sjuan fall back to number and name
            "key:DPAD_DOWN*2", "shot:53-grid-logo-fallback",
            // TV3's programme has start-over in the guide, so the strip's pills say so too (X3, R4).
            "key:BACK*2", "expect:video", "wait:4000", "key:DPAD_DOWN*2", "wait:600", "expectpill:Från början", "shot:54-osd-fallback",
            "key:DPAD_CENTER", "shot:54-osd-fallback-controls",
            "key:BACK", "hold:DPAD_CENTER", "wait:300", "expectpill:Från början", "repeat:55-held-ok-is-ok-logo"); // A5: the same frame as the OK above

    @Test
    public void logos() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        Fixtures.failLogos.add("3");
        Fixtures.failLogos.add("7");
        Fixtures.failLightLogos.add("2");
        try {
            stable(LOGOS, true, "logos");
        } finally {
            Fixtures.failLogos.clear();
            Fixtures.failLightLogos.clear();
        }
    }

    // ---- Walkthrough (client/design/WALKTHROUGH.md): every feature as the viewer meets it ------------

    /** W1: zap with the strip, a burst, Back hides the strip, Back again opens the browser. */
    static final String WALK_ZAP = String.join(";",
            "wait:300", "key:DPAD_DOWN", "wait:300", "expectpassive", "shot:160-zap-down",
            "key:DPAD_DOWN", "key:DPAD_DOWN", "wait:100", "shot:161-zap-burst-preview",
            "wait:600", "repeat:162-zap-burst-landed", // the preview already shows the channel's strip; landing retunes underneath
            "key:DPAD_UP*3", "wait:600", "shot:163-zap-back-on-svt1",
            "key:BACK", "wait:100", "shot:164-back-hides-the-strip",
            "key:BACK", "wait:500", "expect:grid", "shot:165-back-opens-the-browser",
            "key:BACK*2", "wait:300", "expect:video",
            "wait:4500", "expect:video", "repeat:167-live-clean"); // nothing on screen, as 164

    /**
     * W2: the browser, the grid, catch-up; Back on video opens it; the strip's Tablå pill; start-over from the
     * grid's picker; held Back returns to live.
     */
    static final String WALK_GUIDE = String.join(";",
            "wait:300", "key:GUIDE", "wait:500", "expect:grid", "shot:170-tabla-from-back",
            "key:DPAD_DOWN*2", "expectgrid:TV3|Efterlyst", "shot:171-grid-tv3-now",
            "key:DPAD_LEFT*2", "expectgrid:TV3|Spin City", "expectgridfooter:Spela", "shot:174-grid-tv3-catchup-block",
            "key:DPAD_CENTER", "wait:300", "expect:osd:timeline", "expecteyebrow:TV3", "expecttitle:Spin City", "expectpassive", "expectgrid:off", "shot:175-catchup-banner", // a catch-up is its channel's frame (A2)
            "key:DPAD_CENTER", "expectpills:Paus,Från början,Direkt,Tablå,Alternativ", "shot:175-catchup-controls",
            "key:DPAD_RIGHT", "shot:175-catchup-controls-right",
            "key:BACK", "wait:100", "shot:175-catchup-controls-hidden",
            "key:DPAD_CENTER", "key:DPAD_RIGHT*2", "expectfocuspill:Direkt", "expecthint:Dölj", // Paus, Från början, Direkt
            "key:DPAD_LEFT*3", "expectfocuspill:Paus", "key:BACK", "expect:video", "repeat:178-strip-back-closes", // ← from the first pill stays on it (no rail)
            "key:MENU", "wait:3000", "expect:grid", "hold:DPAD_CENTER", "expectpicker:Alternativ", "expectpicker:Titta direkt", "shot:172-grid-hold-from-the-start", "key:DPAD_DOWN", "expectpicker:Från början",
            // F12c/U0: "Från början" is a rewind: the channel's own frame (its name, the bar; no mode word, no Direkt hint), and Back never jumps to live.
            "key:DPAD_CENTER", "wait:300", "expect:osd:timeline", "expecteyebrow:TV3", "expectnohint:Direkt",
            "expectplayback:delayed", "expectpassive", "shot:179-rewound-from-tabla",
            "key:DPAD_CENTER", "expectpills:Paus,Från början,Direkt,Tablå,Alternativ", "shot:179-rewound-controls",
            "key:BACK", "expectplayback:delayed",
            "hold:BACK", "wait:4500", "expect:video", "expectplayback:delayed", "repeat:180-held-back-stays-delayed"); // nothing on screen, still behind live

    /** W3: Bildformat in the strip's Alternativ picker (persisted, A5; held OK on video is no sheet); Logga ut is gone with the panel. */
    static final String WALK_SETTINGS = String.join(";",
            "wait:300", "expectformat:contain", "key:DPAD_CENTER", "key:DPAD_RIGHT*3", "expectfocuspill:Alternativ",
            "key:DPAD_CENTER", "expectpicker:Alternativ", "shot:184-picker",
            "key:DPAD_DOWN*3", "expectpicker:Bildformat", "key:DPAD_CENTER", "expectformat:cover", "shot:185-bildformat-toggled",
            "key:DPAD_CENTER", "expectformat:contain", "repeat:187-bildformat-back",
            "key:BACK", "expect:osd:channel", "expectosdfocus:actions", "expectfocuspill:Alternativ", "expectformat:contain", "shot:188-back-to-the-strip",
            "key:BACK", "wait:4000", "expect:video", "hold:DPAD_CENTER", "expectosdfocus:actions", "expectformat:contain", "repeat:189-held-ok-is-ok"); // A5: no sheet over the video

    /** W4: the catalogue lens with the keys a viewer presses: letters, Mellanslag, ⌫, Rensa, Röst, → to the shelves, ↑ to the tabs. */
    static final String WALK_SEARCH = String.join(";",
            "wait:300", "key:SEARCH", "expectsection:Katalog & Sök", "wait:300", "shot:190-field",
            "key:DPAD_CENTER", "key:DPAD_UP", "expecthint:Tala", "shot:191-rost-key", // OK opens the keys on a, ↑ to the action row: Röst (Y4)
            "key:DPAD_DOWN", "key:DPAD_RIGHT", "key:DPAD_CENTER", "key:DPAD_CENTER", "wait:800", "shot:193-typed-bb",
            "key:DPAD_UP", "key:DPAD_RIGHT*2", "key:DPAD_CENTER", "shot:194-backspace", // Röst → Mellanslag → ⌫
            "key:DPAD_RIGHT", "key:DPAD_CENTER", "shot:195-cleared", // ✕ Rensa: focus stays on the key
            "text:bonde", "wait:800", "key:DPAD_RIGHT", "expectcard:", "shot:196-first-shelf", // ✕ is in the keys' last column: → leaves them for the results
            "key:GUIDE", "expectsection:Tablå", "expect:grid", "shot:197-tabs"); // Guide goes to Tablå from anywhere

    /**
     * G5: Ljud och text. The tracks are fabricated (TrackFixtures). English audio with Swedish subtitles: Automatisk
     * shows Swedish text at once; the pill is the last one; its sheet has the Undertext group (one audio track is no
     * choice, so no Ljud group); a pick applies at once, Back returns to the pill; the caption sits above the overlay.
     */
    static final String TRACKS = String.join(";",
            "wait:300", "tracks:none", "key:DPAD_CENTER", "expectnopill:Ljud", "key:BACK", // one audio track, no text: no pill (U0)
            "tracks:enSv", "expecttracks:audio=en text=sv", "cue:Det här är en text\\növer två rader", "expectcaption:clear", "shot:400-caption-clear",
            "key:DPAD_CENTER", "expectpills:Paus,Från början,Tablå,Ljud och text,Alternativ", "expectcaption:above", "shot:401-caption-above-overlay",
            "pill:Ljud och text", "expectpicker:Ljud och text", "expectpicker:Av", "shot:402-sheet-undertext",
            "key:DPAD_DOWN*2", "key:DPAD_CENTER", "expecttracks:audio=en text=en+sdh", "shot:403-sheet-sdh-picked",
            "key:DPAD_UP*2", "key:DPAD_CENTER", "expecttracks:audio=en text=off", "repeat:404-sheet-av",
            "key:BACK", "expect:osd:channel", "expectosdfocus:actions", "expectfocuspill:Ljud och text", "shot:405-back-to-pill",
            "key:BACK", "tracks:svEn", "expecttracks:audio=sv text=off", "key:DPAD_CENTER", "expectpills:Paus,Från början,Tablå,Ljud och text,Alternativ",
            "pill:Ljud och text", "expectpicker:Ljud och text", "expectpicker:Svenska", "shot:406-sheet-ljud",
            "key:DPAD_DOWN", "key:DPAD_CENTER", "expecttracks:audio=en text=off", "shot:407-sheet-english-audio",
            "key:BACK", "wait:800", "key:BACK", "wait:800", "tracks:ad", "expecttracks:audio=en text=off", // the English pick of the session holds in the next stream; the description does not play on its own
            "key:DPAD_CENTER", "pill:Ljud och text", "key:DPAD_DOWN", "expectpicker:Svenska", "shot:408-sheet-ad-and-layouts",
            "key:BACK", "wait:800", "key:BACK", "wait:800", "tracks:long", "key:DPAD_CENTER", "pill:Ljud och text", "key:DPAD_DOWN*2", "shot:409-sheet-long-names",
            // The strip's Alternativ picker: Undertext and Ljudspråk, each a value that cycles in place; a change decides again at once.
            "key:BACK", "wait:800", "key:BACK", "key:DPAD_CENTER", "key:DPAD_RIGHT*4", "expectfocuspill:Alternativ", "key:DPAD_CENTER",
            "key:DPAD_DOWN", "expectpicker:Undertext", "key:DPAD_CENTER", "expecttracks:audio=sv text=sv", "shot:410-picker-undertext",
            "key:DPAD_DOWN", "expectpicker:Ljudspråk", "repeat:406b", // the English audio picked in the sheet is what the setting remembers
            "key:DPAD_CENTER", "repeat:411a", "key:DPAD_CENTER", "repeat:411b", "repeat:411-picker-ljudsprak");

    @Test
    public void walkthrough() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(WALK_ZAP, true, "walk-zap");
        run(WALK_GUIDE, true, "walk-guide");
        run(WALK_SETTINGS, true, "walk-settings");
        run(WALK_SEARCH, true, "walk-search");
    }

    /**
     * F15 in the catalogue lens: Fortsätt titta is the first shelf (a card plays where it stopped), the
     * Serie detail opens from a search card (hero, actions, season chips, episodes), the Avsnitt pill of
     * the strip opens the playing episode's row, Nästa avsnitt is the picker (Back only closes it), and the
     * ★ marker follows "Lägg till i Min lista" in the picker. Lists recorded from Telia on 2026-10-15.
     */
    static final String LIBRARY = String.join(";",
            "wait:300",
            "key:GUIDE", "wait:500", "expect:grid", "shot:60-tabla-from-back", "key:SEARCH", "wait:300", "expectsection:Katalog & Sök", "shot:60-katalog-continue",
            "key:DPAD_DOWN", "key:DPAD_RIGHT*2", "expectcard:", "shot:61-continue-card", // ↓ from the field: the first shelf
            "key:DPAD_DOWN", "expectcard:", "shot:62-just-nu-shelf",
            "key:GUIDE", "expectsection:Tablå", "expect:grid", "key:BACK", "wait:800", "expect:video",
            "search:bonde", "opencard:Bonde söker fru", "wait:2000", "expectband:22", "expectepisodes:3", "shot:64-series-detail",
            "key:DPAD_RIGHT", "expectlisttop", "repeat:65-series-mylist-pill",
            "key:DPAD_DOWN", "expectlisttop", "shot:66-series-chips",
            "key:DPAD_DOWN", "shot:67-series-episode",
            "key:DPAD_DOWN*2", "key:DPAD_CENTER", "wait:300", "expect:osd:seek", "shot:68-episode-started",
            "hold:DPAD_CENTER", "expectosdfocus:actions", "expectpills:Paus,Från början,Avsnitt,Tablå,Alternativ", "shot:69-held-ok-media", // A5: no sheet; the row is the media's own
            "key:BACK", "wait:300", "pill:Avsnitt", "wait:500", // Paus, Från början, Avsnitt
            "expect:panel:Bonde söker fru", "expectfocus:", "shot:70-avsnitt-from-controls",
            "key:BACK", "expect:video", "repeat:71-back-to-video");

    @Test
    public void library() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(LIBRARY, true, "library");
    }

    @Test
    public void tracks() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(TRACKS, true, "tracks");
    }

    @Test
    public void login() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run("wait:300;shot:00-login", false);
    }

    // ---- B4: the guide grid ---------------------------------------------------------------------------

    /**
     * The grid opens from the rail's Tablå on SVT1's programme on air; → is a future block (the footer says when it
     * airs, OK does nothing), ← ← a past one with catch-up, whose OK plays it from its start (F14 continues after it).
     */
    static final String GRID_BLOCKS = String.join(";",
            "wait:300", "menu:Tablå", "wait:800", "expect:grid", "expectgrid:SVT1|Fråga Lund", "expectgridfooter:Titta direkt", "expectgridfooter:Håll OK", "shot:300-grid-on-air",
            "key:DPAD_RIGHT", "expectgrid:SVT1|Vår älskade låtskatt", "expectgridfooter:Sänds 21:00", "shot:301-grid-future-block",
            "key:DPAD_CENTER", "expect:grid", "expectgrid:SVT1|Vår älskade låtskatt", // a future block: nothing happens
            "key:DPAD_LEFT*2", "expectgrid:SVT1|Lokala nyheter", "expectgridfooter:Spela", "shot:302-grid-past-block",
            "key:DPAD_CENTER", "wait:300", "expect:osd:timeline", "expecteyebrow:SVT1", "expectpassive", "expectgrid:off", "shot:303-grid-catchup-started");

    /**
     * Several channels with logos, a channel with a gap in its programmes, and one with no guide at all ("Tablå saknas":
     * the server answers with no programmes, fixtures/guide__777.json; a fetch that fails is the error row instead, E9).
     */
    static final String GRID_ROWS = String.join(";",
            "wait:300", "menu:Tablå", "wait:800", "expectgrid:SVT1|Fråga Lund",
            "key:DPAD_DOWN*2", "expectgrid:TV3|", "shot:304-grid-rows-with-logos",
            "key:DPAD_DOWN*23", "wait:3000", "expectgrid:TV Malmö|", "shot:305-grid-gap-row",
            "key:DPAD_DOWN", "wait:600", "expectgrid:Testkanal|", "expectgridfooter:Tablå saknas", "shot:306-grid-no-guide",
            "key:DPAD_UP*3", "wait:300", "shot:307-grid-moved-back-up",
            "key:BACK*2", "expect:video", "expectgrid:off");

    /** Held OK on a block is the one deeper pattern (B7); ← from the first column hands over to the rail (U10). */
    static final String GRID_HOLD = String.join(";",
            "wait:300", "menu:Tablå", "wait:800", "hold:DPAD_CENTER", "expectpicker:Alternativ", "expectpicker:Titta direkt", "shot:308-grid-hold-picker",
            "key:BACK", "expect:grid", "expectgrid:SVT1|Fråga Lund",
            "key:DPAD_DOWN*26", "wait:3000", "key:DPAD_DOWN", "key:DPAD_LEFT", "expectrail:Tablå", "shot:309-grid-left-to-the-tabs",
            "key:DPAD_DOWN", "expect:grid", "key:MENU", "expect:video"); // Menu opens and closes the browser

    /**
     * Step 3c: ↑ ↓ never move the time window (the cut block is drawn on its visible part), ← → scroll it; OK on the Tablå
     * tab (↑ from the top row) brings the window and the focus back to now; held OK offers Gå till nu first when the window
     * is away from now.
     */
    static final String GRID_TIME = String.join(";",
            "wait:300", "menu:Tablå", "wait:3000", "expectgrid:SVT1|Fråga Lund", "markgrid",
            "key:DPAD_DOWN*3", "key:DPAD_UP", "expectgridwindow:same", "shot:312-grid-updown-keeps-the-window",
            "key:DPAD_UP*2", "expectgridwindow:same", "key:DPAD_RIGHT*5", "expectgridwindow:moved", "markgrid",
            "key:DPAD_DOWN*2", "key:DPAD_UP*2", "expectgridwindow:same", "key:DPAD_LEFT*18", "expectgridwindow:moved", "shot:313-grid-window-away",
            "hold:DPAD_CENTER", "expectpicker:Gå till nu", "shot:314-grid-hold-ga-till-nu", "key:DPAD_CENTER", "expectgridwindow:moved", "expectgrid:SVT1|Fråga Lund",
            "markgrid", "key:DPAD_LEFT*18", "expectgridwindow:moved", "key:DPAD_UP", "key:DPAD_CENTER", "expectgridwindow:same", "expectgrid:SVT1|Fråga Lund", "shot:315-grid-now-from-the-tab");

    /** B7: held OK on a block opens the one picker; Back closes it and the grid has the focus again. */
    static final String GRID_FROM_KANALER = String.join(";",
            "wait:300", "key:MENU", "wait:800", "key:DPAD_DOWN*3", "hold:DPAD_CENTER", "expectpicker:Alternativ", "expectpicker:Titta direkt", "shot:310-grid-hold-picker",
            "key:BACK", "expectgrid:TV4|", "expect:grid", "shot:311-grid-back-from-picker",
            "key:BACK*2", "expect:video");

    @Test
    public void grid() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(GRID_BLOCKS, true, "grid-blocks");
        stable(GRID_ROWS, true, "grid-rows");
        run(GRID_HOLD, true, "grid-hold");
        run(GRID_FROM_KANALER, true, "grid-kanaler");
        run(GRID_TIME, true, "grid-time");
    }

    // ---- F17: accidental ↑ ↓ never loses your place ------------------------------------------------

    /**
     * Delayed live keeps its place: ← ×25 rewinds SVT1 (Fråga Lund) 12:30 into its start-over stream; ↑
     * zaps away at once and the new channel's banner says nothing about it (owner: no "väntar" and no
     * "tillbaka"); ↓ zaps back and playback resumes the exact content moment (the mark), on the usual
     * delayed timeline. A second zap away from the resumed session keeps the place again.
     */
    static final String PLACE_ZAP = String.join(";",
            "wait:300", "key:DPAD_LEFT*25", "wait:1300", "expect:osd:timeline", "expecttitle:Fråga Lund", "mark:svt1",
            "shot:200-rewound-svt1",
            "key:BACK", "key:DPAD_UP", "wait:300", "expect:osd:channel",
            "shot:201-zapped-up-place-waits",
            "key:DPAD_DOWN", "wait:300", "expect:osd:timeline", "expecttitle:Fråga Lund", "expectmark:svt1",
            "shot:202-zapped-back-resumed",
            "wait:4200", "expect:video",
            "key:DPAD_DOWN", "wait:300", "expect:osd:channel", "shot:203-zapped-down-place-waits",
            "key:DPAD_UP", "wait:300", "expect:osd:timeline", "expectmark:svt1");

    /** A title started by hand: ↑ and ↓ show its banner and never zap (the title stays, no channel banner). */
    static final String PLACE_TITLE = String.join(";",
            "wait:300", "search:bonde", "opencard:Bonde söker fru", "wait:2000",
            "expect:panel:Bonde söker fru", "key:DPAD_DOWN*4", "expectfocus:3. Dags att välja", "key:DPAD_CENTER", "wait:300", "expect:osd:seek",
            "wait:4500", "expect:video",
            "key:DPAD_UP", "wait:100", "expect:osd:seek", "shot:204-title-up-shows-banner",
            "wait:4500", "expect:video",
            "key:DPAD_DOWN", "wait:100", "expect:osd:seek",
            "key:DPAD_CENTER", "wait:100", "key:DPAD_UP", "expect:osd:seek",
            "shot:205-title-up-keeps-controls");

    /** An expired place (31 min away): the channel plays live, silently. */
    static final String PLACE_EXPIRED = String.join(";",
            "wait:300", "key:DPAD_LEFT*25", "wait:1300", "expect:osd:timeline", "mark:svt1",
            "key:BACK", "key:DPAD_UP", "wait:300", "expect:osd:channel",
            "clock:1860000", "wait:4500", "expect:video",
            "key:DPAD_DOWN", "wait:300", "expect:osd:channel", "expecttitle:Fråga Lund", "expectplayback:live",
            "shot:206-expired-place-live-silent",
            "key:DPAD_UP", "wait:300", "expect:osd:channel", "shot:207-no-place-left"); // the dead place is gone, and it never said anything

    @Test
    public void places() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(PLACE_ZAP, true, "places-zap");
        run(PLACE_TITLE, true, "places-title");
        run(PLACE_EXPIRED, true, "places-expired");
    }

    // ---- U11, F18: the one overlay ------------------------------------------------------------------

    /**
     * U11 on live (SVT1, which can be rewound into): ← → from clean video open the overlay on the bar, OK on
     * the first pill; ↑ ↓ move between the rows and never zap; Back hides it and playback stays; the row is
     * Paus, Från början; the rail peeks out while the pills have the focus and ← on the first pill opens it
     * (A4). Then ← rewinds (the bar scrubs, the frame is the delayed one) and Direkt joins the row (A3);
     * Direkt tunes live again.
     */
    static final String OVERLAY_LIVE = String.join(";",
            "wait:4500", "expect:video", "expectpeek:no",
            "key:DPAD_RIGHT", "expect:osd:channel", "expectpassive", "shot:210-strip-live-bar", // → on live: nothing to run ahead to, the strip with its numbers
            "key:DPAD_CENTER", "expectosdfocus:actions", "expectfocuspill:Paus", "expectpills:Paus,Från början,Tablå,Alternativ",
            "shot:211-strip-live-actions",
            "key:DPAD_RIGHT", "expectfocuspill:Från början", "shot:212-strip-live-fran-borjan",
            "key:DPAD_UP", "expectbarpills", "expect:osd:channel", "expecteyebrow:SVT1", "expectplayback:live", "shot:226-pills-up-to-the-bar", // ↑ from a pill: the seek bar, never a zap
            "key:DPAD_LEFT", "expectpending:yes", "expectbarpills", "expect:osd:timeline", "shot:227-bar-from-pills-scrubbing", // ← → scrub there
            "key:DPAD_DOWN", "expectosdfocus:actions", "expectfocuspill:Från början", "expecteyebrow:SVT1", // ↓ returns to the pill that was focused
            "key:BACK", "expect:video", "expectplayback:live",
            "key:DPAD_CENTER", "expectosdfocus:actions", "expectfocuspill:Paus", // OK from clean video: the first pill
            "key:DPAD_LEFT", "expectfocuspill:Paus", "expect:osd:channel", "shot:213-left-stays-on-the-first-pill", // no rail: ← on the first pill does nothing
            "key:BACK", "expect:video", "key:DPAD_LEFT", "expectscrub", "expect:osd:timeline", "expecteyebrow:SVT1", "shot:214-strip-rewind-bar", // U11: the bar and its numbers, the playhead moves
            "wait:1300", "expectplayback:delayed", "expect:osd:timeline", "expectpassive", // the keys stopped: the seek committed, the strip stays
            "key:DPAD_CENTER", "expectfocuspill:Paus", "expectpills:Paus,Från början,Direkt,Tablå,Alternativ", "shot:215-strip-delayed-actions", // A3: Direkt only behind live
            "key:DPAD_RIGHT*2", "expectfocuspill:Direkt", "key:DPAD_CENTER", "wait:700", "expectplayback:live", "expectpassive", "shot:216-direkt-is-live",
            "key:DPAD_CENTER", "expectpills:Paus,Från början,Tablå,Alternativ", // clean live again: no Direkt
            "wait:5200", "expect:video"); // five seconds without a key: it hides by itself

    /** U11 on a title: the same row (Avsnitt for an episode), Direkt in catch-up, OK on the first pill is Paus/Spela and the overlay stays up. */
    static final String OVERLAY_TITLE = String.join(";",
            "wait:300", "menu:Tablå", "wait:3000", "key:DPAD_DOWN*2", "key:DPAD_LEFT*2", "expectgrid:TV3|Spin City",
            "key:DPAD_CENTER", "wait:300", "expect:osd:timeline", "expecttitle:Spin City", "expectpassive", // a catch-up from Tablå
            "expectpills:Paus,Från början,Direkt,Tablå,Alternativ", "key:DPAD_CENTER", "expectosdfocus:actions",
            "shot:217-overlay-catchup-actions",
            "key:DPAD_UP", "expectbarpills", "expecttitle:Spin City", "key:DPAD_DOWN", "expectfocuspill:Paus", // ↑ to the seek bar and back, never a zap
            "markosd", "key:DPAD_CENTER", "expectpaused:yes", "expectosdkept", "expectosdfocus:actions", // OK on Paus: only the label changes, the overlay never blinks
            "expectpills:Spela,Från början,Direkt,Tablå,Alternativ", "wait:6000", "expect:osd:timeline", // paused: it stays up
            "shot:218-overlay-catchup-paused",
            "markosd", "key:DPAD_CENTER", "expectpaused:no", "expectosdkept", "expectpills:Paus,Från början,Direkt,Tablå,Alternativ",
            "key:DPAD_DOWN", "key:DPAD_RIGHT*2", "expectfocuspill:Direkt", "key:DPAD_CENTER",
            "wait:700", "expectplayback:live", "expect:osd:channel", "expectpassive"); // Direkt in catch-up: the channel, live

    /**
     * F18: Paus on live switches invisibly to the start-over stream at the live position and pauses; the row
     * then reads Spela, and Spela continues delayed. A channel without start-over has no Paus pill (no pill
     * at all: the row is empty and OK on it opens the rail, A4), and the media key says so instead of doing
     * something else.
     */
    static final String PAUSE_LIVE = String.join(";",
            "wait:300", "marklive:press", "key:DPAD_CENTER", "expectfocuspill:Paus", "markosd", "key:DPAD_CENTER", "wait:300",
            "expectosdkept", "expectpaused:yes", "expectplayback:delayed", "expectclose:press", "expect:osd:timeline", "expecteyebrow:SVT1",
            "expectosdfocus:actions", "expectfocuspill:Spela", "expectpills:Spela,Från början,Direkt,Tablå,Alternativ",
            "shot:219-pause-live-paused",
            "wait:6000", "expect:osd:timeline", // paused: no auto-hide
            "markosd", "key:DPAD_CENTER", "wait:300", "expectosdkept", "expectpaused:no", "expectplayback:delayed", "expectclose:press",
            "expectpills:Paus,Från början,Direkt,Tablå,Alternativ",
            "shot:220-pause-live-resumed");

    /**
     * A channel whose programme has no start-over: no Paus and no Från början (the row keeps Tablå and
     * Alternativ, never a dead end), and the media key answers `Går inte att pausa`.
     */
    static final String PAUSE_NONE = String.join(";",
            "wait:300", "now:Utan start-over", "key:DPAD_UP", "wait:600", "expect:osd:channel",
            "expectnopill:Paus", "expectnopill:Från början",
            "expectpills:Tablå,Alternativ", "expectpassive", "shot:221-no-start-over-no-pause",
            "key:DPAD_CENTER", "expectosdfocus:actions", "expectpills:Tablå,Alternativ", "shot:222-no-pills-the-strip",
            "key:MEDIA_PLAY_PAUSE", "expectpaused:no", "expectplayback:live", "expectstatus:Går inte att pausa", "shot:223-no-start-over-media-key",
            "key:BACK", "now:", "key:DPAD_UP", "key:DPAD_DOWN", "wait:600"); // back to the real guide for the next tour

    /**
     * U11, scrubbing without the full overlay: ← from clean video scrubs on the light overlay (the playhead moves, no
     * pills, no peek, hint "OK Spela härifrån ↩ Dölj"). OK commits at once and opens no pills; Back drops it and hides (the
     * playhead goes back, nothing seeks, the picture stays live); left alone, the keys' pause commits once. After a
     * commit the light overlay carries the usual hint and OK opens the pills.
     */
    static final String SCRUB_LIVE = String.join(";",
            "wait:300", "wait:4500", "expect:video",
            "key:DPAD_LEFT*3", "expectscrub", "expectpending:yes", "shot:224-scrub-strip",
            "key:BACK", "expectpending:no", "expect:video", "expectplayback:live", "wait:1300", "expectplayback:live", // Back cancels: nothing seeks later either
            "key:DPAD_LEFT*2", "expectscrub", "wait:1300", "expectpending:no", "expectplayback:delayed", "expectpassive", // idle: commits once, the usual strip
            "key:DPAD_CENTER", "expectosdfocus:actions", "expectpills:Paus,Från början,Direkt,Tablå,Alternativ", // after a commit OK enters the strip
            "pill:Direkt", "wait:700", "expectplayback:live", "expectpassive",
            "key:DPAD_LEFT*3", "expectscrub", "key:DPAD_CENTER", "expectpending:no", "expectplayback:delayed", "expectpassive", "shot:225-scrub-ok-commits"); // OK: plays from here, no pills

    @Test
    public void scrub() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        run(SCRUB_LIVE, true, "scrub");
    }

    @Test
    public void overlay() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        stable(OVERLAY_LIVE, true, "overlay-live");
        run(OVERLAY_TITLE, true, "overlay-title");
        run(PAUSE_LIVE, true, "pause-live");
        run(PAUSE_NONE, true, "pause-none");
    }

    /**
     * F16: Sök with thumbnails, the Serie detail screen (hero, info, actions, season chips, episodes with
     * thumbnails), a season with nothing playable, a season whose list failed, Back to Sök with the row
     * restored, images still loading (placeholders) and loaded (same geometry), a series with nothing
     * playable, and image failures: Film club has no hero (its muted colour stays), and in the film search
     * the thumbnails of "Filmen om Siw" fail (the placeholder stays in the slot).
     */
    static final String SERIES = String.join(";",
            "wait:300", "search:bonde", "opencard:Bonde söker fru", "wait:2000", "expectband:22", "expectepisodes:3", "shot:81-series-detail",
            "key:DPAD_RIGHT", "expectlisttop", "repeat:82-series-mylist-pill",
            "key:DPAD_DOWN", "expectlisttop", "shot:83-series-chips",
            "key:DPAD_DOWN", "shot:84-series-episode",
            "key:DPAD_UP*4", "expectlisttop", "key:DPAD_RIGHT", "expectlisttop", "repeat:85-series-season-unplayable",
            "key:DPAD_RIGHT", "expectlisttop", "repeat:86-series-season-older",
            "key:BACK", "expect:browser:katalog", "shot:87-back-to-search",
            "slowimg:60000", "key:DPAD_CENTER", "wait:600", "repeat:88-series-images-pending",
            "slowimg:0", "wait:900", "repeat:89-series-none-loaded", "same:88-series-images-pending", // the hero is the same art: the frame may repeat (the geometry comparison is the point)
            "key:BACK", "wait:400", "search:film", "opencard:Film club", "wait:2000", "shot:91-series-hero-fallback",
            "key:BACK", "expect:browser:katalog", "shot:92-back-from-film-club");

    @Test
    public void series() throws Exception {
        Assume.assumeTrue(System.getProperty("scenario", "").isEmpty());
        Fixtures.failImages.add("m-af45486138042d6e");
        try {
            stable(SERIES, true, "series");
        } finally {
            Fixtures.failImages.clear();
        }
    }

    private int osdChanges;
    private long gridWindow;

    /** Broadcast moments remembered by {@code mark:}, for {@code expectmark:} (F17). */
    private final java.util.Map<String, Long> marks = new java.util.HashMap<>();

    /** Per shot: element key to its rect in window coordinates, for {@link #stable}. */
    private java.util.Map<String, java.util.Map<String, Rect>> geometry;
    /** R1: the PNG bytes' hash of every shot of this run, by name. */
    private java.util.Map<String, Integer> hashes;

    java.util.Map<String, java.util.Map<String, Rect>> run(String scenario, boolean loggedIn) throws Exception {
        return run(scenario, loggedIn, "run");
    }

    /** Runs a tour; {@code name} prefixes every step in an error, so a failure names the tour and its step. */
    java.util.Map<String, java.util.Map<String, Rect>> run(String scenario, boolean loggedIn, String name) throws Exception {
        geometry = new java.util.LinkedHashMap<>();
        hashes = new java.util.LinkedHashMap<>();
        org.robolectric.shadows.ShadowLog.stream = System.out;
        Fixtures.install();
        Fixtures.installLogos();
        Fixtures.installImages();
        Fixtures.imageDelayMs = 0;
        Fixtures.delayMs = 0;
        MainActivity.renderOnly = true;
        MainActivity.languageOverride = "sv"; // a Swedish TV: Ljudspråk Automatisk is Swedish
        MainActivity.voiceOverride = true; // draw the Röst key as on a TV with a recognizer
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture")
                .putString("accessToken", loggedIn ? "fixture" : "")
                .putString("refreshToken", loggedIn ? "fixture" : null)
                .commit();
        app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("tracks", Context.MODE_PRIVATE).edit().clear().commit();
        new File(app.getFilesDir(), "titles.txt").delete();
        new File(app.getFilesDir(), "library.json").delete();
        for (String d : new String[] {"logos", "images"}) {
            File[] cached = new File(app.getCacheDir(), d).listFiles();
            if (cached != null) for (File f : cached) f.delete();
        }
        shotsDir.mkdirs();

        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        // A TV is never in touch mode, and its window has focus; without both, ListView keeps no selected row.
        View decor = a.getWindow().getDecorView();
        Object viewRoot = decor.getParent();
        java.lang.reflect.Method ensure = viewRoot.getClass().getDeclaredMethod("ensureTouchMode", boolean.class);
        ensure.setAccessible(true);
        ensure.invoke(viewRoot, false);
        decor.dispatchWindowFocusChanged(true);
        a.onWindowFocusChanged(true);
        settle(1500);
        int stepNo = 0;
        for (String step : scenario.split(";")) {
            String s = step.trim();
            if (s.isEmpty()) continue;
            s = name + "#" + (++stepNo) + " " + s;
            int c = s.indexOf(':');
            String op = c < 0 ? s.substring(s.indexOf(' ') + 1) : s.substring(s.indexOf(' ') + 1, c);
            String arg = c < 0 ? "" : s.substring(c + 1);
            switch (op) {
                case "key": {
                    int times = 1;
                    int star = arg.indexOf('*');
                    if (star > 0) {
                        times = Integer.parseInt(arg.substring(star + 1));
                        arg = arg.substring(0, star);
                    }
                    int code = keyCode(arg);
                    for (int i = 0; i < times; i++) {
                        long t = SystemClock.uptimeMillis();
                        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
                        settle(times > 1 ? 30 : 250);
                    }
                    break;
                }
                case "hold": {
                    int code = keyCode(arg);
                    long t = SystemClock.uptimeMillis();
                    a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                    settle(700);
                    long u = SystemClock.uptimeMillis();
                    a.dispatchKeyEvent(new KeyEvent(t, u, KeyEvent.ACTION_UP, code, 0));
                    settle(250);
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER) { // Y7: one convention for every held-OK sheet
                        String eb = ((MainActivity) a).topSheetEyebrowForTest();
                        if (eb != null && !eb.startsWith("Alternativ")) {
                            throw new AssertionError("CHECK Y7 held OK opened a sheet whose eyebrow is \"" + eb + "\", not Alternativ, at " + s);
                        }
                    }
                    break;
                }
                case "text": {
                    View k = find(a.getWindow().getDecorView(), KeyboardView.class);
                    if (k == null || !k.isShown()) throw new IllegalStateException("no visible keyboard for " + s);
                    for (char ch : arg.toCharArray()) ((KeyboardView) k).type(String.valueOf(ch));
                    settle(250);
                    break;
                }
                case "focus": {
                    // Press down (then up) until the focused row's label starts with arg: real key presses, robust to row counts.
                    // "label#n": the n-th row with that label (a suggestion and a result can share a title).
                    int want = 1;
                    int hash = arg.lastIndexOf('#');
                    if (hash > 0) {
                        want = Integer.parseInt(arg.substring(hash + 1));
                        arg = arg.substring(0, hash);
                    }
                    ListView l = (ListView) find(a.getWindow().getDecorView(), ListView.class);
                    boolean found = false;
                    int seen = 0, lastPos = -1;
                    for (int dir : new int[] {KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP}) {
                        for (int i = 0; i < 80 && !found; i++) {
                            View sel = l.getSelectedView();
                            if (l.hasFocus() && sel != null && l.getSelectedItemPosition() != lastPos
                                    && String.valueOf(sel.getContentDescription()).startsWith(arg)) {
                                lastPos = l.getSelectedItemPosition();
                                if (++seen == want) {
                                    found = true;
                                    break;
                                }
                            }
                            long t = SystemClock.uptimeMillis();
                            a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, dir, 0));
                            a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, dir, 0));
                            settle(30);
                        }
                    }
                    if (!found) throw new IllegalStateException("no row \"" + arg + "\" for " + s);
                    break;
                }
                case "wait":
                case "settle":
                    settle(Integer.parseInt(arg));
                    break;
                case "end": // the on-demand title reaches its end (render-only playback never does)
                    ((MainActivity) a).endOfMediaForTest();
                    settle(250);
                    break;
                case "slow":
                    Fixtures.delayMs = Integer.parseInt(arg);
                    break;
                case "slowimg":
                    Fixtures.imageDelayMs = Integer.parseInt(arg);
                    break;
                case "same": {
                    java.util.Map<String, Rect> before = geometry.get(arg), last = null;
                    for (java.util.Map<String, Rect> g : geometry.values()) last = g;
                    if (before == null || last == null) throw new IllegalStateException("no shot " + arg + " for " + s);
                    if (!before.equals(last)) {
                        java.util.Set<String> keys = new java.util.TreeSet<>(before.keySet());
                        keys.addAll(last.keySet());
                        StringBuilder m = new StringBuilder("CHECK geometry moved after " + arg + ":");
                        for (String k : keys) {
                            if (!java.util.Objects.equals(before.get(k), last.get(k))) {
                                m.append("\n  ").append(k).append(" ").append(before.get(k)).append(" vs ").append(last.get(k));
                            }
                        }
                        throw new AssertionError(m.toString());
                    }
                    System.out.println("CHECK same geometry as " + arg + ": " + before.size() + " elements");
                    break;
                }
                case "debug":
                    View fo = focused(a.getWindow().getDecorView());
                    System.out.println("DEBUG focus=" + fo);
                    System.out.println("DEBUG delayed=" + ((MainActivity) a).delayedForTest());
                    View sl = find(a.getWindow().getDecorView(), se.lite.play.ui.SnapList.class);
                    if (sl != null) System.out.println("DEBUG fade top=" + ((se.lite.play.ui.SnapList) sl).topFadeStrength()
                            + " faded=" + ((se.lite.play.ui.SnapList) sl).topFaded() + " padTop=" + sl.getPaddingTop());
                    View lv = find(a.getWindow().getDecorView(), ListView.class);
                    if (sl != null) { se.lite.play.ui.SnapList L = (se.lite.play.ui.SnapList) sl; StringBuilder sb = new StringBuilder("DEBUG band=" + L.band() + " cut=" + (L.cutRow() == null ? null : L.cutRow().getContentDescription()) + " children:"); for (int i = 0; i < L.getChildCount(); i++) sb.append(" [").append(L.getChildAt(i).getContentDescription()).append(" ").append(L.getChildAt(i).getTop()).append("-").append(L.getChildAt(i).getBottom()).append("]"); System.out.println(sb); }
                    if (lv instanceof ListView && ((ListView) lv).getChildCount() > 0) System.out.println("DEBUG first=" + ((ListView) lv).getFirstVisiblePosition() + " top=" + ((ListView) lv).getChildAt(0).getTop() + " selView=" + ((ListView) lv).getSelectedView());
                    if (lv != null) System.out.println("DEBUG list focus=" + lv.hasFocus()
                            + " sel=" + ((ListView) lv).getSelectedItemPosition()
                            + " count=" + ((ListView) lv).getCount() + " shown=" + lv.isShown());
                    break;
                case "shot":
                    shot(a, arg, false);
                    break;
                case "repeat":
                    shot(a, arg, true);
                    break;
                case "expect": {
                    String got = ((MainActivity) a).surfaceForTest();
                    // The stress replays lengthen titles ("Bonde söker fru Bonde söker fru ...", ÅÄÖ): a detail's name is its title's start.
                    String want = arg.startsWith("panel:") ? "detail:" + arg.substring(6) : arg;
                    boolean same = got.equals(want) || want.startsWith("detail:") && got.startsWith(want + " ");
                    if (!same) throw new AssertionError("R2 expected " + arg + " but " + got + " is on screen at " + s);
                    break;
                }
                case "expectgrid": { // the Tablå lens: the grid is up with this focus ("<channel>|<programme>"; "off": it is closed). A stressed title starts with the recorded one.
                    String got = ((MainActivity) a).gridFocusForTest();
                    boolean ok = arg.equals("off") ? got == null : got != null && (got.equals(arg) || got.startsWith(arg.split("\\|", -1)[0]) && got.contains("|" + arg.split("\\|", -1)[1]));
                    if (!ok) throw new AssertionError("B4 expected the grid focus " + arg + " but it is " + got + " at " + s);
                    break;
                }
                case "expectgridfooter": {
                    GuideGridView g = ((MainActivity) a).gridForTest();
                    String got = g == null ? "(no grid)" : g.shownFooter();
                    if (!got.contains(arg)) throw new AssertionError("B4 expected the grid footer to contain \"" + arg + "\" but it is \"" + got + "\" at " + s);
                    break;
                }
                case "now": { // a programme on air with this title, 20:00-21:00, start-over when arg ends ":so"; "now:" clears it
                    if (arg.isEmpty()) {
                        ((MainActivity) a).nowForTest(null);
                        press(a, KeyEvent.KEYCODE_DPAD_UP);
                        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
                        settle(400);
                        break;
                    }
                    boolean so = arg.endsWith(":so");
                    String title = so ? arg.substring(0, arg.length() - 3) : arg;
                    long t = se.lite.play.Clock.now();
                    Api.Program p = new Api.Program();
                    p.title = title;
                    p.startMs = t - 10 * 60_000;
                    p.endMs = t + 50 * 60_000;
                    if (so) {
                        p.startOver = new Api.Playable();
                        p.startOver.videoId = "v-fixture";
                        p.startOver.watchMode = "STARTOVER";
                    }
                    ((MainActivity) a).nowForTest(p);
                    press(a, KeyEvent.KEYCODE_DPAD_UP); // a zap to the next channel redraws the strip from the new fact
                    press(a, KeyEvent.KEYCODE_DPAD_DOWN);
                    settle(400);
                    break;
                }
                case "search": { // the catalogue lens with the query run at once
                    ((MainActivity) a).searchForTest(arg);
                    settle(900);
                    break;
                }
                case "opencard": { // opens the result card whose title starts with arg, as OK does on it
                    MainActivity m = (MainActivity) a;
                    if (m.sectionForTest() == null) throw new AssertionError("no browser for " + s);
                    String key = m.catalogueKeyForTest(arg);
                    if (key == null) throw new AssertionError("no card \"" + arg + "\" at " + s);
                    settle(400);
                    m.openCardForTest(key);
                    settle(400);
                    break;
                }
                case "expectcard": { // the catalogue lens: the focused poster's title
                    String got = ((MainActivity) a).catalogueFocusForTest();
                    if (got == null || !got.startsWith(arg)) throw new AssertionError("expected the catalogue card \"" + arg + "\" but it is \"" + got + "\" at " + s);
                    break;
                }
                case "expectpicker": { // the picker is open (yes) or closed (no); with a row label, that row has the focus
                    String got = ((MainActivity) a).pickerForTest();
                    boolean want = arg.equals("yes") || !arg.equals("no");
                    boolean has = got != null;
                    boolean row = has && !arg.equals("yes") && !arg.equals("no") && got.contains(arg);
                    if (has != want || (has && !arg.equals("yes") && !arg.equals("no") && !row)) {
                        throw new AssertionError("expected the picker " + arg + " but it is " + got + " at " + s);
                    }
                    break;
                }
                case "expectsection": {
                    String got = ((MainActivity) a).sectionForTest();
                    if (!arg.equals(got)) throw new AssertionError("expected the browser lens " + arg + " but it is " + got + " at " + s);
                    break;
                }
                case "expectrail": {
                    String got = ((MainActivity) a).railFocusForTest();
                    if (!arg.equals(got)) throw new AssertionError("expected the tab row focused on " + arg + " but the focus is " + (got == null ? "not on the tabs" : "on " + got) + " at " + s);
                    break;
                }
                case "expectfocus": {
                    ListView l = (ListView) find(a.getWindow().getDecorView(), ListView.class);
                    View sel = l != null && l.hasFocus() ? l.getSelectedView() : null;
                    String got = sel == null ? "(none)" : String.valueOf(sel.getContentDescription());
                    if (!got.contains(arg)) throw new AssertionError("R2 expected focus on \"" + arg + "\" but it is on " + got + " at " + s);
                    break;
                }
                case "expecttitle": {
                    View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    String got = o == null || !o.isShown() ? "(no OSD)" : ((se.lite.play.ui.OsdView) o).shownTitle();
                    if (!arg.equals(got)) throw new AssertionError("R2 expected the OSD title \"" + arg + "\" but it is \"" + got + "\" at " + s);
                    break;
                }
                case "expecthint": {
                    View h = a.getWindow().getDecorView().findViewWithTag("hint");
                    String got;
                    if (((MainActivity) a).sectionForTest() != null && "Katalog & Sök".equals(((MainActivity) a).sectionForTest())) {
                        got = String.valueOf(((MainActivity) a).catalogueHintForTest());
                    } else if (h != null && h.isShown()) {
                        got = String.valueOf(((android.widget.TextView) h).getText());
                    } else {
                        View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                        got = o == null || !o.isShown() ? "(no footer)" : String.join(" ", ((se.lite.play.ui.OsdView) o).shownHints());
                    }
                    if (!got.contains(arg)) throw new AssertionError("expected the footer to contain \"" + arg + "\" but it is \"" + got + "\" at " + s);
                    break;
                }
                case "expectstatus": {
                    View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    String got = o == null || !o.isShown() ? null : ((se.lite.play.ui.OsdView) o).shownStatus();
                    if (got == null || !got.contains(arg)) {
                        throw new AssertionError("expected the status line to contain \"" + arg + "\" but it is " + (got == null ? "empty" : "\"" + got + "\"") + " at " + s);
                    }
                    break;
                }
                case "markgrid": // step 3c: remember the grid's time window
                    gridWindow = ((MainActivity) a).gridForTest().viewStartMs();
                    break;
                case "expectgridwindow": { // step 3c: the window is where markgrid saw it (same) or elsewhere (moved)
                    long got = ((MainActivity) a).gridForTest().viewStartMs();
                    if ((got == gridWindow) != arg.equals("same")) throw new AssertionError("3c expected the grid window to be " + arg + " but it started " + gridWindow + " and starts " + got + " at " + s);
                    break;
                }
                case "markosd": // blink: remember how often the overlay has appeared or disappeared
                    osdChanges = ((MainActivity) a).osdVisibilityChangesForTest();
                    break;
                case "expectosdkept": { // blink: it neither disappeared nor re-appeared since markosd, and it is fully opaque
                    MainActivity m = (MainActivity) a;
                    if (m.osdVisibilityChangesForTest() != osdChanges || m.osdAlphaForTest() != 1f || m.osdFocusForTest().equals("none")) {
                        throw new AssertionError("blink: the overlay was hidden or faded in again (changes " + osdChanges + " -> "
                                + m.osdVisibilityChangesForTest() + ", alpha " + m.osdAlphaForTest() + ", focus " + m.osdFocusForTest() + ") at " + s);
                    }
                    break;
                }
                case "expectscrub": { // U11: the light overlay scrubbing: the bar has the focus, no pills, no peek, the hint says what OK and Back do
                    se.lite.play.ui.OsdView ov = (se.lite.play.ui.OsdView) find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    String hint = ov == null ? "" : String.join(" ", ov.shownHints());
                    boolean ok = ov != null && ov.isShown() && ov.focusRow() == se.lite.play.ui.OsdView.FOCUS_BAR
                            && hint.contains("OK Spela härifrån") && hint.contains("Dölj")
                            && ((MainActivity) a).seekPendingForTest();
                    if (!ok) throw new AssertionError("U11 expected a scrub (bar focus, \"OK Spela härifrån ↩ Dölj\", a seek pending) but got \"" + hint + "\" at " + s);
                    break;
                }
                case "expectbarpills": {
                    se.lite.play.ui.OsdView ov = (se.lite.play.ui.OsdView) find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    if (ov == null || !ov.isShown() || !ov.pillsOnBar() || !ov.pillsDrawn()) {
                        throw new AssertionError("U11 expected the bar focused with the pills still drawn at " + s + " (shown " + (ov != null && ov.isShown()) + ", onBar " + (ov != null && ov.pillsOnBar()) + ", drawn " + (ov != null && ov.pillsDrawn()) + ")");
                    }
                    break;
                }
                case "expectpending": { // U11: a scrub has (yes) or has no (no) seek waiting to commit
                    boolean got = ((MainActivity) a).seekPendingForTest();
                    if (got != arg.equals("yes")) throw new AssertionError("U11 expected a pending seek: " + arg + " but it is " + got + " at " + s);
                    break;
                }
                case "mark": {
                    long m = ((MainActivity) a).contentMomentForTest();
                    if (m == androidx.media3.common.C.TIME_UNSET) throw new AssertionError("F17 no content moment to mark at " + s);
                    marks.put(arg, m);
                    break;
                }
                case "expectmark": {
                    Long want = marks.get(arg);
                    if (want == null) throw new IllegalStateException("no mark " + arg + " for " + s);
                    long got = ((MainActivity) a).contentMomentForTest();
                    if (got != want) {
                        throw new AssertionError("F17 expected playback at the moment marked " + arg + " (" + want + ") but it is at "
                                + (got == androidx.media3.common.C.TIME_UNSET ? "unknown" : got + ", " + (got - want) + " ms off") + " at " + s);
                    }
                    System.out.println("CHECK F17 content moment " + arg + " resumed exactly");
                    break;
                }
                case "clock":
                    Clock.offsetMs += Long.parseLong(arg);
                    break;
                case "menu":
                case "rail": {
                    // The browser is Back's one-press destination from clean video (clean room 3); a section
                    // name (Tablå, Katalog & Sök) names the lens, "menu" also enters it.
                    MainActivity m = (MainActivity) a;
                    if (m.sectionForTest() == null) press(a, KeyEvent.KEYCODE_GUIDE); // Guide opens the browser on Tablå
                    if (m.sectionForTest() == null) press(a, KeyEvent.KEYCODE_BACK);
                    if (m.sectionForTest() == null) throw new AssertionError("Back did not open the browser at " + s);
                    String want = arg.equals("Tablå") || arg.equals("Katalog & Sök") ? arg : "Katalog & Sök";
                    if (!want.equals(m.sectionForTest())) {
                        // Switch lenses: the catalogue's tab row, or Back then the Katalog lens.
                        if (want.equals("Katalog & Sök")) { press(a, KeyEvent.KEYCODE_MENU); }
                        else { press(a, KeyEvent.KEYCODE_BACK); press(a, KeyEvent.KEYCODE_GUIDE); }
                    }
                    if (!want.equals(m.sectionForTest())) throw new AssertionError("the browser is on " + m.sectionForTest() + ", not " + want + " at " + s);
                    break;
                }
                case "expectnopills":
                    if (!((MainActivity) a).pillsForTest().isEmpty()) {
                        throw new AssertionError("U11 expected an empty row but it is " + ((MainActivity) a).pillsForTest() + " at " + s);
                    }
                    break;
                case "expectpassive": {
                    // The unified strip with no row focused: the pills are drawn dim, Back hides it.
                    View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    se.lite.play.ui.OsdView ov = (se.lite.play.ui.OsdView) o;
                    String hint = ov == null ? "" : String.join(" ", ov.shownHints());
                    boolean passive = ov != null && ov.isShown() && ov.focusRow() == se.lite.play.ui.OsdView.FOCUS_NONE
                            && ov.pillsDrawn() && hint.equals(Theme.BACK + " Dölj");
                    if (!passive) throw new AssertionError("expected the unified strip with no focus at " + s + ": \"" + hint + "\"");
                    break;
                }
                case "expectpeek": {
                    if (!arg.equals("no")) throw new AssertionError("the rail's peek is gone (clean room 2026-10-17) at " + s);
                    break;
                }
                case "tracks": { // G5: the stream reports these (fabricated) tracks
                    ((MainActivity) a).tracksForTest(TrackFixtures.named(arg));
                    settle(100);
                    break;
                }
                case "expecttracks": {
                    String got = ((MainActivity) a).selectedTracksForTest();
                    if (!arg.equals(got)) throw new AssertionError("G5 expected the tracks " + arg + " but " + got + " plays at " + s);
                    break;
                }
                case "cue": { // G5: a subtitle cue arrives
                    ((MainActivity) a).cueForTest(arg.replace("\\n", "\n"));
                    settle(100);
                    break;
                }
                case "expectcaption": { // G5: the caption box is clear of the overlay (above its top edge) while it is up
                    MainActivity m = (MainActivity) a;
                    android.graphics.RectF box = m.captionsForTest().boxRect();
                    if (box == null) throw new AssertionError("G5 no caption on screen at " + s);
                    se.lite.play.ui.OsdView osdView = m.osdForTest();
                    boolean up = osdView.getVisibility() == View.VISIBLE;
                    int contentTop = osdView.getTop() + osdView.contentTop();
                    if (up && box.bottom > contentTop) throw new AssertionError("G5 the caption (bottom " + box.bottom + ") reaches into the overlay (content from " + contentTop + ") at " + s);
                    if (!arg.equals(up ? "above" : "clear")) throw new AssertionError("G5 expected the caption " + arg + " but the overlay is " + (up ? "up" : "down") + " at " + s);
                    break;
                }
                case "expectformat": {
                    String got = ((MainActivity) a).videoModeForTest();
                    if (!arg.equals(got)) throw new AssertionError("A5 expected Bildformat " + arg + " (picture and saved setting) but it is " + got + " at " + s);
                    break;
                }
                case "expecttext": {
                    boolean has = false;
                    for (View v : all(a.getWindow().getDecorView(), android.widget.TextView.class)) {
                        if (String.valueOf(((android.widget.TextView) v).getText()).contains(arg)) has = true;
                    }
                    if (!has) throw new AssertionError("B8 expected a text containing \"" + arg + "\" on screen at " + s);
                    break;
                }
                case "pill": {
                    MainActivity m = (MainActivity) a;
                    if (m.osdFocusForTest().equals("none")) press(a, KeyEvent.KEYCODE_DPAD_CENTER);
                    for (int i = 0; i < 12 && !String.valueOf(m.focusedPillForTest()).startsWith(arg); i++) {
                        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
                    }
                    if (!String.valueOf(m.focusedPillForTest()).startsWith(arg)) {
                        throw new AssertionError("U11 no pill \"" + arg + "\" in " + m.pillsForTest() + " at " + s);
                    }
                    press(a, KeyEvent.KEYCODE_DPAD_CENTER);
                    break;
                }
                case "expectpills": {
                    List<String> got = ((MainActivity) a).pillsForTest();
                    String[] want = arg.split(",");
                    boolean same = got.size() == want.length;
                    for (int i = 0; same && i < want.length; i++) same = got.get(i).startsWith(want[i]);
                    if (!same) throw new AssertionError("U11 expected the pills " + arg + " but the row is " + got + " at " + s);
                    break;
                }
                case "expectpill":
                case "expectnopill": {
                    boolean has = false;
                    for (String p : ((MainActivity) a).pillsForTest()) if (p.startsWith(arg)) has = true;
                    if (has != op.equals("expectpill")) {
                        throw new AssertionError("U11 " + (has ? "a pill starts with \"" : "no pill starts with \"") + arg + "\" at " + s
                                + ": " + ((MainActivity) a).pillsForTest());
                    }
                    break;
                }
                case "expectosdfocus": {
                    String got = ((MainActivity) a).osdFocusForTest();
                    if (!arg.equals(got)) throw new AssertionError("U11 expected the overlay's focus on " + arg + " but it is on " + got + " at " + s);
                    break;
                }
                case "expectfocuspill": {
                    String got = ((MainActivity) a).focusedPillForTest();
                    if (got == null || !got.startsWith(arg)) {
                        throw new AssertionError("U11 expected the focused pill \"" + arg + "\" but it is " + got + " at " + s);
                    }
                    break;
                }
                case "marklive":
                    marks.put(arg, ((MainActivity) a).liveMomentForTest());
                    break;
                case "expectclose": {
                    Long want = marks.get(arg);
                    if (want == null) throw new IllegalStateException("no mark " + arg + " for " + s);
                    long got = ((MainActivity) a).contentMomentForTest();
                    if (got == androidx.media3.common.C.TIME_UNSET || Math.abs(got - want) > 3840) {
                        throw new AssertionError("F18 expected playback within one segment of the live moment marked " + arg + " but it is at "
                                + (got == androidx.media3.common.C.TIME_UNSET ? "unknown" : (got - want) + " ms off") + " at " + s);
                    }
                    System.out.println("CHECK F18 paused " + (got - want) + " ms from the live position at the press");
                    break;
                }
                case "expectpaused": {
                    boolean got = ((MainActivity) a).pausedForTest();
                    if (got != arg.equals("yes")) throw new AssertionError("F18 expected paused=" + arg + " but it is " + got + " at " + s);
                    break;
                }
                case "expectnohint": {
                    View h = a.getWindow().getDecorView().findViewWithTag("hint");
                    String got;
                    if (h != null && h.isShown()) got = String.valueOf(((android.widget.TextView) h).getText());
                    else {
                        View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                        got = o == null || !o.isShown() ? "" : String.join(" ", ((se.lite.play.ui.OsdView) o).shownHints());
                    }
                    if (got.contains(arg)) throw new AssertionError("expected no \"" + arg + "\" in the footer but it is \"" + got + "\" at " + s);
                    break;
                }
                case "expecteyebrow": {
                    View o = find(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class);
                    String got = o == null || !o.isShown() ? "(no OSD)" : ((se.lite.play.ui.OsdView) o).shownEyebrow();
                    if (!arg.equals(got) && !(Fixtures.stress && got.startsWith(arg + " "))) throw new AssertionError("expected the OSD eyebrow \"" + arg + "\" but it is \"" + got + "\" at " + s);
                    break;
                }
                case "expectlisttop": { // R4-A1: the list sits at its top (hero, info and pills where the screen opened)
                    ListView l = (ListView) find(a.getWindow().getDecorView(), ListView.class);
                    boolean atTop = l != null && l.getChildCount() > 0 && l.getFirstVisiblePosition() == 0 && l.getChildAt(0).getTop() == 0;
                    if (!atTop) throw new AssertionError("R4-A1 the list is scrolled (first=" + (l == null ? -1 : l.getFirstVisiblePosition())
                            + " top=" + (l == null || l.getChildCount() == 0 ? 0 : l.getChildAt(0).getTop()) + ") at " + s);
                    break;
                }
                case "expectplayback": {
                    String got = ((MainActivity) a).playbackForTest();
                    if (!arg.equals(got)) throw new AssertionError("F12c expected playback " + arg + " but it is " + got + " at " + s);
                    break;
                }
                case "expectorder": { // B6: these rows of the surface shown come in this order ("a|b|c")
                    List<String> rows = ((MainActivity) a).rowTextsForTest();
                    int from = 0;
                    for (String want : arg.split("\\|")) {
                        int at = -1;
                        for (int i = from; i < rows.size() && at < 0; i++) if (rows.get(i).contains(want)) at = i;
                        if (at < 0) throw new AssertionError("B6 \"" + want + "\" is not after row " + from + " at " + s + ": " + rows);
                        from = at + 1;
                    }
                    break;
                }
                case "expectepisodes": { // B5: room for at least n whole episode rows below the hero band, chips and all
                    int got = ((MainActivity) a).episodeRowsThatFitForTest();
                    if (got < Integer.parseInt(arg)) throw new AssertionError("B5 expected " + arg + " whole episode rows but room for " + got + " at " + s);
                    break;
                }
                case "expectband": { // B5: the hero band (title, meta, pills) is at most n % of the screen height
                    int band = ((MainActivity) a).seriesBandForTest();
                    int screen = a.getResources().getDisplayMetrics().heightPixels;
                    if (band <= 0 || band * 100 > Integer.parseInt(arg) * screen) {
                        throw new AssertionError("B5 the hero band is " + band + " px of " + screen + " (" + (100f * band / screen) + " %), at most " + arg + " % at " + s);
                    }
                    break;
                }
                case "expectrow":
                case "expectnorow": {
                    List<String> rows = ((MainActivity) a).rowTextsForTest();
                    boolean has = false;
                    for (String r : rows) if (r.contains(arg)) has = true;
                    if (has != op.equals("expectrow")) {
                        throw new AssertionError((has ? "a row has \"" : "no row has \"") + arg + "\" at " + s + ": " + rows);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown step " + s);
            }
        }
        Fixtures.delayMs = 0;
        Fixtures.imageDelayMs = 0;
        return geometry;
    }

    /** One key press, down and up, as the "key:" step sends it. */
    private static void press(Activity a, int code) throws InterruptedException {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
        settle(250);
    }

    private static int keyCode(String name) {
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + name);
        if (code == KeyEvent.KEYCODE_UNKNOWN) throw new IllegalArgumentException("unknown key " + name);
        return code;
    }

    /** Lets background API calls finish and advances the main looper's clock by ms. */
    private static void settle(int ms) throws InterruptedException {
        for (int i = 0; i < 8; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idle();
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        Thread.sleep(25);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void shot(Activity a, String name, boolean mayRepeat) throws Exception {
        View root = a.getWindow().getDecorView();
        int w = 1920, h = 1080;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        checkFocusInside(root, name);
        checkWholeRows(root, name);
        checkOneFocus(root, name);
        checkHints(root, name);
        checkFocusOutsideFade(root, name);
        checkRail((MainActivity) a, root, name);
        checkBrand(root, name);
        checkPanels((MainActivity) a, name);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.BLACK);
        paintVideoStandIn(root, canvas);
        // The window background and the SurfaceView would paint over the stand-in picture.
        android.graphics.drawable.Drawable bg = root.getBackground();
        root.setBackground(null);
        View v = find(root, VideoFrame.class);
        View surface = v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0 ? ((ViewGroup) v).getChildAt(0) : null;
        if (surface != null) surface.setVisibility(View.INVISIBLE);
        root.draw(canvas);
        checkRowsCentred(root, name); // after the draw: the rows measure their text block while drawing
        checkGrid((MainActivity) a, name); // after the draw: the grid reports the rects it drew
        checkOverlay((MainActivity) a, root, name); // after the draw: the overlay's drawn rects are those of this frame
        checkReviewR2((MainActivity) a, root, bmp, name);
        if (surface != null) surface.setVisibility(View.VISIBLE);
        root.setBackground(bg);
        // After the draw: the drawn parts of rows and pills are those of this frame, not of a recycled view's last bind.
        java.util.Map<String, Rect> g = new java.util.TreeMap<>();
        collect(root, "", g);
        geometry.put(name, g);
        File out = new File(shotsDir, name + ".png");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        try (FileOutputStream fo = new FileOutputStream(out)) {
            bytes.writeTo(fo);
        }
        int hash = java.util.Arrays.hashCode(bytes.toByteArray());
        if (!mayRepeat) { // R1: a journey that reports a pass on a copied frame did not run
            for (java.util.Map.Entry<String, Integer> e : hashes.entrySet()) {
                if (e.getValue() == hash) throw new AssertionError("R1 shot " + name + " is identical to " + e.getKey());
            }
        }
        hashes.put(name, hash);
        System.out.println("shot " + out);
    }

    /**
     * Round 1 panel rules on every shot: B1 (a rail destination has no title: the rail names it), B2 (a chevron
     * only on a row that opens something; no channel row of Kanaler has one, OK plays).
     */
    static void checkPanels(MainActivity a, String shot) {
        String header = a.sectionHeaderForTest();
        List<String> chevrons = a.chevronViolationsForTest();
        if (!chevrons.isEmpty()) throw new AssertionError("CHECK F1-B2 in shot " + shot + ": " + chevrons);
        if (header != null && !header.isEmpty()) throw new AssertionError("CHECK F1-B1 a rail destination shows a title in shot " + shot + ": \"" + header + "\"");
        // F17 is silent: no overlay line speaks of a place that waits or ended ("väntar", "tillbaka", "har slutat").
        for (View v : all(a.getWindow().getDecorView(), se.lite.play.ui.OsdView.class)) {
            se.lite.play.ui.OsdView o = (se.lite.play.ui.OsdView) v;
            if (!o.isShown()) continue;
            String words = o.shownEyebrow() + " " + o.shownTitle() + " " + o.shownStatus() + " " + String.join(" ", o.shownHints());
            for (String bad : new String[] {"väntar", "↓ tillbaka", "↑ tillbaka", "har slutat"}) {
                if (words.contains(bad)) throw new AssertionError("CHECK F17 the overlay says \"" + bad + "\" in shot " + shot + ": " + words);
            }
        }
    }

    /**
     * B4 on every shot with the grid up: one text column of logo slots, whole rows (E5: nothing past the grid's edges),
     * blocks of one row never overlap, the now-line is inside the grid, a block's width never depends on its title (the
     * stress replay), and the footer is there.
     */
    static void checkGrid(MainActivity a, String shot) {
        GuideGridView g = a.gridForTest();
        if (g == null) return;
        java.util.Map<String, android.graphics.RectF> r = g.debugRects();
        android.graphics.RectF grid = r.get("grid");
        if (grid == null) throw new AssertionError("CHECK B4 the grid drew no grid area in shot " + shot);
        java.util.Map<Integer, List<android.graphics.RectF>> byRow = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, android.graphics.RectF> e : r.entrySet()) {
            String k = e.getKey();
            android.graphics.RectF f = e.getValue();
            if (k.startsWith("block")) {
                if (f.left < grid.left - 0.5f || f.right > grid.right + 0.5f || f.top < grid.top - 0.5f || f.bottom > grid.bottom + 0.5f) {
                    throw new AssertionError("CHECK E5/B4 block " + k + " leaves the grid (" + f + " vs " + grid + ") in shot " + shot);
                }
                byRow.computeIfAbsent(Integer.parseInt(k.substring(5, k.indexOf(':'))), x -> new ArrayList<>()).add(f);
            }
            if (k.startsWith("logo") && (f.top < grid.top || f.bottom > grid.bottom)) throw new AssertionError("CHECK E5/B4 logo slot " + k + " is cut in shot " + shot);
        }
        for (java.util.Map.Entry<Integer, List<android.graphics.RectF>> e : byRow.entrySet()) {
            List<android.graphics.RectF> l = e.getValue();
            for (int i = 1; i < l.size(); i++) {
                if (l.get(i).left < l.get(i - 1).right - 0.5f) throw new AssertionError("CHECK B4 blocks overlap on row " + e.getKey() + " in shot " + shot);
            }
        }
        android.graphics.RectF now = r.get("nowLine");
        if (now != null && (now.centerX() < grid.left || now.centerX() > grid.right)) throw new AssertionError("CHECK B4 the now-line is outside the grid in shot " + shot);
        if (r.get("footer") == null || r.get("ruler") == null) throw new AssertionError("CHECK B4 the grid has no ruler or footer in shot " + shot);
    }

    // ---- Focus check ----------------------------------------------------------------------

    /**
     * The focused view, or the selected row of a focused list, must lie fully inside the visible rect of
     * every ancestor, scale included: getGlobalVisibleRect must equal the view's full rect.
     */
    static void checkFocusInside(View root, String shot) {
        View f = focused(root);
        if (f == null) return; // video: nothing has focus
        List<View> targets = new ArrayList<>();
        targets.add(f);
        if (f instanceof ListView && ((ListView) f).getSelectedView() != null) targets.add(((ListView) f).getSelectedView());
        for (View t : targets) {
            int[] loc = new int[2];
            t.getLocationInWindow(loc);
            Rect full = new Rect(loc[0], loc[1], loc[0] + Math.round(t.getWidth() * t.getScaleX()),
                    loc[1] + Math.round(t.getHeight() * t.getScaleY()));
            Rect vis = new Rect();
            boolean any = t.getGlobalVisibleRect(vis);
            if (!any || !vis.equals(full)) {
                throw new AssertionError("CHECK focus clipped in shot " + shot + ": " + t.getClass().getSimpleName()
                        + " " + describe(t) + " full=" + full.toShortString() + " visible="
                        + (any ? vis.toShortString() : "none"));
            }
        }
    }

    // ---- VISUAL-QA checks C1–C3 (C4 is RowViewTest) -------------------------------------------

    /** C1: no list row is cut at the top of its list (or under its eyebrow band); a row cut at the bottom sits under the fade. */
    static void checkWholeRows(View root, String shot) {
        for (View v : all(root, ListView.class)) {
            ListView l = (ListView) v;
            View first = null;
            for (int i = 0; i < l.getChildCount(); i++) if (l.getChildAt(i).getBottom() > 0) { first = l.getChildAt(i); break; }
            if (first == null) continue;
            boolean fade = l.isVerticalFadingEdgeEnabled() && l.getVerticalFadingEdgeLength() >= 40;
            boolean atEnd = l instanceof se.lite.play.ui.SnapList && ((se.lite.play.ui.SnapList) l).atEnd();
            // A grouped list's band cuts its first row on purpose, through the text under the fade (M-a): firstRowCut knows that cut.
            boolean cut = l instanceof se.lite.play.ui.SnapList ? ((se.lite.play.ui.SnapList) l).firstRowCut() : first.getTop() < 0;
            if (cut && !(fade && atEnd)) {
                throw new AssertionError("CHECK C1 row cut at the top in shot " + shot + ": " + describe(first)
                        + " top=" + first.getTop());
            }
            View last = l.getChildAt(l.getChildCount() - 1);
            if (last.getBottom() > l.getHeight() && !fade) {
                throw new AssertionError("CHECK C1 row cut at the bottom without a fade in shot " + shot + ": "
                        + describe(last));
            }
        }
    }

    /**
     * U10 (C6 and the rail rule): the section's stack holds at most two surfaces below the rail, and from
     * every content list one ← reaches the rail: the Sök list column's left neighbour is the keyboard (two
     * presses, documented), a pill row leaves from its first pill, a key from the keyboard's left column.
     */
    static void checkRail(MainActivity a, View root, String shot) {
        int depth = a.panelDepthForTest();
        if (depth > 1) throw new AssertionError("CHECK C6 the Serie detail is " + depth + " deep in shot " + shot + " (the browser has one level)");
        String section = a.sectionForTest();
        if (section == null || a.surfaceForTest().startsWith("detail")) return; // video, login, the picker, the detail
        View tabs = find(root, se.lite.play.ui.TabsView.class);
        if (tabs == null || !tabs.isShown()) throw new AssertionError("CHECK the browser's tab row is not on screen in shot " + shot + " (lens " + section + ")");
        String left = a.leftTargetForTest();
        boolean ok = left.equals("tabs") || left.equals("key") || left.equals("none");
        if (!ok) throw new AssertionError("CHECK ← from the focus in shot " + shot + " reaches \"" + left + "\"");
    }

    /** C2: at most one view draws the focus look (a focused row card, a keyboard key, a pill, a rail item). */
    static void checkOneFocus(View root, String shot) {
        List<String> looks = new ArrayList<>();
        for (View v : all(root, se.lite.play.ui.RowView.class)) {
            if (((se.lite.play.ui.RowView) v).drawsFocus()) looks.add("row " + describe(v));
        }
        for (View v : all(root, se.lite.play.ui.PillsView.class)) {
            if (((se.lite.play.ui.PillsView) v).drawsFocus()) looks.add("pill " + describe(v));
        }
        for (View v : all(root, KeyboardView.class)) {
            if (v.isFocused()) looks.add("key " + ((KeyboardView) v).focusedKey());
        }
        for (View v : all(root, se.lite.play.ui.OsdView.class)) {
            int a = ((se.lite.play.ui.OsdView) v).focusedAction();
            if (a >= 0) looks.add("osd action " + a);
            if (((se.lite.play.ui.OsdView) v).barFocused()) looks.add("osd bar");
        }
        if (looks.size() > 1) throw new AssertionError("CHECK C2 " + looks.size() + " focus looks in shot " + shot + ": " + looks);
    }

    /** C5: the text block of every drawn row sits on the row's centre line (within 4 px). */
    static void checkRowsCentred(View root, String shot) {
        for (View v : all(root, se.lite.play.ui.RowView.class)) {
            se.lite.play.ui.RowView r = (se.lite.play.ui.RowView) v;
            float c = r.textBlockCentre();
            if (c < 0 || r.getHeight() == 0) continue; // headers and notes, or not drawn yet
            float off = Math.abs(c - r.getHeight() / 2f);
            if (off > 4) {
                throw new AssertionError("CHECK C5 text block off the centre line by " + Math.round(off) + " px in shot "
                        + shot + ": " + describe(v));
            }
        }
    }

    /**
     * R3: the focused row, and the group eyebrow right above it, lie outside the list's fade zones (the
     * fade is drawn only when there is more content on that side).
     */
    static void checkFocusOutsideFade(View root, String shot) {
        for (View v : all(root, ListView.class)) {
            ListView l = (ListView) v;
            if (!l.hasFocus() || !l.isVerticalFadingEdgeEnabled() || l.getChildCount() == 0) continue;
            View sel = l.getSelectedView();
            if (sel == null) continue;
            int fade = l.getVerticalFadingEdgeLength();
            View first = l.getChildAt(0), last = l.getChildAt(l.getChildCount() - 1);
            boolean topFade = l instanceof se.lite.play.ui.SnapList ? ((se.lite.play.ui.SnapList) l).topFaded()
                    : l.getFirstVisiblePosition() > 0 || first.getTop() < 0;
            boolean bottomFade = l.getLastVisiblePosition() < l.getCount() - 1 || last.getBottom() > l.getHeight();
            int top = sel.getTop();
            int i = l.indexOfChild(sel);
            if (i > 0 && l.getChildAt(i - 1) instanceof se.lite.play.ui.RowView
                    && ((se.lite.play.ui.RowView) l.getChildAt(i - 1)).isHeader()) {
                top = l.getChildAt(i - 1).getTop(); // the eyebrow belongs to the row that starts its group
            }
            if (topFade && top < fade) {
                throw new AssertionError("R3 focused row under the top fade in shot " + shot + ": " + describe(sel)
                        + " top=" + top + " fade=" + fade);
            }
            if (bottomFade && sel.getBottom() > l.getHeight() - fade) {
                throw new AssertionError("R3 focused row under the bottom fade in shot " + shot + ": " + describe(sel)
                        + " bottom=" + sel.getBottom() + " limit=" + (l.getHeight() - fade));
            }
        }
    }

    // ---- REVIEW-R2 checks (after the draw) ---------------------------------------------------------

    static void checkReviewR2(MainActivity a, View root, Bitmap bmp, String shot) {
        // R4 / X3: the overlay's pills and the Tablå row agree with hasStartOver.
        String so = a.startOverCheckForTest();
        if (so != null) throw new AssertionError("CHECK R4 start-over call sites disagree in shot " + shot + ": " + so);

        // X1: channel identity is never ellipsized in a logo slot (recorded names; the stressed names are absurd on purpose).
        if (!Fixtures.stress) {
            for (View v : all(root, se.lite.play.ui.RowView.class)) {
                if (((se.lite.play.ui.RowView) v).logoFallbackCut()) {
                    throw new AssertionError("CHECK X1 channel name cut in a row's logo slot in shot " + shot + ": " + describe(v));
                }
            }
            for (View v : all(root, se.lite.play.ui.OsdView.class)) {
                if (((se.lite.play.ui.OsdView) v).logoFallbackCut()) throw new AssertionError("CHECK X1 channel name cut in the OSD logo slot in shot " + shot);
            }
        }

        // Y3: every text row of a list starts its title at one x.
        for (View v : all(root, ListView.class)) {
            ListView l = (ListView) v;
            float x = -1;
            for (int i = 0; i < l.getChildCount(); i++) {
                View ch = l.getChildAt(i);
                if (!(ch instanceof se.lite.play.ui.RowView) || ch.getBottom() <= 0 || ch.getTop() >= l.getHeight()) continue;
                se.lite.play.ui.RowView r = (se.lite.play.ui.RowView) ch;
                if (r.textBlockCentre() < 0) continue; // headers, notes, info, spacers: no text column
                float tx = r.textLeftPx();
                if (x < 0) x = tx;
                else if (Math.abs(tx - x) > 1) {
                    throw new AssertionError("CHECK Y3 two text columns in one list in shot " + shot + ": " + Math.round(x) + " and "
                            + Math.round(tx) + " px (" + describe(r) + ")");
                }
            }
        }

        // Y1 / Y2: with rows above, the top fade erases the list's top edge and the sticky eyebrow names the first row's group.
        View heroView = find(root, se.lite.play.ui.HeroView.class);
        boolean onHero = heroView != null && heroView.isShown(); // the art behind Serie varies pixel by pixel: no edge comparison there
        for (View v : all(root, se.lite.play.ui.SnapList.class)) {
            se.lite.play.ui.SnapList l = (se.lite.play.ui.SnapList) v;
            if (!l.topFaded() || l.getChildCount() == 0) continue;
            int[] loc = new int[2];
            l.getLocationInWindow(loc);
            int top = loc[1];
            for (int x = loc[0] + 16; !onHero && x < loc[0] + l.getWidth() - 16; x += 8) {
                int above = bmp.getPixel(x, top - 2), edge = bmp.getPixel(x, top + 1);
                if (!near(above, edge, 6)) {
                    throw new AssertionError("CHECK Y1 the top fade does not erase the list's top edge in shot " + shot + " at x=" + x
                            + ": " + Integer.toHexString(above) + " above, " + Integer.toHexString(edge) + " at the edge");
                }
            }
            CharSequence want = l.stickyWanted(), got = l.stickyShown();
            if (!java.util.Objects.equals(String.valueOf(want), String.valueOf(got))) {
                throw new AssertionError("CHECK Y2 sticky group eyebrow in shot " + shot + ": wanted \"" + want + "\", drawn \"" + got + "\"");
            }
            // M-a: a scrolled grouped list shows its cue: the first visible row is cut through its text under the band,
            // and the band's lower part holds that row's fading ink (never on the hero, whose art varies pixel by pixel).
            int band = l.band();
            if (band > 0) {
                View cut = l.cutRow();
                if (cut == null) throw new AssertionError("CHECK M-a no row cut under the band in shot " + shot + " (no \"more above\" cue)");
                if (!onHero) {
                    boolean ink = false;
                    int solid = Theme.dp(a, Theme.LIST_FADE - 16);
                    for (int yy = top + solid; yy < top + band && !ink; yy += 2) {
                        for (int x = loc[0] + 16; x < loc[0] + l.getWidth() - 16 && !ink; x += 4) {
                            ink = !near(bmp.getPixel(x, top - 2), bmp.getPixel(x, yy), 24);
                        }
                    }
                    if (!ink) throw new AssertionError("CHECK M-a the band's fade zone holds no ink of the cut row in shot " + shot);
                }
                // M-b: the band names the group of the row it cuts, the first visible row.
                int cutPos = l.getFirstVisiblePosition() + l.indexOfChild(cut);
                CharSequence ofCut = a.groupLabelForTest(cutPos);
                if (!String.valueOf(ofCut).equals(String.valueOf(got))) {
                    throw new AssertionError("CHECK M-b band \"" + got + "\" but the cut row's group is \"" + ofCut + "\" in shot " + shot);
                }
            }
        }

        // M-c: the Sök column's text x is one constant in every state, with or without a thumbnail on screen.
        View kbd = find(root, KeyboardView.class);
        boolean catalogueUp = a.sectionForTest() != null && !a.surfaceForTest().startsWith("detail");
        if (kbd != null && kbd.isShown() && catalogueUp) {
            float want = Theme.dp(a, Theme.SP_L) + Theme.dp(a, Theme.RESULT_W) + Theme.dp(a, Theme.SP_M);
            for (View v : all(root, se.lite.play.ui.RowView.class)) {
                se.lite.play.ui.RowView r = (se.lite.play.ui.RowView) v;
                if (r.textBlockCentre() < 0 || Math.abs(r.textLeftPx() - want) <= 1) continue;
                throw new AssertionError("CHECK M-c Sök text column at " + Math.round(r.textLeftPx()) + " px, not " + Math.round(want)
                        + ", in shot " + shot + ": " + describe(r));
            }
        }

        // Y5: while a key has focus the footer holds key–action pairs only; the column's note explains itself.
        View kb = find(root, KeyboardView.class);
        if (kb != null && kb.isShown() && kb.isFocused()) {
            String[] pairs = a.hintPairsForTest();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                if (pairs[i].isEmpty()) throw new AssertionError("CHECK Y5 a sentence in the key-hint slot in shot " + shot + ": \"" + pairs[i + 1] + "\"");
            }
        }

        // Y6 / E7: an asset or timeline eyebrow starts with its mode word and names its source.
        for (View v : all(root, se.lite.play.ui.OsdView.class)) {
            se.lite.play.ui.OsdView o = (se.lite.play.ui.OsdView) v;
            if (!o.isFrame()) continue;
            if (!o.eyebrowFitsMode()) {
                throw new AssertionError("CHECK Y6/U0 eyebrow does not fit its mode in shot " + shot + ": \"" + o.shownEyebrow() + "\"");
            }
        }

        // U11 / U0: the overlay's row is the one list in the one order (the rail has Kanaler, Tablå and Sök, A4),
        // and no mode word anywhere on it (an eyebrow's kind and source is a title's, not a mode).
        for (View v : all(root, se.lite.play.ui.OsdView.class)) {
            se.lite.play.ui.OsdView o = (se.lite.play.ui.OsdView) v;
            if (!o.isFrame()) continue;
            checkPills(java.util.Arrays.asList(o.shownActions()), shot);
            String words = o.shownEyebrow() + " " + String.join(" ", o.shownHints()) + " " + String.join(" ", o.shownActions());
            for (String m : new String[] {"BÖRJA OM", "FÖRDRÖJT", "Börja om", "Fördröjt"}) {
                if (words.contains(m)) throw new AssertionError("CHECK U0 mode word \"" + m + "\" on the overlay in shot " + shot + ": " + words);
            }
        }

        // Y8 / B5: a chip row shows whole pills only; pills beyond an edge are told by an arrow there, never by a cut word.
        for (View v : all(root, se.lite.play.ui.PillsView.class)) {
            se.lite.play.ui.PillsView p = (se.lite.play.ui.PillsView) v;
            java.util.Map<String, android.graphics.RectF> parts = p.debugRects();
            for (java.util.Map.Entry<String, android.graphics.RectF> e : parts.entrySet()) {
                if (e.getKey().startsWith("pill") && (e.getValue().right > p.getWidth() || e.getValue().left < 0)) {
                    throw new AssertionError("CHECK Y8 a pill is cut by the row's edge in shot " + shot + ": " + e.getKey());
                }
            }
            if (p.moreLeft() != parts.containsKey("moreL")) throw new AssertionError("CHECK Y8 the left arrow and the hidden pills disagree in shot " + shot);
            if (p.moreRight() != parts.containsKey("moreR")) throw new AssertionError("CHECK Y8 the right arrow and the hidden pills disagree in shot " + shot);
        }

        // R4-B1: no UX.md section 6 string is ever ellipsized in a row (a cut negation reads as its opposite).
        for (View v : all(root, se.lite.play.ui.RowView.class)) {
            se.lite.play.ui.RowView r = (se.lite.play.ui.RowView) v;
            if (r.textBlockCentre() < 0) continue;
            String title = String.valueOf(r.getContentDescription()), shown = String.valueOf(r.shownTitle());
            for (String s6 : UX6) {
                if (title.startsWith(s6) && !shown.equals(title)) {
                    throw new AssertionError("CHECK B1 UX.md 6 string cut in shot " + shot + ": \"" + shown + "\"");
                }
            }
        }

        // R4-A2: the hero's art covers its area: no pure-black column or row band right of the panel.
        if (onHero && "image".equals(((se.lite.play.ui.HeroView) heroView).drawnState())) {
            int left = 0;
            for (View v : all(root, android.widget.LinearLayout.class)) if (v.getBackground() == null && v.getWidth() > 600 && v.getWidth() < 1200) left = Math.max(left, v.getWidth());
            if (left == 0) left = 960;
            for (int x = left + 16; x < 1920 - 8; x += 16) {
                boolean black = true;
                for (int y = 8; y < 1080 && black; y += 12) black = Color.red(bmp.getPixel(x, y)) < 10 && Color.green(bmp.getPixel(x, y)) < 10 && Color.blue(bmp.getPixel(x, y)) < 10;
                if (black) throw new AssertionError("CHECK A2 a black column at x=" + x + " in the hero of shot " + shot);
            }
            for (int y = 8; y < 1080 - 8; y += 16) {
                boolean black = true;
                for (int x = left + 16; x < 1920 && black; x += 12) black = Color.red(bmp.getPixel(x, y)) < 10 && Color.green(bmp.getPixel(x, y)) < 10 && Color.blue(bmp.getPixel(x, y)) < 10;
                if (black) throw new AssertionError("CHECK A2 a black row at y=" + y + " in the hero of shot " + shot);
            }
        }

        // A1 / F12b superseded: there is no offset chip. No view shows a signed offset (-33:04, −12:30) anywhere.
        for (View v : all(root, android.widget.TextView.class)) {
            if (v.isShown() && String.valueOf(((android.widget.TextView) v).getText()).trim().matches("^[−-]\\d+:\\d\\d.*")) {
                throw new AssertionError("CHECK A1 a signed offset is on screen in shot " + shot + ": \"" + ((android.widget.TextView) v).getText() + "\"");
            }
        }
    }

    /** U11, A4: the pills are a subsequence of the canonical order (Paus/Spela first); none of them opens a rail section or a sheet. */
    static void checkPills(List<String> pills, String shot) {
        int last = -1;
        for (String p : pills) {
            int r = MainActivity.actionRank(p);
            if (r < 0 || r <= last) throw new AssertionError("CHECK U11 the pills are not in the canonical order in shot " + shot + ": " + pills);
            last = r;
        }
    }

    /**
     * A1, A2 on every shot: the overlay has no wall clock, range or remaining-time element and no text of
     * those kinds; the unified strip draws its pills in every state (clean room 2026-10-17).
     */
    static void checkOverlay(MainActivity a, View root, String shot) {
        se.lite.play.ui.OsdView o = (se.lite.play.ui.OsdView) find(root, se.lite.play.ui.OsdView.class);
        if (o == null || !o.isShown()) return;
        if (!o.isFrame()) return;
        java.util.Map<String, android.graphics.RectF> r = o.drawnRects();
        for (String gone : new String[] {"clock", "range", "label", "meta"}) {
            if (r.containsKey(gone)) throw new AssertionError("CHECK A1 the overlay draws \"" + gone + "\" in shot " + shot);
        }
        for (String t : new String[] {o.shownEyebrow(), String.valueOf(o.shownTitle()), String.join(" ", o.shownBarLabels() == null ? new String[0] : java.util.Arrays.stream(o.shownBarLabels()).filter(java.util.Objects::nonNull).toArray(String[]::new))}) {
            if (t.contains("min kvar") || t.matches(".*[−-]\\d+:\\d\\d.*")) {
                throw new AssertionError("CHECK A1 the overlay shows a remaining or offset time \"" + t + "\" in shot " + shot);
            }
        }
        boolean anyPill = false;
        for (String k : r.keySet()) if (k.startsWith("action")) anyPill = true;
        boolean wanted = o.shownActions().length > 0; // the unified strip: the pills are always there
        if (anyPill != wanted) {
            throw new AssertionError("CHECK A2 the pills are " + (anyPill ? "drawn" : "missing") + " with the overlay's focus on "
                    + a.osdFocusForTest() + " in shot " + shot);
        }
    }

    /** B8: the name of the app is on no screen (the launcher label has it). */
    static void checkBrand(View root, String shot) {
        List<String> texts = new ArrayList<>();
        for (View v : all(root, View.class)) {
            if (v instanceof android.widget.TextView) texts.add(String.valueOf(((android.widget.TextView) v).getText()));
            if (v.getContentDescription() != null) texts.add(String.valueOf(v.getContentDescription()));
            if (v instanceof se.lite.play.ui.OsdView) {
                se.lite.play.ui.OsdView o = (se.lite.play.ui.OsdView) v;
                texts.add(o.shownEyebrow());
                texts.add(String.valueOf(o.shownTitle()));
                texts.add(String.valueOf(o.shownStatus()));
                texts.add(String.join(" ", o.shownHints()));
                texts.add(String.join(" ", o.shownActions()));
            }
        }
        for (String t : texts) {
            if (t.toLowerCase(java.util.Locale.ROOT).contains("flinga")) throw new AssertionError("CHECK B8 the name of the app is on screen in shot " + shot + ": \"" + t + "\"");
        }
    }

    /** UX.md section 6 strings as rows show them: none may ever be ellipsized (R4-B1). */
    static final String[] UX6 = {"Laddar …", "Inget att visa här just nu", "Kunde inte hämta listan", "Sökningen gick inte",
            "Inga träffar för", "Skriv minst två bokstäver", "Inga tidigare sökningar", "Inget sparat ännu", "Programmet gick inte att starta"};

    private static boolean near(int a, int b, int tol) {
        return Math.abs(Color.red(a) - Color.red(b)) <= tol && Math.abs(Color.green(a) - Color.green(b)) <= tol
                && Math.abs(Color.blue(a) - Color.blue(b)) <= tol;
    }

    /**
     * C3: the footer hints follow the state: no "→" without a row to reach, no "OK" without a row that acts.
     * Glyphs: the Back key is always "↩" (never "‹", which read as D-pad left), the last pair of every panel
     * footer, and no hint promises a jump to live (F12c).
     */
    static void checkHints(View root, String shot) {
        for (View v : all(root, se.lite.play.ui.OsdView.class)) {
            String[] pairs = ((se.lite.play.ui.OsdView) v).shownHints();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                if (pairs[i].contains("‹") || pairs[i].contains("›")) throw new AssertionError("CHECK C3 OSD hint with a chevron glyph in shot " + shot + ": " + pairs[i] + " " + pairs[i + 1]);
                if (pairs[i].equals(Theme.BACK) && pairs[i + 1].toLowerCase().contains("direkt")) throw new AssertionError("CHECK F12c Back promises live in shot " + shot);
            }
        }
        View h = root.findViewWithTag("hint");
        if (h == null || !h.isShown()) return;
        String text = String.valueOf(((android.widget.TextView) h).getText());
        if (text.contains("‹")) throw new AssertionError("CHECK C3 panel footer with \"‹\" in shot " + shot + ": " + text);
        int back = text.lastIndexOf(Theme.BACK);
        if (back < 0 || text.indexOf("  ", back) < 0) throw new AssertionError("CHECK C3 panel footer without a \"" + Theme.BACK + " …\" pair at its end in shot " + shot + ": " + text);
        if (text.substring(back).toLowerCase().contains("direkt")) throw new AssertionError("CHECK F12c Back promises live in shot " + shot + ": " + text);
        ListView l = (ListView) find(root, ListView.class);
        se.lite.play.ui.RowAdapter<?> ad = l == null ? null : (se.lite.play.ui.RowAdapter<?>) l.getAdapter();
        boolean anyRow = ad != null && ad.firstSelectable() >= 0;
        if (text.contains("→") && !anyRow) {
            throw new AssertionError("CHECK C3 \"→\" hint with nothing to reach in shot " + shot + ": " + text);
        }
        boolean ok = text.startsWith("OK ") || text.contains("  OK ");
        if (l != null && l.hasFocus()) {
            int sel = l.getSelectedItemPosition();
            boolean acts = sel >= 0 && ad != null && ad.isEnabled(sel);
            if (ok && !acts) throw new AssertionError("CHECK C3 \"OK\" hint without an acting row in shot " + shot + ": " + text);
        }
    }

    static List<View> all(View v, Class<?> type) {
        List<View> out = new ArrayList<>();
        collect(v, type, out);
        return out;
    }

    private static void collect(View v, Class<?> type, List<View> out) {
        if (!v.isShown()) return;
        if (type.isInstance(v)) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), type, out);
        }
    }

    private static String describe(View v) {
        CharSequence d = v.getContentDescription();
        return d == null ? "#" + Integer.toHexString(System.identityHashCode(v)) : "\"" + d + "\"";
    }

    /** getCurrentFocus can be null under Robolectric; walk the tree for the shown, focused view. */
    static View focused(View v) {
        if (!v.isShown()) return null;
        if (v.isFocused()) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = focused(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Bounds of every shown view by tree path, plus the drawn non-text elements of rows and the OSD. */
    private static void collect(View v, String path, java.util.Map<String, Rect> out) {
        if (!v.isShown()) return;
        String key = path + "/" + v.getClass().getSimpleName();
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        out.put(key, new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight()));
        java.util.Map<String, android.graphics.RectF> parts = null;
        if (v instanceof se.lite.play.ui.RowView) parts = ((se.lite.play.ui.RowView) v).debugRects();
        if (v instanceof se.lite.play.ui.OsdView) parts = ((se.lite.play.ui.OsdView) v).debugRects();
        if (v instanceof se.lite.play.ui.PillsView) parts = ((se.lite.play.ui.PillsView) v).debugRects();
        if (v instanceof se.lite.play.ui.HeroView) parts = ((se.lite.play.ui.HeroView) v).debugRects();
        if (v instanceof GuideGridView) parts = ((GuideGridView) v).debugRects();
        if (parts != null) {
            for (java.util.Map.Entry<String, android.graphics.RectF> e : parts.entrySet()) {
                android.graphics.RectF f = e.getValue();
                out.put(key + "#" + e.getKey(), new Rect(Math.round(loc[0] + f.left), Math.round(loc[1] + f.top),
                        Math.round(loc[0] + f.right), Math.round(loc[1] + f.bottom)));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup gr = (ViewGroup) v;
            for (int i = 0; i < gr.getChildCount(); i++) collect(gr.getChildAt(i), path + "/" + i, out);
        }
    }

    /** Paints a 16:9 stand-in picture where the VideoFrame puts the SurfaceView. */
    private static void paintVideoStandIn(View root, Canvas canvas) {
        View v = find(root, VideoFrame.class);
        if (!(v instanceof ViewGroup) || ((ViewGroup) v).getChildCount() == 0) return;
        View surface = ((ViewGroup) v).getChildAt(0);
        int[] loc = new int[2];
        surface.getLocationInWindow(loc);
        Paint p = new Paint();
        p.setShader(new LinearGradient(loc[0], loc[1], loc[0] + surface.getWidth(), loc[1] + surface.getHeight(),
                new int[] {0xFF35506B, 0xFF7A8F6A, 0xFFC9A46A}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(loc[0], loc[1], loc[0] + surface.getWidth(), loc[1] + surface.getHeight(), p);
    }

    static View find(View v, Class<?> type) {
        if (type.isInstance(v)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = find(g.getChildAt(i), type);
                if (r != null) return r;
            }
        }
        return null;
    }
}
