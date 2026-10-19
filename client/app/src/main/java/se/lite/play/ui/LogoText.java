package se.lite.play.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;

/**
 * The channel's name in a logo slot when there is no logo (F13 fallback): "3 TV3", "110 Köket",
 * "87 SVT Barn/SVT24". Channel identity is never ellipsized (REVIEW-R2 X1): the text takes the largest
 * of the given sizes at which it fits on one line; when none does, its words wrap onto two lines at the
 * largest size at which both fit the slot ("87 SVT" over "Barn/SVT24"); when not even that fits, the
 * channel number alone identifies the channel (the row's description keeps the name); only a name with
 * no number that fits nowhere is cut, at the smallest size. The slot itself never changes (E6).
 */
public final class LogoText {
    /** The lines to draw (one or two) and the text size they are drawn at. */
    public final String[] lines;
    public final float sizePx;
    /** True when the text had to be cut; the harness fails a shot that draws such a slot. */
    public final boolean cut;

    private LogoText(String[] lines, float sizePx, boolean cut) {
        this.lines = lines;
        this.sizePx = sizePx;
        this.cut = cut;
    }

    /**
     * Fits {@code name} into a slot of {@code w} x {@code h} px with {@code p} (its typeface is kept, its
     * size is set to the chosen one on return). {@code sizesPx} are tried largest first.
     */
    public static LogoText fit(TextPaint p, String name, float w, float h, float[] sizesPx) {
        String s = name == null ? "" : name.trim();
        for (float size : sizesPx) {
            p.setTextSize(size);
            if (p.measureText(s) <= w) return new LogoText(new String[] {s}, size, false);
        }
        String[] words = s.split(" ");
        if (words.length > 1) {
            for (float size : sizesPx) {
                p.setTextSize(size);
                Paint.FontMetrics fm = p.getFontMetrics();
                if (2 * (fm.descent - fm.ascent) > h) continue;
                String[] two = wrapTwo(p, words, w);
                if (two != null) return new LogoText(two, size, false);
            }
        }
        if (words.length > 1 && words[0].matches("\\d+")) { // the number alone, at the largest size
            for (float size : sizesPx) {
                p.setTextSize(size);
                if (p.measureText(words[0]) <= w) return new LogoText(new String[] {words[0]}, size, false);
            }
        }
        float size = sizesPx[sizesPx.length - 1];
        p.setTextSize(size);
        CharSequence cut = Ui.ellipsize(s, p, w);
        return new LogoText(new String[] {cut.toString()}, size, true);
    }

    /** Greedy wrap of the words onto two lines of at most {@code w} px each, or null when they do not fit. */
    private static String[] wrapTwo(TextPaint p, String[] words, float w) {
        for (int split = 1; split < words.length; split++) {
            String a = String.join(" ", java.util.Arrays.copyOfRange(words, 0, split));
            String b = String.join(" ", java.util.Arrays.copyOfRange(words, split, words.length));
            if (p.measureText(a) <= w && p.measureText(b) <= w) return new String[] {a, b};
        }
        return null;
    }

    /** Draws the lines centred on ({@code cx}, {@code cy}); {@code p} must be the paint passed to {@link #fit}. */
    public void draw(Canvas c, TextPaint p, float cx, float cy, Paint.Align align) {
        p.setTextSize(sizePx);
        p.setTextAlign(align);
        Paint.FontMetrics fm = p.getFontMetrics();
        float line = fm.descent - fm.ascent;
        float top = cy - lines.length * line / 2f;
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], cx, top + i * line - fm.ascent, p);
        }
    }
}
