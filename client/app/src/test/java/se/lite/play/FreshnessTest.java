package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowNetwork;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The owner's TV report of 2026-10-23, replayed offline on the real activity (render-only, recorded fixtures, a fake
 * clock: the looper's for the idle hour, {@link Clock#offsetMs} for the programme times).
 *
 * <ul>
 *   <li>U15 "1 h no activity = reset to live": an hour in Tablå, or paused, returns to live; a title that plays does not.</li>
 *   <li>U16 "Aktuellt going into Kulturnyheterna": the strip never shows a programme whose slot has ended.</li>
 *   <li>E9 "the tablå sometimes shows Laddar after being idle": a stale guide stays on screen while it is fetched again,
 *       a request that never answers ends in the error row, resume and a returning network fetch again.</li>
 *   <li>E10 "always responsive": no request runs on the main thread.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FreshnessTest {
    private ActivityController<MainActivity> controller;
    private volatile boolean release;
    private final List<String> onMain = new CopyOnWriteArrayList<>();

    @After
    public void tearDown() {
        release = true;
        Fixtures.delayMs = 0;
        setDeadline(12_000);
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
    }

    /** Moves the looper's clock (the idle hour) in steps, letting background answers land in between. */
    private static void pass(Duration d) throws Exception {
        long steps = Math.max(1, d.toMinutes());
        for (long i = 0; i < steps; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(d.toMillis() / steps));
            Thread.sleep(5);
        }
        settle();
    }

    /** Http.deadlineMs, set by reflection so this test also runs (and fails) against the code before the fix. */
    private static void setDeadline(long ms) {
        try {
            java.lang.reflect.Field f = Http.class.getDeclaredField("deadlineMs");
            f.setAccessible(true);
            f.setLong(null, ms);
        } catch (ReflectiveOperationException ignored) {
            // the code before the fix had no deadline
        }
    }

    private MainActivity start() throws Exception {
        Fixtures.install();
        Fixtures.installLogos();
        Http.Transport replay = Http.transport;
        Http.transport = (method, url, headers, body, type) -> {
            if (Looper.getMainLooper().isCurrentThread()) onMain.add(url); // E10: never a request on the main thread
            return replay.request(method, url, headers, body, type);
        };
        MainActivity.renderOnly = true;
        MainActivity.languageOverride = "sv";
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture")
                .putString("refreshToken", "fixture").commit();
        app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).edit().clear().commit();
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = controller.get();
        settle();
        assertNotNull("the channels loaded and one is tuned", a.channelForTest());
        return a;
    }

    private static void press(MainActivity a, int key) throws Exception {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    }

    private static void drawGrid(MainActivity a) {
        GuideGridView g = a.gridForTest();
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        g.draw(new android.graphics.Canvas(bmp));
    }

    /** Counts the requests whose body names a GraphQL operation (with its variable when given). */
    private static AtomicInteger count(String op, String channelId) {
        AtomicInteger n = new AtomicInteger();
        Http.Transport t = Http.transport;
        Http.transport = (method, url, headers, body, type) -> {
            if (body != null && body.contains("\"operationName\":\"" + op + "\"")
                    && (channelId == null || body.contains("\"channelId\":\"" + channelId + "\""))) n.incrementAndGet();
            return t.request(method, url, headers, body, type);
        };
        return n;
    }

    // ---- U15: the idle reset -----------------------------------------------------------------------

    @Test
    public void anHourInTablaReturnsToLiveOnTheSameChannel() throws Exception {
        MainActivity a = start();
        String ch = a.channelForTest();
        press(a, KeyEvent.KEYCODE_GUIDE);
        assertEquals("grid", a.surfaceForTest());
        pass(Duration.ofMinutes(58));
        assertEquals("still in Tablå before the hour", "grid", a.surfaceForTest());
        pass(Duration.ofMinutes(3));
        String s = a.surfaceForTest();
        assertFalse("the browser closed after an hour without a key (" + s + ")", s.equals("grid") || s.startsWith("browser"));
        assertEquals("the last live channel", ch, a.channelForTest());
        assertEquals("live", a.playbackForTest());
    }

    @Test
    public void aKeyRestartsTheHour() throws Exception {
        MainActivity a = start();
        press(a, KeyEvent.KEYCODE_GUIDE);
        pass(Duration.ofMinutes(50));
        press(a, KeyEvent.KEYCODE_DPAD_DOWN); // a move in the grid
        pass(Duration.ofMinutes(50));
        assertEquals("100 minutes in Tablå, but a key at 50", "grid", a.surfaceForTest());
    }

    @Test
    public void aPausedLivePictureGoesLiveAfterAnHour() throws Exception {
        MainActivity a = start();
        press(a, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        settle();
        assumeTrue("the fixture channel can pause (start-over)", a.pausedForTest());
        pass(Duration.ofMinutes(61));
        assertFalse("no longer paused", a.pausedForTest());
        assertEquals("live", a.playbackForTest());
    }

    @Test
    public void aCatchUpThatPlaysIsNeverInterrupted() throws Exception {
        MainActivity a = start();
        press(a, KeyEvent.KEYCODE_GUIDE);
        settle();
        drawGrid(a);
        settle();
        press(a, KeyEvent.KEYCODE_DPAD_LEFT); // the programme before the one on air
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        settle();
        assumeTrue("a catch-up started from Tablå", "ondemand".equals(a.playbackForTest()));
        pass(Duration.ofMinutes(130));
        assertEquals("two hours without a key: the catch-up still plays", "ondemand", a.playbackForTest());
    }

    // ---- U16: the programme on air never runs past its slot ------------------------------------------

    @Test
    public void theStripNeverShowsAProgrammeWhoseSlotHasEnded() throws Exception {
        MainActivity a = start();
        String id = a.channelForTest();
        JSONArray items = new JSONObject(Fixtures.read(Fixtures.file("nownext"))).getJSONObject("data")
                .getJSONObject("channels").getJSONArray("channelItems");
        String nowTitle = null, nextTitle = null;
        long nowEnd = 0, t = Clock.now();
        for (int i = 0; i < items.length(); i++) {
            if (!items.getJSONObject(i).getString("id").equals(id)) continue;
            JSONArray ps = items.getJSONObject(i).getJSONObject("programsByTime").getJSONArray("programItems");
            for (int k = 0; k < ps.length(); k++) {
                JSONObject p = ps.getJSONObject(k);
                long s = p.getJSONObject("startTime").getLong("timestamp"), e = p.getJSONObject("endTime").getLong("timestamp");
                if (s <= t && t < e) {
                    nowTitle = p.getString("title");
                    nowEnd = e;
                } else if (s > t && nextTitle == null) {
                    nextTitle = p.getString("title");
                }
            }
        }
        assertNotNull(nowTitle);
        // A minute into the next slot; the pair the app holds is the one fetched before the boundary.
        Clock.offsetMs += nowEnd - Clock.now() + 60_000;
        press(a, KeyEvent.KEYCODE_DPAD_CENTER); // the strip on live
        String shown = a.osdForTest().shownTitle();
        assertNotEquals("the strip shows " + nowTitle + " a minute after its slot ended", nowTitle, shown);
        assertEquals("the next programme takes over at once", nextTitle, shown);
    }

    @Test
    public void theGridRedrawsOnItsOwnWhileItIsOpen() throws Exception {
        MainActivity a = start();
        press(a, KeyEvent.KEYCODE_GUIDE);
        settle();
        drawGrid(a);
        long next;
        try {
            next = (Long) GuideGridView.class.getDeclaredMethod("redrawInForTest").invoke(a.gridForTest());
        } catch (NoSuchMethodException e) {
            throw new AssertionError("the grid plans no redraw of its own: the now-line and the lanes stand still");
        }
        // The now-line moves a pixel every 3 h / grid width (about 7 s at 1920 px); the lanes at least as often.
        assertTrue("the next redraw is planned within 10 s (" + next + " ms)", next > 0 && next <= 10_000);
    }

    // ---- E9: fresh, never stuck on "Laddar …" --------------------------------------------------------

    @Test
    public void aStaleGuideStaysOnScreenWhileItIsFetchedAgain() throws Exception {
        MainActivity a = start();
        int row = a.playingChannelForTest();
        String id = a.channelIdsForTest().get(row);
        a.openGridForTest(row);
        settle();
        drawGrid(a);
        settle();
        drawGrid(a);
        assertFalse("the guide loaded", a.gridForTest().debugRects().containsKey("block" + row + ":none"));
        a.closeGridForTest();
        AtomicInteger refetch = count("guide", id);
        Clock.offsetMs += 2 * 3600_000L; // two hours idle: the guide is older than its TTL
        Fixtures.delayMs = 1; // the refetch has not answered yet
        a.openGridForTest(row);
        drawGrid(a);
        assertFalse("the old programmes are drawn while the guide is fetched again, not \"Laddar …\"",
                a.gridForTest().debugRects().containsKey("block" + row + ":none"));
        assertNotNull(a.gridFocusForTest());
        assertFalse("a focus on a programme", a.gridFocusForTest().endsWith("|"));
        Fixtures.delayMs = 0;
        settle();
        drawGrid(a);
        settle();
        assertTrue("the stale guide was fetched again", refetch.get() >= 1);
    }

    @Test
    public void aGuideRequestThatNeverAnswersEndsInTheErrorRow() throws Exception {
        setDeadline(300);
        MainActivity a = start();
        int row = a.playingChannelForTest();
        String id = a.channelIdsForTest().get(row);
        Http.Transport t = Http.transport;
        Http.transport = (method, url, headers, body, type) -> {
            if (body != null && body.contains("\"operationName\":\"guide\"") && body.contains("\"channelId\":\"" + id + "\"")) {
                long until = System.currentTimeMillis() + 60_000;
                while (!release && System.currentTimeMillis() < until) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break; // the deadline gave up on it
                    }
                }
                throw new java.io.IOException("connection reset (fixture: a request that never answered)");
            }
            return t.request(method, url, headers, body, type);
        };
        a.openGridForTest(row);
        drawGrid(a);
        settle();
        drawGrid(a);
        settle();
        drawGrid(a);
        String footer = a.gridForTest().shownFooter();
        assertFalse("not an endless Laddar (" + footer + ")", footer.contains("Laddar"));
        assertTrue("the row offers OK Försök igen (" + footer + ")", footer.contains("OK Försök igen"));
        // The connection comes back: OK on the row fetches it again and the programmes arrive.
        release = true;
        Http.transport = t;
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        settle();
        drawGrid(a);
        settle();
        assertFalse("after OK Försök igen the guide is there",
                a.gridForTest().debugRects().containsKey("block" + row + ":none"));
    }

    @Test
    public void comingBackFromStandbyFetchesTheDataAgain() throws Exception {
        MainActivity a = start();
        AtomicInteger nowNext = count("nowNext", null);
        controller.pause().stop();
        Clock.offsetMs += 3 * 60_000L; // the TV was in standby
        controller.start().resume();
        settle();
        assertTrue("the now/next pair is fetched again on resume", nowNext.get() >= 1);
    }

    @Test
    public void theNetworkComingBackFetchesTheDataAgain() throws Exception {
        MainActivity a = start();
        AtomicInteger nowNext = count("nowNext", null);
        Clock.offsetMs += 3 * 60_000L;
        ConnectivityManager cm = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager.class);
        for (ConnectivityManager.NetworkCallback cb : shadowOf(cm).getNetworkCallbacks()) cb.onAvailable(ShadowNetwork.newInstance(1));
        settle();
        assertTrue("the now/next pair is fetched again when the network returns", nowNext.get() >= 1);
    }

    // ---- E10: nothing on the main thread --------------------------------------------------------------

    @Test
    public void noRequestRunsOnTheMainThread() throws Exception {
        MainActivity a = start();
        press(a, KeyEvent.KEYCODE_GUIDE);
        settle();
        drawGrid(a);
        settle();
        pass(Duration.ofMinutes(61)); // the idle reset and its refresh
        controller.pause().stop();
        controller.start().resume();
        settle();
        assertTrue("requests on the main thread: " + onMain, onMain.isEmpty());
    }
}
