package se.lite.play;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Channel logos, no library: a memory LRU (40), a disk cache in cacheDir (7 days), at most one request
 * per logo in flight, and failures remembered for 10 minutes so a bad URL is not retried on every bind.
 * The server scales the image (Contentful: w, h, fit=pad, fm=png), about 2.5 kB per logo.
 *
 * Two variants per channel (owner: "use correct light/dark version of the logo depending on context"):
 * the dark-background artwork for dark surfaces and the light-background artwork for light ones (the
 * focused row card). Rows load both, so the swap on focus is instant. Channels whose two variants are
 * the same file (colour logos) load it once: the cache key is the URL.
 *
 * {@link #get} never blocks: it returns the bitmap when it is in memory, else null and starts a load;
 * the listener runs on the main thread when a logo arrives. A null logo is drawn as the channel name in
 * the same fixed slot (DESIGN.md), so a missing or late logo never moves anything.
 */
final class Logos {
    /** Request size in px: the OSD slot at xhdpi (112 x 63 dp), also used for the 80 x 45 dp row slot. */
    static final int W = 224, H = 126;
    private static final int MEMORY = 64; // 27 channels x 2 variants, 11 shared
    private static final long DISK_TTL_MS = 7L * 24 * 3600_000;
    private static final long FAIL_TTL_MS = 10 * 60_000;

    /** The byte seam: tests serve client/fixtures/logos[-light]/<channelId>.png from here. */
    interface Source {
        byte[] get(String channelId, boolean light, String url) throws IOException;
    }

    static volatile Source source = Logos::network;

    private final LruCache<String, Bitmap> memory = new LruCache<>(MEMORY);
    private final Map<String, Long> failedAt = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File dir;
    private final Runnable listener;

    Logos(Context c, Runnable onLogo) {
        dir = new File(c.getCacheDir(), "logos");
        listener = onLogo;
    }

    /** The scaled-image URL for a Contentful source. */
    static String scaled(String url) {
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + "w=" + W + "&h=" + H + "&fit=pad&fm=png";
    }

    /** The dark-background logo of {@code ch} (dark surfaces), or null; see {@link #get(Api.Channel, boolean)}. */
    Bitmap get(Api.Channel ch) {
        return get(ch, false);
    }

    /**
     * The logo variant of {@code ch} for a light ({@code light}) or dark background if loaded; otherwise
     * null, and a load starts (main thread only).
     */
    Bitmap get(Api.Channel ch, boolean light) {
        if (ch == null) return null;
        String src = light ? ch.logoLightUrl : ch.logoUrl;
        if (src == null || src.isEmpty()) return null;
        String url = scaled(src);
        Bitmap b = memory.get(url);
        if (b != null) return b;
        Long f = failedAt.get(url);
        if (f != null && Clock.now() - f < FAIL_TTL_MS) return null;
        if (!inFlight.add(url)) return null;
        String id = ch.id;
        pool.execute(() -> {
            Bitmap got = null;
            try {
                got = load(id, light, url);
            } catch (Exception e) {
                Log.w("Flinga", "logo " + id + (light ? " light" : " dark") + ": " + e);
            }
            Bitmap fin = got;
            ui.post(() -> {
                inFlight.remove(url);
                if (fin == null) {
                    failedAt.put(url, Clock.now());
                    return; // the other variant on its own plate, or the name, stays in the slot
                }
                failedAt.remove(url);
                memory.put(url, fin);
                if (listener != null) listener.run();
            });
        });
        return null;
    }

    /** Disk first (fresh for 7 days), then the source; decodes to at most twice the slot size. */
    private Bitmap load(String id, boolean light, String url) throws IOException {
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".png");
        byte[] bytes = null;
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < DISK_TTL_MS) {
            bytes = Files.readAllBytes(f.toPath());
        }
        boolean fresh = bytes == null;
        if (fresh) bytes = source.get(id, light, url);
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null; // not an image
        int sample = 1;
        while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888; // logos need alpha
        Bitmap b = trim(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, d));
        if (b != null && fresh) {
            try {
                dir.mkdirs();
                Files.write(f.toPath(), bytes);
            } catch (IOException e) {
                Log.w("Flinga", "logo cache " + id + ": " + e);
            }
        }
        return b;
    }

    /**
     * Crops a logo to its visible pixels (alpha > 8). The server pads every logo into the same 16:9 box,
     * so a wordmark fills the width and a round logo the height; trimmed, they can be sized by visible
     * area ({@code LogoFit}) and aligned on their real edges. Done once per decode; the result is cached.
     */
    static Bitmap trim(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, btm = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 8) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > btm) btm = y;
                }
            }
        }
        if (r < l || btm < t) return null; // nothing visible: the name goes in the slot
        if (l == 0 && t == 0 && r == w - 1 && btm == h - 1) return b;
        return Bitmap.createBitmap(b, l, t, r - l + 1, btm - t + 1);
    }

    /** Test hook: forget everything, memory and disk. */
    void clear() {
        memory.evictAll();
        failedAt.clear();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static byte[] network(String id, boolean light, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 512 * 1024) throw new IOException("logo too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
