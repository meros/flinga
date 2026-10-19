package se.lite.play;

import androidx.media3.common.Player;

/**
 * Small rules about the player's lifecycle (G6), pure so the TV-only behaviour has a unit test for its logic.
 */
final class Playback {
    private Playback() {}

    /**
     * The screen stays on only while a picture is meant to move: the player is asked to play and is buffering or
     * ready. Paused, ended or idle, the TV may dim and start its screensaver as it does for any app.
     */
    static boolean keepScreenOn(boolean playWhenReady, int playbackState) {
        return playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING);
    }

    /**
     * Whether the app's own "paused" follows a change of playWhenReady the player made itself: the audio focus went to
     * another app, the headset was unplugged (noisy), a call or a suppression. The viewer's own press is the app's
     * own change and sets "paused" first.
     */
    static boolean followsPlayer(int reason) {
        return reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM;
    }
}
