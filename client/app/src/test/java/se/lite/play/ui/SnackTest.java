package se.lite.play.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.text.Layout;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Y9: a one-line toast is centred in its two-line box (within 4 px), and the box never resizes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-television-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SnackTest {
    private static int[] textCentreAndHeight(Snack s, String text) {
        if (s.getParent() == null) {
            new android.widget.FrameLayout(s.getContext()).addView(s, Ui.snackParams(s.getContext()));
        }
        View root = (View) s.getParent();
        s.show(text, Snack.INFO, 0);
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Layout l = s.getLayout();
        // Where the text is drawn: the total padding includes the gravity's vertical offset.
        int top = s.getTotalPaddingTop() + l.getLineTop(0);
        int bottom = s.getTotalPaddingTop() + l.getLineBottom(l.getLineCount() - 1);
        return new int[] {(top + bottom) / 2, s.getHeight()};
    }

    @Test
    public void oneLineTextIsCentredInTheTwoLineBox() {
        Snack s = Ui.snack(RuntimeEnvironment.getApplication());
        int[] one = textCentreAndHeight(s, "Bildformat: Hela bilden");
        assertTrue("one line: centre " + one[0] + " vs box " + one[1] / 2, Math.abs(one[0] - one[1] / 2) <= 4);
        int[] two = textCentreAndHeight(s, "Kunde inte spela upp: licensen nekades av tjänsten, försök igen om en stund");
        assertEquals("the box never resizes (E6)", one[1], two[1]);
        assertTrue("two lines: centre " + two[0] + " vs box " + two[1] / 2, Math.abs(two[0] - two[1] / 2) <= 4);
    }
}
