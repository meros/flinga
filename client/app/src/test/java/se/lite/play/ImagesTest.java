package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The F16 image budget, measured on decoded bitmaps (DESIGN.md, "Image budget"): recorded JPEGs from
 * Telia's image proxy decode as RGB_565 at exactly the requested size, the decoded bytes never exceed
 * {@link Images#CAP_BYTES} however many images are requested, releasing empties the cache, and a failed
 * image is not requested again within 10 minutes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImagesTest {
    private final File dir = new File(Fixtures.DIR, "images");
    private final AtomicInteger requests = new AtomicInteger();
    private final Map<String, Bitmap> arrived = new HashMap<>();
    private final List<String> failed = new ArrayList<>();
    private Images images;
    private byte[] hero, thumb;

    @Before
    public void setUp() throws Exception {
        hero = Files.readAllBytes(new File(dir, "s-593463056c211ce4-960x540.jpg").toPath());
        thumb = Files.readAllBytes(new File(dir, "m-a2d5d1b2f2037f23-176x100.jpg").toPath());
        Images.source = url -> {
            requests.incrementAndGet();
            if (url.contains("fail")) throw new IOException("HTTP 404");
            return url.contains("resize=960x540") ? hero : thumb;
        };
        images = new Images(RuntimeEnvironment.getApplication(), (url, bmp) -> {
            if (bmp == null) failed.add(url);
            else arrived.put(url, bmp);
        });
        images.clear();
    }

    @After
    public void tearDown() {
        images.clear();
    }

    /** Waits until {@code n} images arrived or failed. */
    private void settle(int n) throws InterruptedException {
        for (int i = 0; i < 400 && arrived.size() + failed.size() < n; i++) {
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idle();
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void heroAndThumbnailsDecodeAsRgb565AtTheirSlotSize() throws Exception {
        String h = Images.scaled("https%3A%2F%2Fexample%2Fhero", 960, 540);
        String t = Images.scaled("https%3A%2F%2Fexample%2Fthumb", 176, 100);
        assertNull(images.get(h)); // never blocks: null now, the image later
        assertNull(images.get(t));
        settle(2);
        Bitmap hb = images.get(h), tb = images.get(t);
        assertEquals(Bitmap.Config.RGB_565, hb.getConfig());
        assertEquals(960, hb.getWidth());
        assertEquals(540, hb.getHeight());
        assertEquals(960 * 540 * 2, hb.getAllocationByteCount());
        assertEquals(Bitmap.Config.RGB_565, tb.getConfig());
        assertEquals(176 * 100 * 2, tb.getAllocationByteCount());
        System.out.println("CHECK image bytes: hero " + hb.getAllocationByteCount() + " B, thumbnail "
                + tb.getAllocationByteCount() + " B");
    }

    @Test
    public void decodedBytesStayUnderTheCap() throws Exception {
        int n = 0;
        for (int i = 0; i < 4; i++, n++) images.get(Images.scaled("https%3A%2F%2Fexample%2Fhero" + i, 960, 540));
        settle(n);
        for (int i = 0; i < 150; i++, n++) {
            images.get(Images.scaled("https%3A%2F%2Fexample%2Fthumb" + i, 176, 100));
            if (i % 20 == 19) settle(n + 1); // the queue keeps the newest 24 waiting; let them land
        }
        settle(n);
        int bytes = images.bytes();
        System.out.println("CHECK image cache: " + images.count() + " bitmaps, " + bytes + " B of "
                + Images.CAP_BYTES + " B cap, " + images.decodes + " decodes");
        assertTrue("decoded " + bytes + " B > cap " + Images.CAP_BYTES, bytes <= Images.CAP_BYTES);
        assertTrue("the cache holds images", bytes > Images.CAP_BYTES / 2);
        images.releaseAll();
        assertEquals(0, images.bytes());
    }

    @Test
    public void aFailedImageIsNotRequestedAgain() throws Exception {
        String f = Images.scaled("https%3A%2F%2Fexample%2Ffail", 176, 100);
        images.get(f);
        settle(1);
        assertEquals(1, failed.size());
        assertTrue(images.failed(f));
        for (int i = 0; i < 5; i++) assertNull(images.get(f));
        Thread.sleep(50);
        assertEquals(1, requests.get());
    }

    @Test
    public void scaledUrlIsTheOfficialAppsProxyUrl() {
        assertEquals("https://director-image.streaming.telia.com/convert/?resize=176x100&shape=nocut&source="
                + "https%3A%2F%2Fx%2Fy.jpg", Images.scaled("https%3A%2F%2Fx%2Fy.jpg", 176, 100));
        assertEquals("https://director-image.streaming.telia.com/convert/?resize=192x108&shape=nocut&source="
                + "https%3A%2F%2Fx%2Fy.jpg", Images.scaled("https://x/y.jpg", 192, 108));
        assertNull(Images.scaled(null, 1, 1));
    }
}
