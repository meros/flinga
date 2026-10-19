package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * VISUAL-QA C4, title priority: pills and meta text give up space before the title does. The rows are
 * laid out at the panel's inner width (768 px minus the panel paddings, as in the tour).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RowViewTest {
    /** The detail column: the panel's width minus its paddings (the rail is gone, clean room). */
    private static int column() {
        android.content.Context c = RuntimeEnvironment.getApplication();
        return Theme.panelWidth(c) - Theme.dp(c, Theme.SAFE_X - Theme.SP_L) - Theme.dp(c, Theme.SP_XL);
    }

    private static RowView draw(RowView r) {
        int W = column();
        r.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        r.layout(0, 0, W, r.getMeasuredHeight());
        r.draw(new Canvas(Bitmap.createBitmap(W, Math.max(1, r.getMeasuredHeight()), Bitmap.Config.ARGB_8888)));
        return r;
    }

    @Test
    public void channelRowKeepsItsProgrammeTitle() {
        // Kanaler (F13): the logo plate is the identity, the now title is the row title.
        RowView r = new RowView(RuntimeEnvironment.getApplication()).reset().tall(true).lane(true)
                .text("Sveriges dummaste", "21:00  Nyheterna").trailing(RowView.TRAIL_CHEVRON)
                .logo(null, "110 Köket");
        assertEquals("Sveriges dummaste", String.valueOf(draw(r).shownTitle()));
    }

    /** X1: the channel's identity is never ellipsized in the 80 x 45 dp row slot, whatever the name. */
    @Test
    public void channelFallbackNameIsNeverCut() {
        for (String name : new String[] {"3 TV3", "7 Sjuan", "110 Köket", "87 SVT Barn/SVT24", "16 TV4 Sportkanalen",
                "19 Kunskapskanalen", "13 Viaplay Sport", "500 TV Malmö"}) {
            RowView r = new RowView(RuntimeEnvironment.getApplication()).reset().tall(true).lane(true)
                    .text("Efterlyst", "21:00  Svenska fall").trailing(RowView.TRAIL_CHEVRON).logo(null, name);
            draw(r);
            assertEquals(name, "name", r.logoDrawn());
            assertTrue(name + " was cut", !r.logoFallbackCut());
            r.setSelected(true); // the focused card draws the same slot
            draw(r);
            assertTrue(name + " was cut on the focused card", !r.logoFallbackCut());
        }
    }

    /** Y3: rows of one list share one text column: a row without a slot starts its text after the list's widest slot. */
    @Test
    public void rowsShareTheListsTextColumn() {
        int indent = Theme.dp(RuntimeEnvironment.getApplication(), Theme.RESULT_W);
        RowView plain = draw(new RowView(RuntimeEnvironment.getApplication()).reset().tall(true).indent(indent).text("Bondeblod", null));
        RowView thumb = draw(new RowView(RuntimeEnvironment.getApplication()).reset().tall(true).indent(indent)
                .text("Bonde söker fru", "Serie · TV4 Play").thumb("k", null, false));
        RowView logo = draw(new RowView(RuntimeEnvironment.getApplication()).reset().tall(true).indent(indent)
                .text("Efterlyst", "21:00  Svenska fall").logo(null, "3 TV3"));
        assertEquals(thumb.textLeftPx(), plain.textLeftPx(), 0.5f);
        assertEquals(thumb.textLeftPx(), logo.textLeftPx(), 0.5f);
    }

    @Test
    public void aLongTitleStillEllipsizesInsideItsSlot() {
        RowView r = new RowView(RuntimeEnvironment.getApplication()).reset().tall(true)
                .text("En programtitel som är alldeles för lång för att få plats på en rad i panelen", null)
                .meta("45 min").trailing(RowView.TRAIL_PLAY);
        String shown = String.valueOf(draw(r).shownTitle());
        assertTrue(shown, shown.endsWith("…") && shown.length() < 60);
    }
}
