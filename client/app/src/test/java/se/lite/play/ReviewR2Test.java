package se.lite.play;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** The rules behind REVIEW-R2 X2, Y4 and Y10, on the recorded fixtures where they have data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReviewR2Test {

    /**
     * X2: for every Fortsätt titta poster Telia sent, the lane and the text come from one (position,
     * duration) pair: a lane only for a title that resumes (position at or above the resume threshold),
     * at position / (position + minutes left), with "N min kvar" from the same numbers; below the
     * threshold the row says the length ("5 min"), never "kvar"; without any numbers it says what plays.
     */
    @Test
    public void continueRowsAgreeWithTheirLane() throws Exception {
        Fixtures.install();
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture").putString("refreshToken", "fixture").commit();
        List<Api.Saved> items = new Api(app).panel(Api.PANEL_CONTINUE, 20);
        assertTrue("fixture has items", items.size() >= 10);
        int lanes = 0, lengths = 0;
        for (Api.Saved sv : items) {
            MainActivity.ContinueState s = MainActivity.continueState(sv);
            boolean resumes = sv.positionMs >= Library.RESUME_MIN_MS;
            if (resumes && sv.minutesLeft >= 0) {
                long dur = sv.positionMs + sv.minutesLeft * 60_000L;
                assertEquals(sv.title + " lane", sv.positionMs / (float) dur, s.progress, 0.021f);
                int minutesFromLane = Math.round((1 - s.progress) * dur / 60_000f);
                assertEquals(sv.title + " text", sv.minutesLeft + " min kvar", s.text);
                assertTrue(sv.title + " lane vs text", Math.abs(minutesFromLane - sv.minutesLeft) <= 1);
                lanes++;
            } else if (!resumes) {
                assertEquals(sv.title + " has no lane before the resume threshold", -1f, s.progress, 0f);
                assertFalse(sv.title + " says kvar without a lane: " + s.text, s.text.contains("kvar"));
                if (sv.minutesLeft >= 0) {
                    assertTrue(sv.title + " says its length: " + s.text, s.text.matches("\\d+ min"));
                    lengths++;
                } else {
                    assertEquals(sv.title, sv.series ? "Nästa avsnitt" : "Från början", s.text);
                }
            }
        }
        assertTrue("fixture covers started titles", lanes >= 2);
        assertTrue("fixture covers barely started titles (Rapport at 6 s)", lengths >= 2);
    }

    /**
     * REVIEW-R3 M-d: one title, one length. For every Fortsätt titta poster whose media is recorded, the
     * minutes the row shows ("45 min", "31 min kvar" plus the position) equal the total the banner's bar
     * starts with ({@code playbackDurationMs}), whatever Telia's nominal duration says.
     */
    @Test
    public void continueRowLengthIsTheBannerBarTotal() throws Exception {
        Fixtures.install();
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture").putString("refreshToken", "fixture").commit();
        Api api = new Api(app);
        int compared = 0, nominalDiffers = 0;
        for (Api.Saved sv : api.panel(Api.PANEL_CONTINUE, 20)) {
            if (sv.mediaId == null || !Fixtures.file("media__" + sv.mediaId).exists()) continue;
            Api.Asset media = api.media(sv.mediaId, sv.title);
            long bar = MainActivity.playbackDurationMs(sv, media.durationMs);
            MainActivity.ContinueState s = MainActivity.continueState(sv);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) min").matcher(s.text);
            if (!m.find()) continue; // "Nästa avsnitt": no length on the row
            long rowMinutes = Long.parseLong(m.group(1)) + (s.text.contains("kvar") ? Math.round(sv.positionMs / 60_000.0) : 0);
            assertEquals(sv.title + ": row \"" + s.text + "\" vs bar " + bar / 60_000.0 + " min", rowMinutes, Math.round(bar / 60_000.0));
            if (Math.abs(media.durationMs - bar) > 60_000) nominalDiffers++;
            compared++;
        }
        assertTrue("fixture covers recorded media", compared >= 3);
        assertTrue("the fixture has a title whose nominal duration is off (Lyxfällan: 60 min nominal, 45 min real)", nominalDiffers >= 1);
    }

    /** Y4: every key names its own action; Röst says Tala, or the no-recognizer text when dim. */
    @Test
    public void everyKeyHasItsOwnHint() {
        for (String letter : new String[] {"a", "ö", "0", "&", "-"}) {
            assertArrayEquals(letter, new String[] {"OK", "Skriv"}, MainActivity.keyHint(letter, false, true));
            assertArrayEquals(letter, new String[] {"OK", "Skriv"}, MainActivity.keyHint(letter, true, false));
        }
        assertArrayEquals(new String[] {"OK", "Mellanslag"}, MainActivity.keyHint("Mellanslag", true, true));
        assertArrayEquals(new String[] {"OK", "Radera"}, MainActivity.keyHint("Radera", false, true));
        assertArrayEquals(new String[] {"OK", "Rensa"}, MainActivity.keyHint("Rensa", false, true));
        assertArrayEquals("nothing to erase", new String[0], MainActivity.keyHint("Radera", true, true));
        assertArrayEquals("nothing to clear", new String[0], MainActivity.keyHint("Rensa", true, true));
        assertArrayEquals(new String[] {"OK", "Tala"}, MainActivity.keyHint("Röst", true, true));
        assertArrayEquals(new String[] {"", "Röstsökning finns inte på den här enheten"}, MainActivity.keyHint("Röst", true, false));
    }
}
