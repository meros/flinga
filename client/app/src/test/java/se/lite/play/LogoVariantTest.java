package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import se.lite.play.ui.RowView;

/**
 * Owner rule F13: "use correct light/dark version of the logo depending on context". The focused row is
 * a light card (light-background artwork), an unfocused row is dark (dark-background artwork); a missing
 * variant falls back to the other; the logo slot does not move between the states (E6).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LogoVariantTest {

    private static Api.Channel channel() {
        Api.Channel c = new Api.Channel();
        c.id = "1";
        c.name = "SVT1";
        c.number = 1;
        c.logoUrl = "https://images.ctfassets.net/x/svt1_for_dark_bg.png";
        c.logoLightUrl = "https://images.ctfassets.net/x/svt1_for_light_bg.png";
        return c;
    }

    @Test
    public void focusedRowRequestsAndDrawsTheLightArtwork() throws Exception {
        List<String> requested = new CopyOnWriteArrayList<>();
        Logos.source = (id, light, url) -> {
            requested.add((light ? "light " : "dark ") + url);
            return java.nio.file.Files.readAllBytes(
                    new java.io.File(new java.io.File(Fixtures.DIR, light ? "logos-light" : "logos"), "1.png").toPath());
        };
        org.robolectric.shadows.ShadowLog.stream = System.out;
        Logos logos = new Logos(RuntimeEnvironment.getApplication(), null);
        logos.clear();
        Api.Channel c = channel();
        // What the channel row binder does: both variants, so the focus swap is instant.
        logos.get(c, false);
        logos.get(c, true);
        for (int i = 0; i < 1000 && (logos.get(c, true) == null || logos.get(c, false) == null); i++) {
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertTrue("light URL requested: " + requested,
                requested.contains("light " + Logos.scaled(c.logoLightUrl)));
        assertTrue("dark URL requested: " + requested,
                requested.contains("dark " + Logos.scaled(c.logoUrl)));

        RowView row = new RowView(RuntimeEnvironment.getApplication());
        row.text("Fråga Lund", "21:00  Vår älskade låtskatt").tall(true)
                .logo(logos.get(c, false), logos.get(c, true), "1 SVT1");
        row.setSelected(true);
        RectF focused = draw(row);
        assertEquals("light", row.logoDrawn());
        row.setSelected(false);
        RectF rest = draw(row);
        assertEquals("dark", row.logoDrawn());
        assertEquals("the slot does not move", focused, rest);
    }

    /** The logo PNGs have alpha (fit=pad padding is transparent): decoding must keep it (ARGB_8888). */
    @Test
    public void decodedLogosKeepAlpha() throws Exception {
        Logos.source = (id, light, url) -> java.nio.file.Files.readAllBytes(
                new java.io.File(new java.io.File(Fixtures.DIR, light ? "logos-light" : "logos"), "1.png").toPath());
        Logos logos = new Logos(RuntimeEnvironment.getApplication(), null);
        logos.clear();
        Api.Channel c = channel();
        for (boolean light : new boolean[] {false, true}) {
            Bitmap b = null;
            for (int i = 0; i < 1000 && b == null; i++) {
                b = logos.get(c, light);
                Thread.sleep(10);
                shadowOf(Looper.getMainLooper()).idle();
            }
            assertTrue("decoded " + (light ? "light" : "dark"), b != null);
            assertEquals(Bitmap.Config.ARGB_8888, b.getConfig());
            int min = 255;
            for (int y = 0; y < b.getHeight(); y++) {
                for (int x = 0; x < b.getWidth(); x++) min = Math.min(min, android.graphics.Color.alpha(b.getPixel(x, y)));
            }
            assertTrue("keeps transparent pixels", b.hasAlpha() && min == 0);
        }
    }

    /**
     * Optical normalisation: svt1 (wordmark), TV4 and Kanal 5 (round) trimmed and fitted into the row slot
     * are centred on it and have the same visible size, sqrt(area) within 10 % of each other.
     */
    @Test
    public void logosAreTrimmedAndBalanced() throws Exception {
        RectF slot = new RectF(32, 20, 32 + 160, 20 + 90); // the 80 x 45 dp row slot at xhdpi
        double[] sizes = new double[3];
        String[] ids = {"1", "4", "5"};
        for (int i = 0; i < ids.length; i++) {
            byte[] bytes = java.nio.file.Files.readAllBytes(
                    new java.io.File(new java.io.File(Fixtures.DIR, "logos"), ids[i] + ".png").toPath());
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = Logos.trim(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o));
            RectF out = new RectF();
            se.lite.play.ui.LogoFit.fit(b, slot, se.lite.play.ui.LogoFit.CENTER, out);
            assertEquals("centred x " + ids[i], slot.centerX(), out.centerX(), 0.5f);
            assertEquals("centred y " + ids[i], slot.centerY(), out.centerY(), 0.5f);
            assertTrue("inside the slot " + ids[i], slot.contains(out));
            sizes[i] = Math.sqrt(out.width() * out.height());
        }
        double mean = (sizes[0] + sizes[1] + sizes[2]) / 3;
        for (int i = 0; i < 3; i++) {
            assertEquals("visible size of " + ids[i] + " " + java.util.Arrays.toString(sizes), mean, sizes[i], mean * 0.10);
        }
    }

    @Test
    public void missingVariantFallsBackToTheOther() {
        Bitmap dark = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        RowView row = new RowView(RuntimeEnvironment.getApplication());
        row.text("Spårlöst försvunnen", null).logo(dark, null, "2 SVT2");
        row.setSelected(true);
        draw(row);
        assertEquals("dark-silhouette", row.logoDrawn());
        row.logo(null, null, "2 SVT2");
        draw(row);
        assertEquals("name", row.logoDrawn());
    }

    private static RectF draw(RowView row) {
        row.measure(View.MeasureSpec.makeMeasureSpec(656, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        row.draw(new Canvas(Bitmap.createBitmap(656, row.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        return row.debugRects().get("logo");
    }
}
