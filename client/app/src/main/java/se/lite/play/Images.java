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
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Catalogue images (F16) under a strict budget (DESIGN.md, "Image budget"): no library.
 *
 * - Every image is scaled by Telia's image proxy to the exact pixel size of its slot, as the official app
 *   builds the URL (ImageProxyImageUrlTransformation): convert/?resize=WxH&shape=nocut&source=<source>.
 *   `Accept: image/jpeg` makes the proxy answer JPEG instead of PNG (332 kB instead of 459 kB for 960x540).
 * - Opaque images decode as RGB_565 (2 bytes per pixel): a 960x540 hero is 1.04 MB, a 176x100 thumbnail 35 kB.
 * - Decoded bitmaps live in one LRU capped in bytes ({@link #CAP_BYTES}); {@link #releaseAll} empties it
 *   when the panel closes, so nothing is held while video plays.
 * - Loads run on two background threads, newest request first (a row scrolled into view beats one that
 *   left it), at most {@link #QUEUE_MAX} waiting; failures are remembered for 10 minutes (no retry storm).
 * - A small disk cache (16 MB, 3 days) makes reopening Sök or a series cheap after playback.
 *
 * {@link #get} never blocks: it returns the bitmap when it is in memory, else null and starts a load. The
 * listener runs on the main thread when an image arrives (or fails); the slot drew a placeholder of the
 * same size meanwhile, so nothing moves (E6).
 */
final class Images {
    /** Decoded bitmaps, all sizes together. With the logos (under 5 MB for 27 channels) about 12 MB. */
    static final int CAP_BYTES = 7 * 1024 * 1024;
    static final int QUEUE_MAX = 24;
    private static final long DISK_TTL_MS = 3L * 24 * 3600_000;
    private static final long DISK_MAX_BYTES = 16L * 1024 * 1024;
    private static final long FAIL_TTL_MS = 10 * 60_000;
    private static final int MAX_DOWNLOAD = 1024 * 1024;
    static final String PROXY = "https://director-image.streaming.telia.com/convert/";

    /** The byte seam: tests serve client/fixtures/images/ from here. */
    interface Source {
        byte[] get(String url) throws IOException;
    }

    interface Listener {
        /** On the main thread; {@code bmp} is null when the image failed (the placeholder stays). */
        void onImage(String url, Bitmap bmp);
    }

    static volatile Source source = Images::network;

    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(CAP_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getAllocationByteCount();
        }
    };
    private final Map<String, Long> failedAt = new HashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor pool;
    private final File dir;
    private final Listener listener;
    /** Decodes done, for tests. */
    volatile int decodes;

    Images(Context c, Listener listener) {
        dir = new File(c.getCacheDir(), "images");
        this.listener = listener;
        pool = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>() {
            @Override
            public boolean offer(Runnable r) { // newest first; drop the oldest waiting load
                boolean ok = offerFirst(r);
                while (size() > QUEUE_MAX) {
                    Runnable old = pollLast();
                    if (old instanceof Load) ui.post(() -> inFlight.remove(((Load) old).url));
                }
                return ok;
            }
        });
        pool.allowCoreThreadTimeOut(true);
    }

    /** The proxy URL for a source from the API, scaled to w x h px. Sources come URL-encoded already. */
    static String scaled(String source, int w, int h) {
        if (source == null || source.isEmpty()) return null;
        String enc = source;
        if (!source.substring(0, Math.min(16, source.length())).contains("%2F%2F")) {
            try {
                enc = URLEncoder.encode(source, "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }
        return PROXY + "?resize=" + w + "x" + h + "&shape=nocut&source=" + enc;
    }

    /** The bitmap for {@code url} if decoded, else null and a load starts (main thread only). */
    Bitmap get(String url) {
        if (url == null) return null;
        Bitmap b = memory.get(url);
        if (b != null) return b;
        Long f = failedAt.get(url);
        if (f != null && Clock.now() - f < FAIL_TTL_MS) return null;
        if (!inFlight.add(url)) return null;
        pool.execute(new Load(url));
        return null;
    }

    /** True when {@code url} failed in the last 10 minutes (its slot keeps the placeholder). */
    boolean failed(String url) {
        Long f = url == null ? null : failedAt.get(url);
        return f != null && Clock.now() - f < FAIL_TTL_MS;
    }

    /** Bytes of decoded bitmaps held now. */
    int bytes() {
        return memory.size();
    }

    int count() {
        return memory.snapshot().size();
    }

    /** The panel closed: drop every decoded bitmap (the views drop theirs too). Disk stays. */
    void releaseAll() {
        memory.evictAll();
    }

    /** Test hook: forget everything, memory, failures and disk. */
    void clear() {
        memory.evictAll();
        failedAt.clear();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private final class Load implements Runnable {
        final String url;

        Load(String url) {
            this.url = url;
        }

        @Override
        public void run() {
            Bitmap got = null;
            try {
                got = load(url);
            } catch (Exception e) {
                Log.w("Flinga", "image " + e);
            }
            Bitmap fin = got;
            ui.post(() -> {
                inFlight.remove(url);
                if (fin == null) failedAt.put(url, Clock.now());
                else {
                    failedAt.remove(url);
                    memory.put(url, fin);
                }
                if (listener != null) listener.onImage(url, fin);
            });
        }
    }

    /** Disk first, then the source; decodes RGB_565, sampled down if the server sent more than asked. */
    private Bitmap load(String url) throws IOException {
        File f = new File(dir, Integer.toHexString(url.hashCode()) + "-" + url.length() + ".img");
        byte[] bytes = null;
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < DISK_TTL_MS) bytes = Files.readAllBytes(f.toPath());
        boolean fresh = bytes == null;
        if (fresh) bytes = source.get(url);
        if (bytes == null || bytes.length == 0) return null;
        int[] want = size(url);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (want != null && o.outWidth / (sample * 2) >= want[0] && o.outHeight / (sample * 2) >= want[1]) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565; // photos are opaque: half the bytes of ARGB_8888
        d.inDither = false;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, d);
        decodes++;
        if (b != null && fresh) {
            try {
                dir.mkdirs();
                Files.write(f.toPath(), bytes);
                trimDisk();
            } catch (IOException e) {
                Log.w("Flinga", "image cache: " + e);
            }
        }
        return b;
    }

    /** "resize=960x540" of a proxy URL, or null. */
    static int[] size(String url) {
        int i = url.indexOf("resize=");
        if (i < 0) return null;
        int x = url.indexOf('x', i), amp = url.indexOf('&', i);
        try {
            return new int[] {Integer.parseInt(url.substring(i + 7, x)), Integer.parseInt(url.substring(x + 1, amp < 0 ? url.length() : amp))};
        } catch (Exception e) {
            return null;
        }
    }

    private void trimDisk() {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        if (total <= DISK_MAX_BYTES) return;
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified));
        for (File f : fs) {
            if (total <= DISK_MAX_BYTES * 3 / 4) break;
            total -= f.length();
            f.delete();
        }
    }

    private static byte[] network(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "image/jpeg");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_DOWNLOAD) throw new IOException("image too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
