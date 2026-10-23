package se.lite.play;

import java.util.List;

/**
 * What is on a channel at a moment, from what the app knows: the channel's guide when it is loaded, else the
 * now/next pair of the one request for every channel (U16, 2026-10-23: "Aktuellt going into Kulturnyheterna").
 *
 * <p>The schedule is the only clock of a programme the app has. The EPG gives a slot (start and end); the actual
 * broadcast can run over it by minutes, and nothing in the API says so while it happens (the linear item's own end is
 * only known for the slot it belongs to, and is set in advance like the slot). So at a slot's end the app moves to the
 * next programme at once, even if the picture still shows the old one for a while: a bar never runs past 100 %, and
 * never sits at 100 % of a programme that has ended. When the next programme is not known, nothing is shown as on air
 * (the bar's slot stays empty) and the data is fetched again.
 */
final class Schedule {
    /** The now/next pair is refetched this long after the earliest end of a programme on air (UX.md 3.4: +5 s). */
    static final long BOUNDARY_SLACK_MS = 5_000;
    /** And at least this often, whatever the boundaries say. */
    static final long NOW_NEXT_MAX_MS = 15 * 60_000L;

    private Schedule() {}

    /** The programme of {@code guide} on air at {@code t}, or null. */
    static Api.Program in(List<Api.Program> guide, long t) {
        if (guide != null) for (Api.Program p : guide) if (p.startMs <= t && t < p.endMs) return p;
        return null;
    }

    /**
     * The programme on {@code c} at {@code t}: the guide's, else the now/next pair's (the next one once the one "now"
     * has ended), else null (a gap, or nothing known yet).
     */
    static Api.Program onAt(Api.Channel c, List<Api.Program> guide, long t) {
        Api.Program g = in(guide, t);
        if (g != null) return g;
        if (c == null) return null;
        if (covers(c.now, t)) return c.now;
        if (covers(c.next, t)) return c.next;
        return null;
    }

    private static boolean covers(Api.Program p, long t) {
        return p != null && p.startMs <= t && t < p.endMs;
    }

    /**
     * True when the now/next pair of {@code c} no longer says what is on at {@code t}: the programme "now" has ended
     * and the next one is not known or has ended too. A gap in the schedule (now null, next later) is not stale.
     */
    static boolean stale(Api.Channel c, long t) {
        if (c == null) return false;
        if (c.now != null && t < c.now.endMs) return false;
        if (c.next != null && t < c.next.endMs) return false;
        return c.now != null || c.next != null;
    }

    /**
     * When the now/next pair should be fetched again, from {@code t}: the earliest end of any channel's programme on air
     * plus {@link #BOUNDARY_SLACK_MS}, so the next "next" is known before it is needed; at most {@link #NOW_NEXT_MAX_MS}
     * away, and never in the past.
     */
    static long nextRefreshAt(List<Api.Channel> channels, long t) {
        long at = t + NOW_NEXT_MAX_MS;
        if (channels != null) {
            for (Api.Channel c : channels) {
                if (stale(c, t)) return t + BOUNDARY_SLACK_MS; // the pair is out of date already: soon
                Api.Program p = onAt(c, null, t);
                if (p != null && p.endMs > t) at = Math.min(at, p.endMs + BOUNDARY_SLACK_MS);
            }
        }
        return Math.max(t + BOUNDARY_SLACK_MS, at);
    }

    /** The earliest end after {@code t} of a programme on air on any of {@code channels}; 0 when none is known. */
    static long nextBoundary(List<Api.Channel> channels, long t) {
        long at = 0;
        if (channels == null) return 0;
        for (Api.Channel c : channels) {
            Api.Program p = onAt(c, null, t);
            if (p != null && p.endMs > t && (at == 0 || p.endMs < at)) at = p.endMs;
        }
        return at;
    }
}
