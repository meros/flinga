package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** F15 rules of the cache: thresholds, finished titles, server wins except newer local positions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LibraryTest {
    private static final long MIN = 60_000;

    private static Library lib() throws Exception {
        File dir = Files.createTempDirectory("lib").toFile();
        return new Library(dir, Runnable::run);
    }

    private static Api.Playable episode(String id, String series, long serverPos, long dur) {
        Api.Playable p = new Api.Playable();
        p.mediaId = id;
        p.title = "Avsnitt " + id;
        p.seriesId = series;
        p.seriesTitle = series == null ? null : "Serien";
        p.startPositionMs = serverPos;
        p.durationMs = dur;
        return p;
    }

    @Test
    public void shortViewingDoesNotEnterButLongerDoes() throws Exception {
        Library l = lib();
        l.played(episode("m-1", null, 0, 60 * MIN), 90_000, 60 * MIN, null, 1);
        assertTrue(l.continueWatching.isEmpty());
        l.played(episode("m-1", null, 0, 60 * MIN), 3 * MIN, 60 * MIN, null, 2);
        assertEquals(1, l.continueWatching.size());
        assertEquals(57, l.continueWatching.get(0).minutesLeft);
    }

    @Test
    public void finishedFilmLeavesAndSeriesMovesToNextEpisode() throws Exception {
        Library l = lib();
        l.played(episode("m-f", null, 0, 60 * MIN), 30 * MIN, 60 * MIN, null, 1);
        l.played(episode("m-f", null, 0, 60 * MIN), 58 * MIN, 60 * MIN, null, 2);
        assertTrue(l.continueWatching.isEmpty());

        l.played(episode("m-e1", "s-1", 0, 30 * MIN), 28 * MIN, 30 * MIN, "m-e2", 3);
        assertEquals(1, l.continueWatching.size());
        Api.Saved s = l.continueWatching.get(0);
        assertEquals("s-1", s.id);
        assertEquals("m-e2", s.mediaId);
        assertEquals(0, s.positionMs);
    }

    @Test
    public void resumeUsesTheNewerLocalPositionAndSkipsTheFirstMinute() throws Exception {
        Library l = lib();
        Api.Playable p = episode("m-1", "s-1", 10 * MIN, 30 * MIN);
        assertEquals(10 * MIN, l.resumeMs(p));
        l.played(p, 12 * MIN, 30 * MIN, null, 5);
        assertEquals(12 * MIN, l.resumeMs(p));
        assertEquals(0, l.resumeMs(episode("m-2", null, 30_000, 30 * MIN)));
        assertEquals(0, l.resumeMs(episode("m-3", null, 29 * MIN, 30 * MIN))); // finished
    }

    @Test
    public void serverWinsExceptPositionsSavedAfterTheRefreshStarted() throws Exception {
        Library l = lib();
        Api.Playable p = episode("m-1", "s-1", 0, 30 * MIN);
        l.played(p, 12 * MIN, 30 * MIN, null, 100);
        l.applyServer(server(), new ArrayList<>(), 50); // fetched before the local save
        assertEquals(12 * MIN, l.continueWatching.get(0).positionMs);
        l.applyServer(server(), new ArrayList<>(), 200); // fetched after: Telia has it
        assertEquals(5 * MIN, l.continueWatching.get(0).positionMs);
        assertNull(l.local("m-1"));
    }

    /** A fresh server answer, as every refresh parses one. */
    private static List<Api.Saved> server() {
        List<Api.Saved> server = new ArrayList<>();
        Api.Saved s = new Api.Saved();
        s.id = "s-1";
        s.title = "Serien";
        s.series = true;
        s.mediaId = "m-1";
        s.positionMs = 5 * MIN;
        server.add(s);
        return server;
    }

    @Test
    public void myListToggleAndDiskCopy() throws Exception {
        File dir = Files.createTempDirectory("lib").toFile();
        Library l = new Library(dir, Runnable::run);
        l.setInMyList("s-9", "Storage Wars", true, true);
        assertTrue(l.inMyList("s-9"));
        Library again = new Library(dir, Runnable::run);
        assertTrue(again.load());
        assertTrue(again.inMyList("s-9"));
        again.setInMyList("s-9", "Storage Wars", true, false);
        assertFalse(again.inMyList("s-9"));
    }

    private static Api.Saved saved(String id) {
        Api.Saved s = new Api.Saved();
        s.id = id;
        s.title = "Titel " + id;
        s.series = true;
        return s;
    }

    private static String ids(List<Api.Saved> list) {
        StringBuilder b = new StringBuilder();
        for (Api.Saved s : list) b.append(b.length() == 0 ? "" : " ").append(s.id);
        return b.toString();
    }

    /**
     * B6: Min lista shows the starred titles last watched first. Telia's panel rows carry no timestamp (the
     * recorded fixtures have only position, percent and time left), so "last watched" is the order of
     * Fortsätt titta (the panel is Telia's own, newest first, and this TV moves a title it played to the top);
     * starred titles that were never watched keep the order the API returned them in, after the watched ones.
     */
    @Test
    public void starredTitlesComeLastWatchedFirstThenInApiOrder() throws Exception {
        Library l = lib();
        List<Api.Saved> cw = new ArrayList<>(), ml = new ArrayList<>();
        cw.add(saved("s-c"));
        cw.add(saved("s-a"));
        ml.add(saved("s-a"));
        ml.add(saved("s-b"));
        ml.add(saved("s-c"));
        ml.add(saved("s-d"));
        l.applyServer(cw, ml, 1);
        assertEquals("s-c s-a s-b s-d", ids(l.starred()));
        assertEquals("the API order itself is kept", "s-a s-b s-c s-d", ids(l.myList));

        // Playing the starred B on this TV puts it first, before the server has heard of it.
        Api.Playable p = episode("m-b1", "s-b", 0, 30 * MIN);
        l.played(p, 10 * MIN, 30 * MIN, null, 5);
        assertEquals("s-b s-c s-a s-d", ids(l.starred()));
    }
}
