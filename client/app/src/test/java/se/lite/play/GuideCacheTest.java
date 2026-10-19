package se.lite.play;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

/**
 * The guide cache ages (found on the TV, 2026-10-17: Tablå opened after a day in the app was blank: the
 * cache had no age, so nothing fetched again and every row drew outside the window). The clock moves 5
 * hours ahead — past the one-hour TTL, but inside the recorded guides' coverage (they run about 5.6 h
 * ahead of the recording); beyond that the replay has no data, so the tests stop where the fixtures stop.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GuideCacheTest {
    /** Past the one-hour TTL, but inside the recorded guides' coverage. */
    private static final long AHEAD_MS = 5 * 3600_000L;

    private static void settle() throws Exception {
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
    }

    private MainActivity start() throws Exception {
        Fixtures.install();
        Fixtures.installLogos();
        MainActivity.renderOnly = true;
        MainActivity.languageOverride = "sv";
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture")
                .putString("refreshToken", "fixture").commit();
        app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).edit().clear().commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        assertNotNull("the channels loaded and one is tuned", a.channelForTest());
        return a;
    }

    /** The grid's rows are wanted at draw time (onDraw notifies); a screenshot draws, a plain settle does not. */
    private static void drawGrid(MainActivity a) {
        GuideGridView g = a.gridForTest();
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        g.draw(new android.graphics.Canvas(bmp));
    }

    @Test
    public void theGridRefetchesGuidesOnceTheCacheExpires() throws Exception {
        MainActivity a = start();
        a.openGridForTest(a.playingChannelForTest());
        settle();
        drawGrid(a);
        settle();
        String first = a.gridFocusForTest();
        assertNotNull("the grid opens on the programme on air", first);
        a.closeGridForTest();
        // Five hours pass: every cached guide is older than its TTL now, and the grid must fetch again.
        Clock.offsetMs += AHEAD_MS;
        a.openGridForTest(a.playingChannelForTest());
        settle();
        drawGrid(a);
        settle();
        // The grid opened with a stale cache: the refetches answered, so the focus is a programme again,
        // not the blank state the bug left the grid in. SVT1's programme on air 5 h after the recording.
        String again = a.gridFocusForTest();
        assertNotNull("the grid has a focus after the TTL passed", again);
        assertTrue("the focus is the programme on air now (\"" + again + "\")", again.contains("|Diagnoser"));
    }

    @Test
    public void onAirNowAlsoFallsBackToARefetchOnceTheCacheExpires() throws Exception {
        MainActivity a = start();
        assertNotNull(a.programmeOnAirTitleForTest());
        Clock.offsetMs += AHEAD_MS;
        a.prefetchOnAirForTest();
        settle();
        // The refetched guide feeds onAirNow again: a programme is on air after the TTL passed.
        String after = a.programmeOnAirTitleForTest();
        assertNotNull("a programme is on air after the TTL passed (\"" + after + "\")", after);
    }
}
