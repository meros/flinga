package se.lite.play;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import se.lite.play.ui.LogoFit;
import se.lite.play.ui.LogoText;
import se.lite.play.ui.Theme;
import se.lite.play.ui.Ui;

/**
 * The guide grid (B4): every channel as a row, time as columns, drawn on one View so that moving costs one
 * redraw and nothing is allocated per frame beyond the few strings that are cut to a block's width.
 *
 * <pre>
 *  TORSDAG 15 OKT   20:00      20:30      21:00      21:30      22:00      22:30     the ruler: a tick every 30 min
 *  [svt1]  | Rapport | Fråga Lund            | Vår älskade låtskatt  | Lokala |    one row per channel: logo, blocks
 *  [svt2]  | Dokumentär: Havet     ▏now     | Spårlöst försvunnen              |    the now-line runs over every row
 *  [tv3]   | Tablå saknas                                                          a row without data says so
 *  OK Titta direkt   Håll OK Mer   ↩ Stäng                                         the footer: what OK does here
 * </pre>
 *
 * Layout is fixed (E6): a block's width comes from its duration on one time scale (3 h across the grid), never
 * from its title, which is cut inside it with an ellipsis. Rows are whole (E5): the first visible row moves
 * in steps of one row. The activity owns what the keys do ({@link Listener}) and where the data comes from
 * ({@link Source}); the view owns the focus, the time window and the drawing.
 */
final class GuideGridView extends View {
    /** The data the grid draws. */
    interface Source {
        int rows();

        /** The number and name of the channel, for the logo slot when it has no logo ("12 SVT1"). */
        String rowLabel(int row);

        /** The logo to draw in the row's slot, or null (the label is drawn instead). */
        Bitmap logo(int row);

        /** True when {@link #logo} is the light-background artwork (drawn as a silhouette on the dark grid). */
        boolean logoIsLight(int row);

        /** The channel's programmes, oldest first: null while loading, empty when there is none or it failed. */
        List<Api.Program> guide(int row);

        /** True when the row has no programmes because the fetch failed (the row offers OK Försök igen, U4). */
        default boolean failed(int row) {
            return false;
        }
    }

    /** The text of a row without programmes (UX.md 6): waiting, failed (OK retries), or the channel has none. */
    static final String LOADING = "Laddar …", FAILED = "Kunde inte hämta listan · OK Försök igen", NONE = "Tablå saknas";

    /** What the keys lead to. */
    interface Listener {
        /** The rows whose guides are wanted now (the visible ones, the activity adds its own margin). */
        void onVisibleRows(int first, int last, int focusRow);

        void onOk(int row, Api.Program p);

        void onHold(int row, Api.Program p);

        /** ← at the first column: the grid hands over to the video or the rail (U10). */
        void onLeftEdge();

    }

    static final long WINDOW_MS = 3 * 3600_000L;
    private static final long STEP_MS = 30 * 60_000L;
    /**
     * How far back and ahead the grid scrolls in time: the whole guide history the fetch returns (24 h back), and ahead
     * as far as any loaded guide goes (at least 6 h).
     */
    static final long BACK_MS = 24 * 3600_000L, AHEAD_MS = 6 * 3600_000L;

