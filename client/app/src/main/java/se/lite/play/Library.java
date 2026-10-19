package se.lite.play;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fortsätt titta and Min lista (F15), cache first: the lists come from Telia's own panels, so they match
 * the official app on every device; a copy on disk draws the menu instantly on the next start (E6, UX.md
 * 3.4), and positions saved on this TV show at once, before Telia has processed the progress events.
 *
 * Server data wins: a refresh replaces both lists, except for positions this TV saved after the refresh
 * started. Main thread only; disk writes go to the executor.
 *
 * Thresholds (UX.md 7.4, after Android TV's Watch Next rules): a title enters Fortsätt titta after 2 min,
 * counts as finished with less than 3 min left or at 95 %, and playback resumes silently from 1 min.
 */
final class Library {
    static final long ENTER_MS = 2 * 60_000L;
    static final long FINISHED_LEFT_MS = 3 * 60_000L;
    static final long RESUME_MIN_MS = 60_000L;
    static final int KEEP = 20;

    /** A position this TV saved; wins over the server's until a refresh started after it. */
    static final class Local {
        long positionMs, durationMs, at;
    }

    final List<Api.Saved> continueWatching = new ArrayList<>();
    final List<Api.Saved> myList = new ArrayList<>();
    private final Map<String, Local> positions = new HashMap<>();
    private final File file;
    private final Executor disk;
    /** False until the first server answer or a disk copy: the menu then leaves the groups out. */
    boolean known;

    Library(File dir, Executor disk) {
        this.file = new File(dir, "library.json");
        this.disk = disk;
    }

    // ---- Queries ----------------------------------------------------------------------------

    boolean inMyList(String id) {
        return id != null && find(myList, id) != null;
    }

    /**
     * Min lista in display order (B6): last watched first. Telia's panel rows carry no timestamp, so the
     * order of Fortsätt titta stands for it (Telia's own list, newest first; a title played on this TV moves
     * to its top); starred titles never watched follow in the order the API returned them.
     */
    List<Api.Saved> starred() {
        List<Api.Saved> out = new ArrayList<>(myList.size());
        for (Api.Saved w : continueWatching) {
            Api.Saved s = find(myList, w.id);
            if (s != null && !out.contains(s)) out.add(s);
        }
        for (Api.Saved s : myList) if (!out.contains(s)) out.add(s);
        return out;
    }

    /** Where to start an on-demand title: this TV's position if newer, else Telia's; 0 = from the start. */
    long resumeMs(Api.Playable p) {
        if (p == null || p.mediaId == null) return 0;
        Local l = positions.get(p.mediaId);
        long pos = l != null ? l.positionMs : p.startPositionMs;
        long dur = l != null && l.durationMs > 0 ? l.durationMs : p.durationMs;
        if (pos < RESUME_MIN_MS) return 0;
        if (dur > 0 && finished(pos, dur)) return 0;
        return pos;
    }

    /** The title with this id (series or film) in Fortsätt titta, else in Min lista; null when in neither. */
    Api.Saved saved(String id) {
        if (id == null) return null;
        Api.Saved s = find(continueWatching, id);
        return s != null ? s : find(myList, id);
    }

    /** What this TV saved for a media since the last refresh, or null. */
    Local local(String mediaId) {
        return mediaId == null ? null : positions.get(mediaId);
    }

    static boolean finished(long pos, long dur) {
        return dur > 0 && (dur - pos < FINISHED_LEFT_MS || pos >= dur * 0.95);
    }

    // ---- Updates ----------------------------------------------------------------------------

    /**
     * Playback of an on-demand title stopped at `pos`. Updates Fortsätt titta at once: the title moves to
     * the top with its position, or, when finished, a film leaves the row and a series points at
     * `nextMediaId` (or leaves when there is none).
     */
    void played(Api.Playable p, long pos, long dur, String nextMediaId, long now) {
        if (p == null || p.mediaId == null) return;
        Local l = new Local();
        l.positionMs = pos;
        l.durationMs = dur;
        l.at = now;
        positions.put(p.mediaId, l);
        boolean series = p.seriesId != null;
        String id = series ? p.seriesId : p.mediaId;
        Api.Saved s = find(continueWatching, id);
        boolean done = finished(pos, dur);
        if (s == null && (pos < ENTER_MS || done && !series)) {
            save();
            return;
        }
        if (s == null) {
            s = new Api.Saved();
            s.id = id;
            s.series = series;
            s.title = series && p.seriesTitle != null && !p.seriesTitle.isEmpty() ? p.seriesTitle : p.title;
        }
        continueWatching.remove(s);
        if (done) {
            if (!series || nextMediaId == null) {
                save();
                return;
            }
            s.mediaId = nextMediaId;
            s.positionMs = 0;
            s.percent = 0;
            s.minutesLeft = -1;
        } else {
            s.mediaId = p.mediaId;
            s.positionMs = pos;
            s.percent = dur > 0 ? (int) (100 * pos / dur) : 0;
            s.minutesLeft = dur > 0 ? (int) Math.max(1, (dur - pos + 59_999) / 60_000) : -1;
        }
        continueWatching.add(0, s);
        while (continueWatching.size() > KEEP) continueWatching.remove(continueWatching.size() - 1);
        known = true;
        save();
    }

    /** Optimistic Min lista change; the caller reverts it with the opposite call if the server refuses. */
    void setInMyList(String id, String title, boolean series, boolean in) {
        Api.Saved s = find(myList, id);
        if (in && s == null) {
            s = new Api.Saved();
            s.id = id;
            s.title = title;
            s.series = series;
            s.mediaId = series ? null : id;
            myList.add(0, s);
        } else if (!in && s != null) {
            myList.remove(s);
        }
        known = true;
        save();
    }

    void hide(String id) {
        Api.Saved s = find(continueWatching, id);
        if (s != null) continueWatching.remove(s);
        save();
    }

    /**
     * The server's lists, fetched from `startedAt`. Positions this TV saved after that stay on top of
     * them; older local positions are dropped, because Telia now has them.
     */
    void applyServer(List<Api.Saved> cw, List<Api.Saved> ml, long startedAt) {
        if (cw != null) {
            continueWatching.clear();
            continueWatching.addAll(cw);
            for (Iterator<Map.Entry<String, Local>> it = positions.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Local> e = it.next();
                if (e.getValue().at < startedAt) it.remove();
            }
            for (Api.Saved s : continueWatching) {
                Local l = s.mediaId == null ? null : positions.get(s.mediaId);
                if (l == null) continue;
                s.positionMs = l.positionMs;
                if (l.durationMs > 0) {
                    s.percent = (int) (100 * l.positionMs / l.durationMs);
                    s.minutesLeft = (int) Math.max(1, (l.durationMs - l.positionMs + 59_999) / 60_000);
                }
            }
        }
        if (ml != null) {
            myList.clear();
            myList.addAll(ml);
        }
        known = true;
        save();
    }

    private static Api.Saved find(List<Api.Saved> list, String id) {
        for (Api.Saved s : list) if (s.id.equals(id)) return s;
        return null;
    }

    // ---- Disk ---------------------------------------------------------------------------------

    /** Reads the copy on disk; call once, before the first menu. Returns false when there is none. */
    boolean load() {
        try {
            if (!file.exists()) return false;
            JSONObject j = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            read(j.optJSONArray("continue"), continueWatching);
            read(j.optJSONArray("myList"), myList);
            JSONObject pos = j.optJSONObject("positions");
            if (pos != null) {
                for (Iterator<String> k = pos.keys(); k.hasNext(); ) {
                    String id = k.next();
                    JSONObject o = pos.getJSONObject(id);
                    Local l = new Local();
                    l.positionMs = o.optLong("p");
                    l.durationMs = o.optLong("d");
                    l.at = o.optLong("t");
                    positions.put(id, l);
                }
            }
            known = true;
            return true;
        } catch (Exception e) {
            return false; // a broken copy is the same as none: the server answer replaces it
        }
    }

    private void save() {
        final String text;
        try {
            JSONObject pos = new JSONObject();
            for (Map.Entry<String, Local> e : positions.entrySet()) {
                pos.put(e.getKey(), new JSONObject().put("p", e.getValue().positionMs)
                        .put("d", e.getValue().durationMs).put("t", e.getValue().at));
            }
            text = new JSONObject().put("continue", write(continueWatching)).put("myList", write(myList))
                    .put("positions", pos).toString();
        } catch (Exception e) {
            return;
        }
        disk.execute(() -> {
            try {
                File tmp = new File(file.getPath() + ".tmp");
                Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
                if (!tmp.renameTo(file)) Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // the next save writes it again
            }
        });
    }

    private static JSONArray write(List<Api.Saved> list) throws Exception {
        JSONArray a = new JSONArray();
        for (Api.Saved s : list) {
            a.put(new JSONObject().put("id", s.id).put("title", s.title).put("provider", s.provider)
                    .put("series", s.series).put("media", s.mediaId == null ? "" : s.mediaId)
                    .put("img", s.imageUrl == null ? "" : s.imageUrl)
                    .put("pos", s.positionMs).put("pct", s.percent).put("left", s.minutesLeft));
        }
        return a;
    }

    private static void read(JSONArray a, List<Api.Saved> into) {
        into.clear();
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optString("id").isEmpty()) continue;
            Api.Saved s = new Api.Saved();
            s.id = o.optString("id");
            s.title = o.optString("title");
            s.provider = o.optString("provider");
            s.series = o.optBoolean("series");
            s.mediaId = o.optString("media").isEmpty() ? null : o.optString("media");
            s.imageUrl = o.optString("img").isEmpty() ? null : o.optString("img");
            s.positionMs = o.optLong("pos");
            s.percent = o.optInt("pct");
            s.minutesLeft = o.optInt("left", -1);
            into.add(s);
        }
    }
}
