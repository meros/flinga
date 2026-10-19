package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;

/**
 * The panel list: the focused row clear of the fades, whole rows at the top, fades at both ends, and a
 * sticky group eyebrow in the top band (VISUAL-QA C1, R3; REVIEW-R2 Y1, Y2; REVIEW-R3 M-a, M-b).
 *
 * ListView scrolls by whatever brings the selected row into view, so the row lands at the very edge,
 * under the fade, and the first row ends up cut under the header. This list corrects both after every
 * layout and after every key scroll, in this order: (1) the selected row, and the group eyebrow right
 * above it, are moved out of the fade zones (a fade is drawn only when there is more content on that
 * side); (2) a partly visible first row is either shown whole (when the selected row still fits above
 * the bottom fade) or scrolled out. At the very end of the list neither is possible (the content
 * cannot move up any further), so the top edge fades over 40 dp like the bottom edge does, and a row
 * cut there reads as "more above", never as a broken row.
 *
 * The top band: while rows are scrolled out above, the top 40 dp is the band. Its upper part is erased
 * whole and, in a grouped list (day eyebrows in Tablå, result groups in Sök), carries the eyebrow of the
 * first visible row's group, drawn where a header row would stand; its lower part is a fade through
 * which that first row shows, cut through its text, the mirror of the bottom end (REVIEW-R4 B2: every
 * list, grouped or not). So the scrolled state is visibly different from the top of the list (a row
 * slides out under the band) and no row is ever orphaned from its group. When only the list's first
 * header is scrolled out, the list goes back to its top instead (B5). The band is the fade's own room,
 * so nothing moves (E6). No per-frame work: a layout snaps in place; a key scroll posts one snap.
 */
public class SnapList extends ListView {
    private boolean snapping, pending;
    private final Runnable later = () -> {
        pending = false;
        snap();
    };

    /** Names the group of a position (its eyebrow's text and count), or null when the position has no group. */
    public interface Groups {
        CharSequence groupOf(int position);

        /** The group eyebrow's right-hand meta ("8"), or null. */
        default CharSequence metaOf(int position) {
            return null;
        }

        /** True when the item at {@code position} is a group header row. */
        default boolean headerAt(int position) {
            return false;
        }
    }

    private Groups groups;
    private final TextPaint stickyP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence sticky;
    private final Paint fadeP = new Paint();
    private final Matrix fadeM = new Matrix();
    private LinearGradient fadeG, bandG;
    /** The band's solid part (the eyebrow's ground); the rest of the band fades the cut row. */
    private final int bandSolid, cutDepth;

    public SnapList(Context c) {
        super(c);
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength(Theme.dp(c, Theme.LIST_FADE));
        bandSolid = Theme.dp(c, Theme.LIST_FADE - 16);
        cutDepth = Theme.dp(c, 2); // the cut row's text starts below the band's solid part: faded, never clipped (A3)
        stickyP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        stickyP.setTypeface(Theme.BOLD);
        stickyP.setLetterSpacing(0.06f);
        stickyP.setFontFeatureSettings("tnum");
        stickyP.setColor(Theme.TEXT_3);
        fadeP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** The group source; null answers for every position of a list without groups (Kanaler, Meny). */
    public void setGroups(Groups groups) {
        this.groups = groups;
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        snap();
    }

    /** D-pad scrolling happens inside ListView's key handling; snap right after it. */
    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        boolean handled = super.onKeyDown(code, e);
        if (handled && !pending) {
            pending = true;
            post(later);
        }
        return handled;
    }

    /** True when rows are scrolled out above the list's top edge. */
    private boolean contentAbove() {
        return getChildCount() > 0 && (getFirstVisiblePosition() > 0 || getChildAt(0).getTop() < 0);
    }

    /** The height of the band in px: the fade length while rows are scrolled out above, else 0. */
    public int band() {
        return contentAbove() ? getVerticalFadingEdgeLength() : 0;
    }