    private final TextPaint titleP = paint(Theme.TEXT_LABEL, Theme.MEDIUM);
    private final TextPaint tickP = paint(Theme.TEXT_CAPTION, Theme.REGULAR);
    private final TextPaint dayP = paint(Theme.TEXT_CAPTION, Theme.BOLD);
    private final TextPaint keyP = paint(Theme.TEXT_CAPTION, Theme.BOLD);
    private final TextPaint hintP = paint(Theme.TEXT_CAPTION, Theme.REGULAR);
    private final TextPaint logoName = paint(Theme.TEXT_LABEL, Theme.BOLD);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final RectF dst = new RectF();
    private final Map<String, RectF> drawn = new LinkedHashMap<>();
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.forLanguageTag("sv-SE"));
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("d MMM", Locale.forLanguageTag("sv-SE"));
    private final float[] logoSizes;
    private final int safeX, safeY, safeB, minRowH, logoW, gap, rulerH, footerH, pad, radius, laneH, cueW;

    /** The row height drawn: the room between the ruler and the footer shared out over whole rows (never under {@link #minRowH}). */
    private int rowH;
    private Source source;
    private Listener listener;
    private int row, firstRow;
    private boolean centreRow, notifyPending;
    /** The focused block; null on a row with nothing to focus (loading, no guide). */
    private Api.Program focus;
    private long viewStart;
    /**
     * The time the focus stands on. ↑ ↓ choose in the new row the programme at this time and never move the window;
     * ← → set it to the start of the programme they land on (clamped into the window).
     */
    private long cursor;

    GuideGridView(Context c) {
        super(c);
        setFocusable(true);
        setContentDescription("Tablå");
        safeX = Theme.dp(c, Theme.SAFE_X);
        safeY = Theme.dp(c, Theme.TAB_H); // below the browser's tab row (its block holds the top margin)
        safeB = Theme.dp(c, Theme.SAFE_Y); // the footer sits on the bottom safe edge, as on every other screen
        minRowH = Theme.dp(c, 56);
        rowH = minRowH;
        logoW = Theme.dp(c, 96);
        gap = Theme.dp(c, 4);
        rulerH = Theme.dp(c, 32);
        footerH = Theme.dp(c, 44);
        pad = Theme.dp(c, 10);
        radius = Theme.dp(c, Theme.R_S);
        laneH = Theme.dp(c, Theme.PROGRESS_ROW);
        cueW = Theme.dp(c, 14);
        logoSizes = new float[] {Theme.sp(c, Theme.TEXT_LABEL), Theme.sp(c, Theme.TEXT_CAPTION)};
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        tintP.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.TEXT, android.graphics.PorterDuff.Mode.SRC_IN));
        for (TextPaint t : new TextPaint[] {titleP, tickP, dayP, keyP, hintP, logoName}) t.setFontFeatureSettings("tnum");
        dayP.setLetterSpacing(0.06f);
        setVisibility(GONE);
    }

    private TextPaint paint(float sp, android.graphics.Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(Theme.sp(getContext(), sp));
        p.setTypeface(tf);
        p.setColor(Theme.TEXT);
        return p;
    }

    void bind(Source s, Listener l) {
        source = s;
        listener = l;
    }

    // ---- State ---------------------------------------------------------------------------------

    /** Opens the grid on {@code channelRow}, focused on the programme on air there (when its guide is known), that row centred. */
    void open(int channelRow, long now) {
        row = Math.max(0, Math.min(source.rows() - 1, channelRow));
        viewStart = homeStart(now);
        cursor = now;
        focus = null;
        pickFocus(now);
        centreRow = true;
        notifyPending = true;
        invalidate();
    }

    /** A guide arrived or failed: a row waiting for its data gets its focus now, at the time the cursor stands on. */
    void dataChanged() {
        if (focus == null) pickFocus(cursor);
        invalidate();
    }

    /** The window that puts now about a fifth in from the left (never before the start of the range). */
    private static long homeStart(long now) {
        long t = now - WINDOW_MS / 5;
        return Math.max(now - BACK_MS, t - Math.floorMod(t, 5 * 60_000L));
    }

    /** True when the window does not include now (held OK offers "Gå till nu" then). */
    boolean windowOffNow() {
        long now = Clock.now();
        return now < viewStart || now >= viewStart + WINDOW_MS;
    }

    /** The way back to now (OK on the Tablå tab, held OK's "Gå till nu"): the window puts now at a fifth, the focus goes to {@code channelRow}'s programme on air, that row centred. */
    void goNow(int channelRow, long now) {
        open(channelRow, now);
        notifyPending = true;
        invalidate();
    }

    int focusRow() {
        return row;
    }

    Api.Program focusProgramme() {
        return focus;
    }

    long viewStartMs() {
        return viewStart;
    }

    int firstVisibleRow() {
        return firstRow;
    }

    private static long floor(long t) {
        return t - Math.floorMod(t, STEP_MS);
    }

    /** The programme on {@code anchor}, else the nearest one in the row, else null. */
    private Api.Program at(int r, long anchor) {
        List<Api.Program> g = source.guide(r);
        if (g == null || g.isEmpty()) return null;
        Api.Program best = null;
        long bestD = Long.MAX_VALUE;
        for (Api.Program p : g) {
            if (p.startMs <= anchor && anchor < p.endMs) return p;
            long d = anchor < p.startMs ? p.startMs - anchor : anchor - p.endMs;
            if (d < bestD || d == bestD && best != null && p.startMs > best.startMs) { // a tie goes to the later one
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void pickFocus(long anchor) {
        focus = at(row, anchor);
    }

    /** Moves the time window as little as needed to show {@code p} whole (a longer one: its start). */
    private void scrollTo(Api.Program p) {
        if (p == null) return;
        long minStart = floor(Clock.now() - BACK_MS), maxStart = rangeEnd() - WINDOW_MS + STEP_MS;
        if (p.startMs < viewStart) viewStart = floor(p.startMs);
        else if (p.endMs > viewStart + WINDOW_MS) viewStart = p.endMs - p.startMs >= WINDOW_MS ? floor(p.startMs) : floor(p.endMs - WINDOW_MS + STEP_MS - 1);
        viewStart = Math.max(minStart, Math.min(maxStart, viewStart));
    }

    /** The end of the time range: the last programme end of any loaded guide, at least {@link #AHEAD_MS} from now. */
    private long rangeEnd() {
        long end = floor(Clock.now() + AHEAD_MS);
        for (int r = 0; r < source.rows(); r++) {
            List<Api.Program> g = source.guide(r);
            if (g != null && !g.isEmpty()) end = Math.max(end, g.get(g.size() - 1).endMs);
        }
        return end;
    }

    private int visibleRows() {
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        int room = h - safeY - rulerH - footerH - safeB;
        int n = Math.max(1, room / minRowH);
        rowH = Math.max(minRowH, room / n); // the rows fill the room: no empty band above the footer
        return n;
    }

    private void keepRowVisible() {
        int vis = visibleRows(), n = source.rows();
        if (centreRow) {
            firstRow = row - vis / 2;
            centreRow = false;
        }
        if (row < firstRow) firstRow = row;
        if (row >= firstRow + vis) firstRow = row - vis + 1;
        firstRow = Math.max(0, Math.min(Math.max(0, n - vis), firstRow));
    }

    // ---- Keys ----------------------------------------------------------------------------------

    /**
     * ↑ ↓: the neighbouring channel, on the programme at the cursor's time. The window never moves: a block that is cut
     * by its edge is drawn on its visible part. Returns false when ↑ is pressed on the top row (the activity moves to the tab row).
     */
    boolean moveRow(int d) {
        if (d < 0 && row == 0) return false;
        int to = Math.max(0, Math.min(source.rows() - 1, row + d));
        if (to == row) return true;
        row = to;
        focus = at(row, cursor);
        changed();
        return true;
    }

    /** ← →: the previous or next programme of the row; ← on the first one hands over to the video or the rail. */
    void moveProgramme(int d) {
        List<Api.Program> g = source.guide(row);
        if (g == null || g.isEmpty() || focus == null) {
            if (d < 0 && listener != null) listener.onLeftEdge();
            return;
        }
        int i = g.indexOf(focus) + d;
        long rangeStart = floor(Clock.now() - BACK_MS);
        if (i < 0 || (d < 0 && focus.startMs <= rangeStart)) {
            if (d < 0 && listener != null) listener.onLeftEdge();
            return;
        }
        if (i >= g.size()) return;
        focus = g.get(i);
        scrollTo(focus);
        cursor = Math.max(viewStart, Math.min(viewStart + WINDOW_MS - 1, focus.startMs)); // the next ↑ ↓ lands near it
        changed();
    }

    void ok() {
        if (listener == null) return;
        listener.onOk(row, focus);
    }

    void hold() {
        if (listener != null) listener.onHold(row, focus);
    }

    private void changed() {
        invalidate();
        notifyRows();
    }

    private void notifyRows() {
        if (listener == null) return;
        keepRowVisible(); // the first visible row follows the focus now, not at the next draw
        int vis = visibleRows();
        listener.onVisibleRows(firstRow, Math.min(source.rows() - 1, firstRow + vis - 1), row);
    }

    // ---- Drawing -------------------------------------------------------------------------------

    /** The rects of the drawn parts (rows' logo slots, blocks, the now-line, the ruler) for the geometry replay. */
    Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    private void record(String key, float l, float t, float r, float b) {
        RectF f = drawn.get(key);
        if (f == null) drawn.put(key, new RectF(l, t, r, b));
        else f.set(l, t, r, b);
    }

    private float x(long t, float gridL, float gridR) {
        return gridL + (t - viewStart) * (gridR - gridL) / (float) WINDOW_MS;
    }

    @Override
    protected void onDraw(Canvas c) {
        drawn.clear();
        removeCallbacks(redraw);
        if (source == null) return;
        int w = getWidth(), h = getHeight();
        long now = Clock.now();
        redrawIn = se.lite.play.ui.Progress.MAX_REDRAW_MS;
        Ui.fillExcept(c, w, h, pip, Theme.BG);
        keepRowVisible();
        if (notifyPending) {
            notifyPending = false;
            notifyRows(); // the guides of the rows on screen are wanted now
        }
        float gridL = safeX + logoW + gap * 2, gridR = w - safeX;
        float rulerTop = safeY, rowsTop = safeY + rulerH, rowsBottom = h - safeB - footerH;
        record("grid", gridL, rowsTop, gridR, rowsBottom);

        drawRuler(c, gridL, gridR, rulerTop, rowsTop, rowsBottom);

        drawNow(c, now, gridL, gridR, rulerTop, rowsBottom); // under the blocks: it shows in the gaps and never crosses a title
        int vis = visibleRows();
        c.save();
        c.clipRect(0, rowsTop, w, rowsBottom);
        for (int i = 0; i < vis && firstRow + i < source.rows(); i++) {
            int r = firstRow + i;
            float top = rowsTop + i * rowH;
            drawLogo(c, r, top);
            drawRow(c, r, top, gridL, gridR, now);
        }
        c.restore();

        drawFooter(c, safeX, rowsBottom + Theme.dp(getContext(), 20), gridR, now);
        // The now-line moves a pixel every WINDOW_MS / width; the lanes asked for their own pixel and end above.
        redrawIn = Math.min(redrawIn, Math.max(se.lite.play.ui.Progress.MIN_REDRAW_MS, (long) (WINDOW_MS / Math.max(1f, gridR - gridL))));
        if (getVisibility() == VISIBLE) postDelayed(redraw, redrawIn);
    }

    /**
     * The next redraw (U16, E9): the now-line and the lanes move on their own while the grid is up, the programme that
     * ends hands over to the next on time, and the rows on screen are asked for again (a guide that went stale while
     * the grid stood open is fetched again behind what is drawn). One runnable, no allocation; nothing runs while the
     * grid is hidden (a hidden view is not drawn, so nothing is posted again).
     */
    private final Runnable redraw = () -> {
        if (getVisibility() != VISIBLE) return;
        notifyRows();
        invalidate();
    };
    private long redrawIn;

    /** Test hook: how long until the grid redraws on its own (ms), as planned by the last draw. */
    long redrawInForTest() {
        return redrawIn;
    }

    /** Test hook: the placeholder text of row {@code r} (null when it has programmes). */
    String placeholderForTest(int r) {
        List<Api.Program> g = source.guide(r);
        return g == null || g.isEmpty() ? placeholder(r) : null;
    }

    private String placeholder(int r) {
        return source.guide(r) == null ? LOADING : source.failed(r) ? FAILED : NONE;
    }

    /** The now-line: the one mark of "now" over the ruler and between the rows; the blocks are drawn over it. */
    private void drawNow(Canvas c, long now, float gridL, float gridR, float rulerTop, float rowsBottom) {
        float nx = x(now, gridL, gridR);
        if (nx < gridL || nx > gridR) return;
        line.setColor(Theme.ACCENT_ON_DARK);
        line.setStrokeWidth(Theme.dp(getContext(), 2));
        c.drawLine(nx, rulerTop + rulerH * 0.55f, nx, rowsBottom, line);
        fill.setColor(Theme.ACCENT_ON_DARK);
        c.drawCircle(nx, rulerTop + rulerH * 0.55f, Theme.dp(getContext(), 4), fill);
        record("nowLine", nx - 2, rulerTop, nx + 2, rowsBottom);
    }

    private void drawRuler(Canvas c, float gridL, float gridR, float top, float rowsTop, float rowsBottom) {
        Paint.FontMetrics tm = tickP.getFontMetrics();
        float base = top + rulerH * 0.55f - (tm.ascent + tm.descent) / 2f - Theme.dp(getContext(), 1);
        String day = dayFmt.format(new Date(viewStart + WINDOW_MS / 2)).replace(".", "").toUpperCase(Locale.forLanguageTag("sv-SE"));
        dayP.setColor(Theme.TEXT_2);
        float dayL = safeX, dayR = safeX + logoW + gap * 2;
        CharSequence d = Ui.ellipsize(day, dayP, dayR - dayL);
        c.drawText(d, 0, d.length(), dayL, base, dayP);
        record("day", dayL, top, dayR, top + rulerH);
        line.setColor(Theme.HAIRLINE);
        line.setStrokeWidth(Theme.dp(getContext(), 1));
        tickP.setColor(Theme.TEXT_2);
        for (long t = viewStart + Math.floorMod(-viewStart, STEP_MS); t <= viewStart + WINDOW_MS; t += STEP_MS) {
            float tx = x(t, gridL, gridR);
            c.drawLine(tx, top + rulerH * 0.7f, tx, rowsBottom, line);
            String s = hhmm.format(new Date(t));
            float tw = tickP.measureText(s);
            if (tx + tw + gap > gridR + safeX / 2f) break; // a label never leaves the safe area
            c.drawText(s, tx + gap * 1.5f, base, tickP);
        }
        record("ruler", gridL, top, gridR, rowsTop);
    }

    private void drawLogo(Canvas c, int r, float top) {
        float slotL = safeX, slotR = safeX + logoW;
        rect.set(slotL, top + gap, slotR, top + rowH - gap);
        record("logo" + r, rect.left, rect.top, rect.right, rect.bottom);
        Bitmap b = source.logo(r);
        if (b != null && b.getWidth() > 0 && b.getHeight() > 0) {
            LogoFit.fit(b, rect, LogoFit.LEFT, dst);
            c.drawBitmap(b, null, dst, source.logoIsLight(r) ? tintP : bmpP);
        } else { // the number and name in the same slot, never cut (F13)
            logoName.setColor(Theme.TEXT_2);
            LogoText.fit(logoName, source.rowLabel(r), rect.width(), rect.height(), logoSizes)
                    .draw(c, logoName, rect.left, rect.centerY(), Paint.Align.LEFT);
        }
    }

    private void drawRow(Canvas c, int r, float top, float gridL, float gridR, long now) {
        List<Api.Program> g = source.guide(r);
        boolean focusRow = r == row && active; // with the tab row focused the grid shows no focus look
        float bt = top + gap, bb = top + rowH - gap;
        if (g == null || g.isEmpty()) { // a placeholder block over the whole row
            boolean loading = g == null;
            rect.set(gridL + gap / 2f, bt, gridR - gap / 2f, bb); // the blocks' own edges
            fill.setColor(focusRow ? Theme.FOCUS : Theme.SURFACE);
            c.drawRoundRect(rect, radius, radius, fill);
            record("block" + r + ":none", rect.left, rect.top, rect.right, rect.bottom);
            titleP.setColor(focusRow ? Theme.ON_FOCUS_2 : Theme.TEXT_3);
            drawTitle(c, placeholder(r), rect.left, rect.right, bt, bb);
            return;
        }
        for (int k = 0; k < g.size(); k++) {
            Api.Program p = g.get(k);
            if (p.endMs <= viewStart) continue;
            if (p.startMs >= viewStart + WINDOW_MS) break;
            float l = Math.max(gridL, x(p.startMs, gridL, gridR)) + gap / 2f;
            float rr = Math.min(gridR, x(p.endMs, gridL, gridR)) - gap / 2f;
            if (rr - l < Theme.dp(getContext(), 3)) continue;
            boolean f = focusRow && p == focus;
            boolean past = p.endMs <= now, air = p.startMs <= now && now < p.endMs;
            rect.set(l, bt, rr, bb);
            fill.setColor(f ? Theme.FOCUS : Theme.SURFACE);
            c.drawRoundRect(rect, radius, radius, fill);
            record("block" + r + ":" + k, rect.left, rect.top, rect.right, rect.bottom);
            titleP.setColor(f ? Theme.ON_FOCUS : past && p.catchup == null ? Theme.TEXT_3 : air || past ? Theme.TEXT : Theme.TEXT_2);
            boolean cutL = f && p.startMs < viewStart, cutR = f && p.endMs > viewStart + WINDOW_MS;
            drawTitle(c, p.title, l + (cutL ? cueW : 0), rr - (cutR ? cueW : 0), bt, bb); // the title stays on the visible part
            if (cutL || cutR) { // the block goes on beyond the window: a cue at that edge
                titleP.setColor(Theme.ON_FOCUS_2);
                Paint.FontMetrics cm = titleP.getFontMetrics();
                float cy = bt + (bb - bt) * 0.42f - (cm.ascent + cm.descent) / 2f;
                if (cutL) c.drawText("‹", l + pad / 2f, cy, titleP);
                if (cutR) c.drawText("›", rr - pad / 2f - titleP.measureText("›"), cy, titleP);
            }
            if (air && p.endMs > p.startMs) { // the lane marks how far the programme has come
                float pw = (rr - l) * Math.max(0f, se.lite.play.ui.Progress.fraction(p.startMs, p.endMs, now));
                // Redrawn when the lane moves a pixel, and at the programme's end: the handover is on time (U16).
                redrawIn = Math.min(redrawIn, se.lite.play.ui.Progress.redrawDelay(p.startMs, p.endMs, Math.max(1f, rr - l - 2 * pad), now));
                float ly = bb - laneH - gap;
                rect.set(l + pad, ly, rr - pad, ly + laneH);
                fill.setColor(f ? Theme.TRACK_ON_FOCUS : Theme.TRACK);
                if (rect.width() > 0) c.drawRect(rect, fill);
                rect.set(l + pad, ly, Math.max(l + pad, Math.min(rr - pad, l + pw)), ly + laneH);
                fill.setColor(f ? Theme.ACCENT : Theme.ACCENT_ON_DARK);
                if (rect.width() > 0) c.drawRect(rect, fill);
            }
        }
    }

    /** The title inside a block: left padded, ellipsized to the block's width, on the block's upper half. */
    private void drawTitle(Canvas c, String s, float l, float r, float top, float bottom) {
        float avail = r - l - 2 * pad;
        if (avail < Theme.dp(getContext(), 24)) return;
        CharSequence t = Ui.ellipsize(s, titleP, avail);
        Paint.FontMetrics fm = titleP.getFontMetrics();
        float cy = top + (bottom - top) * 0.42f;
        c.drawText(t, 0, t.length(), l + pad, cy - (fm.ascent + fm.descent) / 2f, titleP);
    }

    private android.graphics.RectF pip;

    /** The chip's hole in the grid's background (px), or null while no picture plays (the tab row draws the chip's frame). */
    void setPip(android.graphics.RectF r) {
        pip = r == null ? null : new android.graphics.RectF(r);
        invalidate();
    }

    /** False while the browser's tab row has the focus: the grid keeps its place but shows no focus look. */
    private boolean active = true;

    void setActive(boolean on) {
        if (active == on) return;
        active = on;
        invalidate();
    }

    /** What OK does on the focused block, as the key hints of every footer (U6): Håll OK is the one deeper pattern (B7). */
    String[] footerPairs(long now) {
        Api.Program p = focus;
        if (!active) return new String[] {"← →", "Byt vy", "↓", "Gå in", Theme.BACK, "Stäng"}; // the tab row has the focus
        String[] tail = {Theme.BACK, "Flikar"}; // Back steps out to the tab row first
        String[] head;
        if (p == null && source.failed(row)) {
            head = new String[] {"OK", "Försök igen"};
        } else if (p == null) {
            head = new String[] {"", source.guide(row) == null ? LOADING : NONE};
        } else if (p.startMs <= now && now < p.endMs) {
            head = new String[] {"OK", "Titta direkt", "Håll OK", "Mer"};
        } else if (p.endMs <= now) {
            head = p.catchup != null ? new String[] {"OK", "Spela", "Håll OK", "Mer"} : new String[] {"", "Går inte att se i efterhand"};
        } else {
            head = new String[] {"", "Sänds " + hhmm.format(new Date(p.startMs))};
        }
        String[] all = new String[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    private void drawFooter(Canvas c, float x, float top, float right, long now) {
        String[] pairs = footerPairs(now);
        Paint.FontMetrics fm = keyP.getFontMetrics();
        float b = top + (footerH - gap * 3) / 2f - (fm.ascent + fm.descent) / 2f;
        float hx = x;
        record("footer", x, top, right, top + footerH - gap * 3);
        for (int i = 0; i + 1 < pairs.length && hx < right; i += 2) {
            if (!pairs[i].isEmpty()) {
                keyP.setColor(Theme.TEXT);
                c.drawText(pairs[i], hx, b, keyP);
                hx += keyP.measureText(pairs[i]) + gap * 2;
            }
            hintP.setColor(Theme.TEXT_2);
            CharSequence t = Ui.ellipsize(pairs[i + 1], hintP, Math.max(0, right - hx));
            c.drawText(t, 0, t.length(), hx, b, hintP);
            hx += hintP.measureText(t, 0, t.length()) + Theme.dp(getContext(), Theme.SP_XL);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        centreRow = true;
        if (source != null) notifyRows();
    }

    /** The footer hints as drawn (harness). */
    String shownFooter() {
        return String.join(" ", footerPairs(Clock.now())).trim();
    }
}
