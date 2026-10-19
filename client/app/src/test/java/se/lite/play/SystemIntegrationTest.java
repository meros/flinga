package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.media3.common.Player;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * G6 on the real MainActivity (render-only, recorded fixtures): links and searches from outside reach the right place
 * without recreating the activity, an unknown link changes nothing, the player's own pauses show in the app, the screen
 * stays on only while playing, the media session follows the app's life, and the Watch Next rows follow Fortsätt titta.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SystemIntegrationTest {
    private ActivityController<MainActivity> c;
    private MainActivity a;

    private static void settle() throws Exception {
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
    }

    @Before
    public void start() throws Exception {
        Fixtures.install();
        Fixtures.installLogos();
        MainActivity.renderOnly = true;
        MainActivity.languageOverride = "sv";
        MainActivity.watchNextStore = null;
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture").putString("refreshToken", "fixture").commit();
        app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).edit().clear().commit();
        c = Robolectric.buildActivity(MainActivity.class).setup();
        a = c.get();
        settle();
        assertNotNull("the channels loaded and one is tuned", a.channelForTest());
    }

    private void newIntent(Intent i) throws Exception {
        c.newIntent(i);
        settle();
    }

    private static Intent view(String uri) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }

    // ---- Links ---------------------------------------------------------------------------------

    @Test
    public void aChannelLinkTunesThatChannelInTheSameActivity() throws Exception {
        List<String> ids = a.channelIdsForTest();
        String want = ids.get(3).equals(a.channelForTest()) ? ids.get(4) : ids.get(3);
        newIntent(view("flinga://channel/" + Uri.encode(want)));
        assertEquals(want, a.channelForTest());
        assertSame("singleTask: the activity is the same one, its player and session go on", a, c.get());
    }

    @Test
    public void aLinkToAChannelTheSubscriptionLacksChangesNothing() throws Exception {
        String before = a.channelForTest(), surface = a.surfaceForTest();
        newIntent(view("flinga://channel/c-not-ours"));
        assertEquals(before, a.channelForTest());
        assertEquals(surface, a.surfaceForTest());
    }

    @Test
    public void unknownLinksAndPlainStartsChangeNothing() throws Exception {
        String before = a.channelForTest(), surface = a.surfaceForTest();
        for (Intent i : new Intent[] {view("flinga://nowhere"), view("https://example.com/"), new Intent(Intent.ACTION_MAIN), new Intent(Intent.ACTION_VIEW)}) {
            newIntent(i);
            assertEquals(before, a.channelForTest());
            assertEquals(surface, a.surfaceForTest());
        }
    }

    @Test
    public void theGuideLinkOpensTheGrid() throws Exception {
        newIntent(view("flinga://guide"));
        assertEquals("grid", a.surfaceForTest());
    }

    @Test
    public void aSearchLinkAndASystemSearchOpenSokWithTheQueryTyped() throws Exception {
        newIntent(view("flinga://search?q=bonde"));
        assertEquals("browser:katalog", a.surfaceForTest());
        assertEquals("bonde", a.queryForTest());
        newIntent(new Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "film"));
        assertEquals("film", a.queryForTest());
    }

    @Test
    public void aSpokenChannelNameTunesItWithoutASearch() throws Exception {
        List<String> names = a.channelNamesForTest(), ids = a.channelIdsForTest();
        int i = names.indexOf("SVT2") >= 0 ? names.indexOf("SVT2") : 2;
        if (ids.get(i).equals(a.channelForTest())) i = 1;
        newIntent(new Intent(VoiceSearch.ACTION_PLAY_FROM_SEARCH).putExtra(SearchManager.QUERY, names.get(i)));
        assertEquals(ids.get(i), a.channelForTest());
        assertFalse("no Sök on screen: the channel was the answer", a.surfaceForTest().startsWith("browser:katalog"));
    }

    @Test
    public void aSpokenTitleThatIsNotCertainLeavesTheResultsOnScreen() throws Exception {
        newIntent(new Intent(VoiceSearch.ACTION_PLAY_FROM_SEARCH).putExtra(SearchManager.QUERY, "bonde"));
        assertEquals("browser:katalog", a.surfaceForTest()); // no result is titled exactly "bonde": nothing plays
        assertEquals("bonde", a.queryForTest());
    }

    @Test
    public void theCertainResultOfASpokenSearchOpensItsSeries() throws Exception {
        Api.Asset series = new Api.Asset();
        series.id = "s-6b00061dc059998b";
        series.title = "Bonde söker fru";
        series.isSeries = true;
        a.playCertainForTest("bonde söker fru", java.util.Collections.singletonList(series));
        settle();
        assertEquals("detail:Bonde söker fru", a.surfaceForTest());
    }

    // ---- The player's own pause, the screen, the session ----------------------------------------

    @Test
    public void anAudioFocusLossPausesTheAppAndGainingItResumes() throws Exception {
        assertFalse(a.pausedForTest());
        a.playWhenReadyForTest(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        assertFalse("a viewer's own press sets paused itself, not this path", a.pausedForTest());
        a.playWhenReadyForTest(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
        assertTrue(a.pausedForTest());
        a.playWhenReadyForTest(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
        assertFalse(a.pausedForTest());
        a.playWhenReadyForTest(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY);
        assertTrue("the headset was pulled", a.pausedForTest());
    }

    @Test
    public void theScreenStaysOnOnlyWhilePlaying() throws Exception {
        assertFalse("nothing plays in render-only mode", a.screenKeptOnForTest());
        a.keepAwakeForTest(true, Player.STATE_READY);
        assertTrue(a.screenKeptOnForTest());
        a.keepAwakeForTest(false, Player.STATE_READY);
        assertFalse(a.screenKeptOnForTest());
        a.keepAwakeForTest(true, Player.STATE_BUFFERING);
        assertTrue(a.screenKeptOnForTest());
        c.pause().stop();
        assertFalse("leaving the app lets the TV sleep", a.screenKeptOnForTest());
    }

    @Test
    public void theMediaSessionIsActiveOnlyWhileTheAppPlaysOnScreen() throws Exception {
        SessionBridge s = a.sessionForTest();
        assertTrue(s.active());
        c.pause().stop();
        assertFalse("Android TV expects playback to stop when the app leaves the screen", s.active());
        c.start().resume();
        settle();
        assertTrue(s.active());
        SessionBridge.State st = a.sessionState();
        assertEquals("the harness has no first frame: the tune is still loading", android.media.session.PlaybackState.STATE_BUFFERING, st.state);
    }

    @Test
    public void theSessionOfADestroyedActivityIsReleasedAndItsCallbacksAreQuiet() throws Exception {
        android.media.session.MediaSession.Callback t = a.sessionForTest().callbackForTest();
        c.pause().stop().destroy();
        t.onPause();
        t.onPlay();
        t.onFastForward();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10));
    }

    @Test
    public void theSystemsPauseAndPlayGoThroughThePausePath() throws Exception {
        android.media.session.MediaSession.Callback t = a.sessionForTest().callbackForTest();
        // Another app took the audio focus: the player paused itself. The Assistant's "spela" is the Spela pill.
        a.playWhenReadyForTest(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
        assertTrue(a.pausedForTest());
        t.onPlay();
        assertFalse("onPlay is the Spela pill and the play key", a.pausedForTest());
        // "Pausa" is the Paus pill: on a live channel without start-over it says so and the picture goes on.
        t.onPause();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNotNull(a.channelForTest());
        t.onFastForward();
        t.onRewind();
        t.onSkipToNext(); // a channel has no next episode: nothing happens
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNotNull(a.channelForTest());
    }

    // ---- Watch Next ----------------------------------------------------------------------------

    @Test
    public void fortsattTittaReachesTheHomeScreenOnceTheListIsKnown() throws Exception {
        List<WatchNext.Entry> rows = a.watchNextForTest();
        assertNotNull("the list arrived from the server and was handed on", rows);
        assertFalse(rows.isEmpty());
        assertTrue(rows.size() <= WatchNext.MAX);
        for (WatchNext.Entry e : rows) {
            assertTrue(e.id, e.id.startsWith("s-") || e.id.startsWith("m-"));
            assertTrue(e.intentUri, e.intentUri.startsWith("intent://title/" + e.id));
        }
    }

    private static final class Spy implements WatchNext.Store {
        final List<String> calls = new ArrayList<>();
        final List<WatchNext.Row> rows = new ArrayList<>();
        long next = 1;

        @Override public synchronized List<WatchNext.Row> read() { return new ArrayList<>(rows); }
        @Override public synchronized void delete(long id) { calls.add("delete"); rows.removeIf(r -> r.rowId == id); }
        @Override public synchronized void update(long id, WatchNext.Entry e) { calls.add("update " + e.id); }
        @Override public synchronized void insert(WatchNext.Entry e) { calls.add("insert " + e.id); rows.add(new WatchNext.Row(next++, e.id, e)); }
    }

    @Test
    public void theProviderGetsTheRowsOnTheIoThreadAndALogoutEmptiesIt() throws Exception {
        c.pause().stop().destroy();
        Spy spy = new Spy();
        MainActivity.watchNextStore = spy;
        c = Robolectric.buildActivity(MainActivity.class).setup();
        a = c.get();
        settle();
        List<String> inserted = new ArrayList<>();
        synchronized (spy) {
            for (String s : spy.calls) if (s.startsWith("insert ")) inserted.add(s);
        }
        assertFalse("the rows were written: " + spy.calls, inserted.isEmpty());
        assertEquals("one row per title", inserted.size(), new java.util.HashSet<>(inserted).size());
    }
}
