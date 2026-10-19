package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** G6: what the media session offers for each kind of playback, and that its life ends with the activity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SessionBridgeTest {
    private static boolean has(SessionBridge.State s, long action) {
        return (s.actions & action) != 0;
    }

    @Test
    public void anEpisodeCanBePausedSoughtSkippedAndRestarted() {
        SessionBridge.State s = SessionBridge.of(false, false, false, true, true, true, 90_000, 1_800_000, "Avsnitt 3", "Storage Wars");
        assertEquals(PlaybackState.STATE_PLAYING, s.state);
        for (long a : new long[] {PlaybackState.ACTION_PLAY, PlaybackState.ACTION_PAUSE, PlaybackState.ACTION_PLAY_PAUSE, PlaybackState.ACTION_SEEK_TO,
                PlaybackState.ACTION_FAST_FORWARD, PlaybackState.ACTION_REWIND, PlaybackState.ACTION_SKIP_TO_NEXT, PlaybackState.ACTION_SKIP_TO_PREVIOUS}) {
            assertTrue(Long.toHexString(a), has(s, a));
        }
        assertEquals(90_000, s.positionMs);
        assertEquals(1_800_000, s.durationMs);
        assertEquals("Avsnitt 3", s.title);
        assertEquals("Storage Wars", s.subtitle);
    }

    @Test
    public void skipToNextOnlyWithANextEpisode() {
        assertFalse(has(SessionBridge.of(false, false, false, true, false, true, 0, 1000, "F", ""), PlaybackState.ACTION_SKIP_TO_NEXT));
    }

    @Test
    public void aLiveChannelWithoutStartOverOffersNothingToControl() {
        SessionBridge.State s = SessionBridge.of(false, false, true, false, false, false, 5_000, 0, "Rapport", "SVT1");
        assertEquals("no pause, no seek: the key says Går inte att pausa", 0, s.actions);
        assertEquals(PlaybackState.PLAYBACK_POSITION_UNKNOWN, s.positionMs);
        assertEquals(-1, s.durationMs);
    }

    @Test
    public void aLiveChannelWithStartOverCanPauseAndSeek() {
        SessionBridge.State s = SessionBridge.of(false, false, true, true, false, true, 5_000, 0, "Rapport", "SVT1");
        assertTrue(has(s, PlaybackState.ACTION_PAUSE) && has(s, PlaybackState.ACTION_REWIND) && has(s, PlaybackState.ACTION_SEEK_TO));
        assertFalse(has(s, PlaybackState.ACTION_SKIP_TO_NEXT));
        assertEquals(-1, s.durationMs);
    }

    @Test
    public void pausedAndBufferingAreTheirOwnStates() {
        assertEquals(PlaybackState.STATE_PAUSED, SessionBridge.of(true, false, false, true, false, false, 0, 1000, "t", "").state);
        assertEquals(PlaybackState.STATE_BUFFERING, SessionBridge.of(false, true, false, true, false, false, 0, 1000, "t", "").state);
        assertEquals("paused wins over buffering: the viewer asked", PlaybackState.STATE_PAUSED,
                SessionBridge.of(true, true, false, true, false, false, 0, 1000, "t", "").state);
    }

    private static final class Log implements SessionBridge.Controls {
        final List<String> calls = new ArrayList<>();

        public void play() { calls.add("play"); }
        public void pause() { calls.add("pause"); }
        public void seekTo(long p) { calls.add("seekTo " + p); }
        public void fastForward() { calls.add("ff"); }
        public void rewind() { calls.add("rw"); }
        public void next() { calls.add("next"); }
        public void previous() { calls.add("previous"); }
    }

    @Test
    public void theTransportControlsReachTheControls() {
        Log log = new Log();
        SessionBridge b = new SessionBridge(RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()), log);
        b.show(SessionBridge.of(false, false, false, true, true, true, 0, 1000, "t", "s"), null);
        assertTrue(b.active());
        android.media.session.MediaSession.Callback t = b.callbackForTest();
        t.onPause();
        t.onPlay();
        t.onSeekTo(30_000);
        t.onFastForward();
        t.onRewind();
        t.onSkipToNext();
        t.onSkipToPrevious();
        assertEquals(java.util.Arrays.asList("pause", "play", "seekTo 30000", "ff", "rw", "next", "previous"), log.calls);
        b.release();
    }

    @Test
    public void afterReleaseNothingArrivesAndHideIsQuiet() {
        Log log = new Log();
        SessionBridge b = new SessionBridge(RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()), log);
        b.show(SessionBridge.of(false, false, false, true, false, false, 0, 1000, "t", ""), null);
        android.media.session.MediaSession.Callback t = b.callbackForTest();
        b.hide();
        assertFalse(b.active());
        b.release();
        t.onPause();
        t.onSeekTo(1);
        assertTrue("no callback after release: " + log.calls, log.calls.isEmpty());
        b.hide();
    }
}
