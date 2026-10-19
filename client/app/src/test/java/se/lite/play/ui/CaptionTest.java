package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** G5: how subtitles look and where they sit. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class CaptionTest {
    @Test
    public void theStockSystemStyleGetsTheSafeDefault() {
        CaptionLook l = CaptionLook.from(Color.WHITE, Color.BLACK, 0, Color.BLACK, null, 1f);
        assertEquals(Color.WHITE, l.foreground);
        assertEquals("semi-transparent black", 0xB3000000, l.background);
        assertEquals(CaptionLook.EDGE_NONE, l.edge);
        assertEquals(0.055f, l.size, 0.0001f);
    }

    @Test
    public void aStyleTheViewerSetIsUsed() {
        CaptionLook l = CaptionLook.from(Color.YELLOW, 0xFF000080, 1, Color.BLACK, Typeface.SERIF, 1.5f);
        assertEquals(Color.YELLOW, l.foreground);
        assertEquals(0xFF000080, l.background);
        assertEquals(CaptionLook.EDGE_OUTLINE, l.edge);
        assertEquals(Typeface.SERIF, l.typeface);
        assertEquals(0.055f * 1.5f, l.size, 0.0001f);
        assertEquals("raised and depressed edges are drawn as a shadow", CaptionLook.EDGE_SHADOW, CaptionLook.from(Color.YELLOW, Color.BLACK, 3, 0, null, 1f).edge);
    }

    @Test
    public void theFontScaleIsBounded() {
        assertEquals(0.055f * 2f, CaptionLook.from(Color.YELLOW, Color.BLACK, 0, 0, null, 9f).size, 0.0001f);
        assertEquals(0.055f, CaptionLook.from(Color.YELLOW, Color.BLACK, 0, 0, null, 0f).size, 0.0001f);
    }

    @Test
    public void theSystemStyleReadsWithoutFailing() {
        CaptionLook l = CaptionLook.system(RuntimeEnvironment.getApplication());
        assertNotNull(l);
        assertTrue(l.size >= 0.055f);
    }

    private static FrameLayout stage(Context c, CaptionView v, View overlay) {
        FrameLayout root = new FrameLayout(c);
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, 180, Gravity.BOTTOM));
        relayout(root);
        return root;
    }

    private static void relayout(FrameLayout root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
    }

    @Test
    public void theCaptionSitsAboveTheOverlayWhileItIsUpAndLowerWhenItIsNot() {
        Context c = RuntimeEnvironment.getApplication();
        CaptionView v = new CaptionView(c);
        View overlay = new View(c);
        v.avoid(overlay, () -> 40);
        FrameLayout root = stage(c, v, overlay);
        v.setCue("Hej\nOch hej då");
        relayout(root);

        overlay.setVisibility(View.GONE);
        RectF clear = v.boxRect();
        assertNotNull(clear);
        assertEquals("a safe margin from the bottom", 1080 - Theme.dp(c, Theme.SAFE_Y), clear.bottom, 1f);

        overlay.setVisibility(View.VISIBLE);
        relayout(root);
        RectF up = v.boxRect();
        assertTrue("above the overlay's content (" + (overlay.getTop() + 40) + "): " + up, up.bottom < overlay.getTop() + 40);
        assertTrue("and the same box otherwise", Math.abs(up.width() - clear.width()) < 1f && Math.abs(up.height() - clear.height()) < 1f);
        assertTrue(up.top > 0);
    }

    @Test
    public void textIsFivePointFivePercentOfTheHeight() {
        Context c = RuntimeEnvironment.getApplication();
        CaptionView v = new CaptionView(c);
        View overlay = new View(c);
        v.avoid(overlay, () -> 40);
        FrameLayout root = stage(c, v, overlay);
        overlay.setVisibility(View.GONE);
        v.setCue("Hej");
        relayout(root);
        RectF one = v.boxRect();
        v.setCue("Hej\nHej");
        RectF two = v.boxRect();
        float line = two.height() - one.height();
        assertEquals("a line is about 5.5 % of 1080 px tall (font line height 1.1 to 1.5 x)", 1080 * 0.055f * 1.25f, line, 1080 * 0.055f * 0.3f);
    }

    @Test
    public void noCueNoBoxAndTheViewIsGone() {
        Context c = RuntimeEnvironment.getApplication();
        CaptionView v = new CaptionView(c);
        v.setCue("");
        assertEquals(View.GONE, v.getVisibility());
        assertNull(v.boxRect());
        v.setCue("x");
        assertEquals(View.VISIBLE, v.getVisibility());
        v.setCue(null);
        assertEquals(View.GONE, v.getVisibility());
    }
}
