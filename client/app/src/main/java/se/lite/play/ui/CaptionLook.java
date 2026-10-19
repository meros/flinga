package se.lite.play.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.accessibility.CaptioningManager;

/**
 * How subtitles are drawn (G5): the viewer's own accessibility style when one is set, else white on a
 * semi-transparent black box. The system's stock style (white on opaque black, no edge) is what every device
 * reports until the viewer changes something, so it counts as "not set" and gets the app's default.
 */
public final class CaptionLook {
    public static final int EDGE_NONE = 0, EDGE_OUTLINE = 1, EDGE_SHADOW = 2;
    /** Text height as a share of the view's height, before the system's font scale. */
    public static final float SIZE = 0.055f;
    public static final int DEFAULT_FOREGROUND = Color.WHITE;
    public static final int DEFAULT_BACKGROUND = 0xB3000000;

    public final int foreground, background, edge, edgeColor;
    public final Typeface typeface;
    public final float size;

    CaptionLook(int foreground, int background, int edge, int edgeColor, Typeface typeface, float size) {
        this.foreground = foreground;
        this.background = background;
        this.edge = edge;
        this.edgeColor = edgeColor;
        this.typeface = typeface;
        this.size = size;
    }

    public static CaptionLook defaults() {
        return new CaptionLook(DEFAULT_FOREGROUND, DEFAULT_BACKGROUND, EDGE_NONE, Color.BLACK, Typeface.DEFAULT, SIZE);
    }

    /**
     * Pure, so a test can feed it any style. {@code systemEdge} is CaptioningManager.CaptionStyle's edge type
     * (0 none, 1 outline, 2 drop shadow, 3 raised, 4 depressed); the last two are drawn as a shadow.
     */
    public static CaptionLook from(int fg, int bg, int systemEdge, int edgeColor, Typeface face, float fontScale) {
        float scale = fontScale < 0.5f ? 1f : Math.min(fontScale, 2f);
        boolean stock = fg == Color.WHITE && bg == Color.BLACK && systemEdge == 0;
        if (stock) return new CaptionLook(DEFAULT_FOREGROUND, DEFAULT_BACKGROUND, EDGE_NONE, Color.BLACK, face == null ? Typeface.DEFAULT : face, SIZE * scale);
        int edge = systemEdge == 1 ? EDGE_OUTLINE : systemEdge == 0 ? EDGE_NONE : EDGE_SHADOW;
        return new CaptionLook(fg, bg, edge, edgeColor, face == null ? Typeface.DEFAULT : face, SIZE * scale);
    }

    /** The look from the device's accessibility settings. */
    public static CaptionLook system(Context c) {
        CaptioningManager m = (CaptioningManager) c.getSystemService(Context.CAPTIONING_SERVICE);
        if (m == null) return defaults();
        CaptioningManager.CaptionStyle s = m.getUserStyle();
        return from(s.foregroundColor, s.backgroundColor, s.edgeType, s.edgeColor, s.getTypeface(), m.getFontScale());
    }
}
