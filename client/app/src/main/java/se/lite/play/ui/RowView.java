package se.lite.play.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One list row drawn on a single View: no child views, no layout pass per row.
 *
 * <pre>
 * [logo]  Title ..................  [NU][   meta][>]
 *               secondary line
 *               ======-------------- progress
 * </pre>
 *
 * Geometry comes from fixed slots, never from the text (DESIGN.md, "Stable geometry"): the trailing icon,
 * the meta slot (right-aligned text, ellipsized inside it), the NU pill slot, the logo
 * slot and the progress lane sit at the same place whatever the strings are and whether a neighbour is
 * present. The height comes from the list ({@link #tall}), not from whether this row has a subtitle.
 *
 * A header row ({@link #header}) is a 40 dp caption naming the group below it ("SERIER  8"); a note row
 * ({@link #note}) is up to three lines of explanation with its height reserved. Neither takes focus.
 * {@link #highlight} draws part of the title in TEXT and the rest in TEXT_2 (search completions).
 *
 * Focus follows {@link #isSelected()} (set by ListView on the selected row) or {@link #isFocused()}:
 * the row fills with {@link Theme#FOCUS} inside its own bounds and its text inverts to {@link Theme#ON_FOCUS}.
 * Call {@link #reset()} before binding; {@link RowAdapter} does that for you.
 */
public class RowView extends View {
    public static final int TRAIL_NONE = 0;
    /** Playable (catch-up, episode, film): small play triangle. */
    public static final int TRAIL_PLAY = 1;
    /** Opens another level (series, season, guide): chevron. */
    public static final int TRAIL_CHEVRON = 2;
    private static final int NOTE_LINES = 3;

    private final TextPaint titleP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint metaP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint logoP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** Only the other variant exists: its alpha drawn as a silhouette in the text colour of the surface. */
    private final Paint tintP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final RectF logoDst = new RectF();
    private final Path icon = new Path();
    /** Last drawn rects of the non-text parts, for the harness's geometry check. */
    private final Map<String, RectF> drawn = new LinkedHashMap<>();

    private final int padH, gap, hOne, hTwo, hHead, hHeadFirst, progH, barW, radius, iconSz, pillPadH, pillH,
            inset, laneGap, pillR, metaW, logoW, logoH, logoWs, logoHs, thumbW, thumbH, resultW, resultH, hThumb, plateR;

    private CharSequence title = "", subtitle, meta, logoName;
    private CharSequence titleShown = "", subShown, metaShown;
    /** The fallback name fitted into the logo slot (X1: never ellipsized while a size fits). */
    private LogoText logoShown;
    private final float[] logoSizes;
    /** The list's shared lead column (px): the widest slot of any row, so every title starts at one x (Y3). */
    private int indent;
    private Bitmap logo, logoLight;
    /** F16: a 16:9 thumbnail slot (search result or episode); the placeholder fills it until the image comes. */
    private Bitmap thumb;
    private String thumbKey;
    private boolean hasThumb, bigThumb, info;
    private CharSequence infoMeta;
    /** An empty, unfocusable row of this height (px), so a list can scroll whole sections; 0 = none. */
    private int spacer;
    private final Paint thumbP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF thumbDst = new RectF();
    /** What the logo slot drew last: "light", "dark" or "name". For tests. */
    private String logoDrawn = "";
    private float progress = -1f;
    private int trailing = TRAIL_NONE;
    private boolean live, dim, marker, header, note, tall, lane, hasLogo, first, logoColumn;
    private int hlStart = -1, hlEnd = -1;
    private StaticLayout noteLayout;
    /** Ellipsized text is rebuilt lazily in onDraw, once per bind. */
    private boolean dirty = true;

    public RowView(Context c) {
        super(c);
        padH = Theme.dp(c, Theme.SP_L);
        gap = Theme.dp(c, Theme.SP_M);
        hOne = Theme.dp(c, Theme.ROW_H_ONE);
        hTwo = Theme.dp(c, Theme.ROW_H_TWO);
        hHead = Theme.dp(c, Theme.ROW_H_HEADER);
        hHeadFirst = Theme.dp(c, Theme.ROW_H_HEADER_FIRST);
        progH = Theme.dp(c, Theme.PROGRESS_ROW);
        barW = Theme.dp(c, Theme.FOCUS_BAR);
        radius = Theme.dp(c, Theme.R_M);
        iconSz = Theme.dp(c, 12);
        pillPadH = Theme.dp(c, 6);
        pillH = Theme.dp(c, 22);
        inset = Theme.dp(c, 2);
        laneGap = Theme.dp(c, 8);
        pillR = Theme.dp(c, Theme.R_S);
        metaW = Theme.dp(c, Theme.ROW_META_W);
        logoW = Theme.dp(c, Theme.LOGO_W);
        logoH = Theme.dp(c, Theme.LOGO_H);
        logoWs = Theme.dp(c, Theme.HEADER_LOGO_W);
        logoHs = Theme.dp(c, Theme.HEADER_LOGO_H);
        thumbW = Theme.dp(c, Theme.THUMB_W);
        thumbH = Theme.dp(c, Theme.THUMB_H);
        resultW = Theme.dp(c, Theme.RESULT_W);
        resultH = Theme.dp(c, Theme.RESULT_H);
        hThumb = Theme.dp(c, Theme.ROW_H_THUMB);
        plateR = Theme.dp(c, Theme.R_S);
        titleP.setTextSize(Theme.sp(c, Theme.TEXT_BODY));
        titleP.setTypeface(Theme.MEDIUM);
        titleP.setFontFeatureSettings("tnum");
        subP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        subP.setFontFeatureSettings("tnum");
        metaP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        metaP.setFontFeatureSettings("tnum");
        pillP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        pillP.setTypeface(Theme.BOLD);
        headP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        headP.setTypeface(Theme.BOLD);
        headP.setLetterSpacing(0.06f);
        headP.setFontFeatureSettings("tnum");
        // The fallback name at the title's size (VISUAL-QA M14), stepping down and wrapping before it is ever cut (X1).
        logoSizes = new float[] {Theme.sp(c, Theme.TEXT_BODY), Theme.sp(c, Theme.TEXT_LABEL), Theme.sp(c, Theme.TEXT_CAPTION)};
        logoP.setTextSize(logoSizes[0]);
        logoP.setTypeface(Theme.BOLD);
        logoP.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Theme.dp(c, 2));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    // ---- Binding -----------------------------------------------------------------------

    /** Clears every optional part. The logo bitmap is kept for {@link #logo} to compare. */
    public RowView reset() {
        title = "";
        subtitle = meta = logoName = null;
        progress = -1f;
        trailing = TRAIL_NONE;
        live = dim = marker = header = note = tall = lane = hasLogo = false;
        hlStart = hlEnd = -1;
        noteLayout = null;
        if (hasThumb || bigThumb || info) requestLayout();
        hasThumb = bigThumb = info = false;
        thumb = null;
        thumbKey = null;
        infoMeta = null;
        if (spacer > 0) requestLayout();
        spacer = 0;
        changed(true);
        return this;
    }

    /** Title (required) and optional secondary line. */
    public RowView text(CharSequence title, CharSequence subtitle) {
        this.title = title == null ? "" : title;
        this.subtitle = subtitle;
        setContentDescription(this.title);
        changed(false);
        return this;
    }

    /** All rows of a list share one height: tall (72 dp, room for a secondary line) or not (56 dp). */
    public RowView tall(boolean tall) {
        if (this.tall != tall) {
            this.tall = tall;
            changed(true);
        }
        return this;
    }

    /** All rows of a list reserve the progress lane when any row of it has progress. */
    public RowView lane(boolean lane) {
        if (this.lane != lane) {
            this.lane = lane;
            changed(false);
        }
        return this;
    }

    /** A sheet's logo column: the logo slot is the header's 56 x 32 dp, not the list's 80 x 45 dp (M8). */
    public RowView logoColumn(boolean column) {
        if (this.logoColumn != column) {
            this.logoColumn = column;
            changed(false);
        }
        return this;
    }

    /**
     * The list's shared lead column in px (the widest logo or thumbnail slot of any of its rows): rows
     * without a slot, or with a narrower one, still start their text after it, so one list has one text
     * column (REVIEW-R2 Y3). 0 = no column.
     */
    public RowView indent(int px) {
        if (this.indent != px) {
            this.indent = px;
            changed(false);
        }
        return this;
    }

    /** Where the title starts, in px from the row's left edge, as drawn last (Y3 check). */
    public float textLeftPx() {
        return textLeft();
    }

    /** True when the logo slot drew a cut channel name last (X1 check: must never happen). */
    public boolean logoFallbackCut() {
        return "name".equals(logoDrawn) && logoShown != null && logoShown.cut;
    }

    /** Right-aligned text in the fixed meta slot, e.g. "45 min", "S2 · A3", "till 21:00". */
    public RowView meta(CharSequence meta) {
        this.meta = meta;
        changed(false);
        return this;
    }


    /**
     * Channel logo in the fixed logo slot. {@code bmp} null draws {@code name} in the slot instead
     * (missing or failed logo), so the slot is never empty and nothing moves when the logo arrives.
     */
    public RowView logo(Bitmap bmp, CharSequence name) {
        return logo(bmp, null, name);
    }

    /**
     * Channel logo with both artworks: {@code dark} for the dark row, {@code light} for the light
     * focused card. Both are kept, so focus moving onto the row swaps them in the same frame.
     */
    public RowView logo(Bitmap dark, Bitmap light, CharSequence name) {
        hasLogo = true;
        if (dark != logo || light != logoLight) {
            logo = dark;
            logoLight = light;
            invalidate();
        }
        logoName = name;
        changed(false);
        return this;
    }

    /** Centre y of the text block as drawn last; the harness checks it sits on the row's centre line (C5). */
    private float blockCentre = -1f;

    public float textBlockCentre() {
        return blockCentre;
    }

    /** "light", "dark" or "name": what the logo slot drew last. */
    public String logoDrawn() {
        return logoDrawn;
    }

    /** What a screen reader says for this row, when it differs from the title (e.g. the channel name). */
    public RowView describe(CharSequence d) {
        if (d != null) setContentDescription(d);
        return this;
    }

    /** {@link #logo} when {@code has}; otherwise no logo slot. For binders. */
    public RowView logoIf(boolean has, Bitmap dark, Bitmap light, CharSequence name) {
        if (has) return logo(dark, light, name);
        hasLogo = false;
        logo = logoLight = null;
        return this;
    }

    /**
     * A 16:9 thumbnail slot at the left: {@code big} is the episode slot (128 x 72 dp, the row is 88 dp
     * tall), otherwise the search result slot (96 x 54 dp). {@code bmp} null draws the placeholder of the
     * same size (loading or failed), so the image arriving moves nothing. {@code key} names the image, so
     * {@link #thumbArrived} can put it in without a rebind.
     */
    public RowView thumb(String key, Bitmap bmp, boolean big) {
        boolean resize = !hasThumb || bigThumb != big;
        hasThumb = true;
        bigThumb = big;
        thumbKey = key;
        thumb = bmp;
        changed(resize);
        return this;
    }

    /** An image arrived: shown when it is the one this row's slot waits for. True if it was. */
    public boolean thumbArrived(String key, Bitmap bmp) {
        if (!hasThumb || key == null || !key.equals(thumbKey) || bmp == thumb) return false;
        thumb = bmp;
        invalidate();
        return true;
    }

    /** Drops the decoded thumbnail (the panel closed); the slot draws its placeholder. */
    public void releaseImages() {
        thumb = null;
        invalidate();
    }

    /**
     * Turns the row into the Serie meta line ("TV4 Play · Reality-TV · 3 säsonger · 0+", B5): one line,
     * ellipsized, height fixed; never focused.
     */
    public RowView info(CharSequence meta) {
        info = true;
        infoMeta = meta;
        title = "";
        setContentDescription(meta == null ? "" : meta);
        changed(true);
        return this;
    }

    /** Turns the row into empty space of {@code px}: room at the end of a list to scroll whole sections. */
    public RowView spacer(int px) {
        spacer = Math.max(1, px);
        title = "";
        changed(true);
        return this;
    }

    /** Progress 0..1, or negative for none. */
    public RowView progress(float p) {
        this.progress = p < 0 ? -1f : Math.min(1f, p);
        changed(false);
        return this;
    }

    public RowView trailing(int kind) {
        this.trailing = kind;
        changed(false);
        return this;
    }

    /** Marks the row as on air now: accent bar on the left and an "NU" pill. */
    public RowView live(boolean live) {
        this.live = live;
        changed(false);
        return this;
    }

    /** Marks what is playing right now (the tuned channel): the accent bar without the NU pill. */
    public RowView marker(boolean marker) {
        this.marker = marker;
        changed(false);
        return this;
    }

    /** Turns the row into a group header: title and meta as captions, 40 dp, never focused. */
    public boolean isHeader() {
        return header;
    }

    /** The row is the first of its list: a group header then drops the air above its text. */
    public RowView first(boolean first) {
        if (this.first != first) {
            this.first = first;
            changed(true);
        }
        return this;
    }

    public RowView header(boolean header) {
        if (this.header != header) {
            this.header = header;
            changed(true);
        }
        return this;
    }

    /** Turns the row into up to three lines of explanation (the title), height reserved, never focused. */
    public RowView note(boolean note) {
        if (this.note != note) {
            this.note = note;
            noteLayout = null;
            changed(true);
        }
        return this;
    }

    /** Draws title characters [start, end) in TEXT and the rest in TEXT_2; pass -1 for none. */
    public RowView highlight(int start, int end) {
        hlStart = start;
        hlEnd = end;
        changed(false);
        return this;
    }

    /** Dims the row: not playable (past without catch-up, upcoming). Still focusable for scrolling. */
    public RowView dim(boolean dim) {
        this.dim = dim;
        changed(false);
        return this;
    }

    /** The rects of the non-text parts as last drawn (icon, pill, meta slot, logo slot, progress, bar). */
    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    /** True when this row draws the focus look (one per screen, VISUAL-QA C2). */
    public boolean drawsFocus() {
        return focusedLook() && !header && !note;
    }

    /** The title as drawn last (ellipsized inside its slot); for the title-priority test (C4). */
    public CharSequence shownTitle() {
        if (dirty && getWidth() > 0) ellipsize();
        return titleShown;
    }

    // ---- View --------------------------------------------------------------------------

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (spacer > 0) {
            setMeasuredDimension(w, spacer);
            return;
        }
        if (info) {
            setMeasuredDimension(w, infoHeight());
            return;
        }
        if (hasThumb && bigThumb) {
            setMeasuredDimension(w, hThumb);
            return;
        }
        if (note) {
            Paint.FontMetrics fm = subP.getFontMetrics();
            setMeasuredDimension(w, Math.round(NOTE_LINES * (fm.descent - fm.ascent)) + 2 * gap);
            return;
        }
        setMeasuredDimension(w, header ? first ? hHeadFirst : hHead : tall ? hTwo : hOne);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        dirty = true;
        noteLayout = null;
    }

    @Override
    public void setSelected(boolean selected) {
        boolean was = isSelected();
        super.setSelected(selected);
        if (was != selected) invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        invalidate();
    }

    private boolean focusedLook() {
        return isSelected() || isFocused() || isPressed();
    }

    private void changed(boolean resize) {
        if (resize) requestLayout();
        dirty = true;
        invalidate();
    }

    // Fixed slots, computed from the width only.
    private float trailLeft() { return getWidth() - padH - iconSz; }
    private float metaRight() { return trailLeft() - gap; }
    private float metaLeft() { return metaRight() - metaW; }
    /** The NU pill sits right-aligned in the meta slot: a live row has no meta (its end is the next row). */
    private float pillRight() { return metaRight(); }
    /** The meta text ends at the slot's right edge, or left of the NU pill when the row is live. */
    private float metaTextRight() { return live ? pillRight() - pillWidth() - gap : metaRight(); }
    /** The logo slot: 80 x 45 dp in lists; 56 x 32 dp (the header's) in a sheet's logo column. */
    private int logoW() { return logoColumn ? logoWs : logoW; }
    private int logoH() { return logoColumn ? logoHs : logoH; }
    private float pillWidth() { return pillP.measureText("NU") + 2 * pillPadH; }
    private float logoLeft() { return padH; }

    /** This row's own slot width: the thumbnail or the logo slot, else 0. */
    private int slotW() {
        if (hasThumb) return bigThumb ? thumbW : resultW;
        return hasLogo || logoColumn ? logoW() : 0;
    }

    private float textLeft() {
        int col = Math.max(indent, slotW());
        return logoLeft() + (col > 0 ? col + gap : 0);
    }

    /** Height of a note row: three lines reserved. */
    public int noteHeight() {
        Paint.FontMetrics fm = subP.getFontMetrics();
        return Math.round(NOTE_LINES * (fm.descent - fm.ascent)) + 2 * gap;
    }

    /** Height of the meta line of a Serie: one line of text and a little air (B5). */
    public int infoHeight() {
        Paint.FontMetrics m = metaP.getFontMetrics();
        return Math.round(m.descent - m.ascent) + Theme.dp(getContext(), Theme.SP_XS + Theme.SP_S);
    }

    /**
     * Where the title must end: before the leftmost right-hand element this row uses. The title has
     * priority (VISUAL-QA C4): the meta slot is fixed, but the title runs up to the meta text inside it,
     * and the pill takes the meta slot when there is no meta. Only text moves; slots stay.
     */
    private float titleRight() {
        float r = getWidth() - padH;
        if (trailing != TRAIL_NONE) r = trailLeft() - gap;
        if (meta != null) r = metaTextRight() - (metaShown == null ? metaW : metaP.measureText(metaShown, 0, metaShown.length())) - gap;
        else if (live) r = pillRight() - pillWidth() - gap;
        return r;
    }

    private void ellipsize() {
        int w = getWidth();
        dirty = false;
        metaShown = meta == null ? null : Ui.ellipsize(meta, metaP, metaW);
        float avail = Math.max(0, titleRight() - textLeft());
        titleShown = Ui.ellipsize(title, titleP, avail);
        float subRight = progress >= 0 || meta == null ? w - padH : titleRight();
        subShown = subtitle == null ? null
                : Ui.ellipsize(subtitle, subP, Math.max(0, subRight - textLeft()));
        int pad = Theme.dp(getContext(), 4);
        logoShown = logoName == null ? null
                : LogoText.fit(logoP, logoName.toString(), logoW() - 2 * pad, logoH() - 2 * pad, logoSizes);
    }

    private StaticLayout noteLayout(int w) {
        int nw = Math.max(1, w - 2 * padH);
        if (noteLayout == null || noteLayout.getWidth() != nw) {
            noteLayout = StaticLayout.Builder.obtain(title, 0, title.length(), subP, nw)
                    .setMaxLines(NOTE_LINES).setEllipsize(TextUtils.TruncateAt.END).build();
        }
        return noteLayout;
    }

    private void drawInfo(Canvas c, int w) {
        Paint.FontMetrics mm = metaP.getFontMetrics();
        if (infoMeta != null) {
            CharSequence m = Ui.ellipsize(infoMeta, metaP, w - 2 * padH);
            metaP.setColor(Theme.TEXT_2);
            c.drawText(m, 0, m.length(), padH, Theme.dp(getContext(), Theme.SP_XS) - mm.ascent, metaP);
        }
        rect.set(padH, 0, w - padH, getHeight());
        record("info", rect);
    }

    /** The thumbnail slot: placeholder plate, then the image (sized exactly for the slot), dim when not playable. */
    private void drawThumb(Canvas c, int h, boolean f) {
        float tw = bigThumb ? thumbW : resultW, th = bigThumb ? thumbH : resultH;
        float l = logoLeft(), top = (h - th) / 2f;
        rect.set(l, top, l + tw, top + th);
        record("thumb", rect);
        fill.setColor(f ? Theme.FOCUS_PLATE : Theme.SURFACE);
        c.drawRoundRect(rect, plateR, plateR, fill);
        if (thumb != null && thumb.getWidth() > 0) {
            thumbDst.set(rect);
            thumbP.setAlpha(dim && !f ? 102 : 255);
            c.drawBitmap(thumb, null, thumbDst, thumbP);
        }
    }

    private void record(String name, RectF r) {
        RectF d = drawn.get(name);
        if (d == null) drawn.put(name, new RectF(r));
        else d.set(r);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (dirty) ellipsize();
        drawn.clear();
        blockCentre = -1f; // headers, notes and the info block have no text block on the centre line
        if (spacer > 0) return;
        if (info) {
            drawInfo(c, w);
            return;
        }
        if (header) {
            drawHeader(c, w, h);
            return;
        }
        if (note) {
            c.save();
            c.translate(padH, gap);
            subP.setColor(Theme.TEXT_2);
            noteLayout(w).draw(c);
            c.restore();
            return;
        }
        boolean f = focusedLook();

        if (f) {
            fill.setColor(Theme.FOCUS);
            rect.set(0, inset, w, h - inset);
            c.drawRoundRect(rect, radius, radius, fill);
            record("focus", rect);
        }
        if (live || marker) {
            fill.setColor(Theme.ACCENT);
            rect.set(0, inset + radius, barW, h - inset - radius); // clear of the card's rounded corners
            c.drawRoundRect(rect, barW / 2f, barW / 2f, fill);
            record("bar", rect);
        }

        int cTitle = f ? Theme.ON_FOCUS : dim ? Theme.TEXT_3 : Theme.TEXT;
        int cSub = f ? Theme.ON_FOCUS_2 : dim ? Theme.TEXT_3 : Theme.TEXT_2;

        // Vertical rhythm from the list, not the text: the lane is reserved when any row of the list has
        // progress, and the text block (title, plus the subtitle when there is one) is centred in what is
        // left. Within one list nothing moves when a subtitle or a bar appears on refresh, because the
        // subtitle and the lane are properties of the row kind, not of the data.
        Paint.FontMetrics tm = titleP.getFontMetrics();
        Paint.FontMetrics sm = subP.getFontMetrics();
        float titleH = tm.descent - tm.ascent;
        float subH = tall && subShown != null ? sm.descent - sm.ascent : 0;
        // The text block (title, plus the subtitle when there is one) is centred on the row's centre line;
        // the progress lane sits in the bottom margin below it (VISUAL-QA C5). A lone title in a tall row
        // therefore reads centred in its card, like a title in a short row.
        float top = (h - titleH - subH) / 2f;
        blockCentre = top + (titleH + subH) / 2f;
        float titleBase = top - tm.ascent;
        float x = textLeft();

        drawTitle(c, x, titleBase, cTitle, f);
        if (subShown != null) {
            subP.setColor(cSub);
            c.drawText(subShown, 0, subShown.length(), x, titleBase + tm.descent - sm.ascent, subP);
        }
        if (hasThumb) drawThumb(c, h, f);
        if (hasLogo) {
            // Centred on the text block (title + secondary line), kept inside the row and its focus card.
            float block = top + (titleH + (tall ? sm.descent - sm.ascent : 0)) / 2f;
            float cy = Math.max(inset + logoH() / 2f, Math.min(h - inset - logoH() / 2f, block));
            drawLogo(c, cy, f);
        }

        float midY = titleBase + (tm.ascent + tm.descent) / 2f;
        if (trailing != TRAIL_NONE) { // purple marks state, not decoration: the icon takes the accent only on the focused card
            drawIcon(c, trailLeft(), midY, f ? Theme.ACCENT : dim ? Theme.TEXT_3 : Theme.TEXT_2);
        }
        if (metaShown != null) {
            metaP.setColor(cSub);
            float mw = metaP.measureText(metaShown, 0, metaShown.length());
            c.drawText(metaShown, 0, metaShown.length(), metaTextRight() - mw, titleBase, metaP);
            rect.set(metaLeft(), titleBase + tm.ascent, metaRight(), titleBase + tm.descent);
            record("metaSlot", rect);
        }
        if (live) {
            float pw = pillWidth(), r = pillRight();
            Paint.FontMetrics pm = pillP.getFontMetrics();
            float pillTop = midY - pillH / 2f;
            rect.set(r - pw, pillTop, r, pillTop + pillH);
            fill.setColor(Theme.ACCENT);
            c.drawRoundRect(rect, pillR, pillR, fill);
            record("pill", rect);
            pillP.setColor(0xFFFFFFFF);
            c.drawText("NU", r - pw + pillPadH, rect.centerY() - (pm.ascent + pm.descent) / 2f, pillP);
        }

        if (progress >= 0 && hasThumb && drawn.get("thumb") != null) {
            // A row with a thumbnail has no spare line for a lane under its text (it would run through the
            // secondary line): the lane lies along the thumbnail's bottom edge, as on a catalogue card.
            RectF t = drawn.get("thumb");
            float lx0 = t.left + plateR, lx1 = t.right - plateR, ly = t.bottom - progH;
            rect.set(lx0, ly, lx1, ly + progH);
            fill.setColor(Theme.TRACK);
            c.drawRect(rect, fill);
            record("track", rect);
            rect.right = lx0 + (lx1 - lx0) * progress;
            fill.setColor(Theme.ACCENT);
            c.drawRect(rect, fill);
        } else if (progress >= 0) {
            float py = h - inset - laneGap - progH;
            float x1 = w - padH;
            rect.set(x, py, x1, py + progH);
            fill.setColor(f ? Theme.TRACK_ON_FOCUS : Theme.TRACK);
            c.drawRoundRect(rect, progH / 2f, progH / 2f, fill);
            record("track", rect);
            rect.right = x + (x1 - x) * progress;
            fill.setColor(f ? Theme.ACCENT : Theme.ACCENT_ON_DARK);
            c.drawRoundRect(rect, progH / 2f, progH / 2f, fill);
        }
    }

    private void drawTitle(Canvas c, float x, float base, int color, boolean f) {
        int n = titleShown.length();
        titleP.setColor(color);
        if (hlStart >= 0 && hlEnd > hlStart && !dim) {
            // Completion: the typed part in full contrast, the rest one step down.
            int a = Math.min(hlStart, n), b = Math.min(hlEnd, n);
            int rest = f ? Theme.ON_FOCUS_2 : Theme.TEXT_2;
            float cx = x;
            titleP.setColor(rest);
            c.drawText(titleShown, 0, a, cx, base, titleP);
            cx += titleP.measureText(titleShown, 0, a);
            titleP.setColor(color);
            c.drawText(titleShown, a, b, cx, base, titleP);
            cx += titleP.measureText(titleShown, a, b);
            titleP.setColor(rest);
            c.drawText(titleShown, b, n, cx, base, titleP);
        } else {
            c.drawText(titleShown, 0, n, x, base, titleP);
        }
    }

    /**
     * The logo slot: an invisible fixed rect. The logo PNGs have alpha, so the artwork is drawn straight
     * on the surface, with the variant made for it: the dark-background artwork on the dark row, the
     * light-background artwork on the light focused card. When the right variant is missing the other is
     * drawn as a silhouette in the surface's text colour (its alpha keeps the shape), so it never vanishes
     * on a matching background; no logo at all draws "12 SVT1" as plain text in the slot. Nothing about the
     * slot changes size.
     */
    private void drawLogo(Canvas c, float cy, boolean f) {
        float l = logoLeft();
        rect.set(l, cy - logoH() / 2f, l + logoW(), cy + logoH() / 2f);
        Bitmap b;
        boolean lightArt;
        if (f) {
            lightArt = logoLight != null;
            b = lightArt ? logoLight : logo;
        } else {
            lightArt = logo == null && logoLight != null;
            b = lightArt ? logoLight : logo;
        }
        record("logo", rect); // no fill: the slot is invisible
        if (b != null && b.getWidth() > 0 && b.getHeight() > 0) {
            logoDrawn = lightArt ? "light" : "dark";
            LogoFit.fit(b, rect, LogoFit.CENTER, logoDst); // equal visual weight, centred on the slot axis
            boolean wrongVariant = f != lightArt; // dark artwork on the light card, or the other way round
            if (wrongVariant) {
                tintP.setColorFilter(new android.graphics.PorterDuffColorFilter(f ? Theme.ON_FOCUS : Theme.TEXT,
                        android.graphics.PorterDuff.Mode.SRC_IN));
            }
            c.drawBitmap(b, null, logoDst, wrongVariant ? tintP : bmpP);
            if (wrongVariant) logoDrawn += "-silhouette";
        } else if (logoShown != null) {
            logoDrawn = "name";
            logoP.setColor(f ? Theme.ON_FOCUS_2 : Theme.TEXT_2);
            logoShown.draw(c, logoP, rect.centerX(), rect.centerY(), Paint.Align.CENTER);
        }
    }

    /** Caption at the bottom of the 40 dp slot, so it sits close to the group it names. */
    private void drawHeader(Canvas c, int w, int h) {
        float base = h - Theme.dp(getContext(), Theme.SP_S) - headP.getFontMetrics().descent;
        headP.setColor(Theme.TEXT_3);
        float r = w - padH;
        if (meta != null) {
            CharSequence m = Ui.ellipsize(meta, headP, metaW);
            float mw = headP.measureText(m, 0, m.length());
            c.drawText(m, 0, m.length(), r - mw, base, headP);
            r -= metaW + gap;
        }
        CharSequence t = Ui.ellipsize(title, headP, Math.max(0, r - padH));
        c.drawText(t, 0, t.length(), padH, base, headP);
    }

    private void drawIcon(Canvas c, float left, float cy, int color) {
        icon.reset();
        float s = iconSz, top = cy - s / 2f;
        rect.set(left, top, left + s, top + s);
        record("icon", rect);
        if (trailing == TRAIL_PLAY) {
            icon.moveTo(left + s * 0.15f, top);
            icon.lineTo(left + s, cy);
            icon.lineTo(left + s * 0.15f, top + s);
            icon.close();
            fill.setColor(color);
            c.drawPath(icon, fill);
        } else {
            icon.moveTo(left + s * 0.3f, top);
            icon.lineTo(left + s * 0.8f, cy);
            icon.lineTo(left + s * 0.3f, top + s);
            stroke.setColor(color);
            c.drawPath(icon, stroke);
        }
    }
}
