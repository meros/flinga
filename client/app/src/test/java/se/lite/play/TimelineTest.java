package se.lite.play;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The ticket's timeLine (skip intro/recap, the official app's SGTimelineItem) and the pill it drives.
 * The types are OPENING_CREDITS, CLOSING_CREDITS and RECAP, each with offset and duration in ms. Every
 * ticket measured so far (catch-up, recordings, ADS.md) carries an empty array; a series episode is the
 * open question, so the pill is only there while the playhead is inside a span.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TimelineTest {
    @Test
    public void theTimelineParsesItsThreeTypesAndSkipsUnknowns() throws Exception {
        JSONArray tl = new JSONArray("["
                + "{\"type\":\"OPENING_CREDITS\",\"offset\":1000,\"duration\":45000},"
                + "{\"type\":\"CLOSING_CREDITS\",\"offset\":900000,\"duration\":60000},"
                + "{\"type\":\"RECAP\",\"offset\":50000,\"duration\":90000},"
                + "{\"offset\":1,\"duration\":2}," // no type: dropped
                + "]"); // whatever Telia adds later
        List<Api.TimelineItem> out = Api.parseTimeline(tl);
        assertEquals(3, out.size());
        assertEquals(Api.TimelineItem.OPENING, out.get(0).type);
        assertEquals(1000, out.get(0).offsetMs);
        assertEquals(45000, out.get(0).durationMs);
        assertEquals(Api.TimelineItem.CLOSING, out.get(1).type);
        assertEquals(Api.TimelineItem.RECAP, out.get(2).type);
    }

    @Test
    public void noTimelineOrAnEmptyOneMeansNoSpans() {
        assertTrue(Api.parseTimeline(null).isEmpty());
        assertTrue(Api.parseTimeline(new JSONArray()).isEmpty());
    }

    @Test
    public void theSkipPillSitsBetweenFranBorjanAndDirekt() {
        int skip = MainActivity.actionRank("Hoppa över intro");
        assertTrue(skip > MainActivity.actionRank("Från början"));
        assertTrue(skip < MainActivity.actionRank("Direkt"));
        // The recap spelling is the same pill: one rank, one place.
        assertEquals(skip, MainActivity.actionRank("Hoppa över recap"));
    }

    @Test
    public void theRowCarriesTheSkipOnlyWhileInsideASpan() {
        List<String> inside = MainActivity.actionLabels(true, false, null, "Hoppa över intro", false, false, false);
        assertEquals(java.util.Arrays.asList("Paus", "Hoppa över intro", "Tablå", "Alternativ"), inside);
        List<String> outside = MainActivity.actionLabels(true, false, null, null, false, false, false);
        assertEquals(java.util.Arrays.asList("Paus", "Tablå", "Alternativ"), outside);
        // Any "Hoppa över …" spelling shares the one rank (startsWith); the label decides the wording.
        assertEquals(MainActivity.actionRank("Hoppa över intro"), MainActivity.actionRank("Hoppa över recap"));
    }
}