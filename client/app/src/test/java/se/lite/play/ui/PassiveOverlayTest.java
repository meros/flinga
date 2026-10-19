package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Map;

/**
 * Owner feedback round 1 (A1, A2): the overlay is one component with two states. Passive (a zap, every start)
 * it draws no pills; interactive it draws them. The bar holds the numbers: nothing floats free (no wall
 * clock at the right, no range beside the title, no label right of the bar), the title has the whole width.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PassiveOverlayTest {
    private static Map<String, RectF> render(OsdView v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        v.draw(new Canvas(Bitmap.createBitmap(1920, Math.max(1, v.getMeasuredHeight()), Bitmap.Config.ARGB_8888)));
        return v.debugRects();
    }

    private static boolean drawsPills(Map<String, RectF> r) {
        for (String k : r.keySet()) if (k.startsWith("action")) return true;
        return false;
    }

    @Test
    public void theUnifiedStripAlwaysDrawsThePillsDimUntilTheFocus() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00"}, 0);
        assertTrue("clean room: the strip shows its pills with no key pressed (no passive/interactive split)", drawsPills(render(v)));
        v.focusRow(OsdView.FOCUS_BAR);
        assertTrue("scrubbing keeps the pills on the strip", drawsPills(render(v)));
        v.focusRow(OsdView.FOCUS_ACTIONS);
        assertTrue(drawsPills(render(v)));
        v.focusRow(OsdView.FOCUS_NONE);
        assertTrue("still the same strip", drawsPills(render(v)));
    }

    /** The hint slot of the unified strip: Back hides it, and while scrubbing OK plays from the playhead. */
    @Test
    public void theHintSlotSaysWhatOkDoesInEachState() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus"}, 0);
        assertEquals("no \"OK Visa knappar\" on the unified strip", Theme.BACK + " Dölj", String.join(" ", v.shownHints()));
        v.focusRow(OsdView.FOCUS_BAR);
        assertEquals("OK Spela härifrån " + Theme.BACK + " Dölj", String.join(" ", v.shownHints()));
        v.focusRow(OsdView.FOCUS_ACTIONS);
        assertEquals("the hint while the pills have the focus", Theme.BACK + " Dölj", String.join(" ", v.shownHints()));
        v.focusRow(OsdView.FOCUS_NONE);
        v.clearActions();
        assertEquals("an empty row still only hides", Theme.BACK + " Dölj", String.join(" ", v.shownHints()));
    }

    /** Owner, TCL TV: the passive overlay is bottom aligned like the interactive one; its lowest element ends at the same margin. */
    @Test
    public void passiveAndInteractiveEndAtTheSameBottomMargin() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00"}, 0);
        Map<String, RectF> passive = render(v);
        float height = v.getMeasuredHeight();
        float margin = Theme.dp(RuntimeEnvironment.getApplication(), Theme.SAFE_Y);
        assertEquals("the hint slot ends at the safe margin from the bottom", height - margin, passive.get("hints").bottom, 0.5f);
        assertTrue("the hint is drawn in that slot (text, not just room)", passive.get("hintText") != null && passive.get("hintText").bottom <= passive.get("hints").bottom
                && passive.get("hintText").top >= passive.get("hints").top);
        assertTrue("the bar's last element sits above the hint, nothing floats mid-height", passive.get("labelHead").bottom <= passive.get("hintText").top);
        v.focusRow(OsdView.FOCUS_ACTIONS);
        Map<String, RectF> interactive = render(v);
        assertEquals("the pills end at the same margin", height - margin, interactive.get("action0").bottom, 0.5f);
        assertEquals(passive.get("hints").bottom, interactive.get("hints").bottom, 0f);
    }

    /**
     * Owner, TCL TV: no overlay element sits nearer than the safe margin (SAFE_X) to the screen's left edge (the peek strip is
     * gone with the rail; the safe margin stays), and the same from the right edge.
     */
    @Test
    public void everyElementKeepsTheSafeMarginInEveryState() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        OsdView v = new OsdView(c);
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00", "Direkt"}, 0);
        float gap = Theme.dp(c, Theme.SAFE_X);
        int[] states = {OsdView.FOCUS_NONE, OsdView.FOCUS_BAR, OsdView.FOCUS_ACTIONS, -1};
        for (int st : states) {
            if (st < 0) v.focusBarKeepingPills(); else v.focusRow(st);
            Map<String, RectF> r = render(v);
            for (Map.Entry<String, RectF> e : r.entrySet()) {
                if (e.getKey().equals("view") || e.getValue().width() <= 0) continue;
                // the first pill's plate reaches its own padding into the margin: its text lines up with the title above
                float min = e.getKey().startsWith("action") ? gap - Theme.dp(c, 16) : gap;
                assertTrue("state " + st + ": " + e.getKey() + " is " + e.getValue().left + " px from the left edge, wanted " + min,
                        e.getValue().left >= min);
                assertTrue("state " + st + ": " + e.getKey() + " keeps the same margin at the right",
                        1920 - e.getValue().right >= gap);
            }
        }
    }

    /** ↑ from a pill puts the focus on the bar with the pills still drawn; ↓ goes back to them. */
    @Test
    public void theBarCanHaveTheFocusWhileThePillsStayDrawn() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00"}, 1);
        v.focusRow(OsdView.FOCUS_ACTIONS);
        v.focusBarKeepingPills();
        assertTrue(v.pillsOnBar());
        assertTrue("the pills stay on screen", drawsPills(render(v)));
        v.focusRow(OsdView.FOCUS_ACTIONS);
        assertFalse(v.pillsOnBar());
        v.focusRow(OsdView.FOCUS_BAR); // a scrub keeps the same strip
        assertTrue(drawsPills(render(v)));
    }

    /** The rail's peek is gone with the rail (clean room 2026-10-17). */
    @Test
    public void thePeekIsGone() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00"}, 0);
        assertFalse(v.peekWanted());
        v.focusRow(OsdView.FOCUS_ACTIONS);
        assertFalse("no rail, no peek", v.peekWanted());
        v.focusBarKeepingPills();
        assertFalse(v.peekWanted());
    }

    /** Back is always "↩ Dölj" on the overlay, whatever else the hint slot says (owner: the same wording, above all while scrubbing with the pills up). */
    @Test
    public void backIsAlwaysDolj() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus"}, 0);
        int[] states = {OsdView.FOCUS_NONE, OsdView.FOCUS_BAR, OsdView.FOCUS_ACTIONS};
        for (int st : states) {
            v.focusRow(st);
            assertTrue("state " + st, String.join(" ", v.shownHints()).endsWith(Theme.BACK + " Dölj"));
            assertFalse(String.join(" ", v.shownHints()).contains("Avbryt"));
        }
        v.focusBarKeepingPills();
        assertTrue(String.join(" ", v.shownHints()).endsWith(Theme.BACK + " Dölj"));
    }

    @Test
    public void noNumberFloatsFreeAndTheBarHoldsThem() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showTimeline(1, "SVT1", "Fråga Lund", now - 20 * 60_000, now + 40 * 60_000, now - 10_000, now - 12 * 60_000);
        Map<String, RectF> r = render(v);
        for (String gone : new String[] {"clock", "range", "label"}) {
            assertNull("A1: no \"" + gone + "\" element: the wall clock, the range and the remaining time are gone", r.get(gone));
        }
        for (String k : new String[] {"labelStart", "labelEnd", "labelHead"}) assertNotNull("A1: " + k + " is drawn", r.get(k));
        RectF bar = r.get("bar"), start = r.get("labelStart"), end = r.get("labelEnd"), head = r.get("labelHead");
        assertTrue("the start sits left of the bar's left end", start.right <= bar.left);
        assertTrue("the end sits right of the bar's right end", end.left >= bar.right);
        assertEquals("start and end share the bar's line", start.top, end.top, 0.5f);
        assertTrue("the picture's time is under the bar", head.top >= bar.bottom);
        assertEquals("the title starts where the numbers start", r.get("title").left, start.left, 0.5f);
        assertEquals("the title has the whole width: it ends where the end label ends", r.get("title").right, end.right, 0.5f);
    }
}
