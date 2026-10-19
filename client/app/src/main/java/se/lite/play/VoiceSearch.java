package se.lite.play;

import android.app.SearchManager;
import android.content.Intent;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Search handed to the app by the system or the Assistant (G6): ACTION_SEARCH and ACTION_ASSIST open Sök with the
 * query run; MEDIA_PLAY_FROM_SEARCH ("spela Bron") does the same and plays the one result that is certain.
 * Pure, so the intent parsing and the confidence rule are unit tests.
 */
final class VoiceSearch {
    private VoiceSearch() {}

    /** android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH, spelled out: no import for a string. */
    static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String EXTRA_TITLE = "android.intent.extra.title";

    static final class Request {
        final String query;
        /** The Assistant asked to play, not to look. */
        final boolean play;

        Request(String query, boolean play) {
            this.query = query;
            this.play = play;
        }
    }

    /** The request in an intent, or null when it is not a search. The query can be empty (Sök opens bare). */
    static Request parse(Intent i) {
        if (i == null || i.getAction() == null) return null;
        String a = i.getAction();
        boolean play = ACTION_PLAY_FROM_SEARCH.equals(a);
        if (!play && !Intent.ACTION_SEARCH.equals(a) && !Intent.ACTION_ASSIST.equals(a)) return null;
        String q = i.getStringExtra(SearchManager.QUERY);
        if ((q == null || q.trim().isEmpty()) && play) q = i.getStringExtra(EXTRA_TITLE);
        q = q == null ? "" : q.trim();
        return new Request(q, play && !q.isEmpty());
    }

    /** Lower case, no accents, letters and digits only: "SVT 1", "svt1" and "Svt-1" are one name. */
    static String norm(String s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** The one channel whose name is the query, or null when none or several are. */
    static Api.Channel channel(String query, List<Api.Channel> channels) {
        String q = norm(query);
        if (q.isEmpty()) return null;
        Api.Channel hit = null;
        for (Api.Channel c : channels) {
            if (!q.equals(norm(c.name))) continue;
            if (hit != null) return null;
            hit = c;
        }
        return hit;
    }

    /**
     * The one result that is certain: its title is the query (case, accents and punctuation aside) and no other
     * result's is. Two results with the same title, a series and its film, are not certain: Sök shows them.
     */
    static Api.Asset asset(String query, List<Api.Asset> results) {
        String q = norm(query);
        if (q.isEmpty()) return null;
        Api.Asset hit = null;
        for (Api.Asset a : results) {
            if (!q.equals(norm(a.title))) continue;
            if (hit != null) return null;
            hit = a;
        }
        return hit;
    }
}
