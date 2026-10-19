package se.lite.play.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import se.lite.play.MainActivity;

/**
 * Owner rule F12c (2026-10-16): "Jag vill inte ha distinktion börja om för bakåt ... Man spolar helt
 * enkelt framåt till live igen!" Rewinding on a channel is rewinding: the switch into the start-over
 * stream is invisible, so the OSD of a start-over session is the frame of any delayed channel, and no
 * hint offers Back as the way to live. Also REVIEW-R4 A2: the hero trims baked-in black bars.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RewindTest {
    @Test
    public void aStartOverSessionLooksLikeAnyDelayedChannel() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now(), start = now - 18 * 60_000, end = start + 3600_000;
        // The live window, rewound 12:30.
        v.showTimeline(MainActivity.delayedEyebrow("SVT1"), "Fråga Lund", start, end, now - 10_000, now - 10_000 - 12 * 60_000 - 30_000);
        String eyebrowDelayed = v.shownEyebrow();
        String[] hintsDelayed = v.shownHints();
        // The start-over stream, at the programme's start.
        v.showTimeline(MainActivity.delayedEyebrow("SVT1"), "Fråga Lund", start, end, now - 10_000, start);
        assertEquals("one eyebrow for both", eyebrowDelayed, v.shownEyebrow());
        assertArrayEquals("one hint set for both", hintsDelayed, v.shownHints());
        assertEquals("the channel alone: no mode word (U0)", "SVT1", eyebrowDelayed);
        String joined = String.join(" ", hintsDelayed);
        assertFalse("Back is never the way to live: " + joined, joined.contains("Direkt"));
        assertFalse("no start-over mode word: " + joined, joined.contains("Börja om"));
        assertTrue("Back hides the strip (clean room): " + joined, joined.endsWith(Theme.BACK + " Dölj"));
        for (String m : OsdView.MODE_WORDS) assertFalse(m, m.contains("BÖRJA OM") || m.contains("FÖRDRÖJT"));
        assertTrue(v.eyebrowFitsMode());
        v.showTimeline("FÖRDRÖJT · SVT1", "Fråga Lund", start, end, now - 10_000, start);
        assertFalse("a mode word on the timeline fails the check", v.eyebrowFitsMode());
    }

    @Test
    public void heroTrimsBakedInBlackBars() {
        Bitmap b = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF000000);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(0xFF7A4A1A);
        c.drawRect(0, 60, 740, 480, p); // picture: pillarbox on the right, letterbox top and bottom
        HeroView h = new HeroView(RuntimeEnvironment.getApplication());
        Rect art = h.content(b);
        assertEquals(0, art.left);
        assertEquals(60, art.top, 4);
        assertEquals(740, art.right, 4);
        assertEquals(480, art.bottom, 4);
        Bitmap clean = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        clean.eraseColor(0xFF203040);
        assertEquals(new Rect(0, 0, 64, 36), h.content(clean));
    }
}
