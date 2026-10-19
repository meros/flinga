package se.lite.play;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * One audio or text track group of the stream playing, reduced to what the choice needs (G5): a plain value,
 * so the policy and the sheet can be tested without a player. {@code group} is the index in
 * {@link Tracks#getGroups()} the choice is applied to.
 */
final class TrackInfo {
    static final int AUDIO = C.TRACK_TYPE_AUDIO, TEXT = C.TRACK_TYPE_TEXT;
    private static final int SDH_ROLES = C.ROLE_FLAG_TRANSCRIBES_DIALOG | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND;

    final int type;
    /** Normalised BCP 47 (sv, en, pt-BR) or null when the stream does not say. */
    final String language;
    final int roleFlags;
    final int channels;
    /** The sample MIME type of the first track ("audio/eac3"), or null. */
    final String mime;
    /** The stream's own name for the track, used only when it has no language. */
    final String label;
    final int group;
    boolean selected;

    TrackInfo(int type, String language, int roleFlags, int channels, String mime, String label, int group, boolean selected) {
        this.type = type;
        this.language = language == null || language.isEmpty() || "und".equals(language) ? null : language;
        this.roleFlags = roleFlags;
        this.channels = channels;
        this.mime = mime;
        this.label = label;
        this.group = group;
        this.selected = selected;
    }

    /** An audio track that describes the picture (audio description, "Syntolkning"). */
    boolean describes() {
        return type == AUDIO && (roleFlags & C.ROLE_FLAG_DESCRIBES_VIDEO) != 0;
    }

    /** A text track for the hard of hearing (SDH): it also transcribes sound. */
    boolean sdh() {
        return type == TEXT && (roleFlags & SDH_ROLES) != 0;
    }

    /** The same track in the next stream, as the session's choice remembers it: language and kind. */
    String key() {
        return (language == null ? "und" : language) + (describes() ? "+ad" : "") + (sdh() ? "+sdh" : "");
    }

    /** The audio and text groups the player offers and can play, in stream order. */
    static List<TrackInfo> from(Tracks tracks) {
        List<TrackInfo> out = new ArrayList<>();
        List<Tracks.Group> groups = tracks.getGroups();
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group g = groups.get(i);
            int type = g.getType();
            if ((type != AUDIO && type != TEXT) || !g.isSupported() || g.length == 0) continue;
            Format f = g.getTrackFormat(0);
            String lang = f.language == null ? null : Util.normalizeLanguageCode(f.language);
            String mime = f.sampleMimeType != null ? f.sampleMimeType : MimeTypes.getMediaMimeType(f.codecs);
            out.add(new TrackInfo(type, lang, f.roleFlags, f.channelCount, mime, f.label, i, g.isSelected()));
        }
        return out;
    }
}
