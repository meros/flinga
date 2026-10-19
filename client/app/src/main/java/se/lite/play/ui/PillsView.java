package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One list row of fixed-width pills, drawn on a single View (F16): the Serie actions ("Fortsätt titta ·
 * S21 A3", "Min lista") and the season chips ("Säsong 21", "Säsong 20", ...).
 *
 * <pre>
 * [▶ Fortsätt titta · S21 A3 ]  [☆ Min lista ]          actions
 * [Säsong 21] [Säsong 20] [Säsong 19] ›                  chips, scrolled so the focused one shows
 * </pre>
 *
 * ← → move the focused pill inside the row (the caller handles the keys and rebinds). When the row has
 * the list's selection, the focused pill takes the FOCUS fill; otherwise the chosen pill (the season
 * shown below) keeps a raised fill, so the row says which season the list shows. Dim pills (a season
 * Telia cannot play) draw in TEXT_3 and stay focusable. Every pill has a fixed width (labels ellipsize
 * inside it) and the row has a fixed height: nothing depends on the strings (E6). Only whole pills draw (B5):
 * when more pills lie beyond an edge, a small arrow there says so and the pills that do not fit wait out of
 * sight. The first visible pill moves only as far as needed to show the focused one, without animation.
 */
public final class PillsView extends View {
    public static final int ICON_NONE = 0, ICON_PLAY = 1, ICON_STAR = 2, ICON_STAR_FILLED = 3;

    private final TextPaint textP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Map<String, RectF> drawn = new LinkedHashMap<>();
    private final int h, pillH, gap, padL, radius, padX, iconSz, hairline, arrowW;

    private String[] labels = new String[0];
    private int[] widths = new int[0];
    private int[] icons = new int[0];
    private boolean[] dims = new boolean[0];
    private int focus, chosen = -1, first;

    public PillsView(Context c) {
        super(c);
        h = Theme.dp(c, Theme.ROW_H_PILLS);
        arrowW = Theme.dp(c, Theme.SP_XL);
        pillH = Theme.dp(c, Theme.PILL_H);
        gap = Theme.dp(c, Theme.SP_S);
        padL = Theme.dp(c, Theme.SP_L); // the rows' own inset: a pill's edge lines up with a thumbnail's
        radius = Theme.dp(c, Theme.R_M);
        padX = Theme.dp(c, Theme.SP_L);
        iconSz = Theme.dp(c, 12);
        hairline = Theme.dp(c, 1);
        textP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        textP.setTypeface(Theme.MEDIUM);
        textP.setFontFeatureSettings("tnum");
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(hairline);
    }

    /**
     * Binds the pills: labels, fixed widths in px, icons ({@link #ICON_PLAY} ...), dim flags, the focused
     * pill and the chosen one (-1 = none).
     */
    public PillsView set(String[] labels, int[] widthsPx, int[] icons, boolean[] dims, int focus, int chosen) {
        this.labels = labels;
        this.widths = widthsPx;
        this.icons = icons;
        this.dims = dims;
        this.focus = Math.max(0, Math.min(labels.length - 1, focus));
        this.chosen = chosen;
        setContentDescription(labels.length == 0 ? "" : labels[this.focus]);
        scrollToFocus();
        invalidate();
        return this;
    }

    /** The first visible pill: unchanged while the focused one fits, else just far enough to show it. */
    private void scrollToFocus() {
        if (getWidth() - 2 * gap <= 0 || labels.length == 0) return;
        first = Math.min(first, labels.length - 1);
        if (focus < first) first = focus;
        while (first < focus && lastWhole(first) < focus) first++;
        if (first > 0 && lastWhole(0) >= focus) first = 0;
    }

    /**
     * The last pill that fits whole when the row starts at {@code from}: each side with more pills beyond it
     * keeps room for its arrow. At least {@code from} itself (a pill is never wider than the row).
     */
    private int lastWhole(int from) {
        int avail = getWidth() - padL - gap - (from > 0 ? arrowW : 0);
        int x = 0, last = from;
        for (int i = from; i < labels.length; i++) {
            int end = x + widths[i];
            if (end > avail - (i < labels.length - 1 ? arrowW : 0)) break;
            x = end + gap;
            last = i;
        }
        return last;
    }

    /** True when a pill draws the focus look (the row has the list's selection). */
    public boolean drawsFocus() {
        return (isSelected() || isFocused()) && labels.length > 0;
    }

    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    @Override
    protected void onSizeChanged(int w, int hh, int ow, int oh) {
        scrollToFocus();
    }

    @Override
    public void setSelected(boolean selected) {
        boolean was = isSelected();
        super.setSelected(selected);
        if (was != selected) invalidate();
    }

    /** True when pills wait beyond the right edge (an arrow shows there). */
    public boolean moreRight() {
        return labels.length > 0 && lastWhole(first) < labels.length - 1;
    }

