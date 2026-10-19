package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The catalogue lens (Surface 2, "Katalog & Sök"): the search field and shelves of fixed 16:9 cards.
 *
 * <pre>
 *  Browse (keyboard closed)                 Search (keyboard open)
 *  [ Sök serier, filmer, program ... ]      [ query      ]  Serier
 *  Fortsätt titta                           [ a b c d e f]  [card][card][card]
 *  [card] [card] [card] [card]              [ g h i j k l]  [card][card][card]
 *  Just nu                                  [ ...        ]  Filmer
 *  [card] [card] [card] [card]                              [card][card][card]
 * </pre>
 *
 * Geometry is fixed (E6): every card of a layout has one size, every shelf row one stride, and a title is
 * ellipsized inside its reserved lines. In the search layout the keyboard is a narrow column at the left
 * (the activity places its View under the field) and the cards are smaller, so about nine results show at
 * once. The D-pad moves by geometry ({@link #move}): ↑ ↓ between rows and shelves, ← → along a row; an edge
 * is reported to the activity, which owns the neighbouring surfaces. The view only draws: the activity owns
 * the rows, the keys and the images, which arrive by key ({@link #setImages}).
 */
public final class CatalogueView extends View {
    /** One poster: a title, a secondary line and the image key (null: the placeholder). */
    public static final class Card {
        public final String title, sub, imageKey;
        public final String key;
        public final float progress;

        public Card(String key, String title, String sub, String imageKey, float progress) {
            this.key = key;
            this.title = title;
            this.sub = sub;
            this.imageKey = imageKey;
            this.progress = progress;
        }
    }

    /** One shelf: an eyebrow and its cards. */
    public static final class Shelf {
        public final String title;
        public final java.util.List<Card> cards;

        public Shelf(String title, java.util.List<Card> cards) {
            this.title = title;
            this.cards = cards;
        }
    }

    private final TextPaint shelfP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint fieldP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final RectF dst = new RectF();
    private final Map<String, RectF> drawn = new LinkedHashMap<>();
    private final int bigW, bigH, bigCap, smallW, smallH, smallCap, gap, shelfGap, radius, pad, fieldH, safeX, safeY;

    private java.util.List<Shelf> shelves = new java.util.ArrayList<>();
    private String query = "";
    private CharSequence hint = "";
    private boolean fieldFocused, keyboardFocused;
    /** The focus: a shelf index and a card index inside it; -1 = the search field. */
    private int shelf = 0, card = 0;
    /** The search layout: the keyboard's column at the left, the results as compact cards at the right. */
    private boolean searchLayout;
    /** The shelves' scroll offset, so the focused row stays whole in the content area. */
    private int scrollY;
    /** Browse: each shelf is one row that scrolls sideways; this is how far each has. Search: unused (a wrapping grid). */
    private int[] shelfScrollX = new int[0];

    public CatalogueView(Context c) {
        super(c);
        bigW = Theme.dp(c, Theme.POSTER_W);
        bigH = Theme.dp(c, Theme.POSTER_H);
        bigCap = Theme.dp(c, Theme.POSTER_CAPTION);
        smallW = Theme.dp(c, Theme.SEARCH_POSTER_W);
        smallH = Theme.dp(c, Theme.SEARCH_POSTER_H);
        smallCap = Theme.dp(c, Theme.SEARCH_POSTER_CAPTION);
        gap = Theme.dp(c, Theme.POSTER_GAP);
        shelfGap = Theme.dp(c, Theme.SP_L);
        radius = Theme.dp(c, Theme.R_M);
        pad = Theme.dp(c, Theme.SP_L);
        fieldH = Theme.dp(c, Theme.FIELD_H);
        safeX = Theme.dp(c, Theme.SAFE_X);
        safeY = Theme.dp(c, Theme.SAFE_Y);
        shelfP.setTextSize(Theme.sp(c, Theme.TEXT_TITLE));
        shelfP.setTypeface(Theme.MEDIUM);
        titleP.setTextSize(Theme.sp(c, Theme.TEXT_LABEL));
        titleP.setTypeface(Theme.MEDIUM);
        subP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        subP.setTypeface(Theme.REGULAR);
        fieldP.setTextSize(Theme.sp(c, Theme.TEXT_BODY));
        fieldP.setTypeface(Theme.REGULAR);
        hintP.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        hintP.setTypeface(Theme.REGULAR);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Theme.dp(c, 1));
        setFocusable(true); // the shelves and the field take the D-pad; the keyboard is a separate view
        setContentDescription("Katalog");
    }

    // ---- Binding ------------------------------------------------------------------------

    public void setShelves(java.util.List<Shelf> s) {
        shelves = s == null ? new java.util.ArrayList<>() : s;
        if (shelfScrollX.length != shelves.size()) shelfScrollX = new int[shelves.size()];
        clamp();
        invalidate();
    }

    public void setQuery(String q) {
        query = q == null ? "" : q;
        invalidate();
    }

    /** The footer line under the lens (the key hints of the focused shelf, field or keyboard). */
    public void setHint(CharSequence h) {
        hint = h == null ? "" : h;
        invalidate();
    }

    /** The hint as drawn last (harness). */
    public CharSequence shownHint() {
        return hint;
    }

    public void setFocus(int shelfIndex, int cardIndex) {
        shelf = shelfIndex;
        card = cardIndex;
        clamp();
        invalidate();
    }

    /** ↑ from the top shelf: the focus is on the tab row above (the activity draws it); -1 = the search field. */
    public void focusField(boolean on) {
        fieldFocused = on;
        if (on) {
            shelf = -1;
            card = -1;
        }
        invalidate();
    }

    public void setKeyboardFocus(boolean on) {
        keyboardFocused = on;
        invalidate();
    }

    public int shelfIndex() {
        return shelf;
    }

    public int cardIndex() {
        return card;
    }

    public boolean fieldHasFocus() {
        return fieldFocused && !keyboardFocused;
    }

    public boolean keyboardHasFocus() {
        return keyboardFocused;
    }

    /** The card at a position in a shelf, or null. */
    public Card cardAt(int shelfIndex, int cardIndex) {
        if (shelfIndex < 0 || shelfIndex >= shelves.size()) return null;
        java.util.List<Card> cs = shelves.get(shelfIndex).cards;
        return cardIndex >= 0 && cardIndex < cs.size() ? cs.get(cardIndex) : null;
    }

    /** The card with this key anywhere in the lens, or null. */
    public Card cardByKey(String key) {
        for (Shelf sh : shelves) for (Card c : sh.cards) if (c.key.equals(key)) return c;
        return null;
    }

    /** The focused card, or null on the field or an empty lens. */
    public Card focusedCard() {
        if (shelf < 0 || shelf >= shelves.size()) return null;
        java.util.List<Card> cs = shelves.get(shelf).cards;
        return card >= 0 && card < cs.size() ? cs.get(card) : null;
    }

    public int shelfCount() {
        return shelves.size();
    }

    public int cardCount(int i) {
        return i >= 0 && i < shelves.size() ? shelves.get(i).cards.size() : 0;
    }

    public String shelfTitle(int i) {
        return i >= 0 && i < shelves.size() ? shelves.get(i).title : "";
    }

    /** The rects drawn last (posters, shelves, the field), for the geometry replay and the harness. */
    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        for (Map.Entry<String, RectF> e : drawn.entrySet()) out.put(e.getKey(), new RectF(e.getValue()));
        return out;
    }

    /** The first card rect of every shelf, for the harness: "poster:<key>" to its rect. */
    public RectF rectOf(String key) {
        RectF r = drawn.get(key);
        return r == null ? null : new RectF(r);
    }

    private void clamp() {
        if (shelves.isEmpty()) return; // the first shelf and card when they arrive
        shelf = Math.max(0, shelf);
        card = Math.max(0, card);
        shelf = Math.min(shelf, shelves.size() - 1);
        int n = shelves.get(shelf).cards.size();
        card = n == 0 ? -1 : Math.max(0, Math.min(n - 1, card));
    }

    // ---- Layout --------------------------------------------------------------------------

    /** The keyboard is up (or not): the search layout puts the field and the keys at the left, the cards at the right. */
    public void setSearchLayout(boolean on) {
        if (searchLayout == on) return;
        searchLayout = on;
        scrollY = 0;
        invalidate();
    }

    public boolean searchLayout() {
        return searchLayout;
    }

    private int cardW() {
        return searchLayout ? smallW : bigW;
    }

    private int cardH() {
        return searchLayout ? smallH : bigH;
    }

    private int capH() {
        return searchLayout ? smallCap : bigCap;
    }

    private int kbW() {
        return Theme.dp(getContext(), 6 * Theme.KEY_W);
    }

    private int top0() {
        return Theme.dp(getContext(), Theme.SP_S);
    }

    private int hintH() {
        return Theme.dp(getContext(), 20);
    }

    private int fieldRight() {
        return searchLayout ? safeX + kbW() : getWidth() - safeX;
    }

    private int contentLeft() {
        return searchLayout ? safeX + kbW() + Theme.dp(getContext(), Theme.SP_XL) : safeX;
    }

    private int contentRight() {
        return getWidth() - safeX;
    }

    /** The cards start under the field in the browse layout, and at the top beside the keyboard in the search layout. */
    private int contentTop() {
        return searchLayout ? top0() : top0() + fieldH + Theme.dp(getContext(), Theme.SP_M);
    }

    private int hintTop() {
        return getHeight() - safeY - hintH();
    }

    private int contentBottom() {
        return hintTop() - Theme.dp(getContext(), Theme.SP_S);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hSpec)
                : getResources().getDisplayMetrics().heightPixels;
        setMeasuredDimension(w, h);
    }

    /**
     * The cards one row holds. The search layout is a grid that wraps at the width; browsing is one row per
     * shelf that scrolls sideways (the whole shelf is one row).
     */
    private int perRow() {
        if (!searchLayout) return 1_000_000;
        return Math.max(1, (contentRight() - contentLeft() + gap) / (cardW() + gap));
    }

    private int headerH() {
        return Theme.dp(getContext(), Theme.SP_XXL);
    }

    private int rowStride() {
        return cardH() + capH() + gap;
    }

    /** A shelf's height: its eyebrow and as many card rows as it needs (never from its titles, E6). */
    private int shelfHeight(int i) {
        int n = i >= 0 && i < shelves.size() ? shelves.get(i).cards.size() : 0;
        int rows = Math.max(1, (n + perRow() - 1) / perRow());
        return headerH() + rows * rowStride();
    }

    /** The y of a shelf's top, in the scrolled content (before the scroll offset). */
    private int shelfTop(int i) {
        int y = 0;
        for (int k = 0; k < i; k++) y += shelfHeight(k) + shelfGap;
        return y;
    }

    /** Scrolls so the focused card's whole row is in the content area (a shelf can be taller than the area). */
    private void keepFocusVisible() {
        if (shelf < 0 || shelf >= shelves.size() || card < 0) return;
        int row = card / perRow();
        int top = shelfTop(shelf) + (row == 0 ? 0 : headerH() + row * rowStride());
        int bottom = shelfTop(shelf) + headerH() + row * rowStride() + cardH() + capH();
        int view = contentBottom() - contentTop();
        if (top - scrollY < 0) scrollY = top;
        else if (bottom - scrollY > view) scrollY = bottom - view;
        scrollY = Math.max(0, scrollY);
        if (!searchLayout && shelf < shelfScrollX.length) { // a browse row scrolls sideways to keep its card whole
            int x0 = card * (cardW() + gap), viewW = contentRight() - contentLeft();
            if (x0 < shelfScrollX[shelf]) shelfScrollX[shelf] = x0;
            else if (x0 + cardW() > shelfScrollX[shelf] + viewW) shelfScrollX[shelf] = x0 + cardW() - viewW;
            shelfScrollX[shelf] = Math.max(0, shelfScrollX[shelf]);
        }
    }

    /** What {@link #move} says: the focus moved, or the step ran into an edge the activity decides about. */
    public static final int MOVED = 0, EDGE_UP = 1, EDGE_DOWN = 2, EDGE_LEFT = 3, EDGE_RIGHT = 4;

    /**
     * One D-pad step over the cards by geometry. ← → along the row, ↑ ↓ to the card in the same column of
     * the row above or below, across shelves; a shorter last row takes its last card. An edge changes nothing.
     */
    public int move(int dx, int dy) {
        if (shelves.isEmpty() || shelf < 0 || shelf >= shelves.size()) {
            return dy < 0 ? EDGE_UP : dy > 0 ? EDGE_DOWN : dx < 0 ? EDGE_LEFT : EDGE_RIGHT;
        }
        int n = shelves.get(shelf).cards.size();
        int pr = perRow();
        int r = Math.max(0, card) / pr, c = Math.max(0, card) % pr;
        if (dx < 0) {
            if (c == 0) return EDGE_LEFT;
            card--;
        } else if (dx > 0) {
            if (c >= pr - 1 || card >= n - 1) return EDGE_RIGHT;
            card++;
        } else if (dy > 0) {
            if (card + pr < n) card += pr;
            else if (r < (n - 1) / pr) card = n - 1;
            else {
                int s2 = shelf + 1;
                while (s2 < shelves.size() && cardCount(s2) == 0) s2++;
                if (s2 >= shelves.size()) return EDGE_DOWN;
                shelf = s2;
                card = Math.min(c, cardCount(s2) - 1);
            }
        } else if (dy < 0) {
            if (r > 0) card -= pr;
            else {
                int s2 = shelf - 1;
                while (s2 >= 0 && cardCount(s2) == 0) s2--;
                if (s2 < 0) return EDGE_UP;
                int n2 = cardCount(s2);
                shelf = s2;
                card = Math.min(((n2 - 1) / pr) * pr + c, n2 - 1);
            }
        }
        invalidate();
        return MOVED;
    }

    @Override
    protected void onDraw(Canvas c) {
        drawn.clear();
        int w = getWidth();
        c.drawColor(Theme.BG); // opaque: the video must not show between the elements
        keepFocusVisible();
        int left = contentLeft(), right = contentRight();
        c.save();
        // A browse row bleeds to the screen's edge (it scrolls sideways); the search grid ends at the margin.
        c.clipRect(left - Theme.dp(getContext(), 4), contentTop(), searchLayout ? right + Theme.dp(getContext(), 4) : w, contentBottom());
        shelfP.setTextSize(Theme.sp(getContext(), searchLayout ? Theme.TEXT_BODY : Theme.TEXT_TITLE));
        float y = contentTop() - scrollY;
        Paint.FontMetrics sm = shelfP.getFontMetrics();
        int pr = perRow();
        for (int i = 0; i < shelves.size(); i++) {
            Shelf sh = shelves.get(i);
            float shelfBottom = y + shelfHeight(i);
            if (shelfBottom >= contentTop() && y <= contentBottom()) { // only what shows (nothing allocated off screen)
                shelfP.setColor(Theme.TEXT_2);
                c.drawText(sh.title, left, y + headerH() - sm.descent - Theme.dp(getContext(), Theme.SP_XS), shelfP);
                drawn.put("shelf" + i, new RectF(left, y, right, y + headerH()));
                for (int k = 0; k < sh.cards.size(); k++) {
                    int rIdx = k / pr, col = k % pr;
                    float cl = left + col * (cardW() + gap) - (searchLayout || i >= shelfScrollX.length ? 0 : shelfScrollX[i]);
                    float ct = y + headerH() + rIdx * rowStride();
                    if (ct + cardH() + capH() < contentTop() || ct > contentBottom()) continue;
                    if (cl + cardW() < left - gap || cl > right + gap) continue; // off the row's ends
                    drawCard(c, sh.cards.get(k), cl, ct, i == shelf && k == card && !fieldFocused && !keyboardFocused && isFocused(), i, k);
                }
            }
            y = shelfBottom + shelfGap;
        }
        c.restore();
        drawField(c, top0(), w);
        drawHint(c, hintTop(), w);
    }

    /** The footer line: the key hints of what has the focus, along the bottom. */
    private void drawHint(Canvas c, float top, int w) {
        Paint.FontMetrics fm = hintP.getFontMetrics();
        float b = top - fm.ascent;
        CharSequence t = Ui.ellipsize(hint, hintP, w - 2 * safeX);
        hintP.setColor(Theme.TEXT_2);
        c.drawText(t, 0, t.length(), safeX, b, hintP);
        drawn.put("hint", new RectF(safeX, top, w - safeX, top + fm.descent - fm.ascent));
    }

    private void drawCard(Canvas c, Card cd, float left, float top, boolean focused, int si, int ki) {
        int cw = cardW(), ch = cardH();
        rect.set(left, top, left + cw, top + ch);
        fill.setColor(Theme.SURFACE);
        c.drawRoundRect(rect, radius, radius, fill);
        android.graphics.Bitmap bmp = cd.imageKey == null ? null : images.get(cd.imageKey);
        if (bmp != null && bmp.getWidth() > 0) {
            dst.set(rect);
            float bAspect = bmp.getWidth() / (float) bmp.getHeight(), sAspect = cw / (float) ch;
            boolean logo = cd.imageKey.startsWith("logo:"); // a channel's logo sits in its card, not edge to edge
            if (logo || Math.abs(bAspect / sAspect - 1f) > 0.05f) { // a logo or a square image: whole, centered, never stretched
                float scale = Math.min(cw / (float) bmp.getWidth(), ch / (float) bmp.getHeight()) * (logo ? 0.6f : 0.8f);
                float bw = bmp.getWidth() * scale, bh = bmp.getHeight() * scale;
                dst.set(left + (cw - bw) / 2f, top + (ch - bh) / 2f, left + (cw + bw) / 2f, top + (ch + bh) / 2f);
            }
            c.drawBitmap(bmp, null, dst, bmpP);
        }
        if (cd.progress >= 0) { // a lane along the poster's bottom edge (Fortsätt titta)
            float lh = Theme.dp(getContext(), Theme.PROGRESS_ROW);
            rect.set(left + radius, top + ch - lh, left + cw - radius, top + ch);
            fill.setColor(Theme.TRACK);
            c.drawRect(rect, fill);
            rect.set(left + radius, top + ch - lh, left + radius + (cw - 2 * radius) * cd.progress, top + ch);
            fill.setColor(Theme.ACCENT);
            c.drawRect(rect, fill);
        }
        if (focused) { // the focus fill is a ring, so the art stays visible
            rect.set(left, top, left + cw, top + ch);
            stroke.setColor(Theme.FOCUS);
            stroke.setStrokeWidth(Theme.dp(getContext(), 3));
            c.drawRoundRect(rect, radius, radius, stroke);
            stroke.setStrokeWidth(Theme.dp(getContext(), 1));
        }
        rect.set(left, top, left + cw, top + ch);
        drawn.put("poster:" + cd.key, new RectF(rect));
        titleP.setTextSize(Theme.sp(getContext(), searchLayout ? Theme.TEXT_CAPTION : Theme.TEXT_LABEL));
        subP.setTextSize(Theme.sp(getContext(), searchLayout ? 12f : Theme.TEXT_CAPTION));
        float tx = left, tr = left + cw;
        float tb = top + ch + Theme.dp(getContext(), Theme.SP_S) - titleP.getFontMetrics().ascent;
        titleP.setColor(focused ? Theme.FOCUS : Theme.TEXT);
        drawCut(c, cd.title, titleP, tx, tr, tb);
        if (cd.sub != null && !cd.sub.isEmpty()) {
            subP.setColor(focused ? Theme.TEXT : Theme.TEXT_2);
            drawCut(c, cd.sub, subP, tx, tr, tb + (titleP.getFontMetrics().descent - titleP.getFontMetrics().ascent)
                    + subP.getFontMetrics().descent);
        }
    }

    /** The field: a row at the top across the width, or, with the keyboard up, the head of its column. */
    private void drawField(Canvas c, float y, int w) {
        int top = (int) y;
        rect.set(safeX, top, fieldRight(), top + fieldH);
        int fr = Theme.dp(getContext(), Theme.R_M);
        fill.setColor(fieldFocused || keyboardFocused ? Theme.SURFACE_RAISED : Theme.SURFACE);
        c.drawRoundRect(rect, fr, fr, fill);
        boolean fieldRing = fieldFocused && isFocused(); // the tab row can have the focus while the field is the lens's own
        stroke.setColor(fieldRing ? Theme.FOCUS : Theme.HAIRLINE);
        stroke.setStrokeWidth(Theme.dp(getContext(), fieldRing ? 2 : 1));
        c.drawRoundRect(rect, fr, fr, stroke);
        stroke.setStrokeWidth(Theme.dp(getContext(), 1));
        Paint.FontMetrics fm = fieldP.getFontMetrics();
        float b = rect.centerY() - (fm.ascent + fm.descent) / 2f;
        boolean empty = query.isEmpty();
        fieldP.setColor(empty ? Theme.TEXT_3 : Theme.TEXT);
        String s = empty ? (searchLayout ? "Sök titel eller kanal" : "Sök serier, filmer, program eller kanaler") : query;
        float r = rect.right - pad;
        if (!empty && keyboardFocused) s = s + "|";
        CharSequence cut = TextUtils.ellipsize(s, fieldP, r - rect.left - pad, empty ? TextUtils.TruncateAt.END : TextUtils.TruncateAt.START);
        c.drawText(cut, 0, cut.length(), rect.left + pad, b, fieldP);
        drawn.put("field", new RectF(rect));
    }

    private void drawCut(Canvas c, String s, TextPaint p, float l, float r, float b) {
        CharSequence t = Ui.ellipsize(s, p, r - l);
        c.drawText(t, 0, t.length(), l, b, p);
    }

    /** Where a poster's decoded bitmap comes from; null while it loads (the placeholder stays). */
    public interface Bitmaps {
        android.graphics.Bitmap get(String key);
    }

    private Bitmaps images = key -> null;

    /** The decoded images by key (the activity's cache). */
    public void setImages(Bitmaps provider) {
        images = provider == null ? key -> null : provider;
    }
}
