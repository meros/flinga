package se.lite.play;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Sizes its one child (the video SurfaceView) to the video's display aspect ratio, so the picture
 * is never stretched. CONTAIN shows the whole picture with black bars; COVER fills the screen and
 * crops the edges. Until the first video size is known, the child fills the frame.
 */
final class VideoFrame extends FrameLayout {
    static final int CONTAIN = 0, COVER = 1;

    private float aspect; // width / height, including the pixel aspect ratio; 0 = unknown
    private int mode = CONTAIN;

    VideoFrame(Context c) {
        super(c);
    }

    void setVideoAspect(float aspect) {
        if (aspect > 0 && Math.abs(aspect - this.aspect) > 0.001f) {
            this.aspect = aspect;
            requestLayout();
        }
    }

    void setMode(int mode) {
        if (mode != this.mode) {
            this.mode = mode;
            requestLayout();
        }
    }

    int mode() {
        return mode;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        if (getChildCount() == 0) return;
        int cw = w, ch = h;
        if (aspect > 0 && w > 0 && h > 0) {
            boolean wider = aspect > w / (float) h;
            // CONTAIN: the side that hits the frame first decides. COVER: the other side does.
            if (wider == (mode == CONTAIN)) ch = Math.round(w / aspect);
            else cw = Math.round(h * aspect);
        }
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) return;
        View c = getChildAt(0);
        int w = r - l, h = b - t;
        int x = (w - c.getMeasuredWidth()) / 2, y = (h - c.getMeasuredHeight()) / 2;
        c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
    }
}
