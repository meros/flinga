package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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

/** B5: a chip row shows whole chips only and says with an arrow that more wait beyond an edge. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PillsViewTest {
    private static PillsView chips(int n, int focus, int widthDp) {
        Context c = RuntimeEnvironment.getApplication();
        String[] labels = new String[n];
        int[] w = new int[n], icons = new int[n];
        for (int i = 0; i < n; i++) {
            labels[i] = "Säsong " + (n - i);
            w[i] = Theme.dp(c, Theme.CHIP_W);
        }
        PillsView v = new PillsView(c);
        int W = Theme.dp(c, widthDp);
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, W, v.getMeasuredHeight());
        v.set(labels, w, icons, null, focus, focus);
        v.setSelected(true);
        v.draw(new Canvas(Bitmap.createBitmap(W, v.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        return v;
    }

    private static int shown(PillsView v) {
        int n = 0;
        for (String k : v.debugRects().keySet()) if (k.startsWith("pill")) n++;
        return n;
    }

    private static void assertWhole(PillsView v) {
        for (Map.Entry<String, RectF> e : v.debugRects().entrySet()) {
            if (!e.getKey().startsWith("pill")) continue;
            assertTrue(e.getKey() + " " + e.getValue(), e.getValue().left >= 0 && e.getValue().right <= v.getWidth());
        }
    }

    @Test
    public void wideRowShowsEveryChipAndNoArrow() {
        PillsView v = chips(3, 0, 440);
        assertEquals(3, shown(v));
        assertFalse(v.moreLeft());
        assertFalse(v.moreRight());
    }

    @Test
    public void narrowRowDrawsWholeChipsAndAnArrowTowardTheRest() {
        PillsView v = chips(8, 0, 440);
        assertWhole(v);
        assertTrue(shown(v) < 8);
        assertFalse(v.moreLeft());
        assertTrue(v.moreRight());
        assertTrue(v.debugRects().containsKey("moreR"));
    }

    @Test
    public void scrollingToTheLastChipShowsItWholeAndTheArrowBack() {
        PillsView v = chips(8, 7, 440);
        assertWhole(v);
        assertTrue(v.debugRects().containsKey("pill7"));
        assertTrue(v.moreLeft());
        assertFalse(v.moreRight());
        assertTrue(v.debugRects().containsKey("moreL"));
    }
}