    /** True when pills wait beyond the left edge (an arrow shows there). */
    public boolean moreLeft() {
        return first > 0;
    }

    @Override
    protected void onDraw(Canvas c) {
        drawn.clear();
        boolean rowFocused = isSelected() || isFocused();
        float x = padL + (first > 0 ? arrowW : 0), top = (getHeight() - pillH) / 2f;
        Paint.FontMetrics fm = textP.getFontMetrics();
        // Only whole pills draw (B5); a side with more pills beyond it shows an arrow (Y8: never a cut word).
        int last = labels.length == 0 ? -1 : lastWhole(first);
        for (int i = first; i <= last; i++) {
            float w = widths[i];
            rect.set(x, top, x + w, top + pillH);
            boolean f = rowFocused && i == focus;
            boolean ch = !f && i == chosen;
            boolean dim = dims != null && i < dims.length && dims[i];
            if (f) {
                fill.setColor(Theme.FOCUS);
                c.drawRoundRect(rect, radius, radius, fill);
                record("focus", rect);
            } else if (ch) {
                fill.setColor(Theme.SURFACE_RAISED);
                c.drawRoundRect(rect, radius, radius, fill);
                stroke.setColor(Theme.TEXT_2);
                rect.inset(hairline / 2f, hairline / 2f);
                c.drawRoundRect(rect, radius, radius, stroke);
                rect.inset(-hairline / 2f, -hairline / 2f);
            } else {
                stroke.setColor(Theme.HAIRLINE);
                rect.inset(hairline / 2f, hairline / 2f);
                c.drawRoundRect(rect, radius, radius, stroke);
                rect.inset(-hairline / 2f, -hairline / 2f);
            }
            record("pill" + i, rect);
            int color = f ? (dim ? Theme.ON_FOCUS_2 : Theme.ON_FOCUS) : dim ? Theme.TEXT_3 : Theme.TEXT;
            float tx = x + padX;
            int icon = icons != null && i < icons.length ? icons[i] : ICON_NONE;
            float cy = rect.centerY();
            if (icon != ICON_NONE) {
                drawIcon(c, icon, tx, cy, f ? Theme.ACCENT : dim ? Theme.TEXT_3 : Theme.ACCENT_ON_DARK);
                tx += iconSz + gap;
            }
            CharSequence t = Ui.ellipsize(labels[i], textP, x + w - padX - tx);
            textP.setColor(color);
            c.drawText(t, 0, t.length(), tx, cy - (fm.ascent + fm.descent) / 2f, textP);
            x += w + gap;
        }
        if (first > 0) drawArrow(c, gap + arrowW / 2f, false);
        if (last >= 0 && last < labels.length - 1) drawArrow(c, getWidth() - gap - arrowW / 2f, true);
    }

    /** A small chevron at {@code cx}: "more pills this way". */
    private void drawArrow(Canvas c, float cx, boolean right) {
        float s = iconSz / 2f, cy = getHeight() / 2f, d = right ? 1 : -1;
        path.reset();
        path.moveTo(cx - d * s / 2f, cy - s);
        path.lineTo(cx + d * s / 2f, cy);
        path.lineTo(cx - d * s / 2f, cy + s);
        stroke.setColor(Theme.TEXT_2);
        stroke.setStrokeWidth(Theme.dp(getContext(), 2));
        c.drawPath(path, stroke);
        stroke.setStrokeWidth(hairline);
        rect.set(cx - s, cy - s, cx + s, cy + s);
        record(right ? "moreR" : "moreL", rect);
    }

    private void record(String name, RectF r) {
        drawn.put(name, new RectF(r));
    }

    private void drawIcon(Canvas c, int icon, float left, float cy, int color) {
        float s = iconSz, top = cy - s / 2f;
        path.reset();
        if (icon == ICON_PLAY) {
            path.moveTo(left + s * 0.15f, top);
            path.lineTo(left + s, cy);
            path.lineTo(left + s * 0.15f, top + s);
            path.close();
            fill.setColor(color);
            c.drawPath(path, fill);
            return;
        }
        // Five-pointed star, outlined (not in Min lista) or filled (in Min lista).
        float cx = left + s / 2f, ro = s / 2f + Theme.dp(getContext(), 1), ri = ro * 0.45f;
        for (int k = 0; k < 10; k++) {
            double a = Math.PI / 2 + k * Math.PI / 5;
            float r = k % 2 == 0 ? ro : ri;
            float px = cx + (float) (r * Math.cos(a)), py = cy - (float) (r * Math.sin(a));
            if (k == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        if (icon == ICON_STAR_FILLED) {
            fill.setColor(color);
            c.drawPath(path, fill);
        } else {
            stroke.setColor(color);
            stroke.setStrokeWidth(Theme.dp(getContext(), 1.5f));
            c.drawPath(path, stroke);
            stroke.setStrokeWidth(hairline);
        }
    }
}
