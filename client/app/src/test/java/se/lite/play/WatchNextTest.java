package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.media.tv.TvContract;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** G6: Fortsätt titta on the home screen: which rows, when one is rewritten, and that a refusing provider is quiet. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WatchNextTest {
    private static final long MIN = 60_000;
    private static final String PKG = "se.lite.play";

    private static Api.Saved saved(String id, String title, boolean series, long posMs, int minutesLeft) {
        Api.Saved s = new Api.Saved();
        s.id = id;
        s.title = title;
        s.series = series;
        s.provider = "SVT Play";
        s.mediaId = series ? "m-" + id : id;
        s.positionMs = posMs;
        s.minutesLeft = minutesLeft;
        s.percent = minutesLeft < 0 ? 0 : (int) (100 * posMs / (posMs + minutesLeft * MIN));
        s.imageUrl = "https%3A%2F%2Fimg.example%2F" + id + ".jpg";
        return s;
    }

    private static List<WatchNext.Entry> entries(Api.Saved... s) {
        return WatchNext.entries(Arrays.asList(s), null, PKG, 1_000_000L);
    }

    // ---- Which rows ----------------------------------------------------------------------------

    @Test
    public void aTitleInProgressContinuesWithPositionAndLength() {
        WatchNext.Entry e = entries(saved("m-film", "Vetenskapens värld", false, 20 * MIN, 31)).get(0);
        assertEquals(WatchNext.TYPE_CONTINUE, e.type);
        assertEquals(20 * MIN, e.positionMs);
        assertEquals(51 * MIN, e.durationMs);
        assertEquals("31 min kvar · SVT Play", e.description);
        assertFalse(e.series);
    }

    @Test
    public void aSeriesWithoutAStartedEpisodeIsNext() {
        WatchNext.Entry e = entries(saved("s-1", "Rapport", true, 0, -1)).get(0);
        assertEquals(WatchNext.TYPE_NEXT, e.type);
        assertEquals("Nästa avsnitt · SVT Play", e.description);
        assertTrue(e.series);
        assertEquals("under a minute watched is not a position", WatchNext.TYPE_NEXT, entries(saved("s-2", "X", true, 30_000, 20)).get(0).type);
    }

    @Test
    public void aFilmNotStartedHasNoRow() {
        assertTrue(entries(saved("m-film", "Film", false, 0, -1)).isEmpty());
    }

    @Test
    public void aRowWithoutATitleOrIdHasNoRow() {
        Api.Saved noTitle = saved("s-1", "", true, 0, -1);
        Api.Saved noId = saved("", "Titel", true, 0, -1);
        assertTrue(entries(noTitle, noId).isEmpty());
    }

    @Test
    public void theLengthComesFromMinutesLeftElseFromThePercent() {
        Api.Saved a = saved("m-a", "A", false, 10 * MIN, 20);
        assertEquals(30 * MIN, WatchNext.durationOf(a));
        Api.Saved b = saved("m-b", "B", false, 10 * MIN, -1);
        b.percent = 25;
        assertEquals(40 * MIN, WatchNext.durationOf(b));
        Api.Saved c = saved("m-c", "C", false, 10 * MIN, -1);
        c.percent = 0;
        assertEquals("unknown", 0, WatchNext.durationOf(c));
        assertTrue("a position without a length is no CONTINUE: a film has no row", entries(c).isEmpty());
    }

    @Test
    public void onlyTheTopRowsGoToTheHomeScreenInTheOrderOfTheList() {
        List<Api.Saved> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) list.add(saved("s-" + i, "T" + i, true, 5 * MIN, 30));
        List<WatchNext.Entry> out = WatchNext.entries(list, null, PKG, 5_000_000L);
        assertEquals(WatchNext.MAX, out.size());
        assertEquals("s-0", out.get(0).id);
        for (int i = 1; i < out.size(); i++) assertTrue("newest first by engagement time", out.get(i).engagedMs < out.get(i - 1).engagedMs);
    }

    @Test
    public void aTitlePlayedHereKeepsItsOwnLaterEngagementTime() throws Exception {
        File dir = Files.createTempDirectory("wn").toFile();
        Library l = new Library(dir, Runnable::run);
        Api.Playable p = new Api.Playable();
        p.mediaId = "m-s-1";
        p.seriesId = "s-1";
        p.seriesTitle = "Serien";
        l.played(p, 10 * MIN, 40 * MIN, null, 9_000_000L);
        List<WatchNext.Entry> out = WatchNext.entries(l.continueWatching, l, PKG, 1_000L);
        assertEquals(9_000_000L, out.get(0).engagedMs);
    }

    @Test
    public void thePosterIsTheScaledSixteenByNineSourceAndTheIntentPlaysTheTitle() {
        WatchNext.Entry e = entries(saved("s-1", "Rapport", true, 5 * MIN, 30)).get(0);
        assertTrue(e.posterUri, e.posterUri.contains("resize=480x270"));
        assertTrue(e.intentUri, e.intentUri.startsWith("intent://title/s-1?play=1#Intent;"));
        assertTrue(e.intentUri.contains("package=se.lite.play"));
        Api.Saved bare = saved("s-2", "Utan bild", true, 5 * MIN, 30);
        bare.imageUrl = null;
        assertNull("no image known: no poster", entries(bare).get(0).posterUri);
    }

    // ---- The plan ------------------------------------------------------------------------------

    private static WatchNext.Entry entry(String id, long pos) {
        WatchNext.Entry e = new WatchNext.Entry();
        e.id = id;
        e.title = "T " + id;
        e.type = WatchNext.TYPE_CONTINUE;
        e.positionMs = pos;
        e.durationMs = 60 * MIN;
        return e;
    }

    private static WatchNext.Row row(long rowId, WatchNext.Entry e) {
        return new WatchNext.Row(rowId, e.id, e);
    }

    @Test
    public void newTitlesGoInChangedOnesAreUpdatedGoneOnesGoOut() {
        List<WatchNext.Row> have = Arrays.asList(row(1, entry("a", 10 * MIN)), row(2, entry("b", 10 * MIN)), row(3, entry("gone", 0)));
        WatchNext.Plan p = WatchNext.plan(have, Arrays.asList(entry("a", 25 * MIN), entry("b", 10 * MIN), entry("new", 5 * MIN)));
        assertEquals(1, p.insert.size());
        assertEquals("new", p.insert.get(0).id);
        assertEquals(Collections.singleton(1L), p.update.keySet());
        assertEquals(Collections.singletonList(3L), p.delete);
    }

    @Test
    public void aPositionThatBarelyMovedIsNotWrittenAgain() {
        WatchNext.Plan p = WatchNext.plan(Arrays.asList(row(1, entry("a", 10 * MIN))), Arrays.asList(entry("a", 10 * MIN + 5_000)));
        assertTrue(p.insert.isEmpty() && p.update.isEmpty() && p.delete.isEmpty());
    }

    @Test
    public void aTitleThatChangedKindIsUpdated() {
        WatchNext.Entry next = entry("a", 0);
        next.type = WatchNext.TYPE_NEXT;
        WatchNext.Plan p = WatchNext.plan(Arrays.asList(row(1, entry("a", 10 * MIN))), Arrays.asList(next));
        assertEquals(Collections.singleton(1L), p.update.keySet());
    }

    @Test
    public void duplicateRowsOfOneTitleAndRowsOfNoTitleAreRemoved() {
        WatchNext.Entry noId = entry("x", 0);
        noId.id = null;
        WatchNext.Plan p = WatchNext.plan(Arrays.asList(row(1, entry("a", 0)), row(2, entry("a", 0)), new WatchNext.Row(3, null, noId)),
                Arrays.asList(entry("a", 0)));
        assertEquals(Arrays.asList(2L, 3L), p.delete);
        assertTrue(p.insert.isEmpty());
    }

    @Test
    public void aFinishedTitleLeavesTheHomeScreen() {
        WatchNext.Plan p = WatchNext.plan(Arrays.asList(row(1, entry("a", 0))), Collections.emptyList());
        assertEquals(Collections.singletonList(1L), p.delete);
    }

    // ---- The columns ---------------------------------------------------------------------------

    @Test
    public void theColumnsCarryTheTitleThePositionAndTheKind() {
        WatchNext.Entry e = entries(saved("s-1", "Rapport", true, 20 * MIN, 31)).get(0);
        ContentValues v = WatchNext.values(e);
        assertEquals("s-1", v.getAsString(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID));
        assertEquals("Rapport", v.getAsString(TvContract.WatchNextPrograms.COLUMN_TITLE));
        assertEquals(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE, (int) v.getAsInteger(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE));
        assertEquals(TvContract.WatchNextPrograms.TYPE_TV_EPISODE, (int) v.getAsInteger(TvContract.WatchNextPrograms.COLUMN_TYPE));
        assertEquals(20 * MIN, (long) v.getAsLong(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS));
        assertEquals(51 * MIN, (long) v.getAsLong(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS));
        assertEquals(TvContract.WatchNextPrograms.ASPECT_RATIO_16_9, (int) v.getAsInteger(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO));
        assertTrue(v.getAsString(TvContract.WatchNextPrograms.COLUMN_INTENT_URI).startsWith("intent://title/s-1"));

        WatchNext.Entry n = entries(saved("s-2", "Nästa", true, 0, -1)).get(0);
        ContentValues nv = WatchNext.values(n);
        assertEquals(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT, (int) nv.getAsInteger(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE));
        assertFalse("a NEXT row has no position", nv.containsKey(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS));
        WatchNext.Entry f = entries(saved("m-film", "Film", false, 20 * MIN, 31)).get(0);
        assertEquals(TvContract.WatchNextPrograms.TYPE_MOVIE, (int) WatchNext.values(f).getAsInteger(TvContract.WatchNextPrograms.COLUMN_TYPE));
    }

    // ---- The provider --------------------------------------------------------------------------

    private static final class FakeStore implements WatchNext.Store {
        final List<WatchNext.Row> rows = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        long next = 100;
        RuntimeException failWith;
        boolean absent;

        @Override
        public List<WatchNext.Row> read() {
            if (failWith != null) throw failWith;
            return absent ? null : new ArrayList<>(rows);
        }

        @Override
        public void delete(long rowId) {
            calls.add("delete " + rowId);
            rows.removeIf(r -> r.rowId == rowId);
        }

        @Override
        public void update(long rowId, WatchNext.Entry e) {
            calls.add("update " + rowId);
            rows.removeIf(r -> r.rowId == rowId);
            rows.add(new WatchNext.Row(rowId, e.id, e));
        }

        @Override
        public void insert(WatchNext.Entry e) {
            calls.add("insert " + e.id);
            rows.add(new WatchNext.Row(next++, e.id, e));
        }
    }

    @Test
    public void aSyncBringsTheStoreToTheWantedRowsAndASecondOneWritesNothing() {
        FakeStore s = new FakeStore();
        WatchNext w = new WatchNext(s);
        assertTrue(w.sync(Arrays.asList(entry("a", 10 * MIN), entry("b", 5 * MIN))));
        assertEquals(Arrays.asList("insert a", "insert b"), s.calls);
        s.calls.clear();
        assertTrue(w.sync(Arrays.asList(entry("a", 10 * MIN), entry("b", 5 * MIN))));
        assertTrue("nothing changed, nothing written: " + s.calls, s.calls.isEmpty());
        assertTrue(w.sync(Arrays.asList(entry("b", 5 * MIN))));
        assertEquals(Arrays.asList("delete 100"), s.calls);
    }

    @Test
    public void aTvWithoutTheProviderIsQuiet() {
        FakeStore s = new FakeStore();
        s.absent = true;
        assertFalse(new WatchNext(s).sync(Arrays.asList(entry("a", 0))));
        assertTrue(s.calls.isEmpty());
    }

    @Test
    public void aRefusedPermissionIsQuietAndNotAskedAgain() {
        FakeStore s = new FakeStore();
        s.failWith = new SecurityException("no WRITE_EPG_DATA");
        WatchNext w = new WatchNext(s);
        assertFalse(w.sync(Arrays.asList(entry("a", 0))));
        s.failWith = null;
        assertFalse("it stays quiet for the process", w.sync(Arrays.asList(entry("a", 0))));
        assertTrue(s.calls.isEmpty());
    }

    @Test
    public void anOtherFailureIsQuietButTriedAgain() {
        FakeStore s = new FakeStore();
        s.failWith = new IllegalStateException("provider died");
        WatchNext w = new WatchNext(s);
        assertFalse(w.sync(Arrays.asList(entry("a", 0))));
        s.failWith = null;
        assertTrue(w.sync(Arrays.asList(entry("a", 0))));
    }

    @Test
    public void thePosterSurvivesTheCopyOnDisk() throws Exception {
        File dir = Files.createTempDirectory("wn2").toFile();
        Library l = new Library(dir, Runnable::run);
        l.applyServer(Arrays.asList(saved("s-1", "Rapport", true, 5 * MIN, 30)), null, 0);
        Library again = new Library(dir, Runnable::run);
        assertTrue(again.load());
        assertEquals("https%3A%2F%2Fimg.example%2Fs-1.jpg", again.continueWatching.get(0).imageUrl);
        assertEquals("s-1", again.saved("s-1").id);
        assertNull(again.saved("nope"));
    }

    // ---- The panel answer carries the art ------------------------------------------------------

    @Test
    public void theRecordedPanelHasNoImageAndAnImageIsReadWhenThereIs() throws Exception {
        File f = new File(System.getProperty("fixtures.dir"), "panel__known-panel_container-continue-watching.json");
        JSONObject j = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        List<Api.Saved> recorded = Api.parsePanel(j.getJSONObject("data").getJSONObject("panelX"));
        assertFalse(recorded.isEmpty());
        assertNull("the recording was made before the query asked for image { source }", recorded.get(0).imageUrl);
        // FABRICATED: the same poster with the field the app's search query already reads on a Poster.
        JSONObject withImage = new JSONObject(
                "{\"posters\":{\"items\":[{\"id\":\"p\",\"a11y\":{\"contentLabel\":\"Rapport, SVT\"},"
                        + "\"image\":{\"source\":\"https%3A%2F%2Fimg.example%2Fa.jpg\"},"
                        + "\"link\":{\"link3\":{\"__typename\":\"LinkSeries\",\"to\":\"s-1\"}},"
                        + "\"progress\":{\"__typename\":\"Progress\",\"id\":\"m-1\",\"position\":6098,\"percent\":5,\"timeLeft\":null}}]}}");
        List<Api.Saved> got = Api.parsePanel(withImage);
        assertEquals("https%3A%2F%2Fimg.example%2Fa.jpg", got.get(0).imageUrl);
    }
}
