package se.lite.play.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** Factory for the styled platform views of the design system. Every method is main-thread only. */
public final class Ui {
    private Ui() {}

    // ---- Primitives --------------------------------------------------------------------

    /**
     * Cuts {@code s} to fit {@code avail} px with a trailing "…". Used by every drawn text slot; it measures
     * with the paint itself, so it behaves the same on the TV and in the JVM screenshot harness.
     */
    public static CharSequence ellipsize(CharSequence s, android.graphics.Paint p, float avail) {
        if (s == null) return "";
        if (avail <= 0) return "";
        if (p.measureText(s, 0, s.length()) <= avail) return s;
        float ell = p.measureText("…");
        if (ell > avail) return "";
        int n = p.breakText(s, 0, s.length(), true, avail - ell, null);
        while (n > 0 && Character.isWhitespace(s.charAt(n - 1))) n--;
        return s.subSequence(0, n) + "…";
    }

    /** Rounded solid fill. */
    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** A TextView in one step of the type scale. */
    public static TextView text(Context c, float sp, Typeface tf, int color, CharSequence s) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setText(s);
        return t;
    }

    // ---- Detail (Surface 2's one level) --------------------------------------------------

    /** The Serie detail: a column over the video, the panel's width, full height. */
    public static LinearLayout detail(Context c) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(Theme.SCRIM_PANEL);
        p.setPadding(Theme.dp(c, Theme.SAFE_X - Theme.SP_L), Theme.dp(c, Theme.SAFE_Y),
                Theme.dp(c, Theme.SP_XL), Theme.dp(c, Theme.SAFE_Y));
        p.setVisibility(View.GONE);
        return p;
    }

    /** Params for the Serie detail: the panel's width at the left, over the video. */
    public static FrameLayout.LayoutParams detailParams(Context c) {
        return new FrameLayout.LayoutParams(Theme.panelWidth(c), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
    }

    // ---- Browser (Surface 2) and picker (Surface 3) -------------------------------------

    /** The chip's rectangle in px for a screen {@code w} wide: the top right corner, on the screen margin. */
    public static android.graphics.RectF pipRect(Context c, int w) {
        float right = w - Theme.dp(c, Theme.SAFE_X), top = Theme.dp(c, Theme.SAFE_Y);
        return new android.graphics.RectF(right - Theme.dp(c, Theme.PIP_W), top, right, top + Theme.dp(c, Theme.PIP_H));
    }

    /** Fills the view with {@code color} except {@code hole} (null: all of it): the picture behind shows through the hole. */
    public static void fillExcept(android.graphics.Canvas c, int w, int h, android.graphics.RectF hole, int color) {
        if (hole == null) {
            c.drawColor(color);
            return;
        }
        android.graphics.Paint p = new android.graphics.Paint();
        p.setColor(color);
        c.drawRect(0, 0, hole.left, h, p);
        c.drawRect(hole.right, 0, w, h, p);
        c.drawRect(hole.left, 0, hole.right, hole.top, p);
        c.drawRect(hole.left, hole.bottom, hole.right, h, p);
    }

    /** The browser's tab row ("Tablå", "Katalog & Sök") across the top. */
    public static FrameLayout.LayoutParams tabsParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
    }

    /** The catalogue lens: the whole area below the tab row, so its shelves scroll above the field. */
    public static FrameLayout.LayoutParams catalogueParams(Context c) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP);
        p.topMargin = Theme.dp(c, Theme.TAB_H);
        return p;
    }

    /**
     * The in-app keyboard: a narrow column at the left, under the search field, which heads the same column
     * (the catalogue's search layout; the results fill the rest).
     */
    public static FrameLayout.LayoutParams keyboardParams(Context c) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Theme.dp(c, 6 * Theme.KEY_W),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        p.leftMargin = Theme.dp(c, Theme.SAFE_X);
        p.topMargin = Theme.dp(c, Theme.TAB_H + Theme.SP_S + Theme.FIELD_H + Theme.SP_S);
        return p;
    }

    /** The picker covers the whole screen: it draws its own scrim and the right-aligned sheet. */
    public static FrameLayout.LayoutParams pickerParams() {
        return fullScreen();
    }

    /**
     * Panel heading. Rows inset text by 16 dp; the title lines up with them. One line, ellipsized: the
     * parent goes in the eyebrow above it ("BONDE SÖKER FRU" over "Säsong 18"), never appended to the
     * title, so titles stay short.
     */
    public static TextView panelTitle(Context c) {
        TextView t = text(c, Theme.TEXT_TITLE, Theme.MEDIUM, Theme.TEXT, "");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        // Fixed height: the logo plate (Tablå, Alternativ) fits without changing the header height.
        t.setMinHeight(Theme.dp(c, Theme.HEADER_LOGO_H));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setCompoundDrawablePadding(Theme.dp(c, Theme.SP_M));
        int x = Theme.dp(c, Theme.SP_L);
        t.setPadding(x, 0, x, 0);
        return t;
    }

    /** Eyebrow above the panel title: the parent surface in caption caps, e.g. "TELIA LITE", "BONDE SÖKER FRU". */
    public static TextView panelEyebrow(Context c) {
        TextView t = text(c, Theme.TEXT_CAPTION, Theme.BOLD, Theme.TEXT_2, "");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLetterSpacing(0.06f);
        t.setAllCaps(true);
        int x = Theme.dp(c, Theme.SP_L);
        t.setPadding(x, 0, x, Theme.dp(c, Theme.SP_XS));
        return t;
    }

    /** Sets eyebrow (optional) and title. The eyebrow line is always reserved, so the header never changes height. */
    public static void setHeader(TextView eyebrow, TextView title, CharSequence eb, CharSequence t) {
        boolean has = eb != null && eb.length() > 0;
        eyebrow.setText(has ? eb : " ");
        eyebrow.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        title.setText(t);
        int x = Theme.dp(title.getContext(), Theme.SP_L);
        title.setPadding(x, 0, x, Theme.dp(title.getContext(), Theme.SP_L));
    }

    /**
     * Key hints for the footer: pairs of key and action, e.g. hints("OK", "Titta", Theme.BACK, "Meny").
     * The key is bold TEXT, the action TEXT_2; pairs are separated by wide gaps. The Back key is always
     * {@link Theme#BACK} ("↩", the remote's Back arrow); "←" and "→" are the D-pad keys themselves.
     */
    public static CharSequence hints(String... pairs) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (b.length() > 0) b.append("     ");
            if (pairs[i].isEmpty()) { // an explanation without a key: "Går inte att se i efterhand"
                b.append(pairs[i + 1]);
                continue;
            }
            int start = b.length();
            b.append(pairs[i]);
            b.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, b.length(), 0);
            b.setSpan(new android.text.style.ForegroundColorSpan(Theme.TEXT), start, b.length(), 0);
            if (!pairs[i + 1].isEmpty()) b.append("  ").append(pairs[i + 1]);
        }
        return b;
    }

    /** Key hint footer built with {@link #hints}, e.g. "OK Titta   › Tablå   ↩ Meny". */
    public static TextView panelHint(Context c) {
        TextView t = text(c, Theme.TEXT_CAPTION, Theme.REGULAR, Theme.TEXT_2, ""); // B22: readable at 3 m
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTag("hint"); // the screenshot harness reads it (C3)
        int x = Theme.dp(c, Theme.SP_L);
        t.setPadding(x, Theme.dp(c, Theme.SP_M), x, 0);
        return t;
    }

    /** Styles a ListView for RowViews: no selector drawable (rows draw focus), no divider, no fading. */
    public static ListView styleList(ListView l) {
        l.setDivider(null);
        l.setDividerHeight(0);
        l.setSelector(new ColorDrawable(0));
        l.setDrawSelectorOnTop(false);
        l.setCacheColorHint(0);
        l.setItemsCanFocus(false);
        l.setVerticalScrollBarEnabled(false);
        l.setVerticalFadingEdgeEnabled(false);
        l.setOverScrollMode(View.OVER_SCROLL_NEVER);
        l.setChoiceMode(AbsListView.CHOICE_MODE_NONE);
        return l;
    }

    /** The panel list: whole rows at the top, a fade at the bottom (see {@link SnapList}). */
    public static ListView list(Context c) {
        ListView l = styleList(new SnapList(c));
        l.setVerticalFadingEdgeEnabled(true);
        return l;
    }

    /** Layout params that let the list take the remaining panel height (one weight, no nesting). */
    public static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** A bare row, for use outside an adapter. In a ListView use {@link RowAdapter}. */
    public static RowView row(Context c) {
        return new RowView(c);
    }

    public static <T> RowAdapter<T> adapter(java.util.List<T> items, RowAdapter.Binder<T> binder) {
        return new RowAdapter<>(items, binder);
    }

    // ---- Search ------------------------------------------------------------------------

    /**
     * The search query display: looks like a field, but it is a TextView and never summons the system
     * keyboard (Google TV sends that to the phone). The in-app {@link KeyboardView} types into it.
     * It never draws the focus style (one focus look on screen at a time, VISUAL-QA C2): 1 dp hairline
     * on SURFACE, raised to SURFACE_RAISED while the keyboard types into it, caret in ACCENT_ON_DARK.
     */
    public static TextView searchField(Context c) {
        TextView t = text(c, Theme.TEXT_BODY, Theme.REGULAR, Theme.TEXT, "");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.START); // the end of what you type stays visible
        t.setHintTextColor(Theme.TEXT_3);
        t.setHint("Program, serier, filmer, kanaler");
        int ph = Theme.dp(c, Theme.SP_L), pv = Theme.dp(c, Theme.SP_M);
        t.setPadding(ph, pv, ph, pv);
        t.setFocusable(false);
        setSearchActive(t, false);
        return t;
    }

    public static void setSearchActive(TextView field, boolean active) {
        Context c = field.getContext();
        GradientDrawable d = rounded(active ? Theme.SURFACE_RAISED : Theme.SURFACE, Theme.dp(c, Theme.R_M));
        d.setStroke(Theme.dp(c, 1), Theme.HAIRLINE);
        field.setBackground(d);
    }

    /** Params for the query display: full width of the keyboard column, gap below. */
    public static LinearLayout.LayoutParams searchParams(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Theme.dp(c, Theme.SP_M);
        return p;
    }

    public static KeyboardView keyboard(Context c) {
        return new KeyboardView(c);
    }

    // ---- Overlays ----------------------------------------------------------------------

    public static OsdView osd(Context c) {
        return new OsdView(c);
    }

    /** OSD across the bottom of the screen. */
    public static FrameLayout.LayoutParams osdParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    }

    public static LoginView login(Context c) {
        return new LoginView(c);
    }

    public static FrameLayout.LayoutParams fullScreen() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static Snack snack(Context c) {
        return new Snack(c);
    }

    /** Snack in the top-right safe corner. */
    public static FrameLayout.LayoutParams snackParams(Context c) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        p.topMargin = Theme.dp(c, Theme.SAFE_Y);
        p.rightMargin = Theme.dp(c, Theme.SAFE_X);
        return p;
    }
}
