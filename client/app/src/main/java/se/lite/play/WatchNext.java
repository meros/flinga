package se.lite.play;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fortsätt titta on the Android TV home screen (G6): the platform's Watch Next row, written with the framework's
 * {@link TvContract} (API 26 and up; no AndroidX tvprovider, which is a library for the same few columns).
 * One row per title, as in Fortsätt titta: CONTINUE with the position and the length for a title in progress, NEXT for
 * a series that points at its next episode. A title that leaves Fortsätt titta (finished, hidden, another account)
 * leaves the home screen at the next sync.
 *
 * {@link #entries} and {@link #plan} are pure; {@link #sync} talks to the provider and belongs on the I/O thread.
 * Every provider call is guarded: a TV without the provider, or a permission the system does not grant, leaves the
 * home screen as it was and never reaches the viewer.
 */
final class WatchNext {
    private static final String TAG = "TeliaLite";
    /** Top rows of Fortsätt titta the home screen gets: a longer row is a worse row. */
    static final int MAX = 6;
    /** A title is rewritten when its position moved by this much (once a minute while playing is already the pace). */
    static final long MOVED_MS = 15_000;
    /** The 16:9 art the launcher draws: 480 x 270 is its largest card. */
    static final int ART_W = 480, ART_H = 270;

    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE / NEXT, TYPE_MOVIE / TYPE_TV_EPISODE (inlined constants). */
    static final int TYPE_CONTINUE = 0, TYPE_NEXT = 1;
    private static final int KIND_MOVIE = 0, KIND_EPISODE = 3;

    /** One row of the home screen, from one Fortsätt titta title. */
    static final class Entry {
        /** The series or film id: what the row is keyed by in the provider (internal provider id). */
        String id;
        String title, description, posterUri, intentUri;
        boolean series;
        int type;
        long positionMs, durationMs, engagedMs;

        /** What decides whether a row has to be written again. */
        boolean sameAs(Entry o) {
            return o != null && id.equals(o.id) && type == o.type && eq(title, o.title) && eq(description, o.description)
                    && eq(posterUri, o.posterUri) && eq(intentUri, o.intentUri)
                    && Math.abs(positionMs - o.positionMs) < MOVED_MS;
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * The home screen's rows for a Fortsätt titta list (newest first). A title with at least a minute watched and a
     * known length is CONTINUE; a series not started (Telia, or finished and moved on) is NEXT; a film not started has
     * no row. {@code anchorMs} is the engagement time of the first row; the rest follow one second apart, so the
     * launcher keeps Telia's order; a title played on this TV keeps its own time when later.
     */
    static List<Entry> entries(List<Api.Saved> continueWatching, Library library, String packageName, long anchorMs) {
        List<Entry> out = new ArrayList<>();
        for (Api.Saved s : continueWatching) {
            if (out.size() >= MAX) break;
            if (s.id == null || s.id.isEmpty() || s.title == null || s.title.isEmpty()) continue;
            long dur = durationOf(s);
            boolean started = s.positionMs >= Library.RESUME_MIN_MS;
            Entry e = new Entry();
            e.id = s.id;
            e.title = s.title;
            e.series = s.series;
            if (started && dur > s.positionMs) {
                e.type = TYPE_CONTINUE;
                e.positionMs = s.positionMs;
                e.durationMs = dur;
                e.description = Math.max(1, (dur - s.positionMs + 59_999) / 60_000) + " min kvar"
                        + (s.provider == null || s.provider.isEmpty() ? "" : " · " + s.provider);
            } else if (s.series) {
                e.type = TYPE_NEXT;
                e.description = "Nästa avsnitt" + (s.provider == null || s.provider.isEmpty() ? "" : " · " + s.provider);
            } else {
                continue;
            }
            e.posterUri = Images.scaled(s.imageUrl, ART_W, ART_H);
            e.intentUri = intentUri(s.id, packageName);
            Library.Local l = library == null ? null : library.local(s.mediaId);
            e.engagedMs = Math.max(anchorMs - out.size() * 1000L, l == null ? 0 : l.at);
            out.add(e);
        }
        return out;
    }

    /** The length of the title in progress: position plus the minutes Telia says are left, else from the percent watched. */
    static long durationOf(Api.Saved s) {
        if (s.minutesLeft >= 0) return s.positionMs + s.minutesLeft * 60_000L;
        if (s.percent > 0 && s.positionMs > 0) return s.positionMs * 100 / s.percent;
        return 0;
    }

    /** The intent URI string the launcher fires: our own link, resolved to this package only. */
    static String intentUri(String id, String packageName) {
        return new Intent(Intent.ACTION_VIEW, DeepLink.title(id, true)).setPackage(packageName).toUri(Intent.URI_INTENT_SCHEME);
    }

    // ---- The plan ------------------------------------------------------------------------------

    /** A row the provider already holds for this app. */
    static final class Row {
        final long rowId;
        final String id;
        final Entry as;

        Row(long rowId, String id, Entry as) {
            this.rowId = rowId;
            this.id = id;
            this.as = as;
        }
    }

    static final class Plan {
        final List<Entry> insert = new ArrayList<>();
        final Map<Long, Entry> update = new HashMap<>();
        final List<Long> delete = new ArrayList<>();
    }

    /** What to write so the provider holds exactly {@code want}: new titles in, changed ones updated, gone ones out. */
    static Plan plan(List<Row> have, List<Entry> want) {
        Plan p = new Plan();
        Map<String, Row> byId = new HashMap<>();
        for (Row r : have) {
            if (r.id == null || byId.containsKey(r.id)) p.delete.add(r.rowId); // a row of no title, or a duplicate
            else byId.put(r.id, r);
        }
        for (Entry e : want) {
            Row r = byId.remove(e.id);
            if (r == null) p.insert.add(e);
            else if (!e.sameAs(r.as)) p.update.put(r.rowId, e);
        }
        for (Row r : byId.values()) p.delete.add(r.rowId);
        return p;
    }

    // ---- The provider --------------------------------------------------------------------------

    static ContentValues values(Entry e) {
        ContentValues v = new ContentValues();
        v.put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, e.id);
        v.put(TvContract.WatchNextPrograms.COLUMN_TYPE, e.series ? KIND_EPISODE : KIND_MOVIE);
        v.put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, e.type);
        v.put(TvContract.WatchNextPrograms.COLUMN_TITLE, e.title);
        v.put(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, e.description);
        v.put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, e.engagedMs);
        v.put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, e.intentUri);
        v.put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.WatchNextPrograms.ASPECT_RATIO_16_9);
        if (e.posterUri != null) v.put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, e.posterUri);
        if (e.type == TYPE_CONTINUE) {
            v.put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, (int) Math.min(Integer.MAX_VALUE, e.positionMs));
            v.put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, (int) Math.min(Integer.MAX_VALUE, e.durationMs));
        }
        return v;
    }

    /** The provider, as far as the sync needs it: a fake in tests, {@link ProviderStore} on the TV. */
    interface Store {
        /** The rows this app holds, or null when there is no provider. */
        List<Row> read();

        void delete(long rowId);

        void update(long rowId, Entry e);

        void insert(Entry e);
    }

    private final Store store;
    private boolean denied;

    WatchNext(Store store) {
        this.store = store;
    }

    /**
     * Brings the provider to {@code want}. I/O thread only. Returns false when the provider is not there or refuses
     * (the permission, a TV without Watch Next); it then stays quiet for the rest of the process.
     */
    boolean sync(List<Entry> want) {
        if (denied) return false;
        try {
            List<Row> have = store.read();
            if (have == null) return false;
            Plan p = plan(have, want);
            for (long id : p.delete) store.delete(id);
            for (Map.Entry<Long, Entry> u : p.update.entrySet()) store.update(u.getKey(), u.getValue());
            for (Entry e : p.insert) store.insert(e);
            return true;
        } catch (SecurityException e) {
            denied = true;
            Log.w(TAG, "watch next: no permission", e);
        } catch (RuntimeException e) {
            Log.w(TAG, "watch next", e);
        }
        return false;
    }

    /** The TV's own provider (API 26 and up; below that there is no Watch Next and every call is a no-op). */
    static final class ProviderStore implements Store {
        private final ContentResolver resolver;

        ProviderStore(Context c) {
            resolver = c.getContentResolver();
        }

        private static Uri row(long id) {
            return ContentUris.withAppendedId(TvContract.WatchNextPrograms.CONTENT_URI, id);
        }

        @Override
        public List<Row> read() {
            if (Build.VERSION.SDK_INT < 26) return null;
            String[] cols = {TvContract.WatchNextPrograms._ID, TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                    TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.COLUMN_TITLE,
                    TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.WatchNextPrograms.COLUMN_INTENT_URI, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS};
            try (Cursor c = resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, null)) {
                if (c == null) return null;
                List<Row> rows = new ArrayList<>();
                while (c.moveToNext()) {
                    Entry e = new Entry();
                    e.id = c.getString(1);
                    e.type = c.getInt(2);
                    e.title = c.getString(3);
                    e.description = c.getString(4);
                    e.posterUri = c.getString(5);
                    e.intentUri = c.getString(6);
                    e.positionMs = c.getLong(7);
                    rows.add(new Row(c.getLong(0), e.id, e));
                }
                return rows;
            }
        }

        @Override
        public void delete(long rowId) {
            resolver.delete(row(rowId), null, null);
        }

        @Override
        public void update(long rowId, Entry e) {
            resolver.update(row(rowId), values(e), null, null);
        }

        @Override
        public void insert(Entry e) {
            resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, values(e));
        }
    }
}
