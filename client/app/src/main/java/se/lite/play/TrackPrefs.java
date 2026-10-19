package se.lite.play;

import android.content.SharedPreferences;

import java.util.Locale;

/**
 * The persisted track settings (G5): Undertext (Automatisk, Alltid på, Av), Ljudspråk (Automatisk, Svenska,
 * Engelska, Original, or the language last picked in the sheet) and the subtitle language. The sheet's pick of
 * on or off is never stored here: it holds for the session, in MainActivity.
 */
final class TrackPrefs {
    static final String[] SUB_LABELS = {"Automatisk", "Alltid på", "Av"};
    static final String AUTO = "auto", ORIGINAL = "orig";

    private final SharedPreferences p;

    TrackPrefs(SharedPreferences p) {
        this.p = p;
    }

    int subtitleMode() {
        int m = p.getInt("subMode", TrackPolicy.SUB_AUTO);
        return m >= 0 && m < SUB_LABELS.length ? m : TrackPolicy.SUB_AUTO;
    }

    void nextSubtitleMode() {
        p.edit().putInt("subMode", (subtitleMode() + 1) % SUB_LABELS.length).apply();
    }

    String subtitleLabel() {
        return SUB_LABELS[subtitleMode()];
    }

    /** "auto", "orig" or a language code. */
    String audio() {
        return p.getString("audioLang", AUTO);
    }

    /** Automatisk, Svenska, Engelska, Original, then back; a language picked in the sheet goes to Automatisk. */
    void nextAudio() {
        String a = audio(), n;
        switch (a) {
            case AUTO: n = "sv"; break;
            case "sv": n = "en"; break;
            case "en": n = ORIGINAL; break;
            default: n = AUTO; break;
        }
        p.edit().putString("audioLang", n).apply();
    }

    void setAudio(String language) {
        if (language != null) p.edit().putString("audioLang", language).apply();
    }

    String audioLabel(Locale ui) {
        switch (audio()) {
            case AUTO: return "Automatisk";
            case ORIGINAL: return "Original";
            default:
                String n = Locale.forLanguageTag(audio()).getDisplayLanguage(ui);
                return n.isEmpty() ? audio() : Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    /** The subtitle language: Swedish until the viewer picks another in the sheet. */
    String textLanguage() {
        return p.getString("textLang", "sv");
    }

    void setTextLanguage(String language) {
        if (language != null) p.edit().putString("textLang", language).apply();
    }
}