    /** Where the first whole row starts while a row of {@code rowAboveHeightPx} is the band's cut row (callers that pin a row). */
    public int firstWholeTop(int rowAboveHeightPx) {
        return getVerticalFadingEdgeLength() + rowAboveHeightPx / 2 - cutDepth;
    }

    /** The first child that shows at all (ListView can keep one just above the edge, bottom at 0). */
    private View firstVisible() {
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getBottom() > 0) return getChildAt(i);
        return null;
    }

    /** The row cut by the band in a scrolled grouped list: the first child whose bottom is below the band, when its top is above it; else null. */
    public View cutRow() {
        int band = band();
        if (band == 0) return null;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getBottom() > band) return v.getTop() < band ? v : null;
        }
        return null;
    }

    /** Where a row's bottom must be for the band to cut it through its text: its text centre 6 dp above the band's edge. */
    private int cutBottom(View v, int band) {
        return band + v.getHeight() / 2 - cutDepth;
    }

    /**
     * True when the first visible row starts above the list's top edge and is not the band's designed cut.
     * Under the band, a child that ends inside the band's solid part is erased whole and does not count.
     */
    public boolean firstRowCut() {
        int band = band();
        if (band == 0) {
            View first = firstVisible();
            return first != null && first.getTop() < 0;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getBottom() <= bandSolid) continue;
            return v.getTop() < band && Math.abs(v.getBottom() - cutBottom(v, band)) > 1;
        }
        return false;
    }

    /** True when the content cannot scroll any further up: the last row's bottom is inside the list. */
    public boolean atEnd() {
        int n = getChildCount();
        return n > 0 && getLastVisiblePosition() == getCount() - 1 && getChildAt(n - 1).getBottom() <= getHeight();
    }

    /** What the framework would draw at the top edge (tests). */
    public float topFadeStrength() {
        return super.getTopFadingEdgeStrength();
    }

    /**
     * Both fades are drawn here, not by the framework: on this build the framework's top edge stays
     * invisible when the list is scrolled by whole rows (first position > 0), while its bottom edge draws,
     * and the two must look alike. Each is a LIST_FADE strip in which the rows are erased toward the edge
     * (DST_OUT, like the framework's), so the panel shows through and a row cut there reads as "more
     * above/below", never as a broken row (C1, M7). The band of a grouped list is erased whole in its
     * upper part (the eyebrow's ground) and fades the cut row in its lower part. The rows are drawn once
     * outside the strips and once more inside each strip's layer: three passes at most, only when the
     * list redraws (a key press). The framework edges stay enabled (their length is the strip), with no strength.
     */
    @Override
    protected float getTopFadingEdgeStrength() {
        return 0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return 0f;
    }

    /** The sticky eyebrow drawn last, or null (harness check). */
    public CharSequence stickyShown() {
        return sticky;
    }

    /** The position of the cut row, or -1. */
    private int cutPosition() {
        View cut = cutRow();
        return cut == null ? -1 : getFirstVisiblePosition() + indexOfChild(cut);
    }

    /** The group the band should name right now, or null: the group of the row it cuts (the first visible row). */
    public CharSequence stickyWanted() {
        int pos = cutPosition();
        return pos < 0 || groups == null ? null : groups.groupOf(pos);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        boolean top = topFaded(), bottom = bottomFaded();
        sticky = null;
        if (!top && !bottom) {
            super.dispatchDraw(c);
            return;
        }
        int fade = getVerticalFadingEdgeLength(), w = getWidth(), h = getHeight(), y = getScrollY();
        if (fadeG == null) {
            fadeG = new LinearGradient(0, 0, 0, 1, 0xFF000000, 0, Shader.TileMode.CLAMP);
            bandG = new LinearGradient(0, 0, 0, 1, new int[] {0xFF000000, 0xFF000000, 0},
                    new float[] {0f, bandSolid / (float) fade, 1f}, Shader.TileMode.CLAMP);
        }
        int cutPos = top ? cutPosition() : -1;
        CharSequence group = cutPos < 0 || groups == null ? null : groups.groupOf(cutPos);
        int sc = c.save();
        if (top) clipOut(c, 0, y, w, y + fade);
        if (bottom) clipOut(c, 0, y + h - fade, w, y + h);
        super.dispatchDraw(c);
        c.restoreToCount(sc);
        if (top) strip(c, 0, y, w, y + fade, fade, y, bandG); // the band: solid above, the cut row fading in below
        if (bottom) strip(c, 0, y + h - fade, w, y + h, -fade, y + h, fadeG);
        if (group != null) {
            int padH = Theme.dp(getContext(), Theme.SP_L);
            float base = y + bandSolid - Theme.dp(getContext(), Theme.SP_XS) - stickyP.getFontMetrics().descent;
            float right = w - padH;
            CharSequence meta = groups.metaOf(cutPos);
            if (meta != null && meta.length() > 0) { // the group's count, as its header row draws it
                CharSequence m = Ui.ellipsize(meta, stickyP, Theme.dp(getContext(), Theme.ROW_META_W));
                float mw = stickyP.measureText(m, 0, m.length());
                c.drawText(m, 0, m.length(), right - mw, base, stickyP);
                right -= Theme.dp(getContext(), Theme.ROW_META_W + Theme.SP_M);
            }
            CharSequence t = Ui.ellipsize(group, stickyP, Math.max(0, right - padH));
            c.drawText(t, 0, t.length(), padH, base, stickyP);
            sticky = group;
        }
    }

    @SuppressWarnings("deprecation")
    private static void clipOut(Canvas c, int l, int t, int r, int b) {
        if (android.os.Build.VERSION.SDK_INT >= 26) c.clipOutRect(l, t, r, b);
        else c.clipRect(l, t, r, b, android.graphics.Region.Op.DIFFERENCE);
    }

    /** Draws the rows inside one strip and erases them with {@code g} mapped from the edge at {@code origin} over {@code scale} px. */
    private void strip(Canvas c, int l, int t, int r, int b, int scale, int origin, Shader g) {
        int sc = c.saveLayer(l, t, r, b, null);
        super.dispatchDraw(c);
        fadeM.setScale(1, scale);
        fadeM.postTranslate(0, origin);
        g.setLocalMatrix(fadeM);
        fadeP.setShader(g); // set after the matrix, so every API level picks it up
        c.drawRect(l, t, r, b, fadeP);
        c.restoreToCount(sc);
    }

    /** True when a fade is drawn at the top: there is content above the first visible row. */
    public boolean topFaded() {
        return contentAbove();
    }

    /** True when a fade is drawn at the bottom: there is content below the last visible row. */
    public boolean bottomFaded() {
        int n = getChildCount();
        return n > 0 && (getLastVisiblePosition() < getCount() - 1 || getChildAt(n - 1).getBottom() > getHeight());
    }

    /** The top of the selected row's block: its group eyebrow when one sits right above it (R3). */
    public int selectionTop() {
        View sel = getSelectedView();
        if (sel == null) return 0;
        int i = indexOfChild(sel);
        if (i > 0 && getChildAt(i - 1) instanceof RowView && ((RowView) getChildAt(i - 1)).isHeader()) {
            return getChildAt(i - 1).getTop();
        }
        return sel.getTop();
    }

    /** Snaps now (tests); true when a cut row was put right. */
    public boolean snapNow() {
        boolean cut = firstRowCut();
        snap();
        return cut && !firstRowCut();
    }

    private void snap() {
        if (snapping) return;
        snapping = true;
        try {
            for (int pass = 0; pass < 3 && snapOnce(); pass++) { /* each pass moves the content once */ }
        } finally {
            snapping = false;
        }
    }

    /** True when what is scrolled out above is only the list's first row and it is a header. */
    private boolean onlyFirstHeaderAbove() {
        if (groups == null || getChildCount() == 0) return false;
        int first = getFirstVisiblePosition();
        View c0 = getChildAt(0);
        if (first == 0) return c0.getTop() < 0 && isHeader(c0);
        return first == 1 && groups.headerAt(0) && c0.getTop() <= 0;
    }

    private static boolean isHeader(View v) {
        return v instanceof RowView && ((RowView) v).isHeader();
    }

    /** One correction; true when the content moved. scrollListBy: positive moves the content up. */
    private boolean snapOnce() {
        View first = firstVisible();
        if (first == null) return false;
        int h = getHeight(), fade = getVerticalFadingEdgeLength();
        View sel = getSelectedView();
        boolean bottomFade = bottomFaded();
        if (sel != null) {
            int top = selectionTop();
            if (topFaded() && top < fade) {
                scrollListBy(top - fade);
                return true;
            }
            if (bottomFade && sel.getBottom() > h - fade) {
                scrollListBy(sel.getBottom() - (h - fade));
                return true;
            }
        }
        int limit = h - (bottomFade ? fade : 0);
        int band = band();
        if (band > 0 && onlyFirstHeaderAbove()) { // B5: show the header instead of cutting its first row
            View c0 = getChildAt(0);
            int delta = getFirstVisiblePosition() == 0 ? c0.getTop() : c0.getTop() - Theme.dp(getContext(), Theme.ROW_H_HEADER_FIRST);
            if (delta < 0 && (sel == null || sel.getBottom() - delta <= limit)) {
                scrollListBy(delta);
                return true;
            }
        }
        if (band > 0) return snapBand(band, sel, limit);
        int top = first.getTop();
        if (top >= 0) return false;
        // Reveal the cut row when the selected row still fits above the bottom fade; else hide it.
        boolean reveal = sel == null || sel == first || sel.getBottom() - top <= limit;
        if (!reveal && atEnd()) return false; // nothing to hide behind: the top fade takes it
        scrollListBy(reveal ? top : first.getBottom());
        return true;
    }

    /**
     * The band's cut: the row above the first whole row ends at {@link #cutBottom}, so its text slides out
     * under the eyebrow (M-a). A header is never the cut row (its own caption would double the band's):
     * the row before it is cut instead. The move never pushes the selection into a fade zone; when the
     * nearer cut would, the next row is cut instead; when neither fits, the list stays as it is.
     */
    private boolean snapBand(int band, View sel, int limit) {
        int i = 0;
        while (i < getChildCount() && getChildAt(i).getBottom() <= band) i++;
        if (i >= getChildCount()) return false;
        View c = getChildAt(i);
        // The candidate for the cut: c when it already straddles the band, else the row above it.
        int cutIndex = c.getTop() < band ? i : i - 1;
        if (cutIndex >= 0 && isHeader(getChildAt(cutIndex))) cutIndex--;
        for (int attempt = 0; attempt < 2; attempt++) {
            int delta;
            if (cutIndex < 0) { // the row above is not laid out: bring it in, taking c's height as its measure
                if (getFirstVisiblePosition() == 0 && c == getChildAt(0)) return false; // nothing above to cut
                delta = c.getTop() - cutBottom(c, band);
            } else {
                View cut = getChildAt(cutIndex);
                if (cut == sel) return false; // never cut the selection
                delta = cut.getBottom() - cutBottom(cut, band);
            }
            if (Math.abs(delta) <= 1) return false;
            boolean ok = sel == null || (delta < 0 ? sel.getBottom() - delta <= limit : sel.getTop() - delta >= band);
            if (ok) {
                if (delta > 0 && atEnd()) return false; // cannot move the content up any further
                scrollListBy(delta);
                return true;
            }
            cutIndex++; // the nearer cut would push the selection into a fade: cut the next row instead
            if (cutIndex >= getChildCount() || isHeader(getChildAt(cutIndex))) cutIndex++;
            if (cutIndex >= getChildCount()) return false;
        }
        return false;
    }
}
