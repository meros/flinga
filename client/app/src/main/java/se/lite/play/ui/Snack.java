package se.lite.play.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Toast / snackbar: a small card in the top-right safe corner with a status dot.
 * Top-right so it never collides with the OSD (bottom) or the panel (left); it sits below the
 * safe corner.
 */
public class Snack extends TextView {
    public static final int INFO = 0, SUCCESS = 1, ERROR = 2;

    private final GradientDrawable dot = new GradientDrawable();
    private final Runnable hide = () -> setVisibility(GONE);

    public Snack(Context c) {
        super(c);
        int r = Theme.dp(c, Theme.R_L);
        GradientDrawable bg = Ui.rounded(Theme.SURFACE_RAISED, r);
        bg.setStroke(Theme.dp(c, 1), Theme.HAIRLINE);
        setBackground(bg);
        int ph = Theme.dp(c, Theme.SP_L), pv = Theme.dp(c, Theme.SP_M);
        setPadding(ph, pv, Theme.dp(c, Theme.SP_XL), pv);
        setTextColor(Theme.TEXT);
        setTextSize(Theme.TEXT_BODY);
        setTypeface(Theme.REGULAR);
        setIncludeFontPadding(false);
        // Fixed size (DESIGN.md, stable geometry): the text changes in place, the card never resizes; a
        // one-line text is centred in the two-line box (CENTER_VERTICAL below; SnackTest measures it, Y9).
        setLines(2);
        setEllipsize(TextUtils.TruncateAt.END);
        setWidth(Theme.panelWidth(c));
        setGravity(Gravity.CENTER_VERTICAL);
        int d = Theme.dp(c, 10);
        dot.setShape(GradientDrawable.OVAL);
        dot.setSize(d, d);
        dot.setBounds(0, 0, d, d);
        setCompoundDrawables(dot, null, null, null);
        setCompoundDrawablePadding(Theme.dp(c, Theme.SP_M));
        setVisibility(GONE);
    }

    /** Shows a message; hides after {@code ms}, or stays until {@link #dismiss()} when 0. */
    public void show(CharSequence text, int kind, int ms) {
        removeCallbacks(hide);
        dot.setColor(kind == ERROR ? Theme.ERROR : kind == SUCCESS ? Theme.SUCCESS : Theme.ACCENT_ON_DARK);
        setText(text);
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            if (Theme.animations) {
                setAlpha(0f);
                setTranslationY(-Theme.dp(getContext(), Theme.SP_S));
                animate().alpha(1f).translationY(0f).setDuration(Theme.ANIM_MS).start();
            }
        }
        if (ms > 0) postDelayed(hide, ms);
    }

    public void dismiss() {
        removeCallbacks(hide);
        setVisibility(GONE);
    }
}
