package se.lite.play;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which audio and text track plays, and what the Ljud och text sheet shows (G5). Pure: tracks in, a choice
 * out, so every rule is a unit test. The player is only the place the choice is applied (MainActivity).
 *
 * Audio: the viewer's pick of this session, else the first preferred language, never an audio description
 * unless picked (it is a second track of the same language, not a language). Text: the viewer's pick of this
 * session, else the setting (Automatisk: Swedish subtitles when the audio is not Swedish).
 */
final class TrackPolicy {
    private TrackPolicy() {}

    /** Undertext setting. */
    static final int SUB_AUTO = 0, SUB_ALWAYS = 1, SUB_OFF = 2;
    /** The session's text pick that means "Av". */
    static final String TEXT_OFF = "off";

    /** U0: the pill exists only when there is something to choose: a second audio track or any text track. */
    static boolean pillWanted(List<TrackInfo> tracks) {
        return count(tracks, TrackInfo.AUDIO) > 1 || count(tracks, TrackInfo.TEXT) > 0;
    }

    static int count(List<TrackInfo> tracks, int type) {
        int n = 0;
        for (TrackInfo t : tracks) if (t.type == type) n++;
        return n;
    }

    // ---- The choice ---------------------------------------------------------------------------

    /**
     * The audio track that should play, as an index in {@code tracks}, or -1 when there is none to pick.
     * {@code preferred} are language codes in order (empty = the stream's own choice, "Original"); {@code
     * pick} is the key of the track the viewer chose this session, or null.
     */
    static int pickAudio(List<TrackInfo> tracks, List<String> preferred, String pick) {
        List<Integer> audio = indices(tracks, TrackInfo.AUDIO);
        if (audio.isEmpty()) return -1;
        if (pick != null) for (int i : audio) if (tracks.get(i).key().equals(pick)) return i;
        List<Integer> plain = new ArrayList<>();
        for (int i : audio) if (!tracks.get(i).describes()) plain.add(i);
        List<Integer> pool = plain.isEmpty() ? audio : plain;
        for (String lang : preferred) {
            List<Integer> hit = new ArrayList<>();
            for (int i : pool) if (sameLanguage(tracks.get(i).language, lang)) hit.add(i);
            if (!hit.isEmpty()) return selectedOrFirst(tracks, hit);
        }
        return selectedOrFirst(tracks, pool);
    }

    /**
     * The text track that should show, or -1 for none. {@code audioLanguage} is the language of the audio
     * that plays; {@code pick} is {@link #TEXT_OFF}, the key of a track the viewer chose this session, or null.
     */
    static int pickText(List<TrackInfo> tracks, int mode, String textLanguage, String audioLanguage, String pick) {
        List<Integer> text = indices(tracks, TrackInfo.TEXT);
        if (text.isEmpty()) return -1;
        if (TEXT_OFF.equals(pick)) return -1;
        if (pick != null) for (int i : text) if (tracks.get(i).key().equals(pick)) return i;
        if (mode == SUB_OFF) return -1;
        if (mode == SUB_AUTO && sameLanguage(audioLanguage, "sv")) return -1;
        int hit = bestText(tracks, text, textLanguage);
        if (hit >= 0) return hit;
        return mode == SUB_ALWAYS ? bestText(tracks, text, null) : -1;
    }

    /** The text track in {@code lang} (the plain one before the SDH one); with a null language the first plain one. */
    private static int bestText(List<TrackInfo> tracks, List<Integer> text, String lang) {
        int sdh = -1;
        for (int i : text) {
            TrackInfo t = tracks.get(i);
            if (lang != null && !sameLanguage(t.language, lang)) continue;
            if (!t.sdh()) return i;
            if (sdh < 0) sdh = i;
        }
        return sdh;
    }

    private static int selectedOrFirst(List<TrackInfo> tracks, List<Integer> pool) {
        for (int i : pool) if (tracks.get(i).selected) return i;
        return pool.get(0);
    }

