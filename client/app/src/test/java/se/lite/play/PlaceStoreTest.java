package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** F17: a left channel keeps its content position, not its offset, while its programme lasts, at most 30 min. */
public class PlaceStoreTest {
    private static final long T0 = 1_792_087_200_000L; // 18:00:00, the EPG start of the programme
    private static final long SEGMENT_MS = 3840;

    private static Api.Program programme(boolean startOver) {
        Api.Program p = new Api.Program();
        p.id = "fraga-lund";
        p.title = "Fråga Lund";
        p.startMs = T0;
        p.endMs = T0 + 3600_000;
        p.broadcastStartMs = T0 + 30_000; // the broadcast runs 30 s after the EPG, as on the TV
        p.broadcastEndMs = T0 + 3600_000 - 46_000;
        if (startOver) {
            p.startOver = new Api.Playable();
            p.startOver.videoId = "so-1";
        }
        return p;
    }

    @Test
    public void contentPositionIsTheSameFromTheLiveWindowAndTheStartOverStream() {
        Api.Program p = programme(true);
        long moment = T0 + 30_000 + 5 * 60_000; // five minutes into the broadcast
        // Live window: a window that started an hour before, the player 65 min in.
        long liveWindowStart = T0 - 55 * 60_000;
        assertEquals(moment, PlaceStore.momentOf(liveWindowStart, moment - liveWindowStart));
        assertEquals(5 * 60_000, PlaceStore.positionIn(p, PlaceStore.momentOf(liveWindowStart, moment - liveWindowStart)));
        // Start-over stream: its window starts one segment before the broadcast start (F9).
        long soWindowStart = p.broadcastStartMs - SEGMENT_MS;
        long soPosition = moment - soWindowStart;
        assertEquals(5 * 60_000, PlaceStore.positionIn(p, PlaceStore.momentOf(soWindowStart, soPosition)));
        // Resuming: programme start + position lands on the same broadcast moment.
        PlaceStore s = new PlaceStore();
        PlaceStore.Place pl = s.save("1", p, moment, moment + 60_000);
        assertEquals(5 * 60_000, pl.positionMs);
        assertEquals(moment, pl.momentMs());
        assertEquals(moment - p.broadcastStartMs, pl.momentMs() - p.broadcastStartMs); // the start-over startPositionMs
    }

    @Test
    public void positionNeverGoesBeforeTheProgrammeStart() {
        Api.Program p = programme(true);
        assertEquals(0, PlaceStore.positionIn(p, p.broadcastStartMs - SEGMENT_MS)); // the window's first segment
        Api.Program epgOnly = programme(true);
        epgOnly.broadcastStartMs = 0; // no linear item: the EPG start is the programme start
        assertEquals(90_000, PlaceStore.positionIn(epgOnly, T0 + 90_000));
    }

    @Test
    public void expiresThirtyMinutesAfterLeaving() {
        PlaceStore s = new PlaceStore();
        Api.Program p = programme(true);
        long left = T0 + 5 * 60_000;
        PlaceStore.Place pl = s.save("1", p, left - 60_000, left);
        assertNull(PlaceStore.blocked(pl, left));
        assertNull(PlaceStore.blocked(pl, left + PlaceStore.KEEP_MS));
        assertTrue(PlaceStore.alive(pl, left + PlaceStore.KEEP_MS));
        assertEquals("expired", PlaceStore.blocked(pl, left + PlaceStore.KEEP_MS + 1));
        assertFalse(PlaceStore.alive(pl, left + PlaceStore.KEEP_MS + 1));
    }

    @Test
    public void endsWithTheProgramme() {
        PlaceStore s = new PlaceStore();
        Api.Program p = programme(true);
        long left = PlaceStore.end(p) - 60_000; // a minute before the broadcast end
        PlaceStore.Place pl = s.save("1", p, left - 30_000, left);
        assertNull(PlaceStore.blocked(pl, PlaceStore.end(p) - 1));
        assertEquals("ended", PlaceStore.blocked(pl, PlaceStore.end(p))); // the broadcast end, before the EPG end
        assertTrue(PlaceStore.end(p) < p.endMs);
        Api.Program epgOnly = programme(true);
        epgOnly.broadcastEndMs = 0;
        PlaceStore.Place pl2 = s.save("2", epgOnly, left, left);
        assertEquals("ended", PlaceStore.blocked(pl2, epgOnly.endMs));
    }

    @Test
    public void needsStartOverToResume() {
        PlaceStore s = new PlaceStore();
        PlaceStore.Place pl = s.save("1", programme(false), T0 + 60_000, T0 + 120_000);
        assertEquals("no start-over", PlaceStore.blocked(pl, T0 + 120_000));
        assertTrue(PlaceStore.alive(pl, T0 + 120_000)); // the banner line still names it; the note explains on return
    }

    @Test
    public void keepsOnePlacePerChannelAndAtMostFive() {
        PlaceStore s = new PlaceStore();
        Api.Program p = programme(true);
        for (int i = 1; i <= 7; i++) s.save(String.valueOf(i), p, T0 + i * 1000, T0 + i * 1000);
        assertEquals(PlaceStore.CAP, s.size());
        assertNull(s.get("1"));
        assertNull(s.get("2"));
        assertNotNull(s.get("3"));
        assertNotNull(s.get("7"));
        PlaceStore.Place again = s.save("3", p, T0 + 99_000, T0 + 99_000);
        assertEquals(PlaceStore.CAP, s.size()); // replaced, not added
        assertSame(again, s.get("3"));
        assertEquals(99_000 - 30_000, s.get("3").positionMs);
        assertSame(again, s.take("3"));
        assertNull(s.take("3"));
        assertEquals(PlaceStore.CAP - 1, s.size());
    }

    @Test
    public void pruneDropsDeadPlaces() {
        PlaceStore s = new PlaceStore();
        Api.Program p = programme(true);
        s.save("1", p, T0 + 1000, T0 + 1000);
        s.save("2", p, T0 + 20 * 60_000, T0 + 20 * 60_000);
        s.prune(T0 + 1000 + PlaceStore.KEEP_MS + 1);
        assertNull(s.get("1"));
        assertNotNull(s.get("2"));
        assertNotNull(s.get("2"));
    }
}
