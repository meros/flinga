package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The Serie detail is 50 % of the screen (capped): the rail is gone with the panel (clean room 2026-10-17). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class PanelWidthTest {
    @Test
    public void theDetailColumnIsHalfTheScreen() {
        Context c = RuntimeEnvironment.getApplication();
        int screen = c.getResources().getDisplayMetrics().widthPixels;
        assertEquals(Math.round(screen * 0.50f), Theme.panelWidth(c));
        assertTrue("the detail leaves the video half the screen", Theme.panelWidth(c) < screen / 2 + 1);
    }
}
