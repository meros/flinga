package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** U15: the idle rule with a fake clock: what counts as idle, when the reset fires, and what it does. */
public class IdleTest {
    private static final long H = Idle.RESET_AFTER_MS, MIN = 60_000L;

    private static Idle.State browsing() {
        return new Idle.State(true, true, true); // live plays in the chip, Tablå open
    }

    private static Idle.State film() {
        return new Idle.State(true, false, false); // a film full screen, playing
    }

    @Test
    public void theTimeoutIsOneHour() {
        assertEquals(60 * 60_000L, Idle.RESET_AFTER_MS);
    }

    @Test
    public void browsingForAnHourResetsToLiveAndNotABeforeIt() {
        long t = 1_000_000;
        Idle idle = new Idle(t);
        assertEquals(Idle.NONE, idle.check(t + H - MIN, browsing()));
        assertEquals(Idle.NONE, idle.check(t + H - 1, browsing()));
        assertEquals(Idle.LIVE, idle.check(t + H, browsing()));
        // After the reset the hour starts again.
        assertEquals(Idle.NONE, idle.check(t + H + MIN, browsing()));
    }

    @Test
    public void aKeyRestartsTheHour() {
        long t = 0;
        Idle idle = new Idle(t);
        idle.activity(t + 50 * MIN);
        assertEquals(Idle.NONE, idle.check(t + H + MIN, browsing()));
        assertEquals(Idle.LIVE, idle.check(t + 50 * MIN + H, browsing()));
    }

    @Test
    public void aFilmThatPlaysIsNeverInterruptedAndTheHourStartsWhenItStops() {
        long t = 0;
        Idle idle = new Idle(t);
        for (long m = 0; m <= 180; m++) assertEquals("minute " + m, Idle.NONE, idle.check(t + m * MIN, film()));
        // Paused at minute 180: the hour counts from there.
        Idle.State paused = new Idle.State(false, false, false);
        assertEquals(Idle.NONE, idle.check(t + 180 * MIN + H - 1, paused));
        assertEquals(Idle.LIVE, idle.check(t + 180 * MIN + H, paused));
    }

    @Test
    public void aSurfaceLeftOpenOverAFilmOnlyClosesTheSurface() {
        Idle.State s = new Idle.State(true, false, true);
        assertTrue(Idle.counts(s));
        assertEquals(Idle.CLOSE, Idle.action(s));
    }

    @Test
    public void aDelayedChannelThatPlaysOnItsOwnIsContentInUse() {
        Idle.State delayed = new Idle.State(true, false, false); // behind live, playing (F14 continues it)
        assertFalse(Idle.counts(delayed));
    }

    @Test
    public void liveWithNothingOpenCountsButOnlyRefreshes() {
        Idle.State live = new Idle.State(true, true, false);
        assertTrue(Idle.counts(live));
        assertEquals(Idle.REFRESH, Idle.action(live));
    }

    @Test
    public void aPausedPictureOrAFailedTuneGoesLive() {
        assertEquals(Idle.LIVE, Idle.action(new Idle.State(false, true, false))); // paused live (start-over held)
        assertEquals(Idle.LIVE, Idle.action(new Idle.State(false, false, false))); // paused film
        Idle.State failed = new Idle.State(true, true, false);
        failed.failed = true;
        assertEquals(Idle.LIVE, Idle.action(failed));
    }

    @Test
    public void nothingHappensBeforeTheChannelsAreLoaded() {
        Idle.State login = browsing();
        login.ready = false;
        Idle idle = new Idle(0);
        assertEquals(Idle.NONE, idle.check(3 * H, login));
    }

    @Test
    public void theNextCheckIsAtTheHourOrWithinAMinute() {
        Idle idle = new Idle(0);
        assertEquals(Idle.CHECK_EVERY_MS, idle.nextCheckInMs(0));
        assertEquals(30_000, idle.nextCheckInMs(H - 30_000));
        assertEquals(1_000, idle.nextCheckInMs(H + 5 * MIN)); // overdue (back from standby): at once
    }
}
