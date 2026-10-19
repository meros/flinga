package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The universal picker (Surface 3): one right-aligned sheet, {@link Theme#PICKER_W} wide, over the video or
 * the browser, for every contextual question the app asks.
 *
 * <pre>
 *  +--------------------------------------------------+
 *  | video or browser, dimmed by the scrim            |
 *  |                            ALTERNATIV & INSTÄLLNINGAR |
 *  |                            Fråga Lund            |
 *  |                            [ Spela från början  20:00 ] |
 *  |                              Undertext        Svenska |
 *  |                              Ljudspår    Svenska ›    |
 *  |                              Bildformat  Anpassa ›    |
 *  |                              Min lista     + Lägg till |
 *  +--------------------------------------------------+
 * </pre>
 *
 * It replaces Alternativ, Ljud och text, Nästa avsnitt and the logout confirmation: a value row changes its
 * value in place with OK or ← →, an action row runs on OK, and Back closes the sheet and returns the focus
 * to what is under it. The view only draws; the activity owns the rows, the keys and the effects.
 */
public final class PickerView extends View {
    /** One row: a label and its value, an action, or a note that explains without focus. */
    public static final class Row {
        public static final int VALUE = 0, ACTION = 1, NOTE = 2, HEADER = 3;
        public final String label, value;
        public final int kind;
        public final Object key;
        public final boolean dim;

        public Row(String label, String value, int kind, Object key, boolean dim) {
            this.label = label;
            this.value = value;
            this.kind = kind;
            this.key = key;
            this.dim = dim;
        }

        public static Row value(String label, String value, Object key) {
            return new Row(label, value, VALUE, key, false);
        }

        public static Row action(String label, Object key) {
            return new Row(label, null, ACTION, key, false);
        }

        public static Row note(String label) {
            return new Row(label, null, NOTE, null, false);
        }

        public static Row header(String label) {
            return new Row(label, null, HEADER, null, false);
        }
    }

    private final TextPaint eyebrowP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint rowP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valueP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Map<String, RectF> drawn = new LinkedHashMap<>();
    private final int sheetW, pad, rowH, radius, eyebrowH, titleH, hintH, safeY;

    private String eyebrow = "Alternativ", title = "";
    private List<Row> rows = new ArrayList<>();
    private int focus;
    private Runnable onClose;

    public PickerView(Context c) {
        super(c);
        sheetW = Theme.dp(c, Theme.PICKER_W);
        pad = Theme.dp(c, Theme.PICKER_PAD);
        rowH = Theme.dp(c, Theme.PICKER_ROW_H);
        radius = Theme.dp(c, Theme.R_M);
        safeY = Theme.dp(c, Theme.SAFE_Y);
        eyebrowH = Theme.dp(c, 28);
        titleH = Theme.dp(c, 44);
        hintH = Theme.dp(c, 32);
        eyebrowP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        eyebrowP.setTypeface(Theme.BOLD);
        eyebrowP.setLetterSpacing(0.06f);
        titleP.setTextSize(Theme.sp(c, Theme.TEXT_TITLE));
        titleP.setTypeface(Theme.MEDIUM);
        rowP.setTextSize(Theme.sp(c, Theme.TEXT_BODY));
        rowP.setTypeface(Theme.REGULAR);
        valueP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        valueP.setTypeface(Theme.REGULAR);
        hintP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        hintP.setTypeface(Theme.REGULAR);
        scrim.setColor(0x99000000);
        setContentDescription("Alternativ");
    }

    // ---- Binding ------------------------------------------------------------------------

    public void setContent(String eyebrow, String title, List<Row> rows, int focus) {
        this.eyebrow = eyebrow == null ? "Alternativ" : eyebrow;
        this.title = title == null ? "" : title;
        this.rows = rows == null ? new ArrayList<>() : rows;
        this.focus = focus;
        clamp();
        setContentDescription(this.eyebrow);
        invalidate();
    }

    public void setFocus(int f) {
        focus = f;
        clamp();
        invalidate();
    }

    /** Runs after the picker closes (the focus returns to what opened it: the grid, the strip's pill). */
    public void setOnClose(Runnable r) {
        onClose = r;
    }

    public Runnable onClose() {
        return onClose;
    }

    /** The eyebrow as set last (harness: one sheet convention, Y7). */
    public String eyebrowForTest() {
        return eyebrow;
    }

    /** The eyebrow changes in place (the Nästa avsnitt countdown: text only, no re-layout). */
    public void setEyebrow(String e) {
        eyebrow = e == null ? "Alternativ" : e;
        invalidate();
    }

    public int focus() {
        return focus;
    }

    public int rowCount() {
        return rows.size();
    }

    public Row row(int i) {
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    /** The first row that can take the focus, or -1. */
    public int firstSelectable() {
        for (int i = 0; i < rows.size(); i++) {
            int k = rows.get(i).kind;
            if (k == Row.VALUE || k == Row.ACTION) return i;
        }
        return -1;
    }

    /** The next selectable row in direction {@code d}, clamped; -1 when there is none. */
    public int move(int d) {
        int i = focus;
        for (int n = 0; n < rows.size(); n++) {
            i += d;
            if (i < 0 || i >= rows.size()) break;
            int k = rows.get(i).kind;
            if (k == Row.VALUE || k == Row.ACTION) return i;
        }
        return focus >= 0 && focus < rows.size() ? focus : firstSelectable();
    }

    private void clamp() {
        int k = focus >= 0 && focus < rows.size() ? rows.get(focus).kind : -1;
        if (k != Row.VALUE && k != Row.ACTION) focus = firstSelectable();
    }

    /** The rects drawn last (sheet, rows, focus), for the geometry replay. */
    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    // ---- Layout -------------------------------------------------------------------------

    private int sheetHeight() {
        int h = pad + eyebrowH + titleH + Theme.dp(getContext(), Theme.SP_M) + hintH + pad;
        for (Row r : rows) h += r.kind == Row.HEADER ? Theme.dp(getContext(), 36) : r.kind == Row.NOTE ? Theme.dp(getContext(), 60) : rowH;
        return h;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? sheetW : MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? sheetHeight() + 2 * safeY
                : MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        drawn.clear();
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, scrim);
        int sw = Math.min(sheetW, w), sh = Math.min(sheetHeight(), h - 2 * safeY);
        float left = w - sw, top = Math.max(safeY, (h - sh) / 2f);
        rect.set(left, top, w, top + sh);
        fill.setColor(Theme.SURFACE);
        c.drawRoundRect(rect, radius, radius, fill);
        drawn.put("sheet", new RectF(rect));

        float x = left + pad, right = w - pad, y = top + pad;
        eyebrowP.setColor(Theme.TEXT_3);
        drawCut(c, eyebrow.toUpperCase(java.util.Locale.forLanguageTag("sv-SE")), eyebrowP, x, right, y + eyebrowH * 0.75f);
        y += eyebrowH;
        titleP.setColor(Theme.TEXT);
        drawCut(c, title, titleP, x, right, y + titleH * 0.7f);
        y += titleH + Theme.dp(getContext(), Theme.SP_M);

        Paint.FontMetrics rm = rowP.getFontMetrics(), vm = valueP.getFontMetrics();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind == Row.HEADER) {
                eyebrowP.setColor(Theme.TEXT_3);
                drawCut(c, r.label.toUpperCase(java.util.Locale.forLanguageTag("sv-SE")), eyebrowP, x, right,
                        y + Theme.dp(getContext(), 22));
                drawn.put("row" + i, new RectF(x, y, right, y + Theme.dp(getContext(), 36)));
                y += Theme.dp(getContext(), 36);
                continue;
            }
            if (r.kind == Row.NOTE) {
                rowP.setColor(Theme.TEXT_3);
                CharSequence t = TextUtils.ellipsize(r.label, rowP, right - x, TextUtils.TruncateAt.END);
                c.drawText(t, 0, t.length(), x, y + Theme.dp(getContext(), 20) - rm.ascent, rowP);
                drawn.put("row" + i, new RectF(x, y, right, y + Theme.dp(getContext(), 60)));
                y += Theme.dp(getContext(), 60);
                continue;
            }
            rect.set(x, y, right, y + rowH);
            boolean f = i == focus;
            if (f) {
                fill.setColor(Theme.FOCUS);
                c.drawRoundRect(rect, radius, radius, fill);
                drawn.put("focus", new RectF(rect));
            }
            drawn.put("row" + i, new RectF(rect));
            rowP.setColor(f ? Theme.ON_FOCUS : r.dim ? Theme.TEXT_3 : Theme.TEXT);
            drawCut(c, r.label, rowP, x + Theme.dp(getContext(), Theme.SP_M), right - Theme.dp(getContext(), 120),
                    rect.centerY() - (rm.ascent + rm.descent) / 2f);
            if (r.value != null) {
                valueP.setColor(f ? Theme.ON_FOCUS_2 : Theme.TEXT_2);
                CharSequence v = Ui.ellipsize(r.value, valueP, Theme.dp(getContext(), 110));
                float vw = valueP.measureText(v, 0, v.length());
                c.drawText(v, 0, v.length(), right - Theme.dp(getContext(), Theme.SP_M) - vw,
                        rect.centerY() - (vm.ascent + vm.descent) / 2f, valueP);
            }
            y += rowH;
        }
        hintP.setColor(Theme.TEXT_2);
        drawCut(c, "OK Välj   ← → Ändra   " + Theme.BACK + " Stäng", hintP, x, right, y + hintH * 0.75f);
        drawn.put("hint", new RectF(x, y, right, y + hintH));
    }

    private void drawCut(Canvas c, String s, TextPaint p, float l, float r, float b) {
        if (s == null || s.isEmpty()) return;
        CharSequence t = Ui.ellipsize(s, p, r - l);
        c.drawText(t, 0, t.length(), l, b, p);
    }
}
