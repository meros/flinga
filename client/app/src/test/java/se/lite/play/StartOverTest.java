package se.lite.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * R4: one predicate per capability. {@link MainActivity#hasStartOver} drives the banner hint, the
 * control pill, the Tablå row, the Alternativ row and ← on live; they read one source, the programme on
 * air ({@code onAirNow}: the "current" answer, else the same programme out of the channel's guide, X3).
 * This test checks the data for every channel the tours visit (TOUR, LOGOS, STATES and the walkthrough
 * use channels 1 to 5): the guide names a programme on air, and where a "current" fixture exists it
 * answers the predicate alike. The harness check R4 ({@code Screens.checkReviewR2}) then holds the
 * five call sites to that answer on every shot.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StartOverTest {
    /** The channels the tours open Tablå, Alternativ or the banner for. */
    private static final String[] TOUR_CHANNELS = {"1", "2", "3", "4", "5"};

    private static Api api() throws Exception {
        Fixtures.install();
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("api", Context.MODE_PRIVATE).edit().clear()
                .putString("deviceId", "d-fixture").putString("accessToken", "fixture").putString("refreshToken", "fixture").commit();
        return new Api(app);
    }

    @Test
    public void everyTourChannelResolvesItsProgrammeOnAirFromTheGuide() throws Exception {
        Api api = api();
        List<String> withStartOver = new ArrayList<>();
        int covered = 0;
        for (Api.Channel ch : api.loadChannels()) {
            if (!java.util.Arrays.asList(TOUR_CHANNELS).contains(ch.id)) continue;
            List<Api.Program> guide = api.guide(ch); // every tour channel has a guide fixture
            Api.Program air = MainActivity.onAirIn(guide, Clock.now());
            assertNotNull("a programme on air in the guide of " + ch.name, air);
            if (MainActivity.hasStartOver(air)) withStartOver.add(ch.name);
            Api.Program now;
            try {
                now = api.current(ch);
            } catch (Exception e) {
                now = null; // no "current" fixture: the guide is the one source (X3)
            }
            if (now != null) {
                assertEquals(ch.name + ": current vs guide", air.id, now.id);
                assertEquals("start-over for " + air.title + " on " + ch.name + ": current vs guide",
                        MainActivity.hasStartOver(now), MainActivity.hasStartOver(air));
            }
            covered++;
        }
        assertEquals(TOUR_CHANNELS.length, covered);
        // F18's tours: SVT1 can be rewound into (Paus leads its row); the channels past the five recorded ones have no
        // guide fixture at all, so ↑ from SVT1 reaches a channel with the 61 s buffer only (no Paus pill).
        assertTrue("SVT1 offers start-over: " + withStartOver, withStartOver.contains("SVT1"));
        assertTrue("TV3's programme on air offers start-over in its guide (the LOGOS tour relies on it): " + withStartOver,
                withStartOver.contains("TV3"));
    }

    @Test
    public void predicate() {
        assertTrue(!MainActivity.hasStartOver(null));
        Api.Program p = new Api.Program();
        assertTrue(!MainActivity.hasStartOver(p));
        p.startOver = new Api.Playable();
        assertTrue(!MainActivity.hasStartOver(p)); // a start-over without a video is no start-over
        p.startOver.videoId = "v1";
        assertTrue(MainActivity.hasStartOver(p));
    }
}
