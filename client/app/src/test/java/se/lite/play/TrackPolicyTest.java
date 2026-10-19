package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.C;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * G5: which audio and text track plays, when the pill exists, what the sheet's rows say, what is remembered.
 * Tracks are fabricated ({@link TrackFixtures}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrackPolicyTest {
    private static final Locale SV = Locale.forLanguageTag("sv-SE");
    private static final List<String> SV_FIRST = Collections.singletonList("sv");

    private static String audioKey(List<TrackInfo> t, int i) {
        return i < 0 ? null : t.get(i).key();
    }

    // ---- The pill's presence rule (U0: no empty mode) -------------------------------------------

    @Test
    public void thePillNeedsASecondAudioTrackOrAnyTextTrack() {
        assertFalse("one audio track, no text: nothing to choose", TrackPolicy.pillWanted(TrackFixtures.none()));
        assertFalse(TrackPolicy.pillWanted(Collections.emptyList()));
        assertTrue("sv + en audio", TrackPolicy.pillWanted(TrackFixtures.svEn()));
        assertTrue("one audio track and a text track", TrackPolicy.pillWanted(TrackFixtures.enSv()));
        assertTrue(TrackPolicy.pillWanted(Arrays.asList(TrackFixtures.audio("sv", 0, 2, MimeTypes.AUDIO_AAC, true, 0),
                TrackFixtures.text("sv", 0, false, 1))));
    }

    // ---- Audio ----------------------------------------------------------------------------------

    @Test
    public void audioFollowsThePreferredLanguage() {
        List<TrackInfo> t = TrackFixtures.svEn();
        assertEquals("sv", t.get(TrackPolicy.pickAudio(t, SV_FIRST, null)).language);
        assertEquals("en", t.get(TrackPolicy.pickAudio(t, Collections.singletonList("en"), null)).language);
        assertEquals("a language the stream lacks: the first listed (it is already playing)", "sv",
                t.get(TrackPolicy.pickAudio(t, Collections.singletonList("de"), null)).language);
    }

    @Test
    public void originalLeavesTheStreamsOwnChoice() {
        List<TrackInfo> t = TrackFixtures.svEn();
        t.get(0).selected = false;
        t.get(1).selected = true;
        assertEquals(1, TrackPolicy.pickAudio(t, Collections.emptyList(), null));
    }

    @Test
    public void anAudioDescriptionNeverPlaysUnlessPicked() {
        List<TrackInfo> t = TrackFixtures.ad(); // the stream plays the description (index 0)
        int got = TrackPolicy.pickAudio(t, SV_FIRST, null);
        assertFalse("not the description", t.get(got).describes());
        assertEquals("sv", t.get(got).language);
        assertEquals("the viewer's pick wins", 0, TrackPolicy.pickAudio(t, SV_FIRST, t.get(0).key()));
        assertEquals("the English pick wins over the Swedish preference", 3, TrackPolicy.pickAudio(t, SV_FIRST, t.get(3).key()));
    }

    @Test
    public void aTrackAlreadyPlayingWhichFitsIsKept() {
        List<TrackInfo> t = TrackFixtures.ad();
        t.get(0).selected = false;
        t.get(2).selected = true; // the stereo track plays; the 5.1 one is as good: do not switch
        assertEquals(2, TrackPolicy.pickAudio(t, SV_FIRST, null));
    }

    @Test
    public void aPickThatTheNextStreamLacksFallsBackToTheSettings() {
        List<TrackInfo> t = TrackFixtures.svEn();
        assertEquals(0, TrackPolicy.pickAudio(t, SV_FIRST, "de"));
    }

    @Test
    public void autoLanguageIsTheDeviceLanguage() {
        assertEquals(Collections.singletonList("sv"), TrackPolicy.audioLanguages("auto", "sv"));
        assertEquals(Collections.emptyList(), TrackPolicy.audioLanguages("orig", "sv"));
        assertEquals(Collections.singletonList("en"), TrackPolicy.audioLanguages("en", "sv"));
    }

    // ---- Text -----------------------------------------------------------------------------------

    @Test
    public void automaticShowsSwedishSubtitlesWhenTheAudioIsNotSwedish() {
        List<TrackInfo> t = TrackFixtures.enSv();
        int got = TrackPolicy.pickText(t, TrackPolicy.SUB_AUTO, "sv", "en", null);
        assertEquals("sv", t.get(got).language);
        assertEquals("Swedish audio: no subtitles", -1, TrackPolicy.pickText(t, TrackPolicy.SUB_AUTO, "sv", "sv", null));
    }

    @Test
    public void automaticShowsNothingWhenThereIsNoSwedishText() {
        List<TrackInfo> t = Arrays.asList(TrackFixtures.audio("en", 0, 2, MimeTypes.AUDIO_AAC, true, 0), TrackFixtures.text("en", 0, false, 1));
        assertEquals(-1, TrackPolicy.pickText(t, TrackPolicy.SUB_AUTO, "sv", "en", null));
    }

    @Test
    public void alwaysShowsTextAlsoWithSwedishAudioAndOffNever() {
        List<TrackInfo> t = TrackFixtures.enSv();
        assertEquals(1, TrackPolicy.pickText(t, TrackPolicy.SUB_ALWAYS, "sv", "sv", null));
        assertEquals(-1, TrackPolicy.pickText(t, TrackPolicy.SUB_OFF, "sv", "en", null));
    }

    @Test
    public void alwaysFallsBackToAnyPlainTrackWhenTheLanguageIsMissing() {
        List<TrackInfo> t = TrackFixtures.enSv();
        assertEquals(1, TrackPolicy.pickText(t, TrackPolicy.SUB_ALWAYS, "de", "en", null));
    }

    @Test
    public void thePlainTrackComesBeforeTheOneForTheHardOfHearing() {
        List<TrackInfo> t = Arrays.asList(
                TrackFixtures.text("sv", C.ROLE_FLAG_TRANSCRIBES_DIALOG, false, 0),
                TrackFixtures.text("sv", 0, false, 1));
        assertEquals(1, TrackPolicy.pickText(t, TrackPolicy.SUB_ALWAYS, "sv", "en", null));
        assertEquals("the SDH track alone is used when it is all there is", 0,
                TrackPolicy.pickText(t.subList(0, 1), TrackPolicy.SUB_ALWAYS, "sv", "en", null));
    }

    @Test
    public void theSessionPickBeatsTheSetting() {
        List<TrackInfo> t = TrackFixtures.enSv();
        assertEquals("Av in Alltid på", -1, TrackPolicy.pickText(t, TrackPolicy.SUB_ALWAYS, "sv", "en", TrackPolicy.TEXT_OFF));
        assertEquals("a track in Av", 2, TrackPolicy.pickText(t, TrackPolicy.SUB_OFF, "sv", "en", t.get(2).key()));
        assertEquals("a track with Swedish audio in Automatisk", 1,
                TrackPolicy.pickText(t, TrackPolicy.SUB_AUTO, "sv", "sv", t.get(1).key()));
    }

    @Test
    public void noTextTracksMeansNoText() {
        assertEquals(-1, TrackPolicy.pickText(TrackFixtures.svEn(), TrackPolicy.SUB_ALWAYS, "sv", "en", null));
    }

    // ---- The sheet's rows -----------------------------------------------------------------------

    private static String dump(List<TrackPolicy.Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (TrackPolicy.Row r : rows) {
            sb.append(r.header ? "# " : r.selected ? "* " : "  ").append(r.label);
            if (r.sub != null) sb.append(" / ").append(r.sub);
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    public void rowsOfTwoLanguages() {
        assertEquals("# Ljud\n* Svenska\n  Engelska\n", dump(TrackPolicy.rows(TrackFixtures.svEn(), SV)));
    }

    @Test
    public void rowsOfOneAudioTrackAndText() {
        assertEquals("# Undertext\n* Av\n  Svenska\n  Engelska / Hörselskadade/SDH\n", dump(TrackPolicy.rows(TrackFixtures.enSv(), SV)));
    }

    @Test
    public void rowsTellTracksOfOneLanguageApart() {
        String got = dump(TrackPolicy.rows(TrackFixtures.ad(), SV));
        assertEquals("# Ljud\n"
                + "* Svenska / Syntolkning · Stereo · AAC\n"
                + "  Svenska / 5.1 · E-AC-3\n"
                + "  Svenska / Stereo · AAC\n"
                + "  Engelska\n"
                + "# Undertext\n* Av\n  Svenska\n", got);
    }

    @Test
    public void aLongLanguageNameIsQualifiedOnlyWhenItsSiblingNeedsIt() {
        List<TrackPolicy.Row> rows = TrackPolicy.rows(TrackFixtures.longName(), SV);
        String d = dump(rows);
        assertTrue(d, d.contains("Kinesiska ("));
        assertTrue("two Chinese tracks differ by script and region: " + d, d.indexOf("Kinesiska (") != d.lastIndexOf("Kinesiska ("));
        assertTrue("the Swedish one stays plain: " + d, d.contains("  Svenska\n") || d.contains("* Svenska\n"));
    }

    @Test
    public void everyOptionPointsAtItsTrackAndAvAtNone() {
        List<TrackInfo> t = TrackFixtures.enSv();
        for (TrackPolicy.Row r : TrackPolicy.rows(t, SV)) {
            if (r.header) continue;
            if (r.label.equals("Av")) assertEquals(-1, r.track);
            else assertTrue(t.get(r.track).type == TrackInfo.TEXT);
        }
    }

    @Test
    public void avIsSelectedWhenNoTextTrackPlays() {
        List<TrackInfo> t = TrackFixtures.enSv();
        assertTrue(TrackPolicy.rows(t, SV).get(1).selected);
        t.get(1).selected = true;
        List<TrackPolicy.Row> rows = TrackPolicy.rows(t, SV);
        assertFalse(rows.get(1).selected);
        assertTrue(rows.get(2).selected);
    }

    // ---- From the player's Tracks ---------------------------------------------------------------

    @Test
    public void tracksBecomeInfoWithNormalisedLanguageAndRoles() {
        Format sv = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setLanguage("swe").setChannelCount(6).build();
        Format ad = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("sv").setRoleFlags(C.ROLE_FLAG_DESCRIBES_VIDEO).build();
        Format sub = new Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_TTML).setLanguage("sv")
                .setRoleFlags(C.ROLE_FLAG_TRANSCRIBES_DIALOG).build();
        Format video = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build();
        Tracks tracks = new Tracks(com.google.common.collect.ImmutableList.of(
                new Tracks.Group(new TrackGroup(video), false, new int[] {C.FORMAT_HANDLED}, new boolean[] {true}),
                new Tracks.Group(new TrackGroup(sv), false, new int[] {C.FORMAT_HANDLED}, new boolean[] {true}),
                new Tracks.Group(new TrackGroup(ad), false, new int[] {C.FORMAT_HANDLED}, new boolean[] {false}),
                new Tracks.Group(new TrackGroup(sub), false, new int[] {C.FORMAT_HANDLED}, new boolean[] {false})));
        List<TrackInfo> got = TrackInfo.from(tracks);
        assertEquals(3, got.size());
        assertEquals("sv", got.get(0).language);
        assertEquals(1, got.get(0).group);
        assertTrue(got.get(0).selected);
        assertEquals(6, got.get(0).channels);
        assertTrue(got.get(1).describes());
        assertTrue(got.get(2).sdh());
        assertEquals("sv+sdh", got.get(2).key());
    }

    // ---- Persistence ----------------------------------------------------------------------------

    private static TrackPrefs prefs() {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("tracks-test", Context.MODE_PRIVATE).edit().clear().commit();
        return new TrackPrefs(c.getSharedPreferences("tracks-test", Context.MODE_PRIVATE));
    }

    @Test
    public void settingsDefaultToAutomaticAndSwedishAndSurviveARestart() {
        TrackPrefs p = prefs();
        assertEquals("Automatisk", p.subtitleLabel());
        assertEquals("Automatisk", p.audioLabel(SV));
        assertEquals("sv", p.textLanguage());
        p.nextSubtitleMode();
        p.nextAudio();
        TrackPrefs again = new TrackPrefs(RuntimeEnvironment.getApplication().getSharedPreferences("tracks-test", Context.MODE_PRIVATE));
        assertEquals("Alltid på", again.subtitleLabel());
        assertEquals("Svenska", again.audioLabel(SV));
    }

    @Test
    public void theSettingsCycleThroughTheirValuesAndBack() {
        TrackPrefs p = prefs();
        String[] subs = new String[4], audio = new String[5];
        for (int i = 0; i < subs.length; i++) {
            subs[i] = p.subtitleLabel();
            p.nextSubtitleMode();
        }
        for (int i = 0; i < audio.length; i++) {
            audio[i] = p.audioLabel(SV);
            p.nextAudio();
        }
        assertEquals(Arrays.asList("Automatisk", "Alltid på", "Av", "Automatisk"), Arrays.asList(subs));
        assertEquals(Arrays.asList("Automatisk", "Svenska", "Engelska", "Original", "Automatisk"), Arrays.asList(audio));
    }

    @Test
    public void aLanguagePickedInTheSheetIsRememberedAndOthersFallBackToAutomatic() {
        TrackPrefs p = prefs();
        p.setAudio("de");
        assertEquals("Tyska", p.audioLabel(SV));
        p.nextAudio();
        assertEquals("Automatisk", p.audioLabel(SV));
        p.setTextLanguage("en");
        assertEquals("en", p.textLanguage());
        assertNull(TrackPolicy.languageOf(new TrackInfo(TrackInfo.AUDIO, null, 0, 2, null, null, 0, false)));
        assertEquals("zh", TrackPolicy.languageOf(new TrackInfo(TrackInfo.AUDIO, "zh-Hant-HK", 0, 2, null, null, 0, false)));
    }
}
