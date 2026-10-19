package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * U11, A3, A4: one list of pills for every playback kind, in one order, omitting only what cannot apply. Walks
 * every combination of the five things that can differ (pausable, paused, a restart, behind live or in
 * catch-up, a series' episode). The side rail has Kanaler, Tablå, Sök; none of them is a pill any more.
 */
public class OverlayRowTest {
    @Test
    public void everyCombinationIsASubsequenceOfTheOneOrder() {
        int walked = 0;
        for (int bits = 0; bits < 128; bits++) {
            boolean pausable = (bits & 1) != 0, paused = (bits & 2) != 0, series = (bits & 8) != 0, direkt = (bits & 16) != 0, tracks = (bits & 32) != 0;
            String restart = (bits & 4) != 0 ? "Från början 20:00" : null;
            String skip = (bits & 64) != 0 ? "Hoppa över intro" : null;
            List<String> row = MainActivity.actionLabels(pausable, paused, restart, skip, direkt, series, tracks);
            int last = -1;
            for (String p : row) {
                int r = MainActivity.actionRank(p);
                assertTrue(p + " is in the order: " + row, r >= 0);
                assertTrue(p + " comes after what precedes it: " + row, r > last);
                last = r;
            }
            assertTrue("Tablå is the browser's pill (clean room): " + row, row.contains("Tablå"));
            assertTrue("Alternativ is the picker's pill (clean room): " + row, row.contains("Alternativ"));
            for (String gone : new String[] {"Kanaler", "Sök"}) {
                assertFalse(gone + " is not a pill (A4): " + row, row.contains(gone));
            }
            assertEquals("Paus/Spela leads when it applies: " + row, pausable, !row.isEmpty() && MainActivity.actionRank(row.get(0)) == 0);
            if (pausable) assertEquals(paused ? "Spela" : "Paus", row.get(0));
            assertEquals("Direkt only behind live or in catch-up (A3): " + row, direkt, row.contains("Direkt"));
            assertEquals("Avsnitt only for an episode: " + row, series, row.contains("Avsnitt"));
            assertEquals("Ljud och text only with a choice to make, and last (G5): " + row, tracks, row.contains("Ljud och text"));
            if (tracks) assertEquals("Ljud och text is the last choice pill, before the browser and the picker: " + row, "Ljud och text", row.get(row.size() - 2));
            assertEquals("Från början only when there is one: " + row, restart != null, row.contains(restart == null ? "-" : restart));
            assertEquals("Hoppa över only inside a span: " + row, skip != null, row.contains(skip == null ? "-" : skip));
            walked++;
        }
        assertEquals(128, walked);
    }

    @Test
    public void theOrderIsTheOwnersOne() {
        assertEquals(java.util.Arrays.asList("Paus", "Från början", "Hoppa över", "Direkt", "Avsnitt", "Tablå", "Ljud och text", "Alternativ"),
                java.util.Arrays.asList(MainActivity.ACTION_ORDER));
    }

    @Test
    public void aChannelWithoutStartOverOnCleanLiveHasOnlyTheBrowserAndThePicker() {
        assertEquals(java.util.Arrays.asList("Tablå", "Alternativ"), MainActivity.actionLabels(false, false, null, null, false, false, false));
    }
}
