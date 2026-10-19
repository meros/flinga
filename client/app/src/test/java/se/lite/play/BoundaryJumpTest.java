package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;
import java.util.List;

/**
 * One timeline per channel (owner, 2026-10-18: rewind and fast-forward across programmes). ← → move a
 * cursor along the channel's broadcast time, past programme edges, and the strip shows the programme the
 * cursor is in. The stream that holds the moment loads when the keys stop. A neighbour without anything
 * to play is a hard edge: the cursor stops there. The guide is the one cache every surface reads (X3).
 * A minimal SimpleBasePlayer stands in for ExoPlayer, so the seek paths and the commits run as on the TV.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BoundaryJumpTest {
    private static final long H = 3600_000L;

    private MainActivity a;
    private StubPlayer player;

    @Before
    public void start() throws Exception {
        org.robolectric.shadows.ShadowLog.stream = System.out;
        Fixtures.install();
        Fixtures.installLogos();
        MainActivity.renderOnly = true;
        MainActivity.languageOverride = "sv";
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture")
                .putString("refreshToken", "fixture").commit();
        app.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).edit().clear().commit();
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        assertNotNull("the channels loaded and one is tuned", a.channelForTest());
        player = new StubPlayer(RuntimeEnvironment.getApplication().getMainLooper());
        a.playerForTest(player);
    }

    @After
    public void stop() {
        a.releasePlayerForTest();
        player = null;
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 40; i++) {
            Thread.sleep(25);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        }
    }

    @Test
    public void backwardPastTheStartJumpsIntoThePreviousCatchUpNearItsEnd() throws Exception {
        startCatchupPlaying("Fråga Lund");
        Api.Program prev = guideProgrammeBefore(a.delayedProgrammeForTest());
        a.scrubForTest(-30_000); // from 15 s in: crosses the programme's start
        assertEquals("the cursor is in the neighbour", "Lokala nyheter", a.cursorProgrammeForTest());
        a.commitSeekForTest();
        settle();
        assertEquals("the previous programme plays", "Lokala nyheter", a.playingTitleForTest());
        long span = prev.endMs - prev.startMs;
        assertTrue("near its end, 15 s before it: where the cursor was heading (span " + span + ", start "
                        + a.playingResumeForTest() + ", " + prev.title + ")",
                a.playingResumeForTest() >= span - 20_000 && a.playingResumeForTest() < span);
        assertEquals("catch-up of the same channel", "SVT1", a.delayedChannelNameForTest());
    }

    @Test
    public void forwardPastTheEndJumpsIntoTheNextProgramme() throws Exception {
        // A programme that ended long ago, followed by another that ended too: both can be played.
        startCatchupPlaying("Rapport");
        Api.Program now = a.delayedProgrammeForTest();
        a.scrubForTest(now.endMs - now.startMs - 15_000 - 10_000); // 10 s before the programme's end
        assertEquals("still in the programme", "Rapport", a.cursorProgrammeForTest());
        a.scrubForTest(+30_000); // the next step crosses the edge
        assertEquals("the cursor is in the next programme", "Brottet minut för minut", a.cursorProgrammeForTest());
        a.commitSeekForTest();
        settle();
        assertEquals("the next programme plays", "Brottet minut för minut", a.playingTitleForTest());
        assertEquals("SVT1", a.delayedChannelNameForTest());
    }

    @Test
    public void theCursorNeverPassesLive() throws Exception {
        startCatchupPlaying("Fråga Lund"); // on air now: its end is 41 min ahead
        a.scrubForTest(+2 * H);
        assertEquals("the cursor stops at live, in the programme on air", "Fråga Lund", a.cursorProgrammeForTest());
        a.commitSeekForTest();
        settle();
        assertNull("live: no longer a programme played from the guide", a.delayedChannelNameForTest());
    }

    @Test
    public void theAutoContinueHoldsOffAfterAUserJump() throws Exception {
        startCatchupPlaying("Fråga Lund");
        a.scrubForTest(-30_000); // crosses the start: the cursor is in Lokala nyheter, near its end
        a.commitSeekForTest();
        settle();
        assertEquals("Lokala nyheter", a.playingTitleForTest());
        a.endOfMediaForTest(); // the stream ends under the viewer (what the TV did within seconds)
        assertEquals("the auto-continue holds off: the programme stays",
                "Lokala nyheter", a.playingTitleForTest());
        Clock.offsetMs += 21_000; // past the hold-off
        a.endOfMediaForTest();
        settle();
        assertEquals("after the hold-off the continue runs again",
                "Fråga Lund", a.playingTitleForTest()); // F14: what follows Lokala nyheter
    }

    @Test
    public void aNeighbourWithoutAnOptionToPlayIsAHardBoundary() throws Exception {
        // Queen of the castle (-5.89 h..-5.14 h) has catch-up; the programme before it, Greven från
        // gränden, has none: backwards across the start stops at the edge.
        startCatchupPlaying("Queen of the castle");
        a.scrubForTest(-30_000);
        assertEquals("the cursor stops at the edge, in the same programme", "Queen of the castle", a.cursorProgrammeForTest());
        a.commitSeekForTest();
        assertEquals("still the same programme", "Queen of the castle", a.playingTitleForTest());
    }

    @Test
    public void backCancelsAPendingCrossing() throws Exception {
        startCatchupPlaying("Fråga Lund");
        a.scrubForTest(-30_000);
        assertEquals("Lokala nyheter", a.cursorProgrammeForTest());
        assertTrue(a.cancelSeekForTest());
        assertNull("Back dropped the cursor with the seek", a.cursorProgrammeForTest());
        a.commitSeekForTest();
        assertEquals("Fråga Lund", a.playingTitleForTest());
    }

    // ---- Steps ------------------------------------------------------------------------------

    /** The programme that ends where {@code p} starts (a title can repeat in the guide, so not by title). */
    private Api.Program guideProgrammeBefore(Api.Program p) {
        return a.cachedGuideForTest(a.playingChannelIdForTest()).stream()
                .filter(g -> g.endMs == p.startMs).findFirst().orElse(null);
    }

    /** Plays a programme's catch-up from the guide the way Tablå does, then reports it ready. */
    private void startCatchupPlaying(String title) throws Exception {
        // The guide arrives the way Tablå fetches it: the grid wants the visible rows.
        a.openGridForTest(a.playingChannelForTest());
        settle();
        List<Api.Program> guide = a.cachedGuideForTest(a.playingChannelIdForTest());
        assertNotNull("the channel's guide arrived", guide);
        Api.Program p = guide.stream().filter(g -> g.title.equals(title)).findFirst().orElse(null);
        assertNotNull(title + " is in the guide", p);
        a.closeGridForTest();
        a.playCatchupForTest(p);
        settle();
        // Near the programme's start: one ← step crosses it (15 s in, a 30 s step goes past the start).
        player.ready(p.endMs - p.startMs, 15_000);
        // SimpleBasePlayer fires onPlaybackStateChanged when a listener is attached at the first
        // getState; addListener already ran during onCreate? No: the stub is set after, so fire it now.
        a.playerReadyForTest();
        settle();
    }

    /** A minimal player: one static, seekable media item whose position the test moves. */
    private static final class StubPlayer extends androidx.media3.common.SimpleBasePlayer {
        private long durationUs, positionUs = 30_000;

        StubPlayer(Looper looper) {
            super(looper);
        }

        void ready(long durationMs, long positionMs) {
            this.durationUs = durationMs * 1000;
            this.positionUs = positionMs * 1000;
            invalidateState();
        }

        @Override
        protected State getState() {
            androidx.media3.common.SimpleBasePlayer.MediaItemData item =
                    new androidx.media3.common.SimpleBasePlayer.MediaItemData.Builder("stub")
                            .setIsSeekable(true)
                            .setIsDynamic(false)
                            .setDurationUs(durationUs)
                            .build();
            return new State.Builder()
                    .setAvailableCommands(new androidx.media3.common.Player.Commands.Builder().addAll().build())
                    .setPlaybackState(androidx.media3.common.Player.STATE_READY)
                    .setPlaylist(java.util.Collections.<androidx.media3.common.SimpleBasePlayer.MediaItemData>singletonList(item))
                    .setCurrentMediaItemIndex(0)
                    .setContentPositionMs(positionUs / 1000)
                    .build();
        }
    }
}
