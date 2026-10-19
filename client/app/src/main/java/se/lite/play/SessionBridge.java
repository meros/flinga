package se.lite.play;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.SystemClock;

/**
 * The system's view of what plays (G6): a framework {@link MediaSession} (no library) so the media keys of a remote
 * that does not reach the activity, the Assistant ("pausa", "spela", "spola framåt 30 sekunder") and the now-playing
 * surface of the TV find the player. Every callback goes through {@link Controls}, which MainActivity wires to the code
 * the remote's keys already use. The session is active only while something plays, and {@link #release} ends it with
 * the activity: nothing here posts work that outlives it.
 */
final class SessionBridge {
    /** What the system may ask of the player; each is the same path as the key of that name. */
    interface Controls {
        void play();

        void pause();

        void seekTo(long positionMs);

        void fastForward();

        void rewind();

        void next();

        void previous();
    }

    /** What the session shows: pure, so a test can check every kind of playback. */
    static final class State {
        int state;
        long actions, positionMs, durationMs;
        String title, subtitle;
    }

    /**
     * @param live        a channel on the live window, or any stream the viewer cannot position in
     * @param rewindable  the viewer can pause and seek: on demand, catch-up, start-over, or live with start-over available
     * @param hasNext     an episode with a next one known
     * @param canRestart  the overlay has "Från början"
     */
    static State of(boolean paused, boolean buffering, boolean live, boolean rewindable, boolean hasNext, boolean canRestart,
            long positionMs, long durationMs, String title, String subtitle) {
        State s = new State();
        s.state = paused ? PlaybackState.STATE_PAUSED : buffering ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PLAYING;
        long a = 0;
        if (rewindable) a |= PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND;
        if (hasNext) a |= PlaybackState.ACTION_SKIP_TO_NEXT;
        if (canRestart) a |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        s.actions = a;
        s.positionMs = live && !rewindable ? PlaybackState.PLAYBACK_POSITION_UNKNOWN : Math.max(0, positionMs);
        s.durationMs = live || durationMs <= 0 ? -1 : durationMs;
        s.title = title == null ? "" : title;
        s.subtitle = subtitle == null ? "" : subtitle;
        return s;
    }

    private final MediaSession session;
    private final MediaSession.Callback callback;
    private boolean active, released;

    SessionBridge(Context c, Handler on, Controls ctl) {
        session = new MediaSession(c, "TeliaLite");
        configure(c);
        callback = new MediaSession.Callback() {
            // A call that is already queued when the activity ends does nothing (released).
            @Override
            public void onPlay() {
                if (!released) ctl.play();
            }

            @Override
            public void onPause() {
                if (!released) ctl.pause();
            }

            @Override
            public void onSeekTo(long pos) {
                if (!released) ctl.seekTo(pos);
            }

            @Override
            public void onFastForward() {
                if (!released) ctl.fastForward();
            }

            @Override
            public void onRewind() {
                if (!released) ctl.rewind();
            }

            @Override
            public void onSkipToNext() {
                if (!released) ctl.next();
            }

            @Override
            public void onSkipToPrevious() {
                if (!released) ctl.previous();
            }
        };
        session.setCallback(callback, on);
    }

    @SuppressWarnings("deprecation") // the flags are the only way to ask for media buttons before API 26
    private void configure(Context c) {
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
    }

    /** Shows {@code s}; {@code art} is the channel's logo when it is loaded, else null. */
    void show(State s, Bitmap art) {
        MediaMetadata.Builder m = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, s.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, s.subtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, s.subtitle)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs);
        if (art != null) m.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        session.setMetadata(m.build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(s.actions)
                .setState(s.state, s.positionMs, s.state == PlaybackState.STATE_PLAYING ? 1f : 0f, SystemClock.elapsedRealtime())
                .build());
        if (!active) {
            active = true;
            session.setActive(true);
        }
    }

    /** Nothing plays (the app left the screen, a failure, logout): the system stops offering controls. */
    void hide() {
        if (!active) return;
        active = false;
        session.setActive(false);
    }

    boolean active() {
        return active;
    }

    /** Ends the session: no callback arrives afterwards. */
    void release() {
        released = true;
        active = false;
        session.setCallback(null);
        session.release();
    }

    /** Test hook: what the system calls when the viewer or the Assistant asks (the unit tests have no media session service). */
    MediaSession.Callback callbackForTest() {
        return callback;
    }
}
