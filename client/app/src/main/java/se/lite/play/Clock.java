package se.lite.play;

/** The app's clock. Offline replays set the offset so "now" matches the time the fixtures were recorded. */
public final class Clock {
    static volatile long offsetMs;

    private Clock() {}

    public static long now() {
        return System.currentTimeMillis() + offsetMs;
    }
}
