package se.lite.play.ui;

import se.lite.play.Clock;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The one player overlay (U11, U0), bottom of the screen, full width, drawn on one View. One anatomy for
 * every playback kind, live, delayed live, catch-up and VOD alike (DESIGN.md "OSD"):
 * <pre>
 *  [logo]  or  AVSNITT · TV4 PLAY · STORAGE WARS     eyebrow: the channel (logo, else its number and name) or an episode's kind and source
 *  Fråga Lund                                        title
 *                       DIREKT
 *  20:00 ████████●───────|─────────────────────── 21:00   the bar: start, track, DIREKT tick on a channel, playhead, end (A1)
 *                20:12                                      the wall-clock time of the picture, under the playhead
 *  [Paus] [Från början 20:00] [Direkt]   ↩ Dölj             the pills, only while the overlay has the focus (A2)
 * </pre>
 * One component with two states. Passive (no focus): the zap banner and every start, no pills. Interactive
 * (focus on the bar while scrubbing, or on a pill): the pills too. State, one render. The kind only decides
 * what the bar's data is ({@link #showChannel} the live banner, {@link #showTimeline} a channel behind live
 * or in catch-up, {@link #showSeek} an episode or film). A fatal error before any video is the text layout
 * ({@link #showText}). Use {@link #flash(int)} to show it and auto-hide.
 */
public class OsdView extends View {
    /** The overlay's two layouts: the frame (every playback kind), and a fatal text before any video. */
    private static final int MODE_BAR = 0, MODE_TEXT = 1;
    /** What the bar's data is; the layout is the same for all of them. */
    private static final int BAR_LIVE = 0, BAR_TIMELINE = 1, BAR_SEEK = 2;
    /** Which row has the focus: none (a banner), the bar (scrubbing) or the action row. */
    public static final int FOCUS_NONE = 0, FOCUS_BAR = 1, FOCUS_ACTIONS = 2;
    /** Behind the live position by less than this counts as live. */
    public static final long LIVE_SLACK_MS = 15_000;
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.forLanguageTag("sv-SE"));

    private final TextPaint display = paint(Theme.TEXT_DISPLAY, Theme.MEDIUM);
    private final TextPaint titleP = paint(Theme.TEXT_TITLE, Theme.MEDIUM);
    private final TextPaint label = paint(Theme.TEXT_LABEL, Theme.REGULAR);
    private final TextPaint caption = paint(Theme.TEXT_CAPTION, Theme.BOLD);
    private final TextPaint hintP = paint(Theme.TEXT_CAPTION, Theme.REGULAR);
    private final TextPaint logoName = paint(Theme.TEXT_TITLE, Theme.BOLD); // the slot stands where the title role is (M14)
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final RectF dst = new RectF();
    private final GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x00000000, 0x4D000000, 0xB3000000, Theme.SCRIM_OSD});
    private final Runnable hide = () -> hideNow();
    private Runnable onHide;

    private final int safeX, safeY, top, lineGap, progH, logoW, logoH, hintGap, hintPairGap,
            actionH, actionPad, actionGap, actionR;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint actionP = paint(Theme.TEXT_LABEL, Theme.MEDIUM);
    /** The action row: labels, the focused one, and which row the focus is on. */
    private String[] actions;
    private int action = -1;
    private int focusRow = FOCUS_NONE;
    /** The bar has the focus and the pills stay drawn (↑ from a pill); a scrub from clean video draws none. */
    private boolean barWithPills;
    private Runnable onFocusChange;

    private int mode = MODE_TEXT;
    private int barKind = BAR_LIVE;
    private String num = "", name = "", chName = "", title = "", text = "";
    /** A1: the numbers of the bar: its start, its end, and the picture's time under the playhead. */
    private String labelStart, labelEnd, labelHead;
    private String eyebrow = "", status, textHead = "";
    private boolean statusError;
    private String[] hints = NO_HINTS;
    private Bitmap logo;
    private float liveFrac = -1f, headFrac; // the live position (-1: no tick) and the playhead, as fractions of the track
    private StaticLayout textLayout;

    private static final String[] NO_HINTS = {};
    /** Hint after the action pills while the overlay has the focus: Back only hides (F12c). */
    private static final String[] HINTS_FOCUSED = {Theme.BACK, "Dölj"};
    /**
     * The hint slot of the unified strip: the pills are always drawn (dim until OK focuses the first one,
     * clean room 2026-10-17: no passive/interactive split, no "OK Visa knappar"). Back hides the strip.
     * While scrubbing the bar has the focus and OK plays from the playhead, so the hint says which.
     */
    private static final String[] HINTS_SCRUB = {"OK", "Spela härifrån", Theme.BACK, "Dölj"};
    private boolean logoLightArt;
    /** The fallback name as fitted last; null while a logo is drawn. */
    private LogoText logoFallback;
    private final float[] logoSizes;
    /** The status line as set last, or null (harness). */
    public String shownStatus() {
        return mode == MODE_BAR && barKind == BAR_LIVE ? status : null;
    }

    /** The hint pairs drawn last: "↩ Dölj" after the pills while the overlay has the focus, the text layout's own otherwise (harness). */
    public String[] shownHints() {
        if (mode == MODE_BAR) return frameHints().clone();
        return hints.clone();
    }

    private String[] frameHints() {
        return focusRow == FOCUS_BAR ? HINTS_SCRUB : HINTS_FOCUSED;
    }

    /** The eyebrow as drawn last (harness: Y6/U0, the channel's name alone or an asset's kind and source). */
    public String shownEyebrow() {
        return eyebrow;
    }

    /** True when the logo slot drew a cut channel name last (X1 check: must never happen). */
    public boolean logoFallbackCut() {
        return mode == MODE_BAR && logo == null && logoFallback != null && logoFallback.cut;
    }

    public OsdView(Context c) {
        super(c);
        logoSizes = new float[] {Theme.sp(c, Theme.TEXT_TITLE), Theme.sp(c, Theme.TEXT_BODY), Theme.sp(c, Theme.TEXT_LABEL)};
        safeX = Theme.dp(c, Theme.OSD_SIDE);
        safeY = Theme.dp(c, Theme.SAFE_Y);
        top = Theme.dp(c, 72); // gradient lead-in above the first line
        lineGap = Theme.dp(c, Theme.SP_M);
        progH = Theme.dp(c, Theme.PROGRESS_OSD);
        logoW = Theme.dp(c, Theme.OSD_LOGO_W);
        logoH = Theme.dp(c, Theme.OSD_LOGO_H);
        hintGap = Theme.dp(c, Theme.SP_S);
        headGap = Theme.dp(c, 2);
        hintPairGap = Theme.dp(c, Theme.SP_XL);
        actionH = Theme.dp(c, Theme.ACTION_H);
        actionPad = Theme.dp(c, Theme.SP_L);
        actionGap = Theme.dp(c, Theme.SP_S);
        actionR = Theme.dp(c, Theme.R_M);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Theme.dp(c, 3));
        for (TextPaint t : new TextPaint[] {display, titleP, label, caption, hintP, logoName, actionP}) t.setFontFeatureSettings("tnum");
        logoName.setTextAlign(Paint.Align.LEFT);
        tintP.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.TEXT, android.graphics.PorterDuff.Mode.SRC_IN));
        measureSlots();
        setVisibility(GONE);
    }

    private TextPaint paint(float sp, android.graphics.Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(Theme.sp(getContext(), sp));
        p.setTypeface(tf);
        p.setColor(Theme.TEXT);
        return p;
    }

    // ---- Content -----------------------------------------------------------------------

    /**
     * The banner of a channel on air (the zap banner, and the frame OK and ← → open on live): the channel
     * as the eyebrow, the programme as the title, and the bar spanning the programme with the playhead on
     * the live tick, the programme's times at its ends and the time now under the playhead (A1). Pass null
     * titles when the guide is not loaded yet. Clears the status line.
     */
    public OsdView showChannel(int channelNumber, String channelName, String nowTitle, long nowStartMs, long nowEndMs) {
        long t = Clock.now();
        mode = MODE_BAR;
        barKind = BAR_LIVE;
        channel(channelNumber, channelName);
        title = nowTitle == null ? "" : nowTitle;
        if (nowTitle != null && nowEndMs > nowStartMs) {
            liveStartMs = nowStartMs;
            liveEndMs = nowEndMs;
            liveFrac = Math.max(0f, Progress.fraction(nowStartMs, nowEndMs, t));
            headFrac = liveFrac;
            labelStart = hhmm.format(new Date(nowStartMs));
            labelEnd = hhmm.format(new Date(nowEndMs));
            labelHead = hhmm.format(new Date(t));
            headMinute = t / 60_000;
        } else {
            liveStartMs = liveEndMs = 0;
            liveFrac = -1f;
            headFrac = 0f;
            labelStart = labelEnd = labelHead = null;
        }
        status = null;
        return changed();
    }

    /** The channel's identity: its number and name in the logo slot until a logo arrives, its name as the eyebrow (U0). */
    private void channel(int channelNumber, String channelName) {
        num = channelNumber > 0 ? String.valueOf(channelNumber) : "";
        chName = channelName == null ? "" : channelName;
        eyebrow = chName.toUpperCase(Locale.forLanguageTag("sv-SE"));
    }

    /**
     * Channel logo for the eyebrow slot; null draws the channel's number and name there (same slot,
     * nothing shifts). The OSD is a dark surface: pass the dark-background artwork, or the
     * light-background one with {@code lightArt} true when that is all there is (drawn as a silhouette in
     * TEXT). Call after the channel's frame is shown; an asset's frame clears it.
     */
    public OsdView setLogo(Bitmap bmp, boolean lightArt) {
        logo = bmp;
        logoLightArt = bmp != null && lightArt;
        invalidate();
        return this;
    }

    public OsdView setLogo(Bitmap bmp) {
        return setLogo(bmp, false);
    }

    /**
     * Status line in the bar's slot: an explanation ("Går inte att spola tillbaka", secondary colour) or
     * a failure ("Kanalen gick inte att starta", error colour). Null restores the bar.
     */
    public OsdView status(String message, boolean error) {
        status = message;
        statusError = error;
        return changed();
    }

    /** Key hints for the footer of the text layout, as key/label pairs: {@code hints("OK", "Försök igen", Theme.BACK, "Avsluta")}. */
    public OsdView hints(String... pairs) {
        hints = pairs == null ? NO_HINTS : pairs;
        invalidate();
        return this;
    }

    /**
     * The action row (U11): the pills, in the one canonical order, {@code focused} drawn with the focus
     * look while the row has the focus. They stay through show* calls (the bar keeps following the
     * player) until {@link #clearActions()} or the overlay hides.
     */
    public OsdView actions(String[] labels, int focused) {
        actions = labels == null || labels.length == 0 ? null : labels;
        action = actions == null ? -1 : Math.max(0, Math.min(actions.length - 1, focused));
        invalidate();
        return this;
    }

    public OsdView clearActions() {
        actions = null;
        action = -1;
        setFocusRow(FOCUS_NONE);
        return this;
    }

    /** The pills as set last, or an empty array (harness: U11 order). */
    public String[] shownActions() {
        return actions == null ? new String[0] : actions.clone();
    }

    /** Moves the focus to a row: {@link #FOCUS_NONE} (the passive overlay), {@link #FOCUS_BAR} or {@link #FOCUS_ACTIONS}. */
    public OsdView focusRow(int row) {
        setFocusRow(row);
        return this;
    }

    /** ↑ from a pill: the bar has the focus (the playhead's ring) and the pills stay on screen, unfocused. */
    public OsdView focusBarKeepingPills() {
        setFocusRow(FOCUS_BAR);
        barWithPills = actions != null;
        invalidate();
        if (onFocusChange != null) onFocusChange.run(); // the callback ran before the flag was set: the peek follows the pills

        return this;
    }

    /** True while the bar has the focus with the pills still drawn (it came from them; ↓ goes back). */
    public boolean pillsOnBar() {
        return focusRow == FOCUS_BAR && barWithPills && mode == MODE_BAR;
    }

    private void setFocusRow(int row) {
        boolean was = focusRow != row;
        focusRow = row;
        barWithPills = false;
        invalidate();
        if (was && onFocusChange != null) onFocusChange.run();
    }

    /** Called when the focus row changes or the overlay hides (A4: the activity shows the rail's peek on it). */
    public void setOnFocusChange(Runnable r) {
        onFocusChange = r;
    }

    /** The row with the focus, or {@link #FOCUS_NONE} while hidden. */
    public int focusRow() {
        return getVisibility() == VISIBLE ? focusRow : FOCUS_NONE;
    }

    /** Index of the focused pill, or -1 when the action row has no focus. For the harness (C2). */
    public int focusedAction() {
        return getVisibility() == VISIBLE && actions != null && focusRow == FOCUS_ACTIONS ? action : -1;
    }

    /** True while the bar has the focus (scrubbing): the playhead draws the focus ring. For the harness (C2). */
    public boolean barFocused() {
        return getVisibility() == VISIBLE && mode == MODE_BAR && focusRow == FOCUS_BAR;
    }

    /** Called when the OSD hides, by its timer or {@link #hideNow()}. */
    public void setOnHide(Runnable r) {
        onHide = r;
    }

    /**
     * An episode's or film's frame with its position. The eyebrow names the kind and the source
     * ("AVSNITT · TV4 PLAY · STORAGE WARS"); the bar reads 0:00 at its start, the total at its end and the
     * elapsed time under the playhead (A1). Without a known span the bar is empty.
     */
    public OsdView showSeek(String eyebrow, String assetTitle, long posMs, long durMs) {
        mode = MODE_BAR;
        barKind = BAR_SEEK;
        liveStartMs = liveEndMs = 0;
        assetFrame(eyebrow, assetTitle);
        liveFrac = -1f;
        headFrac = durMs > 0 ? Math.max(0f, Math.min(1f, posMs / (float) durMs)) : 0f;
        if (durMs > 0) {
            labelStart = fmt(0);
            labelEnd = fmt(durMs);
            labelHead = fmt(posMs);
        } else {
            labelStart = labelEnd = labelHead = null;
        }
        return changed();
    }

    /**
     * A channel behind live, on the live window or the start-over stream alike, or a catch-up of one of
     * its programmes: the track spans the programme and shows the live position (tick, DIREKT, only when
     * live is inside the programme) and the playhead; the programme's times sit at the ends and the
     * picture's time under the playhead (A1). All times are epoch ms.
     *
     * @param eyebrow the channel's name in capitals, e.g. "SVT1" (the same on the live window and the
     *                start-over stream; no mode word, U0)
     */
    public OsdView showTimeline(String eyebrow, String programmeTitle, long startMs, long endMs, long liveMs,
                                long headMs) {
        return showTimeline(0, eyebrow, programmeTitle, startMs, endMs, liveMs, headMs);
    }

    /** As {@link #showTimeline(String, String, long, long, long, long)} with the channel's number, for its logo slot's fallback. */
    public OsdView showTimeline(int channelNumber, String channelName, String programmeTitle, long startMs, long endMs,
                                long liveMs, long headMs) {
        mode = MODE_BAR;
        barKind = BAR_TIMELINE;
        liveStartMs = liveEndMs = 0;
        channel(channelNumber, channelName);
        title = programmeTitle == null ? "" : programmeTitle;
        status = null;
        long span = Math.max(1, endMs - startMs);
        headMs = Math.min(headMs, liveMs);
        liveFrac = liveMs <= endMs ? Math.max(0f, Math.min(1f, (liveMs - startMs) / (float) span)) : -1f;
        headFrac = Math.max(0f, Math.min(1f, (headMs - startMs) / (float) span));
        labelStart = hhmm.format(new Date(startMs));
        labelEnd = hhmm.format(new Date(endMs));
        labelHead = hhmm.format(new Date(headMs));
        return changed();
    }

    /** Kinds an episode or film eyebrow starts with (UX.md 1, E7); the source follows after " · ". A channel shows its name alone (U0). */
    public static final String[] MODE_WORDS = {"AVSNITT", "FILM", "SPORT"};

    /** True when an asset eyebrow names what plays and its source: "AVSNITT · TV4 PLAY · …" (Y6, E7). */
    public static boolean namesModeAndSource(String eyebrow) {
        if (eyebrow == null) return false;
        for (String m : MODE_WORDS) if (eyebrow.startsWith(m + " · ") && eyebrow.length() > m.length() + 3) return true;
        return false;
    }

    /** True when the eyebrow is as it must be: a channel's name alone, never a mode word; an asset's kind and source (U0, Y6). */
    public boolean eyebrowFitsMode() {
        if (mode != MODE_BAR) return true;
        if (eyebrow.contains("FÖRDRÖJT") || eyebrow.contains("BÖRJA OM")) return false;
        if (barKind == BAR_LIVE || barKind == BAR_TIMELINE) return !eyebrow.isEmpty() || logo != null || !num.isEmpty();
        return namesModeAndSource(eyebrow);
    }

    private void assetFrame(String eyebrow, String assetTitle) {
        this.eyebrow = eyebrow == null ? "" : eyebrow;
        title = assetTitle == null ? "" : assetTitle;
        chName = num = "";
        logo = null;
        logoFallback = null;
        status = null;
    }

    /** "channel" (live banner), "timeline" (a channel behind live or in catch-up), "seek" (an episode or film) or "text": what the frame holds, for the harness (R2). */
    public String modeName() {
        if (mode == MODE_TEXT) return "text";
        switch (barKind) {
            case BAR_LIVE: return "channel";
            case BAR_TIMELINE: return "timeline";
            default: return "seek";
        }
    }

    /** The bar's numbers as drawn: start, end and the time under the playhead (harness: A1); null when the bar has none. */
    public String[] shownBarLabels() {
        return new String[] {labelStart, labelEnd, labelHead};
    }

    /**
     * True while the rail's peek shows (A4): the pills have the focus (an empty row too: OK there opens the rail),
     * or the bar has it with the pills still up. The light overlay (passive, a scrub from clean video) has none.
     * Clean room 2026-10-17: the rail is gone, so this is always false; kept for the harness.
     */
    public boolean peekWanted() {
        return false;
    }

    /**
     * True while the pills are drawn: always, on the unified strip (clean room 2026-10-17). They are dim while
     * the strip has no focus, and take the focus look on the focused one once OK has entered the row.
     */
    public boolean pillsDrawn() {
        return mode == MODE_BAR && actions != null;
    }

    /** The title drawn: the programme on air or the asset's title; null for a channel whose guide is not loaded. */
    public String shownTitle() {
        return mode == MODE_TEXT ? textHead : barKind == BAR_LIVE && title.isEmpty() ? null : title;
    }

    /** True while the bar follows the player's position (a channel behind live, catch-up, VOD). */
    public boolean isTimeline() {
        return mode == MODE_BAR && barKind != BAR_LIVE;
    }

    /** True for any overlay frame, whatever the kind (harness: Y6/U0, one frame). */
    public boolean isFrame() {
        return mode == MODE_BAR;
    }

    /**
     * Text, may contain line breaks: line 1 is the heading (title size), the rest is body text (three
     * lines reserved). A fatal error before any video.
     */
    public OsdView showText(String message) {
        String m = message == null ? "" : message;
        int nl = m.indexOf('\n');
        textHead = nl < 0 ? m : m.substring(0, nl);
        text = nl < 0 ? "" : m.substring(nl + 1);
        textLayout = null;
        mode = MODE_TEXT;
        hints = NO_HINTS;
        return changed();
    }

    /** Shows the OSD; hides it after {@code hideAfterMs}, or never when 0. */
    public void flash(int hideAfterMs) {
        removeCallbacks(hide);
        if (getVisibility() != VISIBLE) {
            visibilityChanges++;
            setVisibility(VISIBLE);
            if (onFocusChange != null) onFocusChange.run();
            if (Theme.animations) {
                setAlpha(0f);
                animate().alpha(1f).setDuration(Theme.ANIM_MS).start();
            }
        }
        if (hideAfterMs > 0) postDelayed(hide, hideAfterMs);
    }

    public void hideNow() {
        removeCallbacks(hide);
        boolean was = getVisibility() == VISIBLE;
        if (was) visibilityChanges++;
        setVisibility(GONE);
        actions = null;
        action = -1;
        setFocusRow(FOCUS_NONE);
        if (was && onHide != null) onHide.run();
    }

    private int visibilityChanges;

    /** How many times the overlay appeared or disappeared (harness: a pause toggle must not blink it). */
    public int visibilityChanges() {
        return visibilityChanges;
    }

    /** The slot of the programme on air on the live banner (epoch ms), so it is drawn at the time of the draw; 0 otherwise. */
    private long liveStartMs, liveEndMs, headMinute;
    private final Runnable redraw = this::invalidate;

    private OsdView changed() {
        requestLayout();
        invalidate();
        return this;
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ---- Layout and drawing --------------------------------------------------------------
    //
    // Stable layout (REQUIREMENTS E6): no element moves or resizes when a text changes or the kind of
    // playback differs. Every text sits in a slot measured once for its widest value, is ellipsized
    // inside it, and the one frame has a fixed height with every line reserved. Numbers use tabular figures.

    private static final String WIDEST_END = "00:00:00";
    private static final int TEXT_LINES = 3;

    private float slotEnd, slotGap;
    private final int headGap;
    /** Element rects of the last draw, for layout-stability checks (see {@link #debugRects()}). */
    private final Map<String, RectF> drawn = new LinkedHashMap<>();

    private void measureSlots() {
        slotEnd = label.measureText(WIDEST_END);
        slotGap = Theme.dp(getContext(), Theme.SP_L);
    }

    /**
     * The rects of the elements drawn last (eyebrow, title, range, bar, playhead, live, label, the pills,
     * ...), in view coordinates. For tests that check that no element moves when its text changes or the
     * kind of playback does.
     */
    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    /** Y of the first line of the overlay's content, in view coordinates: above it there is only the gradient lead-in. */
    public int contentTop() {
        return top;
    }

    /** Same as {@link #debugRects()}; the name the screenshot harness uses. */
    public Map<String, RectF> drawnRects() {
        return debugRects();
    }

    private void record(String key, float l, float t, float r, float b) {
        RectF f = drawn.get(key);
        if (f == null) drawn.put(key, new RectF(l, t, r, b));
        else f.set(l, t, r, b);
    }

    private static float lh(Paint p) {
        Paint.FontMetrics m = p.getFontMetrics();
        return m.descent - m.ascent;
    }

    /** The eyebrow line holds the channel logo slot, so it is the logo's height for every kind (E6). */
    private float line1H() {
        return Math.max(lh(label), logoH);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float h = top + safeY;
        if (mode == MODE_TEXT) { // heading, body, the same footer slot as the overlay
            h += lh(titleP) + lineGap / 2f + TEXT_LINES * lh(label) + lineGap + actionH;
        } else { // the overlay: eyebrow line, title, bar (the DIREKT caption above it, the picture's time under it), action row
            h += line1H() + lineGap / 2f + lh(display) + lineGap + lh(caption) + lh(label) + headGap + lh(label) + lineGap + actionH;
        }
        setMeasuredDimension(w, Math.round(h));
    }

    private StaticLayout textLayout(int w) {
        int tw = Math.max(1, Math.min(w - 2 * safeX, Math.round(w * 0.6f)));
        if (textLayout == null || textLayout.getWidth() != tw) {
            textLayout = StaticLayout.Builder.obtain(text, 0, text.length(), label, tw)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(TEXT_LINES)
                    .setEllipsize(TextUtils.TruncateAt.END).build();
        }
        return textLayout;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        scrim.setBounds(0, 0, w, h);
        scrim.draw(c);
        float x = safeX, right = w - safeX, y = top;
        drawn.clear();

        if (mode == MODE_TEXT) {
            float tr = Math.min(right, x + textLayout(w).getWidth());
            float b = y - titleP.getFontMetrics().ascent;
            titleP.setColor(Theme.TEXT);
            drawIn(c, textHead, titleP, x, tr, b, false, "text");
            y += lh(titleP) + lineGap / 2f;
            c.save();
            c.translate(x, y);
            label.setColor(Theme.TEXT_2);
            textLayout(w).draw(c);
            c.restore();
            record("body", x, y, tr, y + TEXT_LINES * lh(label));
            y += TEXT_LINES * lh(label) + lineGap;
            drawFooter(c, x, right, y);
            return;
        }

        // Line 1: [logo slot | eyebrow text]
        float l1 = line1H();
        float cy = y + l1 / 2f;
        Paint.FontMetrics lm = label.getFontMetrics();
        rect.set(x, cy - logoH / 2f, x + logoW, cy + logoH / 2f);
        record("logo", rect.left, rect.top, rect.right, rect.bottom); // invisible slot: the logo has alpha
        boolean channel = barKind == BAR_LIVE || barKind == BAR_TIMELINE;
        float eyeR = right;
        record("eyebrow", x, y, eyeR, y + l1); // one rect for every kind: the slot, whatever fills it
        if (channel) {
            if (logo != null && logo.getWidth() > 0 && logo.getHeight() > 0) {
                LogoFit.fit(logo, rect, LogoFit.LEFT, dst); // left-aligned: the title starts at a fixed x
                c.drawBitmap(logo, null, dst, logoLightArt ? tintP : bmpP);
                logoFallback = null;
            } else { // no logo: the number and the name in the slot (F13 fallback), never cut (X1)
                logoName.setColor(Theme.TEXT_2);
                String fb = num.isEmpty() ? chName : num + " " + chName;
                float pad = Theme.dp(getContext(), 4);
                logoFallback = LogoText.fit(logoName, fb, rect.width() - 2 * pad, rect.height() - 2 * pad, logoSizes);
                logoFallback.draw(c, logoName, rect.left, rect.centerY(), Paint.Align.LEFT);
            }
        } else { // an asset: its kind and source
            caption.setColor(Theme.ACCENT_ON_DARK);
            drawIn(c, eyebrow, caption, x, eyeR, cy - (caption.getFontMetrics().ascent + caption.getFontMetrics().descent) / 2f,
                    false, "eyebrowText");
        }
        y += l1 + lineGap / 2f;

        // Line 2: the title, across the whole width
        Paint.FontMetrics dm = display.getFontMetrics();
        float b = y - dm.ascent;
        display.setColor(Theme.TEXT);
        drawIn(c, title, display, x, right, b, false, "title");
        y += lh(display) + lineGap + lh(caption);

        // Line 3: start, the bar (the DIREKT caption sits in the reserved line above it), end; or the status line.
        // Under it the picture's time, in a reserved line of its own.
        float lb = y - lm.ascent;
        if (status != null) {
            label.setColor(statusError ? Theme.ERROR : Theme.TEXT_2);
            drawIn(c, status, label, x, right, lb, false, "status");
        } else {
            boolean blank = barKind == BAR_LIVE && labelStart == null; // no programme on air: the slot stays, nothing is drawn in it
            if (blank) {
                c.save();
                c.clipRect(0, 0, 0, 0);
            }
            drawBar(c, x, right, lb, lb + lh(label) + headGap, lm);
            if (blank) c.restore();
        }
        y += lh(label) + headGap + lh(label) + lineGap;

        // Line 4: the pills, while the overlay has the focus.
        drawFooter(c, safeX, right, y);
    }

    /**
     * The bar (A1): the start at its left end, the track, the watched part, the DIREKT tick on a channel,
     * the playhead (ringed while focused), the end at its right end; the picture's time under the
     * playhead. One size for all the numbers, on the bar's line or the one below.
     */
    private void drawBar(Canvas c, float x, float right, float lb, float hb, Paint.FontMetrics lm) {
        removeCallbacks(redraw);
        if (barKind == BAR_LIVE && liveEndMs > liveStartMs) { // live: the playhead is the clock, drawn where it is now (U16)
            long t = Clock.now();
            liveFrac = headFrac = Math.max(0f, Progress.fraction(liveStartMs, liveEndMs, t));
            if (t / 60_000 != headMinute) {
                headMinute = t / 60_000;
                labelHead = hhmm.format(new Date(t));
            }
            float trackW = right - x - 2 * (slotEnd + slotGap) - 8f * progH / 2f;
            // Drawn again when the playhead moves a pixel, and at the programme's end (the activity hands over then).
            if (getVisibility() == VISIBLE) postDelayed(redraw, Progress.redrawDelay(liveStartMs, liveEndMs, Math.max(1f, trackW), t));
        }
        label.setColor(Theme.TEXT_2);
        drawIn(c, labelStart, label, x, x + slotEnd, lb, false, "labelStart");
        drawIn(c, labelEnd, label, right - slotEnd, right, lb, true, "labelEnd");
        float cy = lb + (lm.ascent + lm.descent) / 2f, r = progH / 2f;
        float pr = 4f * r; // playhead radius: 32 px across at 1080p, visible from the sofa
        float trackL = x + slotEnd + slotGap;
        x = trackL + pr; // the track is inset by the playhead, so the playhead stays inside the safe area
        float barRight = right - slotEnd - slotGap - pr, w = barRight - x;
        rect.set(x, cy - r, barRight, cy + r);
        fill.setColor(Theme.TRACK);
        c.drawRoundRect(rect, r, r, fill);
        record("bar", x, cy - r, barRight, cy + r);
        float headX = x + w * headFrac;
        if (liveFrac >= 0f) { // a channel: broadcast so far in TEXT_3, the live tick with its caption
            float liveX = x + w * liveFrac;
            rect.set(x, cy - r, liveX, cy + r);
            fill.setColor(Theme.TEXT_3); // broadcast but not watched
            c.drawRoundRect(rect, r, r, fill);
            float tickW = Theme.dp(getContext(), 4);
            rect.set(liveX - tickW / 2f, cy - pr, liveX + tickW / 2f, cy + pr);
            fill.setColor(Theme.ACCENT);
            c.drawRoundRect(rect, tickW / 2f, tickW / 2f, fill);
            record("live", rect.left, rect.top, rect.right, rect.bottom);
            caption.setColor(Theme.ACCENT_ON_DARK);
            float cw = caption.measureText("DIREKT");
            float cx = Math.max(x, Math.min(liveX - cw / 2f, barRight - cw)); // clamped inside the bar
            float cb = cy - pr - stroke.getStrokeWidth() - caption.getFontMetrics().descent - Theme.dp(getContext(), 2); // clear of the focus ring
            c.drawText("DIREKT", cx, cb, caption);
            record("liveLabel", cx, cb + caption.getFontMetrics().ascent, cx + cw, cb + caption.getFontMetrics().descent);
        }
        rect.set(x, cy - r, headX, cy + r);
        fill.setColor(Theme.ACCENT_ON_DARK); // watched
        c.drawRoundRect(rect, r, r, fill);
        fill.setColor(Theme.TEXT);
        c.drawCircle(headX, cy, pr, fill);
        record("playhead", headX - pr, cy - pr, headX + pr, cy + pr);
        // The picture's time: a fixed slot (the widest number) centred under the playhead, clamped to the bar's span.
        float hl = Math.max(trackL, Math.min(headX - slotEnd / 2f, right - slotEnd - slotGap - slotEnd));
        record("labelHead", hl, hb + label.getFontMetrics().ascent, hl + slotEnd, hb + label.getFontMetrics().descent);
        if (labelHead != null) {
            label.setColor(Theme.TEXT);
            float hw = label.measureText(labelHead);
            c.drawText(labelHead, hl + (slotEnd - hw) / 2f, hb, label);
        }
        if (focusRow == FOCUS_BAR) { // the one focus look while scrubbing: a ring round the playhead
            float sw = stroke.getStrokeWidth();
            stroke.setColor(Theme.ACCENT);
            c.drawCircle(headX, cy, pr + sw, stroke);
            record("focus", headX - pr - sw, cy - pr - sw, headX + pr + sw, cy + pr + sw);
        }
    }

    /** Draws `s` ellipsized inside [l, r] on baseline `b`; right-aligned when `alignRight`. */
    private void drawIn(Canvas c, String s, TextPaint p, float l, float r, float b, boolean alignRight, String key) {
        record(key, l, b + p.getFontMetrics().ascent, r, b + p.getFontMetrics().descent);
        if (s == null || s.isEmpty() || r <= l) return;
        CharSequence t = TextUtils.ellipsize(s, p, r - l, TextUtils.TruncateAt.END);
        float tx = alignRight ? r - p.measureText(t, 0, t.length()) : l;
        c.drawText(t, 0, t.length(), tx, b, p);
    }

    /**
     * The footer slot (fixed height {@code actionH}): the pills, each at its own rect, then "↩ Dölj". The
     * unified strip draws the row in every state (clean room 2026-10-17): dim while no row has the focus, the
     * focused pill in the focus look while the action row does.
     */
    private void drawFooter(Canvas c, float x, float right, float y) {
        record("hints", x, y, right, y + actionH);
        float hx = pillsDrawn() ? x - actionPad : x; // the first pill's text lines up with the logo, title and bar above
        String[] hints = this.hints;
        if (mode == MODE_BAR) hints = frameHints();
        if (pillsDrawn()) {
            Paint.FontMetrics am = actionP.getFontMetrics();
            float ab = y + actionH / 2f - (am.ascent + am.descent) / 2f;
            boolean rowFocused = focusRow == FOCUS_ACTIONS;
            for (int i = 0; i < actions.length; i++) {
                float w = actionP.measureText(actions[i]) + 2 * actionPad;
                if (hx + w > right) break;
                rect.set(hx, y, hx + w, y + actionH);
                boolean f = rowFocused && i == action;
                if (f) {
                    fill.setColor(Theme.FOCUS);
                    c.drawRoundRect(rect, actionR, actionR, fill);
                }
                record("action" + i, rect.left, rect.top, rect.right, rect.bottom);
                actionP.setColor(f ? Theme.ON_FOCUS : rowFocused ? Theme.TEXT : Theme.TEXT_2);
                c.drawText(actions[i], hx + actionPad, ab, actionP);
                hx += w + actionGap;
            }
            hx += hintPairGap - actionGap;
        }
        float b = y + actionH / 2f - (caption.getFontMetrics().ascent + caption.getFontMetrics().descent) / 2f;
        float hintLeft = hx;
        for (int i = 0; i + 1 < hints.length && hx < right; i += 2) {
            String key = hints[i], lbl = hints[i + 1];
            if (!key.isEmpty()) {
                caption.setColor(Theme.TEXT_2);
                c.drawText(key, hx, b, caption);
                hx += caption.measureText(key) + hintGap;
            }
            hintP.setColor(Theme.TEXT_2); // one step up from TEXT_3: readable at 3 m (B22)
            CharSequence t = TextUtils.ellipsize(lbl, hintP, Math.max(0, right - hx), TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), hx, b, hintP);
            hx += hintP.measureText(t, 0, t.length()) + hintPairGap;
        }
        if (hints.length >= 2) record("hintText", hintLeft, b + caption.getFontMetrics().ascent, Math.min(right, hx - hintPairGap), b + caption.getFontMetrics().descent);
    }
}
