package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import se.lite.play.ui.Progress;

/**
 * U16 (owner, 2026-10-23: "Aktuellt going into Kulturnyheterna"): the programme on air and its progress at a moment,
 * with a fake clock, over the cases the EPG can hold: an overrun of the slot, an overlap, a gap, a stale now/next.
 */
public class ScheduleTest {
    private static final long T0 = 1_792_000_000_000L; // a fixed instant; times below are minutes from it
    private static final long MIN = 60_000L;

    private static Api.Program p(String title, long startMin, long endMin) {
        Api.Program p = new Api.Program();
        p.title = title;
        p.startMs = T0 + startMin * MIN;
        p.endMs = T0 + endMin * MIN;
        return p;
    }

    private static Api.Channel ch(Api.Program now, Api.Program next) {
        Api.Channel c = new Api.Channel();
        c.id = "svt2";
        c.name = "SVT2";
        c.now = now;
        c.next = next;
        return c;
    }

    // ---- Progress: clamped, never past the end ---------------------------------------------------

    @Test
    public void progressIsClampedToTheSlot() {
        long s = T0, e = T0 + 30 * MIN;
        assertEquals(0f, Progress.fraction(s, e, s - MIN), 0f);
        assertEquals(0.5f, Progress.fraction(s, e, s + 15 * MIN), 1e-6f);
        assertEquals(1f, Progress.fraction(s, e, e), 0f);
        assertEquals("an overrun never draws past 100 %", 1f, Progress.fraction(s, e, e + 10 * MIN), 0f);
        assertEquals(-1f, Progress.fraction(e, s, s), 0f); // no span
    }

    @Test
    public void theBarIsRedrawnWhenItMovesOnePixelAndAtTheEnd() {
        long s = T0, e = T0 + 60 * MIN;
        // One hour over 1,500 px: a pixel every 2.4 s.
        long d = Progress.redrawDelay(s, e, 1500f, s + 10 * MIN);
        assertTrue("about 2.4 s, was " + d, d > 2_000 && d <= 2_400);
        // Five minutes over 1,500 px: a pixel every 200 ms (not one jump of 5 px a second).
        assertEquals(200, Progress.redrawDelay(s, s + 5 * MIN, 1500f, s + MIN), 1);
        // Never later than the end: the handover is drawn on time.
        assertEquals(1_000, Progress.redrawDelay(s, s + 5 * MIN, 1f, s + 5 * MIN - 1_000));
        // After the end there is nothing more to draw.
        assertEquals(Progress.MAX_REDRAW_MS, Progress.redrawDelay(s, e, 1500f, e + 1));
        // Never a busy loop.
        assertTrue(Progress.redrawDelay(s, s + 1_000, 100_000f, s + 1) >= Progress.MIN_REDRAW_MS);
    }

    @Test
    public void redrawsMoveTheFillOnePixelEach() {
        long s = T0, e = T0 + 30 * MIN;
        float w = 1300f;
        long t = s + 7 * MIN + 123;
        for (int i = 0; i < 50; i++) {
            float before = Progress.fraction(s, e, t) * w;
            t += Progress.redrawDelay(s, e, w, t);
            float after = Progress.fraction(s, e, t) * w;
            assertTrue("step " + i + " moved " + (after - before) + " px", after - before > 0.2f && after - before <= 1.01f);
        }
    }

    // ---- The programme on air -------------------------------------------------------------------

    @Test
    public void theNextProgrammeTakesOverAtTheEndOfTheSlot() {
        Api.Program aktuellt = p("Aktuellt", 0, 30), kultur = p("Kulturnyheterna", 30, 45);
        Api.Channel c = ch(aktuellt, kultur);
        assertSame(aktuellt, Schedule.onAt(c, null, T0 + 30 * MIN - 1));
        // The now/next pair is five minutes old at 21:30: the next one is on air, never Aktuellt at 100 %.
        assertSame(kultur, Schedule.onAt(c, null, T0 + 30 * MIN));
        assertSame(kultur, Schedule.onAt(c, null, T0 + 32 * MIN));
    }

    @Test
    public void anOverrunOfTheBroadcastIsNotVisibleToTheSchedule() {
        // The broadcast of Aktuellt runs 3 minutes over its slot. The schedule is all the app knows: at 21:30 it is
        // Kulturnyheterna, at 0 %, and the bar of Aktuellt ended at 100 %, not beyond.
        Api.Program aktuellt = p("Aktuellt", 0, 30), kultur = p("Kulturnyheterna", 30, 45);
        aktuellt.broadcastEndMs = T0 + 33 * MIN;
        Api.Program on = Schedule.onAt(ch(aktuellt, kultur), null, T0 + 31 * MIN);
        assertSame(kultur, on);
        assertEquals(1f / 15, Progress.fraction(on.startMs, on.endMs, T0 + 31 * MIN), 1e-6f);
    }

    @Test
    public void anOverlapGoesToTheEarlierSlotUntilItEnds() {
        Api.Program a = p("A", 0, 32), b = p("B", 30, 60);
        List<Api.Program> guide = Arrays.asList(a, b);
        assertSame(a, Schedule.onAt(null, guide, T0 + 31 * MIN));
        assertSame(b, Schedule.onAt(null, guide, T0 + 32 * MIN));
    }

    @Test
    public void aGapShowsNothingOnAir() {
        Api.Program a = p("A", 0, 30), b = p("B", 40, 60);
        assertNull(Schedule.onAt(ch(a, b), Arrays.asList(a, b), T0 + 35 * MIN));
        assertFalse("a gap is not stale data", Schedule.stale(ch(a, b), T0 + 35 * MIN));
    }

    @Test
    public void theGuideIsReadBeforeTheNowNextPair() {
        Api.Program old = p("Aktuellt", 0, 30), real = p("Kulturnyheterna", 30, 45);
        assertSame(real, Schedule.onAt(ch(old, null), Collections.singletonList(real), T0 + 31 * MIN));
    }

    @Test
    public void aStalePairWithNoNextIsNothingAndAsksForARefreshSoon() {
        Api.Channel c = ch(p("Aktuellt", 0, 30), null);
        long t = T0 + 31 * MIN;
        assertNull("never 100 % of a programme that has ended", Schedule.onAt(c, null, t));
        assertTrue(Schedule.stale(c, t));
        assertEquals(t + Schedule.BOUNDARY_SLACK_MS, Schedule.nextRefreshAt(Collections.singletonList(c), t));
    }

    @Test
    public void theNowNextPairIsFetchedAtTheEarliestBoundaryNotOnAFixedTimer() {
        Api.Channel svt1 = ch(p("Rapport", 0, 30), p("Lokala nyheter", 30, 45));
        Api.Channel svt2 = ch(p("Aktuellt", -10, 20), p("Kulturnyheterna", 20, 35));
        long t = T0 + 10 * MIN; // the 15-minute cap is at T0 + 25 min: the boundary at 20 min comes first
        assertEquals(T0 + 20 * MIN + Schedule.BOUNDARY_SLACK_MS, Schedule.nextRefreshAt(Arrays.asList(svt1, svt2), t));
        assertEquals(T0 + 20 * MIN, Schedule.nextBoundary(Arrays.asList(svt1, svt2), t));
        // With a long programme everywhere the pair is still refreshed within 15 minutes.
        Api.Channel film = ch(p("Film", 0, 180), null);
        assertEquals(t + Schedule.NOW_NEXT_MAX_MS, Schedule.nextRefreshAt(Collections.singletonList(film), t));
    }
}
