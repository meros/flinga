package se.lite.play;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * F17: the place of every channel left while playback was behind live, so that zapping back resumes
 * the same content moment instead of the live edge (owner: "Man tappar lätt bort sin plats/serie om man
 * råkar trycka upp eller ner").
 *
 * A place is a content position, not an offset: the programme and the position within it (ms from its
 * broadcast start). Live moves on while the place waits, so the time behind live grows; the content
 * moment does not. The position is the same whether it was read off the live window or the start-over
 * stream: both are dynamic DASH windows whose window start is an epoch time, so
 * {@code windowStart + playerPosition} is the broadcast moment, and {@code moment - programmeStart} the
 * position within the programme ({@link #momentOf}, {@link #positionIn}).
 *
 * A place is worth resuming while its programme lasts and for at most {@link #KEEP_MS} after leaving;
 * resuming needs the programme's start-over stream. The store keeps at most {@link #CAP} places, the
 * most recently left first; only that one gets a line in the banner (UX.md 4).
 */
final class PlaceStore {
    /** How long a left channel keeps its place. */
    static final long KEEP_MS = 30 * 60_000L;
    /** How many channels keep a place at once. */
    static final int CAP = 5;

    static final class Place {
        final String channelId;
        final Api.Program programme;
        /** Position within the programme, ms from its broadcast start. */
        final long positionMs;
        /** When the channel was left (epoch ms). */
        final long leftAtMs;

        Place(String channelId, Api.Program programme, long positionMs, long leftAtMs) {
            this.channelId = channelId;
            this.programme = programme;
            this.positionMs = positionMs;
            this.leftAtMs = leftAtMs;
        }

        /** The broadcast moment to resume at (epoch ms). */
        long momentMs() {
            return start(programme) + positionMs;
        }
    }

    /** Most recently left first. */
    private final ArrayDeque<Place> places = new ArrayDeque<>();

    /** The programme's broadcast start, else its EPG start. */
    static long start(Api.Program p) {
        return p.broadcastStartMs > 0 ? p.broadcastStartMs : p.startMs;
    }

    /** The programme's broadcast end, else its EPG end. */
    static long end(Api.Program p) {
        return p.broadcastEndMs > 0 ? p.broadcastEndMs : p.endMs;
    }

    /** The broadcast moment (epoch ms) of a player position in a dynamic window that starts at {@code windowStartMs}. */
    static long momentOf(long windowStartMs, long positionMs) {
        return windowStartMs + positionMs;
    }

    /** The position within {@code p} of broadcast moment {@code momentMs}, never negative. */
    static long positionIn(Api.Program p, long momentMs) {
        return Math.max(0, momentMs - start(p));
    }

    /**
     * Keeps the place of {@code channelId}: content moment {@code momentMs} in {@code programme}, left at
     * {@code now}. An older place of the same channel is replaced; the oldest goes when there are more
     * than {@link #CAP}.
     */
    Place save(String channelId, Api.Program programme, long momentMs, long now) {
        take(channelId);
        Place p = new Place(channelId, programme, positionIn(programme, momentMs), now);
        places.addFirst(p);
        while (places.size() > CAP) places.removeLast();
        return p;
    }

    /** The place of {@code channelId}, or null. Stays in the store. */
    Place get(String channelId) {
        for (Place p : places) if (p.channelId.equals(channelId)) return p;
        return null;
    }

    /** Removes and returns the place of {@code channelId}, or null. */
    Place take(String channelId) {
        for (Iterator<Place> it = places.iterator(); it.hasNext(); ) {
            Place p = it.next();
            if (p.channelId.equals(channelId)) {
                it.remove();
                return p;
            }
        }
        return null;
    }

    /** Drops every place that is no longer alive at {@code now}. */
    void prune(long now) {
        places.removeIf(p -> !alive(p, now));
    }

    int size() {
        return places.size();
    }

    /** True while the place is worth going back to: within {@link #KEEP_MS} of leaving and its programme has not ended. */
    static boolean alive(Place p, long now) {
        return p != null && now - p.leftAtMs <= KEEP_MS && now < end(p.programme);
    }

    /**
     * Why {@code p} cannot be resumed at {@code now}: "expired", "ended" or "no start-over"; null when
     * it can. Every reason ends the same way for the viewer: the channel plays live with a note.
     */
    static String blocked(Place p, long now) {
        if (p == null) return "none";
        if (now - p.leftAtMs > KEEP_MS) return "expired";
        if (now >= end(p.programme)) return "ended";
        if (!MainActivity.hasStartOver(p.programme)) return "no start-over";
        return null;
    }
}
