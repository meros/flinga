package se.lite.play.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Serie splash (F16): the series' key art full-bleed behind the panel, with a left-to-right scrim so
 * the panel's text reads on it. One bitmap (960 x 540, RGB_565, drawn at 2x with filtering), one gradient
 * rect; nothing else.
 *
 * Until the image arrives, and when it fails, the view is filled with the series' own muted background
 * colour from the API (muteBgColor), else SURFACE: the same size, so nothing moves when the image comes (E6).
 * The image fades in over {@link Theme#FADE_MS} (at most 150 ms, F16 budget), or appears at once when
 * {@link Theme#animations} is off. The view never takes focus.
 */
public final class HeroView extends View {
    private final Paint bmpP = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimP = new Paint();
    private final Paint fill = new Paint();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap image;
    private String key;
    private int placeholder = Theme.BG;
    private int scrimWidth = -1, scrimColor;
    private int alpha = 255;
    private ValueAnimator fade;
    /** For the harness: "image" when the picture was drawn last, else "placeholder". */
    private String drawnState = "placeholder";

    public HeroView(Context c) {
        super(c);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /**
     * Shows the hero for {@code key}: {@code bmp} (null = placeholder colour until {@link #arrived}) and the
     * placeholder colour (0 = BG). The scrim is the panel scrim tinted with that colour, opaque up to
     * {@code panelRight} px and clear by two thirds of the width.
     */
    public void show(String key, Bitmap bmp, int color, int panelRight) {
        boolean same = key != null && key.equals(this.key);
        this.key = key;
        placeholder = color == 0 ? Theme.SURFACE : blend(Theme.BG, color, 0.5f); // no art colour: a slate, not flat black
        if (!same || bmp != image) {
            cancelFade();
            image = bmp;
            alpha = 255;
        }
        if (scrimWidth != panelRight || scrimColor != placeholder) {
            scrimWidth = panelRight;
            scrimColor = placeholder;
            scrimP.setShader(null); // rebuilt with the size in onDraw
        }
        invalidate();
    }

    /** An image arrived: fades in if it is the one this hero waits for. */
    public boolean arrived(String key, Bitmap bmp) {
        if (key == null || !key.equals(this.key) || bmp == null || bmp == image) return false;
        image = bmp;
        cancelFade();
        if (Theme.animations && isShown()) {
            alpha = 0;
            fade = ValueAnimator.ofInt(0, 255).setDuration(Theme.FADE_MS);
            fade.addUpdateListener(a -> {
                alpha = (int) a.getAnimatedValue();
                invalidate();
            });
            fade.start();
        } else {
            alpha = 255;
        }
        invalidate();
        return true;
    }

    /** Drops the bitmap (the surface closed). */
    public void clear() {
        cancelFade();
        image = null;
        key = null;
        invalidate();
    }

    public String key() {
        return key;
    }

    public String drawnState() {
        return drawnState;
    }

    private void cancelFade() {
        if (fade != null) fade.cancel();
        fade = null;
    }

    public Map<String, RectF> debugRects() {
        Map<String, RectF> out = new LinkedHashMap<>();
        out.put("hero", new RectF(0, 0, getWidth(), getHeight()));
        return out;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        scrimP.setShader(null);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        fill.setColor(placeholder);
        c.drawRect(0, 0, w, h, fill);
        drawnState = "placeholder";
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            // Cover: fill the view with the art's content, crop the overflow evenly. Baked-in black bars
            // (letterbox, pillarbox) are trimmed first, so the art never ends in a black band (REVIEW-R4 A2).
            Rect art = content(image);
            float s = Math.max(w / (float) art.width(), h / (float) art.height());
            float sw = w / s, sh = h / s;
            int sx = Math.round(art.left + (art.width() - sw) / 2f), sy = Math.round(art.top + (art.height() - sh) / 2f);
            src.set(sx, sy, Math.round(sx + sw), Math.round(sy + sh));
            dst.set(0, 0, w, h);
            bmpP.setAlpha(alpha);
            c.drawBitmap(image, src, dst, bmpP);
            drawnState = "image";
        }
        if (scrimP.getShader() == null && w > 0) {
            float edge = Math.max(0.05f, Math.min(0.9f, scrimWidth / (float) w));
            int solid = (scrimColor & 0x00FFFFFF) | 0xF0000000; // 94 %: text contrast as on the panel
            int mid = (scrimColor & 0x00FFFFFF) | 0xC0000000;
            int clear = scrimColor & 0x00FFFFFF;
            scrimP.setShader(new LinearGradient(0, 0, w, 0, new int[] {solid, solid, mid, clear},
                    new float[] {0f, edge * 0.85f, edge, Math.min(1f, edge + 0.22f)}, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, scrimP);
    }

    private Bitmap measured;
    private final Rect contentRect = new Rect();

    /**
     * The part of the art that is picture: edge rows and columns that are black (every sampled pixel
     * under {@link #BLACK}) are bars baked into the source and are cut away, once per bitmap. At least
     * half the art is always kept.
     */
    public Rect content(Bitmap b) {
        if (b == measured) return contentRect;
        int w = b.getWidth(), h = b.getHeight();
        int l = 0, t = 0, r = w, bt = h;
        while (l < w / 4 && blackColumn(b, l, 0, h)) l++;
        while (r > w - w / 4 && blackColumn(b, r - 1, 0, h)) r--;
        while (t < h / 4 && blackRow(b, t, l, r)) t++;
        while (bt > h - h / 4 && blackRow(b, bt - 1, l, r)) bt--;
        contentRect.set(l, t, r, bt);
        measured = b;
        return contentRect;
    }

    /** Below this on every channel a pixel is a bar, not picture (the darkest art stays above it). */
    private static final int BLACK = 24;

    private static boolean blackColumn(Bitmap b, int x, int y0, int y1) {
        for (int y = y0; y < y1; y += 4) if (!black(b.getPixel(x, y))) return false;
        return true;
    }

    private static boolean blackRow(Bitmap b, int y, int x0, int x1) {
        for (int x = x0; x < x1; x += 4) if (!black(b.getPixel(x, y))) return false;
        return true;
    }

    private static boolean black(int p) {
        return ((p >> 16) & 0xFF) < BLACK && ((p >> 8) & 0xFF) < BLACK && (p & 0xFF) < BLACK;
    }

    private static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
