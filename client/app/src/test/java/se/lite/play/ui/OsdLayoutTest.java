package se.lite.play.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Map;

/**
 * REQUIREMENTS E6: no OSD element moves or resizes when its text changes. Renders each mode with short
 * and long texts at the same time fractions and compares the element rects.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OsdLayoutTest {
    private static final long H = 3600_000L;

    private static Map<String, RectF> render(OsdView v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        Bitmap b = Bitmap.createBitmap(1920, Math.max(1, v.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        Map<String, RectF> r = v.debugRects();
        r.put("view", new RectF(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight()));
        return r;
    }

    private static void assertSame(Map<String, RectF> a, Map<String, RectF> b, String... keys) {
        for (String k : keys) {
            assertNotNull(k + " drawn", a.get(k));
            assertEquals(k, a.get(k), b.get(k));
        }
    }

    @Test
    public void timelineIgnoresTextLength() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        // Same fractions (live at 80 %, playhead at 40 %); behind 0:04 ("Direkt") vs 4:00:00.
        v.showTimeline("SVT1", "Fråga Lund", 0, 10_000, 8_000, 4_000);
        Map<String, RectF> shortText = render(v);
        v.showTimeline("EN KANAL MED ETT MYCKET LÅNGT NAMN SOM INTE FÅR PLATS",
                "Hockeykväll SHL:s bästa förstakedja med en mycket lång titel som inte får plats",
                0, 10 * H, 8 * H, 4 * H);
        Map<String, RectF> longText = render(v);
        assertSame(shortText, longText, "view", "eyebrow", "title", "labelStart", "labelEnd", "labelHead", "bar", "live",
                "liveLabel", "playhead", "hints");
    }

    @Test
    public void seekAndTimelineShareOneLayout() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        v.showSeek("AVSNITT · TV4 PLAY", "Spin City", 5_000, 10_000); // 0:05 of 0:10
        Map<String, RectF> shortText = render(v);
        v.showSeek("AVSNITT · TV4 PLAY", "Ett avsnitt med en mycket lång titel som måste kortas av i sin plats",
                17_999_500, 35_999_000);
        Map<String, RectF> longText = render(v); // 4:59:59 of 9:59:59
        assertSame(shortText, longText, "view", "eyebrow", "title", "labelStart", "labelEnd", "labelHead", "bar", "playhead", "hints");
        // The timeline has the same frame too, so overlay -> bar never jumps.
        v.showTimeline("SVT1", "Fråga Lund", 0, 10_000, 8_000, 4_000);
        assertSame(shortText, render(v), "view", "eyebrow", "title", "labelStart", "labelEnd", "bar", "hints");
    }

    /** A1: the bar holds the numbers: the programme's start and end at its ends, the picture's time under the playhead, elapsed and total on demand. */
    @Test
    public void theBarHoldsTheNumbers() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        java.text.SimpleDateFormat hhmm = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("sv-SE"));
        long now = se.lite.play.Clock.now();
        long start = now - 20 * 60_000, end = now + 40 * 60_000;
        v.showChannel(1, "SVT1", "Rapport", start, end);
        render(v);
        assertArrayEquals("live: the programme's times, the time now under the tick",
                new String[] {hhmm.format(new java.util.Date(start)), hhmm.format(new java.util.Date(end)), hhmm.format(new java.util.Date(now))},
                v.shownBarLabels());
        v.showTimeline(1, "SVT1", "Rapport", start, end, now - 10_000, now - 12 * 60_000);
        render(v);
        assertArrayEquals("behind live: the picture's own time, not the offset",
                new String[] {hhmm.format(new java.util.Date(start)), hhmm.format(new java.util.Date(end)),
                        hhmm.format(new java.util.Date(now - 12 * 60_000))}, v.shownBarLabels());
        assertNotNull("the live tick is on the bar", render(v).get("live"));
        v.showTimeline(1, "SVT1", "Rapport", start - 3 * H, start - 2 * H, now, start - 3 * H + 5 * 60_000);
        assertNull("a catch-up of an ended programme has no live tick", render(v).get("live"));
        v.showSeek("AVSNITT · TV4 PLAY · STORAGE WARS", "A Plane Flew", 42_000, 90 * 60_000);
        render(v);
        assertArrayEquals("on demand: 0:00, the total, the elapsed time",
                new String[] {"0:00", "1:30:00", "0:42"}, v.shownBarLabels());
        v.showChannel(12, "TV3", null, 0, 0);
        render(v);
        assertArrayEquals("no guide: no numbers", new String[] {null, null, null}, v.shownBarLabels());
    }

    /** A2: the same frame passive and interactive: the pills slot exists in both, only the pills differ. */
    @Test
    public void passiveAndInteractiveShareTheFrame() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(new String[] {"Paus", "Från början 20:00"}, 0);
        Map<String, RectF> unfocused = render(v);
        assertNotNull("clean room: the strip draws its pills with no key pressed", unfocused.get("action0"));
        assertEquals("no \"OK Visa knappar\" on the unified strip", Theme.BACK + " Dölj", String.join(" ", v.shownHints()));
        v.focusRow(OsdView.FOCUS_ACTIONS);
        Map<String, RectF> interactive = render(v);
        assertNotNull(interactive.get("action0"));
        assertSame(unfocused, interactive, "view", "eyebrow", "title", "labelStart", "labelEnd", "bar", "hints", "action0", "action1");
    }

    @Test
    public void channelIgnoresTextLengthMissingGuideAndStatus() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        Map<String, RectF> shortText = render(v);
        v.showChannel(888, "Kanal med ett mycket långt namn som inte får plats", "En lång programtitel som inte får plats på raden",
                now - 3 * H, now + 3 * H);
        Map<String, RectF> longText = render(v);
        assertSame(shortText, longText, "view", "logo", "eyebrow", "title", "bar", "labelStart", "labelEnd", "hints");
        v.showChannel(12, "TV3", null, 0, 0); // guide not loaded: the lines stay reserved
        assertSame(shortText, render(v), "view", "logo", "eyebrow", "title", "labelStart", "labelEnd", "hints");
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.status("Kanalen gick inte att starta.", true); // the status takes the bar's line
        Map<String, RectF> failed = render(v);
        assertSame(shortText, failed, "view", "logo", "eyebrow", "title", "hints");
        assertEquals(shortText.get("labelStart").top, failed.get("status").top, 0.5f);
        v.setLogo(Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888));
        assertSame(shortText, render(v), "view", "logo", "title", "hints");
    }

    /**
     * U11/U0: one anatomy for every playback kind. A live banner, a channel behind live, a catch-up of an
     * ended programme and an episode with its span draw eyebrow, title, the bar with its numbers and the
     * pills at the very same rects, passive or not, so switching kind never moves a pixel.
     */
    @Test
    public void everyPlaybackKindDrawsOneFrame() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        long now = se.lite.play.Clock.now();
        String[] pills = {"Paus", "Från början 20:00", "Direkt"};
        Map<String, Map<String, RectF>> kinds = new java.util.LinkedHashMap<>();
        v.showChannel(1, "SVT1", "Rapport", now - 10 * 60_000, now + 10 * 60_000);
        v.actions(pills, 0).focusRow(OsdView.FOCUS_ACTIONS);
        kinds.put("live", render(v));
        v.showTimeline(1, "SVT1", "Fråga Lund", now - 20 * 60_000, now + 40 * 60_000, now - 10_000, now - 12 * 60_000);
        v.actions(pills, 0).focusRow(OsdView.FOCUS_ACTIONS);
        kinds.put("delayed", render(v));
        v.showTimeline(3, "TV3", "Spin City", now - 5 * H, now - 5 * H + 30 * 60_000, now, now - 5 * H + 12 * 60_000);
        v.actions(pills, 0).focusRow(OsdView.FOCUS_ACTIONS);
        kinds.put("catch-up", render(v));
        v.showSeek("AVSNITT · TV4 PLAY · STORAGE WARS", "A Plane Flew Over The Cuckoos Nest", 5_000, 22 * 60_000);
        v.actions(pills, 0).focusRow(OsdView.FOCUS_ACTIONS);
        kinds.put("vod", render(v));
        Map<String, RectF> ref = kinds.get("live");
        for (Map.Entry<String, Map<String, RectF>> e : kinds.entrySet()) {
            for (String k : new String[] {"view", "eyebrow", "title", "bar", "labelStart", "labelEnd", "hints", "action0", "action2"}) {
                assertNotNull(e.getKey() + " draws " + k, e.getValue().get(k));
                assertEquals(e.getKey() + " " + k, ref.get(k), e.getValue().get(k));
            }
        }
        // Focus moves nothing either.
        v.showTimeline(1, "SVT1", "Fråga Lund", now - 20 * 60_000, now + 40 * 60_000, now - 10_000, now - 12 * 60_000);
        v.actions(pills, 2);
        v.focusRow(OsdView.FOCUS_ACTIONS);
        Map<String, RectF> onPills = render(v);
        v.focusRow(OsdView.FOCUS_BAR);
        Map<String, RectF> onBar = render(v);
        for (String k : new String[] {"view", "eyebrow", "title", "bar", "hints", "action0", "action2"}) {
            assertEquals(k, ref.get(k), onPills.get(k));
        }
        for (String k : new String[] {"view", "eyebrow", "title", "bar", "hints", "action0"}) assertEquals(k, ref.get(k), onBar.get(k));
    }

    @Test
    public void textModeIsOnlyForFatalErrors() {
        OsdView v = new OsdView(RuntimeEnvironment.getApplication());
        v.showText("Det gick inte att starta.\nOK Försök igen");
        assertNotNull(render(v).get("body"));
        assertEquals("text", v.modeName());
        v.showSeek("AVSNITT · TV4 PLAY", "Spin City", 0, 0);
        assertEquals("a title's overlay is the frame, never text", "seek", v.modeName());
    }
}
