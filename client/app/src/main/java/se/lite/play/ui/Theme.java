package se.lite.play.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Design tokens for Flinga. Values mirror design/DESIGN.md; change them there first.
 * Colors are ARGB ints, sizes are dp/sp numbers converted with {@link #dp} and {@link #sp}.
 */
public final class Theme {
    private Theme() {}

    // ---- Color -------------------------------------------------------------------------
    /** App background when no video plays (login, errors before first frame). */
    public static final int BG = 0xFF0B0B10;
    /** Menu panel scrim over live video (92 %). */
    public static final int SCRIM_PANEL = 0xEB0E0E14;
    /** Bottom end of the OSD gradient over video (90 %). */
    public static final int SCRIM_OSD = 0xE6000000;
    /** Cards, search field, toast. */
    public static final int SURFACE = 0xFF1C1C25;
    public static final int SURFACE_RAISED = 0xFF262630;
    /** Focused row / control fill: the inverted, high-contrast focus state. */
    public static final int FOCUS = 0xFFF5F3F8;
    /** Text on a FOCUS fill. */
    public static final int ON_FOCUS = 0xFF14121A;
    public static final int ON_FOCUS_2 = 0xFF55505F;
    /** Logo plate on the focused card when there is no logo (the name): a step darker than FOCUS. */
    public static final int FOCUS_PLATE = 0xFFE3E0EA;
    /** Text on dark: primary, secondary, dim (not playable, upcoming). */
    public static final int TEXT = 0xFFF5F5F7;
    public static final int TEXT_2 = 0xFFB0AFBA;
    public static final int TEXT_3 = 0xFF85848F;
    /** Brand accent: fills only (pills, bars, progress on light). Not for small text on dark. */
    public static final int ACCENT = 0xFF990AE6;
    /** Accent for text, icons and progress on dark backgrounds (7:1 on the panel). */
    public static final int ACCENT_ON_DARK = 0xFFC27CF5;
    /** Progress tracks. */
    public static final int TRACK = 0x40FFFFFF;
    public static final int TRACK_ON_FOCUS = 0x2614121A;
    /** 1 dp separators and resting outlines. */
    public static final int HAIRLINE = 0x1FFFFFFF;
    public static final int SUCCESS = 0xFF4CD787;
    public static final int ERROR = 0xFFFF6B6B;

    // ---- Type scale (sp) ---------------------------------------------------------------
    public static final float TEXT_DISPLAY = 32f; // OSD channel name, login heading
    public static final float TEXT_TITLE = 24f;   // panel title, OSD now-title
    public static final float TEXT_BODY = 20f;    // row title, search input, toast
    public static final float TEXT_LABEL = 16f;   // row secondary line, meta, OSD next
    public static final float TEXT_CAPTION = 14f; // pills, key hints, eyebrows (bold, caps)
    public static final float TEXT_CODE = 40f;    // login code

    // ---- Spacing (dp) ------------------------------------------------------------------
    public static final int SP_XS = 4;
    public static final int SP_S = 8;
    public static final int SP_M = 12;
    public static final int SP_L = 16;
    public static final int SP_XL = 24;
    public static final int SP_XXL = 32;
    /**
     * The one screen margin, on all four sides (owner, 2026-10-19: "mycket marginal på sidorna och neråt, nästan ingen
     * uppåt"; it was 48 dp at the sides, 27 dp below and 8 dp above the tabs). Everything that sits on an edge uses it.
     */
    public static final int SAFE_X = 32;
    public static final int SAFE_Y = 32;

    // ---- Radii (dp) --------------------------------------------------------------------
    public static final int R_S = 4;  // progress bars, pills
    public static final int R_M = 8;  // rows, search field
    public static final int R_L = 12; // toast
    public static final int R_XL = 16; // QR card

    // ---- Component sizes (dp) ----------------------------------------------------------
    public static final int ROW_H_ONE = 56;
    public static final int ROW_H_TWO = 72;
    public static final int ROW_H_HEADER = 40; // group header row ("SERIER · 7")
    /** A group header as the first row: no air above it, so its text sits where a panel eyebrow sits. */
    public static final int ROW_H_HEADER_FIRST = ROW_H_HEADER - SP_L;
    public static final int KEY_H = 36;        // on-screen keyboard key height (72 px at 1080p)
    public static final int KEY_W = 52;        // one keyboard column: six of them are the keyboard's side column
    public static final int ROW_META_W = 96;   // meta slot (right-aligned): fits "120 min", "✓"
    public static final int LIST_FADE = 40;    // fade at both ends of the panel list (SnapList)
    public static final int LOGO_W = 80;       // channel logo slot in rows and headers, 16:9
    public static final int LOGO_H = 45;
    public static final int HEADER_LOGO_W = 56; // channel logo in a panel header (Tablå, Alternativ): the
    public static final int HEADER_LOGO_H = 32; // title's line height, so the logo reads as a title, not a hero
    public static final int OSD_LOGO_W = 112;  // channel logo slot in the OSD, 16:9
    /** The overlay's side margin (the rail's peek is gone with the rail; the margin stays). */
    public static final int OSD_SIDE = SAFE_X; // the strip lines up with the browser's left edge
    /** The Back key in every hint: the hook arrow of the remote's Back button, never "‹" (that read as D-pad left). */
    public static final String BACK = "↩";
    public static final int OSD_LOGO_H = 63;
    // F16 images (DESIGN.md, "Image budget"): fixed 16:9 slots, requested at exactly their pixel size.
    public static final int THUMB_W = 112;     // episode thumbnail in Serie rows (224 x 126 px at xhdpi, B5; owner 2026-10-19: the rows were too small and tight)
    public static final int THUMB_H = 63;
    public static final int ROW_H_THUMB = 76;  // episode row: the thumbnail plus 6.5 dp above and below; 3 whole rows under the hero band at 1080p
    public static final int ROW_H_PILLS = 48;  // the action pills and the season chips (a 40 dp pill, 4 dp above and below)
    public static final int RESULT_W = 96;     // search result thumbnail (192 x 108 px at xhdpi)
    public static final int RESULT_H = 54;
    public static final int PILL_H = 40;       // action buttons and season chips, in a 56 dp row
    public static final int CHIP_W = 112;      // season chip ("Säsong 21"), fixed
    public static final int ACTION_W = 232;    // primary action ("Fortsätt titta · S21 A3"), fixed
    public static final int ACTION_2_W = 168;  // Min lista toggle, fixed
    /** Serie: the detail list column, half the screen; the hero fills the rest. */
    public static final float PANEL_DETAIL_FRACTION = 0.50f;
    public static final int PANEL_DETAIL_MAX = 520;
    public static final int FADE_MS = 120;     // hero fade-in, at most 150 ms (F16 budget)
    public static final int PROGRESS_ROW = 3;
    public static final int PROGRESS_OSD = 4;
    public static final int FOCUS_BAR = 4;     // accent bar on the live row
    /** Alternativ sheet on the right (720 px at 1080p): a logo plate and "Föregående kanal" fit on one row. */
    public static final int SHEET_W = 400; // a logo column and "Bildformat: Hela bilden" on one line; the video stays 58 % visible

    // ---- The browser (Surface 2) and the picker (Surface 3) ---------------------------------
    /** The browser's lens tabs at the top: "Tablå" and "Katalog & Sök". */
    public static final int TAB_PILL_H = 40;
    /** The playing picture as a chip in the top right corner while the browser is up (16:9, as the episode thumbnail). */
    public static final int PIP_W = 112;
    public static final int PIP_H = 63;
    /** The tab row's block: the top margin, the chip's height and 12 dp of air; the lenses start below it. */
    public static final int TAB_H = SAFE_Y + PIP_H + SP_M;
    public static final int TAB_PAD = 16;
    /** The catalogue's fixed 16:9 poster slot (E6: geometry from the slot, never from the title). */
    public static final int POSTER_W = 224;
    public static final int POSTER_H = 126;
    public static final int POSTER_CAPTION = 44; // the title under a poster, two reserved lines
    public static final int POSTER_GAP = 12;
    /** The search layout's compact card: 16:9, three to a row beside the keyboard column. */
    public static final int SEARCH_POSTER_W = 178; // three cards and their gaps fill the width right of the keyboard column
    public static final int SEARCH_POSTER_H = 100;
    public static final int SEARCH_POSTER_CAPTION = 40; // a title line and a secondary line
    public static final int FIELD_H = 52;              // the search field's height, in both layouts
    /** The universal picker sheet: 400 dp at the right, the video or the browser behind it. */
    public static final int PICKER_W = SHEET_W;
    public static final int PICKER_ROW_H = 56;
    public static final int PICKER_PAD = 24;

    // ---- Timing (ms), UX.md 3.3 --------------------------------------------------------
    /** Kanalbanner and the asset banner: after the last key, once the picture is up. */
    public static final int OSD_BANNER_MS = 4000;
    /** A banner may wait this long for the first picture before it hides on its own. */
    public static final int OSD_HOLD_MS = 15_000;
    /** The player controls (OK on video) stay this long after the last key. */
    public static final int OSD_CONTROLS_MS = 5000;
    /** Height of an action pill in the player controls. */
    public static final int ACTION_H = 36;
    public static final int TOAST_MS = 3000;
    public static final int TOAST_ERROR_MS = 6000;

    // ---- Motion ------------------------------------------------------------------------
    public static final int ANIM_MS = 120;
    /** Global switch for the few allowed animations. Set false on slow devices. */
    public static boolean animations = true;

    // ---- Typefaces (system Roboto, no font files) ----------------------------------------
    public static final Typeface REGULAR = Typeface.DEFAULT;
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    public static final Typeface MONO = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    // ---- Helpers -----------------------------------------------------------------------
    /** dp to whole pixels. */
    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /** sp to pixels (respects the user's font scale). For Paint.setTextSize. */
    public static float sp(Context c, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, c.getResources().getDisplayMetrics());
    }

    /**
     * The width of the one surface that has a column over the video: the Serie detail, 50 % of the screen
     * (capped at PANEL_DETAIL_MAX dp). It was the panel's width plus its rail; the rail is gone (clean room).
     */
    public static int panelWidth(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.min(Math.round(m.widthPixels * PANEL_DETAIL_FRACTION), dp(c, PANEL_DETAIL_MAX));
    }
}
