package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The browser's two lenses (Surface 2): one row of tabs at the top, {@link Theme#TAB_H} high.
 *
 * <pre>
 *  [ Tablå ]  Katalog & Sök
 * </pre>
 *
 * The active lens is drawn with the focus fill while the tab row has the focus, and raised (not the focus
 * look) while the focus is in the lens below it, so the active lens never reads as focused by accident.
 * ← → change the lens in place and keep the focus here; ↓ enters the lens; Back from the lens comes here
 * first (the one-press way up from any row). Drawn on one View, nothing allocated per frame.
 */
public final class TabsView extends View {
    public static final int TABLA = 0, KATALOG = 1;
    private static final String[] LABELS = {"Tablå", "Katalog & Sök"};

    private final TextPaint labelP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Map<String, RectF> drawn = new LinkedHashMap<>();
    private final int h, pad, radius, gap;
    private int active = TABLA;
    private Runnable onLens;

    public TabsView(Context c) {
        super(c);
        h = Theme.dp(c, Theme.TAB_H);
        pad = Theme.dp(c, Theme.TAB_PAD);
        gap = Theme.dp(c, Theme.SP_S);
        radius = Theme.dp(c, Theme.R_M);
        labelP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        labelP.setTypeface(Theme.MEDIUM);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Theme.dp(c, 1));
        setFocusable(true); // ↑ from the top shelf reaches it; ↓ returns to the shelves
        setContentDescription("Tablå");
    }

    /** The D-pad moves between the lenses in place (← Tablå, → Katalog & Sök), as the key map says. */
    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
            setActive(TABLA);
            return true;
        }
        if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
            setActive(KATALOG);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /** The lens changed: the browser shows the other one (the activity owns the two views). */
    public void setOnLensChange(Runnable r) {
        onLens = r;
    }

    public void setActive(int lens) {
        int a = lens == KATALOG ? KATALOG : TABLA;
        if (active != a) {
            active = a;
            setContentDescription(LABELS[a]);
            invalidate();
            if (onLens != null) onLens.run();
        }
    }

    public int active() {
        return active;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    /** The rects drawn last (the two tabs), for the geometry replay. */
    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    private RectF pip;

    /** The chip's hole in the strip's background (px, in this view's coordinates), or null while no picture plays. */
    public void setPip(RectF r) {
        pip = r == null ? null : new RectF(r);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        drawn.clear();
        Ui.fillExcept(c, getWidth(), getHeight(), pip, Theme.BG);
        if (pip != null) { // a hairline round the picture
            stroke.setColor(Theme.HAIRLINE);
            c.drawRect(pip.left - stroke.getStrokeWidth(), pip.top - stroke.getStrokeWidth(), pip.right + stroke.getStrokeWidth(), pip.bottom + stroke.getStrokeWidth(), stroke);
        }
        float x = Theme.dp(getContext(), Theme.SAFE_X);
        Paint.FontMetrics fm = labelP.getFontMetrics();
        for (int i = 0; i < LABELS.length; i++) {
            float w = labelP.measureText(LABELS[i]) + 2 * pad;
            rect.set(x, Theme.dp(getContext(), Theme.SAFE_Y), x + w, Theme.dp(getContext(), Theme.SAFE_Y + Theme.TAB_PILL_H));
            boolean on = i == active;
            if (on && isFocused()) { // the lens has the focus: the focus look (the focus is unmistakable)
                fill.setColor(Theme.FOCUS);
                c.drawRoundRect(rect, radius, radius, fill);
                labelP.setColor(Theme.ON_FOCUS);
            } else if (on) { // the lens is shown but the focus is elsewhere: raised, not the focus look
                fill.setColor(Theme.SURFACE_RAISED);
                c.drawRoundRect(rect, radius, radius, fill);
                labelP.setColor(Theme.TEXT);
            } else {
                stroke.setColor(Theme.HAIRLINE);
                c.drawRoundRect(rect, radius, radius, stroke);
                labelP.setColor(Theme.TEXT_2);
            }
            c.drawText(LABELS[i], x + pad, rect.centerY() - (fm.ascent + fm.descent) / 2f, labelP);
            drawn.put(i == TABLA ? "tabla" : "katalog", new RectF(rect));
            x += w + gap;
        }
    }
}
