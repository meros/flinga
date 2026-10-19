package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.List;

/**
 * Subtitles, drawn by the app (G5; no media3-ui): the text cues of the player in one box, centred at the
 * bottom. While the player overlay is up the box sits above the overlay's top edge, so a caption is never
 * under the bar or the pills (the overlay wins the space, the caption moves). Picture cues are not drawn.
 */
public final class CaptionView extends View {
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int gap, pad, bottomMargin;
    private CaptionLook look = CaptionLook.defaults();
    private CharSequence cue = "";
    private View avoid;
    private java.util.function.IntSupplier avoidInset = () -> 0;
    private int lastBottom = -1;
    private StaticLayout layout;
    private final ViewTreeObserver.OnPreDrawListener follow = () -> {
        if (bottom() != lastBottom) invalidate();
        return true;
    };

    public CaptionView(Context c) {
        super(c);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        gap = Theme.dp(c, Theme.SP_L);
        pad = Theme.dp(c, Theme.SP_M);
        bottomMargin = Theme.dp(c, Theme.SAFE_Y);
        setVisibility(GONE);
    }

    /**
     * The view whose content the caption stays above while that view is visible (the overlay); {@code inset} is how far
     * below the view's own top its content starts (the overlay's top is a gradient lead-in).
     */
    public void avoid(View v, java.util.function.IntSupplier inset) {
        avoid = v;
        avoidInset = inset;
    }

    public void setLook(CaptionLook l) {
        look = l;
        layout = null;
        invalidate();
    }

    /** The caption now on screen; empty hides it. */
    public void setCue(CharSequence c) {
        cue = c == null ? "" : c;
        layout = null;
        setVisibility(cue.length() == 0 ? GONE : VISIBLE);
        invalidate();
    }

    public CharSequence cue() {
        return cue;
    }

    /** Y of the caption box's bottom edge: above the overlay's content while it is up, else a safe margin from the screen's bottom. */
    public int bottom() {
        int h = getHeight();
        if (avoid != null && avoid.getVisibility() == VISIBLE && avoid.getHeight() > 0) return Math.min(h - bottomMargin, avoid.getTop() + avoidInset.getAsInt() - gap);
        return h - bottomMargin;
    }

    /** Where the box is drawn, for tests; null when nothing shows. */
    public RectF boxRect() {
        build();
        return layout == null ? null : new RectF(rect);
    }

    private void build() {
        if (cue.length() == 0 || getWidth() == 0) {
            layout = null;
            return;
        }
        text.setTextSize(Math.max(12f, getHeight() * look.size));
        text.setColor(look.foreground);
        text.setTypeface(look.typeface);
        int w = (int) (getWidth() * 0.8f);
        layout = StaticLayout.Builder.obtain(cue, 0, cue.length(), text, w).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        float tw = 0;
        for (int i = 0; i < layout.getLineCount(); i++) tw = Math.max(tw, layout.getLineWidth(i));
        float bw = tw + 2 * pad, bh = layout.getHeight() + pad;
        float left = (getWidth() - bw) / 2f;
        lastBottom = bottom();
        rect.set(left, lastBottom - bh, left + bw, lastBottom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layout = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(follow);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(follow);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (layout == null || lastBottom != bottom()) build();
        if (layout == null) return;
        box.setColor(look.background);
        float r = Theme.dp(getContext(), Theme.R_S);
        canvas.drawRoundRect(rect, r, r, box);
        canvas.save();
        canvas.translate(rect.centerX() - layout.getWidth() / 2f, rect.top + pad / 2f); // the lines are centred in the layout's width
        if (look.edge == CaptionLook.EDGE_OUTLINE) {
            text.setStyle(Paint.Style.STROKE);
            text.setStrokeWidth(Math.max(2f, text.getTextSize() / 12f));
            text.setColor(look.edgeColor);
            layout.draw(canvas);
            text.setStyle(Paint.Style.FILL);
            text.setColor(look.foreground);
        } else if (look.edge == CaptionLook.EDGE_SHADOW) {
            text.setShadowLayer(text.getTextSize() / 12f, 2f, 2f, look.edgeColor);
        }
        layout.draw(canvas);
        text.clearShadowLayer();
        canvas.restore();
    }
}
