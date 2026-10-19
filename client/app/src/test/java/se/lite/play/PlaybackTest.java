package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Player;

import org.junit.Test;

/** G6: when the screen stays on and when the app follows a pause the player made itself. */
public class PlaybackTest {
    @Test
    public void theScreenStaysOnOnlyWhilePlayingOrBuffering() {
        assertTrue(Playback.keepScreenOn(true, Player.STATE_READY));
        assertTrue("a tune that loads", Playback.keepScreenOn(true, Player.STATE_BUFFERING));
        assertFalse("paused", Playback.keepScreenOn(false, Player.STATE_READY));
        assertFalse("paused while buffering", Playback.keepScreenOn(false, Player.STATE_BUFFERING));
        assertFalse("ended", Playback.keepScreenOn(true, Player.STATE_ENDED));
        assertFalse("nothing loaded", Playback.keepScreenOn(true, Player.STATE_IDLE));
    }

    @Test
    public void theAppFollowsEveryChangeThePlayerMadeItself() {
        assertFalse(Playback.followsPlayer(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST));
        assertFalse(Playback.followsPlayer(Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM));
        assertTrue("another app took the audio focus", Playback.followsPlayer(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS));
        assertTrue("the headset was pulled", Playback.followsPlayer(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY));
        assertTrue(Playback.followsPlayer(Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE));
    }
}
