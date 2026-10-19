package se.lite.play;

import android.net.Uri;

/**
 * The app's own links (G6): {@code flinga://channel/<id>}, {@code flinga://title/<id>[?play=1]},
 * {@code flinga://guide} and {@code flinga://search[?q=<text>]}. Watch Next uses them to resume a title, and the
 * Assistant and later launchers can use them to open a place in the app. Pure parsing: an unknown or broken link is
 * {@code null}, and the app then simply opens as it does from its icon.
 */
final class DeepLink {
    static final String SCHEME = "flinga";
    static final int CHANNEL = 0, TITLE = 1, GUIDE = 2, SEARCH = 3;
    private static final int ID_MAX = 128;

    final int kind;
    /** The channel id or the series or media id; null for the guide and for search. */
    final String id;
    /** The text to search for ({@link #SEARCH} only); empty opens an empty Sök. */
    final String query;
    /** {@link #TITLE} only: play it (resume where the viewer left off) instead of opening its page. */
    final boolean play;

    private DeepLink(int kind, String id, String query, boolean play) {
        this.kind = kind;
        this.id = id;
        this.query = query;
        this.play = play;
    }

    static DeepLink parse(Uri u) {
        if (u == null || !SCHEME.equalsIgnoreCase(u.getScheme()) || !u.isHierarchical()) return null;
        String host = u.getHost();
        if (host == null) return null;
        switch (host.toLowerCase(java.util.Locale.ROOT)) {
            case "channel": {
                String id = onlySegment(u);
                return id == null ? null : new DeepLink(CHANNEL, id, null, false);
            }
            case "title": {
                String id = onlySegment(u);
                return id == null ? null : new DeepLink(TITLE, id, null, "1".equals(u.getQueryParameter("play")));
            }
            case "guide":
                return new DeepLink(GUIDE, null, null, false);
            case "search": {
                String q = u.getQueryParameter("q");
                return new DeepLink(SEARCH, null, q == null ? "" : q.trim(), false);
            }
            default:
                return null;
        }
    }

    /** The single path segment, or null when there is none, more than one, or it is too long. */
    private static String onlySegment(Uri u) {
        java.util.List<String> seg = u.getPathSegments();
        if (seg.size() != 1) return null;
        String id = seg.get(0);
        return id.isEmpty() || id.length() > ID_MAX ? null : id;
    }

    static Uri title(String id, boolean play) {
        Uri.Builder b = new Uri.Builder().scheme(SCHEME).authority("title").appendPath(id);
        if (play) b.appendQueryParameter("play", "1");
        return b.build();
    }
}
