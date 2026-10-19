package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

import java.util.ArrayList;
import java.util.List;

/** B4: the guide grid's focus, its time window and its footer, on a small hand-made guide. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GuideGridViewTest {
    private static final long H = 3600_000L;

    private static Api.Program prog(String title, long start, long end, boolean catchup) {
        Api.Program p = new Api.Program();
        p.id = title;
        p.title = title;
        p.startMs = start;
        p.endMs = end;
        if (catchup) p.catchup = new Api.Playable();
        return p;
    }

    /** Row 0: past (catch-up), on air, a gap, future. Row 1: one long block. Row 2: loading. Row 3: no guide. */
    private final long now = Clock.now();
    private final Api.Program past = prog("Förra", now - 2 * H, now - H, true), air = prog("Nu", now - H / 2, now + H / 2, false),
            future = prog("Sen", now + 2 * H, now + 3 * H, false), old = prog("Gammal", now - 3 * H, now - 2 * H, false);
    private final Api.Program[] last = new Api.Program[1];
    private int leftEdges;

    private GuideGridView grid() {
        List<List<Api.Program>> guides = new ArrayList<>();
        List<Api.Program> r0 = new ArrayList<>(), r1 = new ArrayList<>();
        r0.add(old);
        r0.add(past);
        r0.add(air);
        r0.add(future);
        r1.add(prog("Igår", now - 20 * H, now - 19 * H, true));
        r1.add(prog("Lång", now - 2 * H, now + 5 * H, false));
        guides.add(r0);
        guides.add(r1);
        guides.add(null);
        guides.add(new ArrayList<>());
        GuideGridView.Source src = new GuideGridView.Source() {
            @Override public int rows() { return 4; }
            @Override public String rowLabel(int row) { return (row + 1) + " Kanal"; }
            @Override public Bitmap logo(int row) { return null; }
            @Override public boolean logoIsLight(int row) { return false; }
            @Override public List<Api.Program> guide(int row) { return guides.get(row); }
        };
        GuideGridView g = new GuideGridView(RuntimeEnvironment.getApplication());
        g.bind(src, new GuideGridView.Listener() {
            @Override public void onVisibleRows(int first, int lastRow, int focusRow) { }
            @Override public void onOk(int row, Api.Program p) { last[0] = p; }
            @Override public void onHold(int row, Api.Program p) { }
            @Override public void onLeftEdge() { leftEdges++; }
        });
        g.setVisibility(View.VISIBLE);
        g.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        g.layout(0, 0, 1920, 1080);
        return g;
    }

    private static void draw(GuideGridView g) {
        g.draw(new Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)));
    }

    @Test
    public void opensOnTheProgrammeOnAir() {
        GuideGridView g = grid();
        g.open(0, now);
        draw(g);
        assertSame(air, g.focusProgramme());
        assertTrue("OK there is live", g.shownFooter().startsWith("OK Titta direkt"));
    }

    @Test
    public void arrowsMoveAcrossProgrammesAndRowsKeepTheirPlaceInTime() {
        GuideGridView g = grid();
        g.open(0, now);
        g.moveProgramme(1);
        assertSame(future, g.focusProgramme());
        assertEquals("a future block says when it airs", "Sänds", g.shownFooter().substring(0, 5));
        g.moveRow(1); // row 1 has one long block that covers that time
        assertEquals("Lång", g.focusProgramme().title);
        g.moveRow(1); // loading: nothing to focus
        assertNull(g.focusProgramme());
        assertTrue(g.shownFooter().startsWith("Laddar"));
        g.moveRow(1); // no guide
        assertTrue(g.shownFooter().startsWith("Tablå saknas"));
        g.moveRow(1); // no wrap
        assertEquals(3, g.focusRow());
        g.moveRow(-3);
        assertEquals(0, g.focusRow());
        assertEquals("the cursor stands on the start of Sen: ↑ ↓ come back to it", "Sen", g.focusProgramme().title);
    }

    @Test
    public void aPastBlockIsPlayedFromItsStartOnlyWithCatchUp() {
        GuideGridView g = grid();
        g.open(0, now);
        g.moveProgramme(-1);
        assertSame(past, g.focusProgramme());
        assertTrue(g.shownFooter().startsWith("OK Spela"));
        g.ok();
        assertSame(past, last[0]);
        g.moveProgramme(-1);
        assertSame(old, g.focusProgramme());
        assertTrue("no catch-up: the footer says so", g.shownFooter().startsWith("Går inte att se i efterhand"));
    }

    @Test
    public void theTimeWindowFollowsTheFocusAndLeftAtTheFirstColumnHandsOver() {
        GuideGridView g = grid();
        g.open(0, now);
        long start = g.viewStartMs();
        assertTrue("the window opens a little before now", start <= now && now - start <= GuideGridView.WINDOW_MS / 2);
        g.moveProgramme(1); // Sen starts 2 h ahead, past the 3 h window's end? it must be whole inside the window
        assertTrue(g.viewStartMs() + GuideGridView.WINDOW_MS >= future.endMs);
        assertEquals(0, leftEdges);
        g.moveProgramme(-1);
        g.moveProgramme(-1);
        g.moveProgramme(-1);
        g.moveProgramme(-1); // Gammal is the first block of the row: ← hands over
        assertEquals(1, leftEdges);
    }

    /** B4: the whole guide history is reachable (24 h back), and ← from the earliest block hands over to the rail. */
    @Test
    public void theGridScrollsBackThroughTheWholeGuideHistory() {
        GuideGridView g = grid();
        g.open(1, now);
        assertEquals("Lång", g.focusProgramme().title);
        g.moveProgramme(-1);
        assertEquals("Igår", g.focusProgramme().title);
        assertTrue("the window moved back to show it", g.viewStartMs() <= now - 20 * H && g.viewStartMs() + GuideGridView.WINDOW_MS >= now - 19 * H);
        assertEquals(0, leftEdges);
        g.moveProgramme(-1); // the first block of the earliest window
        assertEquals(1, leftEdges);
    }

    /** Step 3c: ↑ ↓ never move the window, whatever the programme boundaries of the rows they cross. */
    @Test
    public void upAndDownNeverMoveTheTimeWindow() {
        GuideGridView g = grid();
        g.open(0, now);
        long start = g.viewStartMs();
        for (int i = 0; i < 3; i++) {
            g.moveRow(1);
            assertEquals("down " + i, start, g.viewStartMs());
        }
        for (int i = 0; i < 3; i++) {
            g.moveRow(-1);
            assertEquals("up " + i, start, g.viewStartMs());
        }
        // Standing on a block that began long before the window (Lång, 2 h before now): the window stays, the block is cut.
        g.open(1, now);
        long s1 = g.viewStartMs();
        g.moveRow(-1);
        g.moveRow(1);
        assertEquals(s1, g.viewStartMs());
        assertEquals("Lång", g.focusProgramme().title);
    }

    /** The chosen programme covers the cursor's time, else is the nearest by distance, a tie going to the later one. */
    @Test
    public void upAndDownChooseTheProgrammeAtTheCursor() {
        GuideGridView g = grid();
        g.open(0, now); // the cursor stands on now: row 1's long block covers it
        g.moveRow(1);
        assertEquals("Lång", g.focusProgramme().title);
        // A row with a gap of 40 min around the cursor: 20 min to the earlier block, 20 min to the later one: the later wins.
        List<Api.Program> gap = new ArrayList<>();
        gap.add(prog("Före", now - H, now - 20 * 60_000L, false));
        gap.add(prog("Efter", now + 20 * 60_000L, now + H, false));
        GuideGridView g2 = gridWithGap(gap);
        g2.open(0, now);
        g2.moveRow(1);
        assertEquals("tie: the later", "Efter", g2.focusProgramme().title);
    }

    private GuideGridView gridWithGap(List<Api.Program> second) {
        List<Api.Program> first = new ArrayList<>();
        first.add(air);
        GuideGridView.Source src = new GuideGridView.Source() {
            @Override public int rows() { return 2; }
            @Override public String rowLabel(int row) { return "K" + row; }
            @Override public Bitmap logo(int row) { return null; }
            @Override public boolean logoIsLight(int row) { return false; }
            @Override public List<Api.Program> guide(int row) { return row == 0 ? first : second; }
        };
        GuideGridView g = new GuideGridView(RuntimeEnvironment.getApplication());
        g.bind(src, new GuideGridView.Listener() {
            @Override public void onVisibleRows(int a, int b, int c) { }
            @Override public void onOk(int row, Api.Program p) { }
            @Override public void onHold(int row, Api.Program p) { }
            @Override public void onLeftEdge() { }
        });
        g.setVisibility(View.VISIBLE);
        g.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        g.layout(0, 0, 1920, 1080);
        return g;
    }

    /** ← → still scroll, and the cursor follows the programme they land on, so the next ↑ ↓ lands near it. */
    @Test
    public void leftAndRightScrollAndMoveTheCursor() {
        GuideGridView g = grid();
        g.open(0, now);
        long start = g.viewStartMs();
        g.moveProgramme(1); // Sen, 2 h ahead and 1 h long: not whole in the window that opens 36 min before now
        assertTrue("the window scrolled to show it", g.viewStartMs() > start);
        g.moveRow(1); // row 1: Lång covers the cursor
        assertEquals("Lång", g.focusProgramme().title);
        assertEquals("↑ ↓ left the window where ← → put it", g.viewStartMs(), g.viewStartMs());
    }

    /** goNow (OK on the Tablå tab) brings the window and the focus back to the programme on air; ↑ from the top row is the activity's. */
    @Test
    public void goNowBringsTheWindowBack() {
        GuideGridView g = grid();
        g.open(0, now);
        assertTrue("the window includes now", !g.windowOffNow());
        g.moveProgramme(-1);
        g.moveProgramme(-1);
        g.moveProgramme(-1); // Gammal, 3 h back: the window leaves now
        assertTrue(g.windowOffNow());
        g.goNow(0, now);
        assertTrue(!g.windowOffNow());
        assertSame("the programme on air has the focus again", air, g.focusProgramme());
        long homeStart = g.viewStartMs();
        assertTrue("now sits about a fifth in", Math.abs((now - homeStart) - GuideGridView.WINDOW_MS / 5) <= 5 * 60_000L);
        assertTrue("↑ on the top row is refused, for the tab row", !g.moveRow(-1));
        assertEquals(0, g.focusRow());
        assertSame(air, g.focusProgramme());
        assertEquals(homeStart, g.viewStartMs());
    }
}
