package se.lite.play;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FABRICATED track lists (G5). Nothing here was recorded from Telia: the stream's real tracks are known only
 * on the TV (client/design/TV-CHECKLIST.md, "Ljud och text"). They stand for the shapes the policy must handle.
 */
final class TrackFixtures {
    private TrackFixtures() {}

    static TrackInfo audio(String lang, int roles, int channels, String mime, boolean selected, int group) {
        return new TrackInfo(TrackInfo.AUDIO, lang, roles, channels, mime, null, group, selected);
    }

    static TrackInfo text(String lang, int roles, boolean selected, int group) {
        return new TrackInfo(TrackInfo.TEXT, lang, roles, 0, MimeTypes.APPLICATION_TTML, null, group, selected);
    }

    /** Swedish and English audio, no subtitles. */
    static List<TrackInfo> svEn() {
        return new ArrayList<>(Arrays.asList(
                audio("sv", 0, 2, MimeTypes.AUDIO_AAC, true, 0),
                audio("en", 0, 2, MimeTypes.AUDIO_AAC, false, 1)));
    }

    /** English audio, Swedish subtitles, English subtitles for the hard of hearing. */
    static List<TrackInfo> enSv() {
        return new ArrayList<>(Arrays.asList(
                audio("en", 0, 2, MimeTypes.AUDIO_AAC, true, 0),
                text("sv", 0, false, 1),
                text("en", C.ROLE_FLAG_TRANSCRIBES_DIALOG | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND, false, 2)));
    }

    /** Swedish audio in two layouts plus an audio description, English audio, Swedish subtitles. */
    static List<TrackInfo> ad() {
        return new ArrayList<>(Arrays.asList(
                audio("sv", C.ROLE_FLAG_DESCRIBES_VIDEO, 2, MimeTypes.AUDIO_AAC, true, 0), // the stream lists the description first and plays it
                audio("sv", 0, 6, MimeTypes.AUDIO_E_AC3, false, 1),
                audio("sv", 0, 2, MimeTypes.AUDIO_AAC, false, 2),
                audio("en", 0, 2, MimeTypes.AUDIO_AAC, false, 3),
                text("sv", 0, false, 4)));
    }

    /** One audio track and no text: nothing to choose. */
    static List<TrackInfo> none() {
        return new ArrayList<>(Arrays.asList(audio("sv", 0, 2, MimeTypes.AUDIO_AAC, true, 0)));
    }

    /** A language whose Swedish name is long, next to its sibling. */
    static List<TrackInfo> longName() {
        return new ArrayList<>(Arrays.asList(
                audio("sv", 0, 2, MimeTypes.AUDIO_AAC, true, 0),
                audio("zh-Hant-HK", 0, 2, MimeTypes.AUDIO_AAC, false, 1),
                audio("zh-Hans-CN", 0, 2, MimeTypes.AUDIO_AAC, false, 2),
                text("sv", 0, false, 3),
                text("zh-Hant-HK", 0, false, 4)));
    }

    static List<TrackInfo> named(String name) {
        switch (name) {
            case "svEn": return svEn();
            case "enSv": return enSv();
            case "ad": return ad();
            case "none": return none();
            case "long": return longName();
            default: throw new IllegalArgumentException(name);
        }
    }
}
