package se.lite.play.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The in-app on-screen keyboard for search, drawn on one View: no child views, no system IME.
 *
 * Alphabetical, 6 columns, action row on top (design/UX.md 5.1). With a D-pad, presses depend on grid
 * distance, not finger memory: a count over 1,000 film titles found 3.32 presses per character on a
 * 6-wide alphabetical grid against 4.01 on 3x11 QWERTY. Swedish order puts å ä ö after z.
 *
 * <pre>
 *  [ Röst    ][ Mellanslag ][ ⌫ ][ ✕ ]    2 + 2 + 1 + 1 columns
 *    a  b  c  d  e  f
 *    g  h  i  j  k  l
 *    m  n  o  p  q  r
 *    s  t  u  v  w  x
 *    y  z  å  ä  ö  -
 *    1  2  3  4  5  6
 *    7  8  9  0  .  &
 * </pre>
 *
 * Without a speech recognizer the Röst key is drawn dim; OK on it still reaches the listener, which
 * explains why nothing happens. Only ⌫ repeats while held. Grid movement does not wrap; up from the top
 * row does nothing; left from the left column goes to the rail (U10), right from the right column to the
 * list column.
 *
 * The focused key fills with {@link Theme#FOCUS}; everything is drawn inside the view's bounds.
 * D-pad keys move within the grid; right from the last column and OK on a key go to the {@link Listener}.
 */
public class KeyboardView extends View {
    public static final int CHAR = 0, SPACE = 1, BACKSPACE = 2, CLEAR = 3, VOICE = 4;
    private static final int COLS = 6;
    private static final String[] ROWS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yzåäö-", "123456", "7890.&"};

    /** What the keyboard asks its owner to do. */
    public interface Listener {
        void onType(String s);

        void onBackspace();

        void onClear();

        void onVoice();

        /** Right from the last column: move focus to what is beside the keyboard. Return false to stay. */
        boolean onExitRight();

        /** Left from the first column: the rail (U10). Return false to stay. */
        default boolean onExitLeft() {
            return false;
        }

        /** The focused key changed (the footer hint names the key under focus, Y4). */
        default void onFocusMoved() {}
    }

    private static final class Key {
        final int type, row, col, span;
        final String ch;

        Key(int type, String ch, int row, int col, int span) {
            this.type = type;
            this.ch = ch;
            this.row = row;
            this.col = col;
            this.span = span;
        }
    }

    private final List<Key> keys = new ArrayList<>();
    private final TextPaint letter = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint caption = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path icon = new Path();
    private final int keyH, gap, radius;
    private Listener listener;
    private boolean voice;
    private int focus;

    public KeyboardView(Context c) {
        super(c);
        setContentDescription("Tangentbord");
        keyH = Theme.dp(c, Theme.KEY_H);
        gap = Theme.dp(c, Theme.SP_XS);
        radius = Theme.dp(c, Theme.R_M);
        letter.setTextSize(Theme.sp(c, Theme.TEXT_BODY));
        letter.setTypeface(Theme.MEDIUM);
        letter.setTextAlign(Paint.Align.CENTER);
        caption.setTextSize(Theme.sp(c, Theme.TEXT_CAPTION));
        caption.setTypeface(Theme.BOLD);
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setLetterSpacing(0.02f);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Theme.dp(c, 2));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(true);
        build();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Shows the mic key; call with false when no speech recognizer is installed. */
    public void setVoiceAvailable(boolean available) {
        voice = available;
        invalidate();
    }

    public boolean voiceAvailable() {
        return voice;
    }

    /** Puts the focus on the first letter (a). */
    public void home() {
        for (int i = 0; i < keys.size(); i++) if ("a".equals(keys.get(i).ch)) focus = i;
        invalidate();
    }

    /** Name of the focused key: "a", "Röst", "Mellanslag", "Radera", "Rensa". */
    public String focusedKey() {
        Key k = keys.get(focus);
        switch (k.type) {
            case VOICE: return "Röst";
            case SPACE: return "Mellanslag";
            case BACKSPACE: return "Radera";
            case CLEAR: return "Rensa";
            default: return k.ch;
        }
    }

    /** The row of the focused key (0 = the action row: Röst, Mellanslag, ⌫, ✕). */
    public int focusedKeyRow() {
        return keys.get(focus).row;
    }

    /** The column of the focused key (0 = the left column, from which ← reaches the rail). */
    public int focusedKeyColumn() {
        return keys.get(focus).col;
    }

    /** Test hook and hardware-keyboard path: types text as if its keys were pressed. */
    public void type(String s) {
        if (listener != null) listener.onType(s);
    }

    private void build() {
        keys.clear();
        keys.add(new Key(VOICE, null, 0, 0, 2));
        keys.add(new Key(SPACE, " ", 0, 2, 2));
        keys.add(new Key(BACKSPACE, null, 0, 4, 1));
        keys.add(new Key(CLEAR, null, 0, 5, 1));
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            for (int i = 0; i < row.length(); i++) {
                keys.add(new Key(CHAR, String.valueOf(row.charAt(i)), r + 1, i, 1));
            }
        }
        home();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), (ROWS.length + 1) * keyH);
    }

    // ---- Keys ----------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        Key k = keys.get(focus);
        // Held keys: only the backspace key repeats; a held letter types once.
        if (e.getRepeatCount() > 0 && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                && k.type != BACKSPACE) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (k.col == 0) { // the rail is to the left: one press reaches it (U10)
                    if (listener != null) listener.onExitLeft();
                    return true;
                }
                return move(k, 0, -1);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (k.col + k.span >= COLS) { // the list column takes focus only when it has a row to select
                    if (listener != null) listener.onExitRight();
                    return true; // consumed either way: the framework must not move focus to a note
                }
                return move(k, 0, 1);
            case KeyEvent.KEYCODE_DPAD_UP:
                return move(k, -1, 0);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return move(k, 1, 0);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                press(k);
                return true;
            case KeyEvent.KEYCODE_DEL:
                if (listener != null) listener.onBackspace();
                return true;
            default:
                int u = e.getUnicodeChar();
                if (u > 0 && listener != null && (Character.isLetterOrDigit(u) || u == ' ')) {
                    listener.onType(String.valueOf((char) u).toLowerCase(Locale.ROOT));
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    private void press(Key k) {
        if (listener == null) return;
        switch (k.type) {
            case CHAR:
            case SPACE:
                listener.onType(k.ch);
                break;
            case BACKSPACE:
                listener.onBackspace();
                break;
            case CLEAR:
                listener.onClear();
                break;
            case VOICE:
                listener.onVoice();
                break;
        }
    }

    /** Moves by one row or one column; a wide key is entered and left at the column you came from. */
    private boolean move(Key from, int dRow, int dCol) {
        int row = from.row + dRow;
        int col = dCol > 0 ? from.col + from.span : dCol < 0 ? from.col - 1 : from.col;
        if (row < 0 || row > ROWS.length || col < 0 || col >= COLS) return true; // edges stop, keep focus here
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (k.row == row && col >= k.col && col < k.col + k.span) {
                if (focus != i) {
                    focus = i;
                    invalidate();
                    if (listener != null) listener.onFocusMoved();
                }
                return true;
            }
        }
        return true;
    }

    // ---- Drawing -------------------------------------------------------------------------

    @Override
    protected void onFocusChanged(boolean gain, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float colW = getWidth() / (float) COLS;
        boolean hasFocus = isFocused();
        Paint.FontMetrics lm = letter.getFontMetrics(), cm = caption.getFontMetrics();
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            boolean f = hasFocus && i == focus;
            // Every key, its fill and its focus fill stay inside the view: half a gap of inset on each side.
            rect.set(k.col * colW + gap / 2f, k.row * keyH + gap / 2f,
                    (k.col + k.span) * colW - gap / 2f, (k.row + 1) * keyH - gap / 2f);
            fill.setColor(f ? Theme.FOCUS : k.type == CHAR ? Theme.SURFACE : Theme.SURFACE_RAISED);
            c.drawRoundRect(rect, radius, radius, fill);
            int fg = f ? Theme.ON_FOCUS : Theme.TEXT;
            float cx = rect.centerX(), cy = rect.centerY();
            switch (k.type) {
                case CHAR:
                    letter.setColor(fg);
                    c.drawText(k.ch, cx, cy - (lm.ascent + lm.descent) / 2f, letter);
                    break;
                case SPACE:
                    caption.setColor(f ? Theme.ON_FOCUS : Theme.TEXT_2);
                    c.drawText("Mellanslag", cx, cy - (cm.ascent + cm.descent) / 2f, caption);
                    break;
                case BACKSPACE:
                    drawBackspace(c, cx, cy, fg);
                    break;
                case CLEAR:
                    drawClear(c, cx, cy, fg);
                    break;
                case VOICE: {
                    int ic = !voice ? (f ? Theme.ON_FOCUS_2 : Theme.TEXT_3) : f ? Theme.ACCENT : Theme.ACCENT_ON_DARK;
                    float tw = caption.measureText("Röst"), iw = Theme.dp(getContext(), 14);
                    float sx = cx - (tw + iw + gap * 2) / 2f;
                    drawMic(c, sx + iw / 2f, cy, ic);
                    caption.setColor(!voice ? (f ? Theme.ON_FOCUS_2 : Theme.TEXT_3) : f ? Theme.ON_FOCUS : Theme.TEXT_2);
                    c.drawText("Röst", sx + iw + gap * 2 + tw / 2f, cy - (cm.ascent + cm.descent) / 2f, caption);
                    break;
                }
            }
        }
    }

    private void drawBackspace(Canvas c, float cx, float cy, int color) {
        float s = Theme.dp(getContext(), 10);
        icon.reset();
        icon.moveTo(cx - s * 1.4f, cy);
        icon.lineTo(cx - s * 0.6f, cy - s * 0.8f);
        icon.lineTo(cx + s * 1.3f, cy - s * 0.8f);
        icon.lineTo(cx + s * 1.3f, cy + s * 0.8f);
        icon.lineTo(cx - s * 0.6f, cy + s * 0.8f);
        icon.close();
        float x = cx + s * 0.35f, d = s * 0.38f;
        icon.moveTo(x - d, cy - d);
        icon.lineTo(x + d, cy + d);
        icon.moveTo(x + d, cy - d);
        icon.lineTo(x - d, cy + d);
        stroke.setColor(color);
        c.drawPath(icon, stroke);
    }

    private void drawClear(Canvas c, float cx, float cy, int color) {
        float d = Theme.dp(getContext(), 6);
        stroke.setColor(color);
        c.drawLine(cx - d, cy - d, cx + d, cy + d, stroke);
        c.drawLine(cx + d, cy - d, cx - d, cy + d, stroke);
    }

    private void drawMic(Canvas c, float cx, float cy, int color) {
        float u = Theme.dp(getContext(), 1);
        fill.setColor(color);
        rect.set(cx - 4 * u, cy - 10 * u, cx + 4 * u, cy + 3 * u);
        c.drawRoundRect(rect, 4 * u, 4 * u, fill);
        stroke.setColor(color);
        rect.set(cx - 7 * u, cy - 6 * u, cx + 7 * u, cy + 6 * u);
        c.drawArc(rect, 0, 180, false, stroke);
        c.drawLine(cx, cy + 6 * u, cx, cy + 10 * u, stroke);
    }
}
