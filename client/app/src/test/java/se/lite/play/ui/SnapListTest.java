package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** VISUAL-QA C1: after any layout or scroll, the first visible row of the panel list starts at or below the top edge. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SnapListTest {
    private static final int W = 656, H = 550;

    private static SnapList list(Context c, int rows) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < rows; i++) items.add("Rad " + i);
        SnapList l = (SnapList) Ui.list(c);
        l.setAdapter(Ui.adapter(items, (it, row) -> row.text(it, null).tall(true)));
        FrameLayout root = new FrameLayout(c);
        root.addView(l, new ViewGroup.LayoutParams(W, H));
        return l;
    }

    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    @Test
    public void centredSelectionNeverLeavesACutRowAtTheTop() {
        SnapList l = list(RuntimeEnvironment.getApplication(), 40);
        View root = (View) l.getParent();
        l.setSelectionFromTop(10, 275 - 60); // the centred default of Kanaler and Tablå
        layout(root);
        assertFalse("first row cut: top=" + l.getChildAt(0).getTop(), l.firstRowCut());
        layout(root); // a second pass (the harness lays out again before a shot) keeps it
        assertFalse(l.firstRowCut());
    }

    /**
     * Y1/Y2: scrolled past a group's eyebrow, the list pins that eyebrow in its top fade band (the band
     * erases the rows under it), so the first visible row is never orphaned from its group.
     */
    @Test
    public void stickyEyebrowNamesTheFirstVisibleRowsGroup() {
        Context c = RuntimeEnvironment.getApplication();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 40; i++) items.add(i % 10 == 0 ? "GRUPP " + i / 10 : "Rad " + i);
        SnapList l = (SnapList) Ui.list(c);
        l.setAdapter(Ui.adapter(items, (it, row) -> row.text(it, null).tall(true).header(it.startsWith("GRUPP"))));
        l.setGroups(pos -> items.get(pos - pos % 10));
        FrameLayout root = new FrameLayout(c);
        root.addView(l, new ViewGroup.LayoutParams(W, H));
        l.setSelectionFromTop(15, 0);
        layout(root);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
        l.draw(new android.graphics.Canvas(bmp));
        assertTrue(l.topFaded());
        int fade = Theme.dp(c, Theme.LIST_FADE);
        // M-a: the row above the selection is cut under the band, through its text, as the cue that more is above.
        View cut = l.cutRow();
        assertTrue("a row is cut under the band", cut != null);
        assertTrue(String.valueOf(cut.getContentDescription()), String.valueOf(cut.getContentDescription()).startsWith("Rad 1")); // a row of group 1
        assertTrue("cut through its text: bottom=" + cut.getBottom(), cut.getBottom() > fade && cut.getBottom() < fade + cut.getHeight() / 2);
        assertFalse("the designed cut is not a C1 cut", l.firstRowCut());
        assertEquals("GRUPP 1", String.valueOf(l.stickyWanted()));
        assertEquals("GRUPP 1", String.valueOf(l.stickyShown()));
        // The band's top edge is erased (transparent); the caption's ink is in its solid part and the cut row's in its fade part.
        assertEquals(0, android.graphics.Color.alpha(bmp.getPixel(W / 2, 1)));
        int solid = Theme.dp(c, Theme.LIST_FADE - 16);
        assertTrue("the sticky eyebrow is drawn in the band", ink(bmp, 0, 200, 0, solid));
        assertTrue("the cut row's text fades in the band's lower part (M-a)", ink(bmp, 0, 300, solid, fade));
        // M-b: when a header would be the cut row, the row before it is cut instead, so the band never doubles a header.
        l.setSelectionFromTop(21, fade + 60);
        layout(root);
        l.draw(new android.graphics.Canvas(bmp));
        cut = l.cutRow();
        assertTrue(cut != null && !"GRUPP 2".equals(String.valueOf(cut.getContentDescription())));
        assertEquals(String.valueOf(cut.getContentDescription()).startsWith("Rad 1") ? "GRUPP 1" : "GRUPP 2", String.valueOf(l.stickyShown()));
        l.setSelectionFromTop(0, 0);
        layout(root);
        l.draw(new android.graphics.Canvas(bmp));
        assertEquals("at the top the header row itself shows", null, l.stickyShown());
    }

    private static boolean ink(android.graphics.Bitmap bmp, int x0, int x1, int y0, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 64) return true;
        return false;
    }

    @Test
    public void aScrollWithoutALayoutSnapsToo() {
        SnapList l = list(RuntimeEnvironment.getApplication(), 40);
        View root = (View) l.getParent();
        layout(root);
        l.scrollListBy(95); // what an arrow scroll does between layouts
        assertTrue(l.firstRowCut());
        l.snapNow(); // what the posted snap after a key does
        assertFalse("first row cut: top=" + l.getChildAt(0).getTop(), l.firstRowCut());
        assertTrue(l.getFirstVisiblePosition() >= 0);
    }
}
