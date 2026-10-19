package se.lite.play;

import android.graphics.Bitmap;
import android.graphics.Color;

import io.nayuki.qrcodegen.QrCode;

final class Qr {
    private Qr() {}

    /** A black-on-white QR code with a 4-module quiet zone, scaled to about sizePx. */
    static Bitmap bitmap(String text, int sizePx) {
        QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
        int modules = qr.size + 8;
        int scale = Math.max(1, sizePx / modules);
        int px = modules * scale;
        int[] pixels = new int[px * px];
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int mx = x / scale - 4, my = y / scale - 4;
                pixels[y * px + x] = qr.getModule(mx, my) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.RGB_565);
    }
}
