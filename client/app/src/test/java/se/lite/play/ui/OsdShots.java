package se.lite.play.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

import se.lite.play.Clock;

/**
 * Renders every OSD state over a stand-in picture, the way the tour cannot (playback is render-only on
 * the JVM): the unified strip, a failed tune, the live timeline, start-over,
 * catch-up, an episode, the text mode, and a toast. Output: shots/osd-*.png.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OsdShots {
    private static final int W = 1920, H = 1080;
    private static final File shots = new File(System.getProperty("shots.dir", "shots"));

    @Test
    public void all() throws Exception {
        Theme.animations = false; // animators never run on the JVM; alpha would stay 0
        Context c = RuntimeEnvironment.getApplication();
        long now = Clock.now();
        long h20 = now - 18 * 60_000, h21 = h20 + 60 * 60_000;
        OsdView osd = new OsdView(c);

        // The zap banner is the passive overlay: no pills, no focus (A2).
        String[] withStartOver = pills(true, false, "Från början 20:00", false, false);
        String[] noStartOver = pills(false, false, null, false, false);
        osd.showChannel(1, "SVT1", "Fråga Lund", h20, h21).actions(withStartOver, 0);
        shot(c, osd, null, null, "osd-channel");

        // OK from clean video: the focus is on the first pill.
        osd.showChannel(1, "SVT1", "Fråga Lund", h20, h21).actions(withStartOver, 0).focusRow(OsdView.FOCUS_ACTIONS);
        shot(c, osd, null, null, "osd-channel-actions", true);

        // A channel without start-over: the row is empty; OK there opens the browser.
        osd.showChannel(1, "SVT1", "Fråga Lund", h20, h21).actions(noStartOver, 0).focusRow(OsdView.FOCUS_ACTIONS);
        shot(c, osd, null, null, "osd-channel-no-pills", true);
        osd.focusRow(OsdView.FOCUS_NONE);

        osd.showChannel(1, "SVT1", "Fråga Lund", h20, h21).actions(noStartOver, 0);
        osd.status("Kanalen gick inte att starta.", true);
        shot(c, osd, null, null, "osd-channel-failed");

        osd.showChannel(1, "SVT1", "Fråga Lund", h20, h21).actions(noStartOver, 0);
        osd.status("Går inte att spola tillbaka", false);
        shot(c, osd, null, null, "osd-channel-norewind");

        // ← → from clean video: a scrub on the light overlay (the playhead's ring, no pills); its numbers say how far behind live (A1).
        String[] behind = pills(true, false, "Från början 20:00", true, false);
        osd.showTimeline(1, "SVT1", "Fråga Lund", h20, h21, now - 10_000, now - 10_000 - 12 * 60_000 - 30_000)
                .actions(behind, 0).focusRow(OsdView.FOCUS_BAR);
        shot(c, osd, null, null, "osd-timeline");

        // OK from clean video: the focus is on the first pill. Rewound to the programme's start: the same frame as any delay (F12c).
        osd.showTimeline(1, "SVT1", "Fråga Lund", h20, h21, now - 10_000, h20)
                .actions(behind, 0).focusRow(OsdView.FOCUS_ACTIONS);
        shot(c, osd, null, null, "osd-timeline-from-start", true);

        // A catch-up is its channel's frame: logo, the programme's times, no live tick (A2).
        osd.showTimeline(3, "TV3", "Spin City", now - 5 * 3600_000L, now - 5 * 3600_000L + 30 * 60_000L, now, now - 5 * 3600_000L + 12 * 60_000L + 30_000L)
                .actions(pills(true, true, "Från början", true, false), 0).focusRow(OsdView.FOCUS_ACTIONS);
        shot(c, osd, null, null, "osd-seek", true);

        // A series' episode: kind and source as the eyebrow, 0:00 / total / elapsed on the bar; Avsnitt joins the row.
        osd.showSeek("AVSNITT · TV4 PLAY · STORAGE WARS", "A Plane Flew Over The Cuckoos Nest", 5 * 60_000, 22 * 60_000)
                .actions(pills(true, false, "Från början", false, true), 0).focusRow(OsdView.FOCUS_BAR);
        shot(c, osd, null, null, "osd-episode");

        osd.showText("Det gick inte att starta.").hints("OK", "Försök igen", Theme.BACK, "Avsluta").actions(null, 0).focusRow(OsdView.FOCUS_NONE);
        shot(c, osd, null, null, "osd-text");

        // The timeline has hidden after a seek: a clean picture (no offset chip, A1), and a toast in the corner.
        osd.setVisibility(View.GONE);
        shot(c, osd, "hidden", "Slut. Tillbaka till direkt.", "osd-toast");

        // F14: the delayed channel moves on to the next programme; the overlay is passive, like a zap .
        osd.showTimeline(1, "SVT1", "Lokala nyheter", now - 3 * 3600_000L, now - 3 * 3600_000L + 5 * 60_000L, now, now - 3 * 3600_000L)
                .actions(pills(true, false, "Från början", true, false), 0).focusRow(OsdView.FOCUS_NONE);
        shot(c, osd, null, null, "osd-f14-next-programme");
    }

    /** The overlay's row for a kind of playback, from the app's one list (U11). */
    private static String[] pills(boolean pausable, boolean paused, String restart, boolean direkt, boolean series) {
        return se.lite.play.MainActivity.actionLabels(pausable, paused, restart, null, direkt, series, false).toArray(new String[0]);
    }

    static void shot(Context c, OsdView osd, String hiddenOsd, String snackText, String name) throws Exception {
        shot(c, osd, hiddenOsd, snackText, name, false);
    }

    /** @param ignoredPeek unused: the rail's peek is gone with the rail (clean room 2026-10-17) */
    static void shot(Context c, OsdView osd, String hiddenOsd, String snackText, String name, boolean ignoredPeek) throws Exception {
        FrameLayout root = new FrameLayout(c);
        root.addView(osd, Ui.osdParams());
        Snack snack = Ui.snack(c);
        root.addView(snack, Ui.snackParams(c));
        if (snackText != null) snack.show(snackText, Snack.INFO, 0);
        if (hiddenOsd == null) osd.setVisibility(View.VISIBLE);
        // An eyebrow names mode and source (Y6).
        if (osd.getVisibility() == View.VISIBLE && !osd.eyebrowFitsMode()) {
            throw new AssertionError("CHECK Y6/U0 eyebrow does not fit its mode in " + name + ": " + osd.shownEyebrow());
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, W, H, new int[] {0xFF35506B, 0xFF7A8F6A, 0xFFC9A46A}, null,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, p);
        root.draw(canvas);
        root.removeView(osd);
        shots.mkdirs();
        File out = new File(shots, name + ".png");
        try (FileOutputStream fo = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
        }
        System.out.println("shot " + out);
    }
}
