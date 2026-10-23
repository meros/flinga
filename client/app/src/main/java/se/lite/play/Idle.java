package se.lite.play;

/**
 * The idle reset (U15, owner 2026-10-23: "1 h no activity = reset to live etc."): after {@link #RESET_AFTER_MS} without
 * a key, a remote or media-session command or a playback action, the app goes back to a clean, ready state on live TV.
 *
 * <p>What counts as idle, and what a reset does, is this class alone, pure and driven by a clock the caller passes
 * (the app passes {@code SystemClock.elapsedRealtime()}, which runs on in standby; the tests pass their own):
 *
 * <ul>
 *   <li>Activity is any key event, a media-session command (play, pause, seek, skip from the system or a phone), a
 *       link or a voice request. Each restarts the clock ({@link #activity}).</li>
 *   <li>Content that plays is in use: a film, an episode, a catch-up or a channel watched behind live, playing full
 *       screen with nothing open over it, never counts as idle, however long nobody touches the remote. The clock
 *       restarts on every check while that is so, and the hour starts when it stops (it is paused, it ends, a
 *       surface is opened over it).</li>
 *   <li>Everything else counts: a surface left open (Tablå, Katalog & Sök, the Serie detail, the picker, the strip
 *       with focus), a paused picture (it also holds a DRM session and a stream position that goes stale), live TV
 *       with nothing open, and time with the app in the background (nothing plays there: leaving the app pauses).</li>
 * </ul>
 *
 * A reset never stops a picture that moves: over content that plays it only closes what is open
 * ({@link #CLOSE}). Over live TV, a paused picture or a failed tune it closes everything and plays the last live
 * channel live ({@link #LIVE}; already on live and playing, the picture is left alone). Live TV with nothing open is
 * the clean state already: the reset only refreshes the data ({@link #REFRESH}).
 */
final class Idle {
    /** One hour without activity (owner, 2026-10-23). */
    static final long RESET_AFTER_MS = 60 * 60_000L;
    /** How often the app asks; the reset lands at most this late. */
    static final long CHECK_EVERY_MS = 60_000L;

    static final int NONE = 0, REFRESH = 1, CLOSE = 2, LIVE = 3;

    /** What the app is doing, as the rule reads it. */
    static final class State {
        /** Channels are loaded and no login or fatal error is on screen: there is a live state to return to. */
        boolean ready = true;
        /** A picture moves: the player plays (not paused, not failed, not ended). */
        boolean playing;
        /** It is a channel live at the edge (not behind live, not a title). */
        boolean liveAtEdge;
        /** Something is open over the picture: the browser, the detail, the picker, the strip with focus. */
        boolean surfaceOpen;
        /** The live tune failed (its banner is up). */
        boolean failed;

        State() {}

        State(boolean playing, boolean liveAtEdge, boolean surfaceOpen) {
            this.playing = playing;
            this.liveAtEdge = liveAtEdge;
            this.surfaceOpen = surfaceOpen;
        }
    }

    private long lastMs;

    Idle(long nowMs) {
        lastMs = nowMs;
    }

    /** A key, a command or a playback action: the hour starts again. */
    void activity(long nowMs) {
        lastMs = nowMs;
    }

    /** How long nothing has happened, as the rule counts it. */
    long idleMs(long nowMs) {
        return Math.max(0, nowMs - lastMs);
    }

    /** True while idle time counts for {@code s}; false while content that plays is in use. */
    static boolean counts(State s) {
        if (!s.ready) return false;
        return !(s.playing && !s.liveAtEdge && !s.surfaceOpen && !s.failed);
    }

    /** What a reset does in state {@code s}. */
    static int action(State s) {
        if (!s.ready) return NONE;
        if (s.failed || !s.playing) return LIVE; // paused, stalled or failed: back to the last live channel
        if (s.liveAtEdge) return s.surfaceOpen ? LIVE : REFRESH;
        return s.surfaceOpen ? CLOSE : NONE; // a title or a delayed channel that plays: never stopped
    }

    /**
     * One check at {@code nowMs}: the action to take now, or {@link #NONE}. While {@code s} does not count, the clock
     * follows it (the hour starts when the content stops). After a reset the hour starts again.
     */
    int check(long nowMs, State s) {
        if (!counts(s)) {
            lastMs = nowMs;
            return NONE;
        }
        if (nowMs - lastMs < RESET_AFTER_MS) return NONE;
        lastMs = nowMs;
        return action(s);
    }

    /** When to check next: at the hour, or within {@link #CHECK_EVERY_MS} (the state can change in between). */
    long nextCheckInMs(long nowMs) {
        long left = RESET_AFTER_MS - (nowMs - lastMs);
        return Math.max(1_000, Math.min(CHECK_EVERY_MS, left));
    }
}
