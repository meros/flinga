package se.lite.play.ui;

/**
 * How far a programme has come, and when that is next worth drawing (U16, 2026-10-23: "The progress to the next
 * programme is not smooth"). Pure, so the clamp and the pacing have unit tests with a fake clock.
 *
 * <p>A progress bar is redrawn when its fill would move by one pixel, not on a fixed one-second tick and not on
 * every frame: a 60-minute programme on a 1,500 px bar moves a pixel every 2.4 s, a 5-minute one every 0.2 s. That is
 * as smooth as a pixel grid can draw, with no running animation (DESIGN.md: "no running animations") and no
 * per-frame work. The redraw is never later than the programme's end, so the handover to the next programme is
 * drawn at once.
 */
public final class Progress {
    /** Redraws are never closer than this (one long frame on the target device). */
    public static final long MIN_REDRAW_MS = 100;
    /** And never further apart than this, so a bar whose span is unknown still catches up. */
    public static final long MAX_REDRAW_MS = 30_000;

    private Progress() {}

    /** The share of {@code [startMs, endMs)} that has passed at {@code t}, clamped to 0..1; -1 when the span is unknown. */
    public static float fraction(long startMs, long endMs, long t) {
        if (endMs <= startMs || startMs <= 0) return -1f;
        if (t <= startMs) return 0f;
        if (t >= endMs) return 1f;
        return (float) ((t - startMs) / (double) (endMs - startMs));
    }

    /**
     * Milliseconds from {@code t} until a bar of {@code widthPx} over {@code [startMs, endMs)} moves its fill by one more
     * pixel, or reaches the end; at least {@link #MIN_REDRAW_MS}, at most {@link #MAX_REDRAW_MS}. After the end there is
     * nothing more to draw: {@link #MAX_REDRAW_MS}.
     */
    public static long redrawDelay(long startMs, long endMs, float widthPx, long t) {
        if (endMs <= startMs || startMs <= 0 || widthPx < 1f || t >= endMs) return MAX_REDRAW_MS;
        if (t < startMs) return clamp(startMs - t);
        double perPx = (endMs - startMs) / (double) widthPx;
        double done = (t - startMs) / perPx; // pixels filled so far, fractional
        long nextPx = (long) Math.ceil((Math.floor(done) + 1) * perPx) + startMs;
        return clamp(Math.min(nextPx, endMs) - t);
    }

    private static long clamp(long ms) {
        return Math.max(MIN_REDRAW_MS, Math.min(MAX_REDRAW_MS, ms));
    }
}
