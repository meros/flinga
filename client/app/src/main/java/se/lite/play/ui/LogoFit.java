package se.lite.play.ui;

import android.graphics.Bitmap;
import android.graphics.RectF;

/**
 * Optical normalisation of channel logos. The bitmaps arrive trimmed to their visible pixels (see
 * {@code Logos.trim}); here each one is scaled so that its visible area matches one target (sqrt(w x h) =
 * {@code target}), then clamped to the slot. A wide wordmark (svt1) and a round logo (TV4) then carry
 * about the same visual weight instead of one filling the width and the other the height.
 */
public final class LogoFit {
    private LogoFit() {}

    public static final int CENTER = 0, LEFT = 1;

    /** Target sqrt(area) as a share of the slot height: 0.9 x 45 dp = 40.5 dp in rows. */
    public static final float WEIGHT = 0.9f;

    /**
     * Where to draw {@code b} inside {@code slot}: equal-area scaling, clamped to the slot, centred on the
     * slot (rows) or left-aligned and vertically centred (headers, OSD: the title then starts at a fixed x).
     */
    public static void fit(Bitmap b, RectF slot, int align, RectF out) {
        float w = b.getWidth(), h = b.getHeight();
        float target = WEIGHT * slot.height();
        float s = target / (float) Math.sqrt(w * h);
        s = Math.min(s, Math.min(slot.width() / w, slot.height() / h));
        float dw = w * s, dh = h * s;
        float left = align == LEFT ? slot.left : slot.centerX() - dw / 2f;
        out.set(left, slot.centerY() - dh / 2f, left + dw, slot.centerY() + dh / 2f);
    }
}