    private static List<Integer> indices(List<TrackInfo> tracks, int type) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).type == type) out.add(i);
        return out;
    }

    /** "sv" matches "sv" and "sv-SE"; a missing language matches nothing. */
    static boolean sameLanguage(String a, String b) {
        if (a == null || b == null) return false;
        return base(a).equals(base(b));
    }

    private static String base(String tag) {
        int dash = tag.indexOf('-');
        return (dash < 0 ? tag : tag.substring(0, dash)).toLowerCase(Locale.ROOT);
    }

    /** The preferred audio languages for the Ljudspråk setting: "auto" is the device language, "orig" none. */
    static List<String> audioLanguages(String setting, String deviceLanguage) {
        if (setting == null || setting.equals("auto")) {
            return deviceLanguage == null || deviceLanguage.isEmpty() ? Collections.emptyList() : Collections.singletonList(deviceLanguage);
        }
        if (setting.equals("orig")) return Collections.emptyList();
        return Collections.singletonList(setting);
    }

    // ---- The sheet ----------------------------------------------------------------------------

    /** One row of the sheet: a group header or an option. {@code track} is the index in the track list, -1 for Av. */
    static final class Row {
        final String label, sub;
        final boolean header, selected;
        final int track;

        Row(String label, String sub, boolean header, boolean selected, int track) {
            this.label = label;
            this.sub = sub;
            this.header = header;
            this.selected = selected;
            this.track = track;
        }
    }

    /**
     * The rows of Ljud och text: the Ljud group (only with two or more audio tracks: one track is no choice),
     * then Undertext with Av first. Language names in Swedish; a track that shares its language with another
     * gets a secondary line that tells them apart (Syntolkning, channel layout and codec).
     */
    static List<Row> rows(List<TrackInfo> tracks, Locale ui) {
        List<Row> out = new ArrayList<>();
        List<Integer> audio = indices(tracks, TrackInfo.AUDIO), text = indices(tracks, TrackInfo.TEXT);
        if (audio.size() > 1) {
            out.add(new Row("Ljud", null, true, false, -1));
            for (int i : audio) {
                TrackInfo t = tracks.get(i);
                boolean shared = sharesLanguage(tracks, audio, i);
                String name = name(tracks, audio, i, ui, "Ljudspår " + (audio.indexOf(i) + 1));
                List<String> sub = new ArrayList<>();
                if (t.describes()) sub.add("Syntolkning");
                if (shared || t.describes()) {
                    String layout = layout(t.channels), codec = codec(t.mime);
                    if (layout != null) sub.add(layout);
                    if (codec != null) sub.add(codec);
                }
                out.add(new Row(name, sub.isEmpty() ? null : String.join(" · ", sub), false, t.selected, i));
            }
        }
        if (!text.isEmpty()) {
            out.add(new Row("Undertext", null, true, false, -1));
            boolean any = false;
            for (int i : text) any |= tracks.get(i).selected;
            out.add(new Row("Av", null, false, !any, -1));
            for (int i : text) {
                TrackInfo t = tracks.get(i);
                out.add(new Row(name(tracks, text, i, ui, "Undertext " + (text.indexOf(i) + 1)), t.sdh() ? "Hörselskadade/SDH" : null,
                        false, t.selected, i));
            }
        }
        return out;
    }

    private static boolean sharesLanguage(List<TrackInfo> tracks, List<Integer> group, int i) {
        for (int j : group) if (j != i && sameLanguage(tracks.get(i).language, tracks.get(j).language)) return true;
        return false;
    }

    /** The language in Swedish; the region or script too only when another track of the group has the same language. */
    private static String name(List<TrackInfo> tracks, List<Integer> group, int i, Locale ui, String fallback) {
        TrackInfo t = tracks.get(i);
        if (t.language == null) return t.label != null && !t.label.isEmpty() ? t.label : fallback;
        boolean qualify = false;
        for (int j : group) if (j != i && sameLanguage(t.language, tracks.get(j).language) && !t.language.equals(tracks.get(j).language)) qualify = true;
        Locale l = Locale.forLanguageTag(t.language);
        String n = qualify ? l.getDisplayName(ui) : l.getDisplayLanguage(ui);
        if (n.isEmpty() || n.equals(t.language)) return t.label != null && !t.label.isEmpty() ? t.label : t.language;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    static String layout(int channels) {
        switch (channels) {
            case 1: return "Mono";
            case 2: return "Stereo";
            case 6: return "5.1";
            case 8: return "7.1";
            default: return channels > 2 ? channels + " kanaler" : null;
        }
    }

    static String codec(String mime) {
        if (mime == null) return null;
        switch (mime) {
            case MimeTypes.AUDIO_AC3: return "AC-3";
            case MimeTypes.AUDIO_E_AC3: return "E-AC-3";
            case MimeTypes.AUDIO_E_AC3_JOC: return "Dolby Atmos";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_OPUS: return "Opus";
            case MimeTypes.AUDIO_DTS: return "DTS";
            default: return null;
        }
    }

    /** The language code of a track for the setting that remembers it ("sv"), or null when it has none. */
    static String languageOf(TrackInfo t) {
        return t.language == null ? null : Util.normalizeLanguageCode(base(t.language));
    }
}
